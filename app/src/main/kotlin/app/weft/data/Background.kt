package app.weft.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.weft.MainActivity
import app.weft.R
import app.weft.WeftService
import app.weft.core.ChatCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Receiving with Weft in the background (BRIEF, Phase 3: a persistent connection, no push service).
 * While a profile is open and "Receive messages while locked" is on, a foreground service keeps the
 * process — and so the core's connections — alive, with the fixed "Weft is running" notice.
 * New messages raise a notice that says only "New message": no name, no text.
 */
@OptIn(FlowPreview::class)
object Background {
    private const val CH_RUNNING = "running"
    private const val CH_MESSAGES = "messages"
    const val ID_RUNNING = 1
    private const val ID_MESSAGE = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Context
    private fun prefs(c: Context) = c.getSharedPreferences("weft.guard", Context.MODE_PRIVATE)

    /** "Receive messages while locked" (BRIEF: on by default). */
    fun listen(c: Context) = prefs(c).getBoolean("listen", true)
    private val _listening = MutableStateFlow(true)
    val listening: StateFlow<Boolean> = _listening

    fun init(context: Context) {
        app = context.applicationContext
        _listening.value = listen(app)
        channels()
        // Settled for a second before the service starts or stops: unlocking from a screen lock
        // closes and reopens the profile within milliseconds, and stopping a service that has not
        // yet reached startForeground() makes Android end the app.
        scope.launch {
            combine(WeftSession.userId, _listening) { uid, on -> uid != null && on }
                .debounce(1_000)
                .distinctUntilChanged()
                .collect(::run)
        }
        scope.launch { WeftSession.screenLocked.collect { if (!it && AutoLock.isForeground) clearMessages() } }
        scope.launch {
            ChatCore.events.collect { event ->
                val r = event.optJSONObject("result") ?: return@collect
                if (r.optString("type") == "newChatItems" && received(r) && !(AutoLock.isForeground && WeftSession.isUnlocked)) newMessage()
            }
        }
    }

    fun setListen(on: Boolean) {
        prefs(app).edit().putBoolean("listen", on).apply()
        _listening.value = on
    }

    /** After a wipe: notices gone, service stopped, back to the default. */
    fun clear() {
        _listening.value = true
        clearMessages()
    }

    /** The user came back to an unlocked Weft: the "New message" notice has done its job. */
    fun clearMessages() = manager().cancel(ID_MESSAGE)

    private fun run(on: Boolean) {
        val intent = Intent(app, WeftService::class.java)
        if (on) runCatching { app.startForegroundService(intent) } else app.stopService(intent)
    }

    private fun received(r: org.json.JSONObject): Boolean {
        val items = r.optJSONArray("chatItems") ?: return false
        return (0 until items.length()).any { i ->
            items.getJSONObject(i).optJSONObject("chatItem")?.optJSONObject("chatDir")?.optString("type")?.endsWith("Rcv") == true
        }
    }

    private fun newMessage() {
        manager().notify(
            ID_MESSAGE,
            builder(CH_MESSAGES)
                .setContentTitle("Weft")
                .setContentText(app.getString(R.string.notice_new_message))
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC) // it says nothing anyway
                .build(),
        )
    }

    /** The fixed notice of [WeftService]. */
    fun runningNotice(c: Context): Notification =
        builder(CH_RUNNING, c)
            .setContentTitle(c.getString(R.string.notice_running))
            .setContentText(c.getString(R.string.notice_running_detail))
            .setOngoing(true)
            .build()

    private fun builder(channel: String, c: Context = app): Notification.Builder =
        Notification.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_notice)
            .setShowWhen(false)
            .setContentIntent(
                PendingIntent.getActivity(
                    c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )

    private fun manager() = app.getSystemService(NotificationManager::class.java)

    private fun channels() {
        manager().createNotificationChannels(
            listOf(
                NotificationChannel(CH_RUNNING, app.getString(R.string.notice_channel_running), NotificationManager.IMPORTANCE_MIN)
                    .apply { setShowBadge(false) },
                NotificationChannel(CH_MESSAGES, app.getString(R.string.notice_channel_messages), NotificationManager.IMPORTANCE_HIGH)
                    .apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC },
            ),
        )
    }
}
