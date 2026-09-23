package app.weft.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Locks the app (core closed, database key dropped) when the user leaves it: at once, or after a
 * time chosen in Security (default 1 min). The wait runs while the app is in the background; if
 * Android froze the process meanwhile, the elapsed time is checked when the user comes back.
 */
object AutoLock {
    /** Options in seconds; 0 is "as soon as I leave". */
    val OPTIONS = listOf(0, 30, 60, 300, 900)
    const val DEFAULT = 60

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pending: Job? = null
    private var leftAt = 0L
    private var started = 0
    private lateinit var app: Application

    private fun prefs(c: Context) = c.getSharedPreferences("weft.guard", Context.MODE_PRIVATE)
    fun seconds(c: Context) = prefs(c).getInt("autolock", DEFAULT)
    fun setSeconds(c: Context, seconds: Int) = prefs(c).edit().putInt("autolock", seconds).apply()

    fun install(application: Application) {
        app = application
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { if (started++ == 0) cameBack() }
            override fun onActivityStopped(activity: Activity) {
                // A rotation stops and restarts the activity: that is not leaving the app.
                if (--started == 0 && !activity.isChangingConfigurations) wentAway()
            }
            override fun onActivityCreated(a: Activity, s: Bundle?) = Unit
            override fun onActivityResumed(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, s: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
    }

    private fun wentAway() {
        if (WeftSession.userId.value == null) return
        leftAt = SystemClock.elapsedRealtime()
        val wait = seconds(app)
        pending?.cancel()
        pending = scope.launch {
            delay(wait * 1000L)
            WeftSession.lock()
        }
    }

    private fun cameBack() {
        pending?.cancel()
        pending = null
        if (WeftSession.userId.value != null && SystemClock.elapsedRealtime() - leftAt >= seconds(app) * 1000L) {
            scope.launch { WeftSession.lock() }
        }
    }
}
