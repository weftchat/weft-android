package app.weft.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Result of opening the encrypted store. */
sealed interface OpenResult {
    data object Ok : OpenResult
    /** The key does not open this database: a wrong PIN. */
    data object WrongKey : OpenResult
    data class Failed(val detail: String) : OpenResult
}

/**
 * The running SimpleX core: one per process. [open] loads the native libraries and opens (or
 * creates) the encrypted store; [send] runs one command at a time off the main thread; [events]
 * carries what the core reports on its own (new messages, a contact connecting, …).
 * Everything crosses the boundary as JSON.
 */
object ChatCore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @OptIn(ExperimentalCoroutinesApi::class)
    private val commands = Dispatchers.IO.limitedParallelism(1)
    private val lock = Mutex()
    private var ctrl: Long = 0
    private var receiver: Job? = null

    private val _events = MutableSharedFlow<JSONObject>(extraBufferCapacity = 256)
    val events: SharedFlow<JSONObject> = _events

    val isOpen: Boolean get() = ctrl != 0L

    /** Opens the store at [dir] with [key], creating it the first time. Safe to call again after a wrong key. */
    suspend fun open(dir: File, key: String): OpenResult = withContext(commands) {
        lock.withLock {
            if (ctrl != 0L) return@withLock OpenResult.Ok
            NativeCore.load()
            dir.mkdirs()
            val r = chatMigrateInit(File(dir, DB_PREFIX).absolutePath, key, "yesUp")
            val result = JSONObject(r[0] as String)
            when (result.optString("type")) {
                "ok" -> {
                    ctrl = r[1] as Long
                    startReceiving()
                    OpenResult.Ok
                }
                "errorNotADatabase" -> OpenResult.WrongKey
                else -> OpenResult.Failed(result.toString())
            }
        }
    }

    /** Sends one command and returns the core's JSON reply. */
    suspend fun send(cmd: String): JSONObject = withContext(commands) {
        check(ctrl != 0L) { "core is not open" }
        JSONObject(chatSendCmdRetry(ctrl, cmd, 0))
    }

    /** Stops listening and closes the store. */
    suspend fun close() = withContext(commands) {
        lock.withLock {
            if (ctrl == 0L) return@withLock
            receiver?.cancel()
            receiver = null
            chatCloseStore(ctrl)
            ctrl = 0
        }
    }

    private fun startReceiving() {
        val c = ctrl
        receiver = scope.launch {
            while (isActive && ctrl == c) {
                val msg = chatRecvMsgWait(c, 500_000)
                if (msg.isNotEmpty()) runCatching { JSONObject(msg) }.onSuccess { _events.emit(it) }
            }
        }
    }

    /** Files are <dir>/simplex_v1_chat.db and <dir>/simplex_v1_agent.db, as in the official app. */
    private const val DB_PREFIX = "simplex_v1"
}
