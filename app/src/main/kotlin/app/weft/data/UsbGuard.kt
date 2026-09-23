package app.weft.data

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * USB guard (BRIEF, threat "forensic extraction over a cable"). When a USB DATA connection appears
 * while Weft is in the background or locked, the session locks at once — core stopped, store
 * closed, database key cleared — and the process is ended, so nothing of the key can stay in memory
 * (the core's own memory included); the PIN is asked next time. A charge-only cable is ignored.
 * With "Wipe on USB data" on (advanced, off by default) everything is wiped instead.
 *
 * Android reports the cable with the system's USB_STATE broadcast: "connected", plus one flag per
 * data function the host has switched on (file transfer, photos, debugging, tethering, MIDI…);
 * charge-only has none. Only the change from "no data" to "data" counts. This runs while Weft's
 * process is alive; when Android has ended it there is no key in memory to protect.
 */
object UsbGuard {
    private const val USB_STATE = "android.hardware.usb.action.USB_STATE"
    private val DATA_FUNCTIONS = listOf("mtp", "ptp", "rndis", "ncm", "midi", "adb", "accessory", "uvc", "audio_source")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Application
    private var dataConnected = false

    private fun prefs(c: Context) = c.getSharedPreferences("weft.guard", Context.MODE_PRIVATE)
    fun wipeOnUsb(c: Context) = prefs(c).getBoolean("usbWipe", false)
    fun setWipeOnUsb(c: Context, on: Boolean) = prefs(c).edit().putBoolean("usbWipe", on).apply()

    fun install(application: Application) {
        app = application
        val filter = IntentFilter().apply {
            addAction(USB_STATE)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val data = isData(intent)
                // The first USB_STATE is the state when Weft started (the user opened it): no change.
                if (isInitialStickyBroadcast) { dataConnected = data; return }
                val appeared = data && !dataConnected
                if (intent.action == USB_STATE) dataConnected = data
                if (appeared) onDataConnection()
            }
        }
        // USB_STATE and the attach broadcasts come only from the system.
        if (Build.VERSION.SDK_INT >= 33) application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else application.registerReceiver(receiver, filter)
    }

    private fun isData(i: Intent): Boolean = when (i.action) {
        USB_STATE -> i.getBooleanExtra("connected", false) && DATA_FUNCTIONS.any { i.getBooleanExtra(it, false) }
        else -> true // a USB device or accessory attached to the phone always carries data
    }

    /** A data connection appeared (or, in debug builds, was simulated). */
    fun onDataConnection() {
        val unlocked = WeftSession.userId.value != null
        // In use, unlocked, in the owner's hands: nothing to do.
        if (AutoLock.isForeground && unlocked) return
        scope.launch {
            if (wipeOnUsb(app)) WeftSession.wipeAll() else if (unlocked) WeftSession.lock()
            if (!AutoLock.isForeground) Process.killProcess(Process.myPid())
        }
    }
}
