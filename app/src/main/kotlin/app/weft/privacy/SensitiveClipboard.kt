package app.weft.privacy

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Everything Weft copies (BRIEF, confirmed by the owner): marked sensitive, so Android shows no
 * preview of it, and cleared after 60 s. It is cleared only if the clipboard still holds Weft's
 * copy; when Android does not let us look (the app is in the background), it is cleared anyway —
 * privacy first. If the process dies before then, Android's own clearing (1 h on Android 13+) applies.
 */
object SensitiveClipboard {
    private const val CLEAR_AFTER_MS = 60_000L
    private const val LABEL = "Weft"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pending: Job? = null

    fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(LABEL, text)
        clip.description.extras = PersistableBundle().apply {
            // ClipDescription.EXTRA_IS_SENSITIVE on Android 13+; the same key is honoured by some older keyboards.
            putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
        }
        clipboard.setPrimaryClip(clip)
        val stamp = clipboard.primaryClipDescription?.timestamp
        pending?.cancel()
        pending = scope.launch {
            delay(CLEAR_AFTER_MS)
            val now = clipboard.primaryClipDescription
            val ours = now == null || (now.label == LABEL && (stamp == null || now.timestamp == stamp))
            if (ours && Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
            else if (ours) clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
