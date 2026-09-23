package app.weft.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** "Wipe after 10 wrong PINs": off by default; when on, the tenth wrong PIN in a row wipes the phone. */
object WipePolicy {
    const val LIMIT = 10
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private fun prefs(c: Context) = c.getSharedPreferences("weft.guard", Context.MODE_PRIVATE)

    fun wipeOnLimit(c: Context) = prefs(c).getBoolean("wipe10", false)
    fun setWipeOnLimit(c: Context, on: Boolean) = prefs(c).edit().putBoolean("wipe10", on).apply()

    /** Called after a wrong PIN has been counted. */
    fun onWrongPin(c: Context) {
        if (wipeOnLimit(c) && WeftSession.throttle.failures >= LIMIT) scope.launch { WeftSession.wipeAll() }
    }
}
