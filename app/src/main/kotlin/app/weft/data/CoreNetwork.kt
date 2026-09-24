package app.weft.data

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

/**
 * How the core reaches relays (BRIEF, Phase 3). The core keeps this setting in memory only, so it is
 * applied every time the core starts, before `/_start`, and again when Tor is switched.
 *
 * - Private routing, always: a message goes to the contact's relay through another relay, so that
 *   relay never sees this phone's IP address. When forwarding fails the core may send directly
 *   only if the IP address is protected (Tor on); otherwise it keeps retrying.
 * - Tor through Orbot (BRIEF v1): every connection goes to Orbot's SOCKS port. With Tor on and
 *   Orbot not running nothing connects at all: Weft never falls back to a direct connection.
 */
object CoreNetwork {
    private const val ORBOT = "org.torproject.android"
    private const val SOCKS_HOST = "127.0.0.1"
    private const val SOCKS_PORT = 9050

    private lateinit var app: Context
    private fun prefs() = app.getSharedPreferences("weft.guard", Context.MODE_PRIVATE)

    private val _tor = MutableStateFlow(false)
    /** The user's choice; whether Orbot actually runs is [orbotRunning]. */
    val tor: StateFlow<Boolean> = _tor

    fun init(context: Context) {
        app = context.applicationContext
        _tor.value = prefs().getBoolean("tor", false)
    }

    /** After a wipe: back to the default (the preference itself is erased with the others). */
    fun clear() { _tor.value = false }

    fun orbotInstalled(): Boolean =
        runCatching { app.packageManager.getPackageInfo(ORBOT, 0); true }.getOrDefault(false)

    /** Orbot's SOCKS port answers on this phone (a local check: nothing leaves the phone). */
    suspend fun orbotRunning(): Boolean = withContext(Dispatchers.IO) {
        runCatching { Socket().use { it.connect(InetSocketAddress(SOCKS_HOST, SOCKS_PORT), 500) }; true }.getOrDefault(false)
    }

    /** Asks Orbot to start (its public "start" request); Orbot may show its own screen. */
    fun startOrbot() {
        app.sendBroadcast(
            Intent("org.torproject.android.intent.action.START")
                .setPackage(ORBOT)
                .putExtra("org.torproject.android.intent.extra.PACKAGE_NAME", app.packageName),
        )
    }

    /** Switches Tor on or off; with the core running, reconnects every relay the new way. */
    suspend fun setTor(on: Boolean) {
        prefs().edit().putBoolean("tor", on).apply()
        _tor.value = on
        if (on) startOrbot()
        if (WeftSession.userId.value != null) {
            apply()
            runCatching { WeftSession.cmd("/reconnect") }
        }
    }

    suspend fun apply() {
        val cfg = WeftSession.cmd("/network").getJSONObject("networkConfig")
            .put("smpProxyMode", "always")
            .put("smpProxyFallback", "allowProtected")
        if (_tor.value) {
            cfg.put("socksProxy", "$SOCKS_HOST:$SOCKS_PORT")
                .put("socksMode", "always")
                .put("hostMode", "onionViaSocks")
        } else {
            cfg.put("socksProxy", JSONObject.NULL)
        }
        WeftSession.cmd("/_network $cfg")
    }
}
