package app.weft.data

import app.weft.relay.PillState
import app.weft.relay.RotateResult
import app.weft.relay.Relays
import app.weft.ui.security.relayHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The user's relays as the Chats pill and "Rotate" see them, from [CoreRelays]. Only message
 * relays (smp://) count here: they hold the conversations; file relays are used per transfer.
 */
object LiveRelays : Relays {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val switching = MutableStateFlow(false)
    private val orbotUp = MutableStateFlow(true)

    private lateinit var app: Context
    private fun prefs() = app.getSharedPreferences("weft.guard", Context.MODE_PRIVATE)
    private val _autoSwitch = MutableStateFlow(true)
    /** "Switch when blocked" (design v3: on by default). */
    val autoSwitch: StateFlow<Boolean> = _autoSwitch
    private val _switchedOnItsOwn = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** A relay switch made by [watch], with the new relay's name (for a toast). */
    val switchedOnItsOwn: SharedFlow<String> = _switchedOnItsOwn

    fun init(context: Context) {
        app = context.applicationContext
        _autoSwitch.value = prefs().getBoolean("autoSwitch", true)
        scope.launch { watch() }
    }

    fun setAutoSwitch(on: Boolean) {
        prefs().edit().putBoolean("autoSwitch", on).apply()
        _autoSwitch.value = on
    }

    /** After a wipe: back to the default (the preference itself is erased with the others). */
    fun clear() { _autoSwitch.value = true }

    /**
     * While unlocked, once a minute: when the message relay in use has been blocked on two checks
     * in a row, every conversation moves to a standby relay that answers a test ([rotate]).
     */
    private suspend fun watch() {
        var strikes = 0
        while (true) {
            delay(60_000)
            if (WeftSession.userId.value == null || !_autoSwitch.value || switching.value) { strikes = 0; continue }
            runCatching { refresh() }
            val standby = standbyToTry(
                messageRelays(), CoreRelays.status.value,
                torWithoutOrbot = CoreNetwork.tor.value && !orbotUp.value,
            )
            strikes = if (standby != null) strikes + 1 else 0
            if (strikes < 2 || standby == null) continue
            strikes = 0
            val r = rotate()
            if (r is RotateResult.Switched) _switchedOnItsOwn.tryEmit(r.name)
        }
    }

    private fun messageRelays() = CoreRelays.servers.value.filter { it.startsWith("smp://") }

    /** The message relay in use: the one holding live queues, else the first that is not blocked. */
    private fun current(status: Map<String, CoreRelays.Status>): String? {
        val smp = messageRelays()
        return smp.firstOrNull { status[it]?.state == CoreRelays.State.InUse }
            ?: smp.firstOrNull { status[it]?.state != CoreRelays.State.Blocked }
    }

    override val pill: StateFlow<PillState> =
        combine(CoreRelays.servers, CoreRelays.status, switching, CoreNetwork.tor, orbotUp) { servers, status, busy, tor, orbot ->
            val smp = servers.filter { it.startsWith("smp://") }
            val route = if (tor) "via Tor" else "direct"
            when {
                busy -> PillState.Switching
                smp.isEmpty() -> PillState.NoRelay
                tor && !orbot -> PillState.OrbotOff
                else -> current(status)?.let { PillState.Live(relayHost(it), route, status[it]?.latencyMs) }
                    ?: PillState.Blocked(relayHost(smp.first()))
            }
        }.stateIn(scope, SharingStarted.Eagerly, PillState.NoRelay)

    /**
     * The standby message relay worth testing when none of [smp] is live: every relay that has been
     * used is blocked and one has not been found blocked. Null when there is nothing to do — a relay
     * still works, nothing is blocked yet, or Tor is on without Orbot (then every relay "fails").
     */
    internal fun standbyToTry(smp: List<String>, status: Map<String, CoreRelays.Status>, torWithoutOrbot: Boolean): String? {
        if (torWithoutOrbot || smp.size < 2) return null
        if (smp.any { status[it]?.state == CoreRelays.State.InUse }) return null
        if (smp.none { status[it]?.state == CoreRelays.State.Blocked }) return null
        return smp.firstOrNull { status[it]?.state != CoreRelays.State.Blocked }
    }

    override suspend fun refresh() {
        if (CoreRelays.servers.value.isEmpty()) runCatching { CoreRelays.load() }
        orbotUp.value = !CoreNetwork.tor.value || CoreNetwork.orbotRunning()
        CoreRelays.refresh()
    }

    /**
     * Every 1:1 conversation gets a new receiving queue on another working message relay (the
     * core's queue switch; contacts are not asked anything). The relay being left may not take new
     * queues meanwhile, so the core has to choose another; it keeps forwarding for private routing.
     */
    override suspend fun rotate(): RotateResult {
        refresh()
        val status = CoreRelays.status.value
        val smp = messageRelays()
        // Leave the relay holding the conversations: the live one, else the blocked one.
        val from = smp.firstOrNull { status[it]?.state == CoreRelays.State.InUse }
            ?: smp.firstOrNull { status[it]?.state == CoreRelays.State.Blocked }
            ?: smp.firstOrNull() ?: return RotateResult.NoStandby
        val candidates = smp.filter { it != from && status[it]?.state != CoreRelays.State.Blocked }
        if (candidates.isEmpty()) return RotateResult.NoStandby
        val uid = WeftSession.userId.value ?: return RotateResult.Failed
        switching.value = true
        // Only a relay that answers a test now: moving to one that does not would lose messages.
        val to = candidates.firstOrNull { CoreRelays.check(it) == null }
        if (to == null) {
            switching.value = false
            return RotateResult.NoStandby
        }
        val others = smp.filter { it != to }
        try {
            // Only [to] may take the new queues, so the core cannot pick another relay.
            others.forEach { CoreRelays.setStorage(it, false) }
            val chats = WeftSession.cmd("/_get chats $uid pcc=on").getJSONArray("chats")
            for (i in 0 until chats.length()) {
                val contact = chats.getJSONObject(i).optJSONObject("chatInfo")?.optJSONObject("contact") ?: continue
                runCatching { WeftSession.cmd("/_switch @" + contact.getLong("contactId")) }
            }
            return RotateResult.Switched(relayHost(to))
        } catch (_: Exception) {
            return RotateResult.Failed
        } finally {
            others.forEach { runCatching { CoreRelays.setStorage(it, true) } }
            switching.value = false
            refresh()
        }
    }
}
