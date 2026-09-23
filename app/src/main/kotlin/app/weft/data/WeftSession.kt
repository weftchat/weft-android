package app.weft.data

import android.content.Context
import app.weft.core.ChatCore
import app.weft.core.OpenResult
import app.weft.security.AttemptThrottle
import app.weft.security.DatabaseKey
import app.weft.security.DeviceIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A command the core refused; [error] is its `{"type":…}` error object. */
class CoreException(val error: JSONObject) : Exception(error.toString())

/**
 * This phone's Weft session: the encrypted store, the core and the signed-in user.
 * - [create]: first run, after "Choose a PIN" — makes the store with the PIN's key, the user and
 *   switches off SimpleX's preset servers (BRIEF: only the user's own relay).
 * - [unlock]: every later start — a wrong PIN simply does not open the store.
 * - [lock]: stops the core and closes the store.
 */
object WeftSession {
    private lateinit var app: Context
    private val keys by lazy { DatabaseKey(app) }
    val throttle by lazy { AttemptThrottle(app) }
    val device by lazy { DeviceIdentity(app) }

    private val dbDir get() = File(app.filesDir, "db")
    private val _userId = MutableStateFlow<Long?>(null)
    /** The open store's key, kept only while unlocked (the core holds it too), to change the PIN. */
    private var currentKey: String? = null
    /** The signed-in user, null while locked. */
    val userId: StateFlow<Long?> = _userId

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** True once an identity exists on this phone (the encrypted store is there). */
    val hasIdentity: Boolean get() = File(dbDir, "simplex_v1_chat.db").exists()

    /** Sends [cmd]; returns the `result` object or throws [CoreException] with the `error`. */
    suspend fun cmd(cmd: String): JSONObject {
        val reply = ChatCore.send(cmd)
        reply.optJSONObject("result")?.let { return it }
        throw CoreException(reply.optJSONObject("error") ?: reply)
    }

    /**
     * First run: creates the store with [pin]'s key, the user with [nickname] (blank: the core picks
     * a random name, which is all contacts see) and starts the core with presets switched off.
     */
    suspend fun create(pin: String, nickname: String) {
        val key = withContext(Dispatchers.Default) { keys.derive(pin.toCharArray()) }
        when (val r = ChatCore.open(dbDir, key)) {
            OpenResult.Ok -> currentKey = key
            else -> error("could not create the store: $r")
        }
        prepare()
        val profile = if (nickname.isBlank()) null else JSONObject().put("displayName", nickname.trim()).put("fullName", "")
        val user = cmd("/_create user " + JSONObject().put("profile", profile ?: JSONObject.NULL).put("pastTimestamp", false))
        disablePresetOperators()
        start(user.getJSONObject("user").getLong("userId"), removePresets = true)
        throttle.succeeded()
    }

    /**
     * A new user comes with a ready-made contact card for the SimpleX team, which would reach their
     * servers. Weft has no preset contacts (BRIEF): a brand-new user has no real contacts yet, so
     * every contact present right after creation is removed, without notifying anyone.
     */
    private suspend fun removePresetContacts(userId: Long) {
        val chats = runCatching { cmd("/_get chats $userId pcc=on").getJSONArray("chats") }.getOrNull() ?: return
        for (chat in chats.objects()) {
            val info = chat.getJSONObject("chatInfo")
            if (info.optString("type") == "direct") {
                val id = info.getJSONObject("contact").getLong("contactId")
                runCatching { cmd("/_delete @$id full notify=off") }
            }
        }
    }

    sealed interface Unlock {
        data object Ok : Unlock
        data object WrongPin : Unlock
        data class Failed(val detail: String) : Unlock
    }

    /** Opens the store with [pin], after any wait owed for earlier wrong PINs. */
    suspend fun unlock(pin: String): Unlock {
        delay(throttle.waitMs())
        val key = withContext(Dispatchers.Default) { keys.derive(pin.toCharArray()) }
        return when (val r = ChatCore.open(dbDir, key)) {
            OpenResult.WrongKey -> { throttle.failed(); Unlock.WrongPin }
            is OpenResult.Failed -> Unlock.Failed(r.detail)
            OpenResult.Ok -> {
                currentKey = key
                throttle.succeeded()
                prepare()
                val user = cmd("/u").getJSONObject("user")
                start(user.getLong("userId"))
                Unlock.Ok
            }
        }
    }

    /** Re-encrypts the store with [newPin]'s key. */
    suspend fun changePin(newPin: String) {
        val old = currentKey ?: error("locked")
        val uid = _userId.value ?: error("locked")
        val key = withContext(Dispatchers.Default) { keys.derive(newPin.toCharArray()) }
        cmd("/_stop")
        try {
            cmd("/_db encryption " + JSONObject().put("currentKey", old).put("newKey", key))
            currentKey = key
        } finally {
            cmd("/_start main=on")
            _userId.value = uid
        }
    }

    suspend fun lock() {
        currentKey = null
        _userId.value = null
        runCatching { cmd("/_stop") }
        ChatCore.close()
    }

    /** File folders (inside the app's private storage) and encrypted local files, before starting. */
    private suspend fun prepare() {
        val files = File(app.filesDir, "files").apply { mkdirs() }
        val temp = File(app.cacheDir, "temp").apply { mkdirs() }
        val assets = File(app.filesDir, "assets").apply { mkdirs() }
        cmd("/set file paths " + JSONObject()
            .put("appFilesFolder", files.absolutePath)
            .put("appTempFolder", temp.absolutePath)
            .put("appAssetsFolder", assets.absolutePath))
        cmd("/_files_encrypt on")
    }

    /** Starts the core; [removePresets] on first run, before anything shows the chat list. */
    private suspend fun start(userId: Long, removePresets: Boolean = false) {
        disablePresetOperators()
        cmd("/_start main=on")
        if (removePresets) removePresetContacts(userId)
        _userId.value = userId
    }

    /** Every server operator off: their preset servers leave the core's configuration entirely. */
    private suspend fun disablePresetOperators() {
        val ops = cmd("/_operators").getJSONObject("conditions").getJSONArray("serverOperators")
        var changed = false
        for (i in 0 until ops.length()) {
            val op = ops.getJSONObject(i)
            if (op.optBoolean("enabled")) { op.put("enabled", false); changed = true }
        }
        if (changed) cmd("/_operators " + ops)
    }

    internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
