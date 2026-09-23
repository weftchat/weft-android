package app.weft.security

import android.content.Context
import android.os.SystemClock

/**
 * Growing wait after wrong PINs (BRIEF): after n failures in a row the next attempt waits
 * 2^(n−1) s, up to 5 minutes. Kept across restarts; measured on the boot clock so changing the
 * phone's time does not skip it. A correct PIN resets it.
 */
class AttemptThrottle(context: Context) {
    private val prefs = context.getSharedPreferences("weft.security", Context.MODE_PRIVATE)

    val failures: Int get() = prefs.getInt(FAILS, 0)

    /** How long to wait before the next attempt may be checked, in ms (0 = now). */
    fun waitMs(): Long {
        val n = failures
        if (n == 0) return 0
        var last = prefs.getLong(LAST, 0)
        val now = SystemClock.elapsedRealtime()
        // After a reboot the boot clock starts again from 0: restart the full wait from now.
        if (last > now) {
            last = now
            prefs.edit().putLong(LAST, now).commit()
        }
        return (delayFor(n) - (now - last)).coerceAtLeast(0)
    }

    fun failed() {
        prefs.edit().putInt(FAILS, failures + 1).putLong(LAST, SystemClock.elapsedRealtime()).commit()
    }

    fun succeeded() {
        prefs.edit().remove(FAILS).remove(LAST).commit()
    }

    private fun delayFor(n: Int): Long = (1000L shl (n - 1).coerceAtMost(9)).coerceAtMost(MAX_MS)

    private companion object {
        const val FAILS = "pin.failures"
        const val LAST = "pin.lastFailure"
        const val MAX_MS = 5 * 60 * 1000L
    }
}
