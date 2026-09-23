package app.weft.data

import android.content.Context
import app.weft.core.ChatCore
import app.weft.core.OpenResult
import app.weft.security.AttemptThrottle
import app.weft.security.DeviceIdentity
import app.weft.security.Sealed
import app.weft.security.Slot
import app.weft.security.Vault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vault by lazy { Vault(app) }
    val throttle by lazy { AttemptThrottle(app) }
    val device by lazy { DeviceIdentity(app) }

    /** Profile folders have neutral names: nothing on disk says which is real and which is the decoy. */
    private val realDir get() = File(app.filesDir, "p0")
    private val decoyDir get() = File(app.filesDir, "p1")
    private val _userId = MutableStateFlow<Long?>(null)
    /** The signed-in user, null while locked. */
    val userId: StateFlow<Long?> = _userId
    private val _entry = MutableStateFlow<Slot?>(null)
    /** Which code opened this session (null while locked). Only the session itself may know. */
    val entry: StateFlow<Slot?> = _entry
    /** True in the decoy profile (opened by the duress or the panic code). */
    val isDecoy: Boolean get() = _entry.value.let { it != null && it != Slot.Real }
    /** The open profile's database key, kept only while unlocked. */
    private var keyBytes: ByteArray? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** True once an identity exists on this phone. */
    val hasIdentity: Boolean get() = vault.exists

    private fun dirFor(slot: Slot) = if (slot == Slot.Real) realDir else decoyDir
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    /** The real profile's sealed notes (whether a duress PIN exists, the decoy's key); real session only. */
    internal fun sealed(): Sealed? = keyBytes?.takeIf { _entry.value == Slot.Real }?.let { Sealed(File(realDir, "m"), it, "m") }

    /** Sends [cmd]; returns the `result` object or throws [CoreException] with the `error`. */
    suspend fun cmd(cmd: String): JSONObject {
        val reply = ChatCore.send(cmd)
        reply.optJSONObject("result")?.let { return it }
        throw CoreException(reply.optJSONObject("error") ?: reply)
    }

    /**
     * First run: makes the vault (the three Keystore keys, the real slot), the real store, the user
     * with [nickname] (blank: the core picks a random name, which is all contacts see) and starts
     * the core with presets switched off. The decoy folder is filled with random bytes of the same
     * sizes, so having only one profile looks exactly like having two.
     */
    suspend fun create(pin: String, nickname: String) {
        val key = withContext(Dispatchers.Default) { vault.create(pin.toCharArray()) }
        keyBytes = key
        _entry.value = Slot.Real
        when (val r = ChatCore.open(realDir, hex(key))) {
            OpenResult.Ok -> Unit
            else -> error("could not create the store: $r")
        }
        prepare(realDir)
        val profile = if (nickname.isBlank()) null else JSONObject().put("displayName", nickname.trim()).put("fullName", "")
        val user = cmd("/_create user " + JSONObject().put("profile", profile ?: JSONObject.NULL).put("pastTimestamp", false))
        start(user.getJSONObject("user").getLong("userId"), removePresets = true)
        withContext(Dispatchers.IO) {
            writeSettings(Notes())
            junkLike(realDir, decoyDir)
        }
        throttle.succeeded()
    }

    sealed interface Unlock {
        data object Ok : Unlock
        data object WrongPin : Unlock
        data class Failed(val detail: String) : Unlock
    }

    /**
     * Opens whichever profile [pin] belongs to, after any wait owed for earlier wrong PINs. Real,
     * duress and panic codes take exactly the same steps and time; a panic code also wipes the real
     * profile in the background once the decoy is open.
     */
    suspend fun unlock(pin: String): Unlock {
        delay(throttle.waitMs())
        val opened = withContext(Dispatchers.Default) { vault.open(pin.toCharArray()) }
        if (opened == null) {
            throttle.failed()
            WipePolicy.onWrongPin(app)
            return Unlock.WrongPin
        }
        val dir = dirFor(opened.slot)
        return when (val r = ChatCore.open(dir, hex(opened.profileKey))) {
            OpenResult.WrongKey -> { throttle.failed(); Unlock.WrongPin }
            is OpenResult.Failed -> Unlock.Failed(r.detail)
            OpenResult.Ok -> {
                keyBytes = opened.profileKey
                _entry.value = opened.slot
                throttle.succeeded()
                prepare(dir)
                val user = cmd("/u").getJSONObject("user")
                start(user.getLong("userId"))
                if (opened.slot == Slot.Panic) scope.launch { wipeRealSilently() }
                Unlock.Ok
            }
        }
    }

    /**
     * Sets the code of the slot this session was opened with to [newPin] (the profile key does not
     * change, so nothing is re-encrypted). False when [newPin] is already another slot's code.
     */
    suspend fun changePin(newPin: String): Boolean {
        val slot = _entry.value ?: error("locked")
        val key = keyBytes ?: error("locked")
        return withContext(Dispatchers.Default) {
            val hit = vault.open(newPin.toCharArray())
            if (hit != null && hit.slot != slot) false else { vault.setSlot(slot, newPin.toCharArray(), key); true }
        }
    }

    suspend fun lock() {
        keyBytes?.fill(0)
        keyBytes = null
        _entry.value = null
        _userId.value = null
        runCatching { cmd("/_stop") }
        ChatCore.close()
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

    /** File folders (inside the app's private storage) and encrypted local files, before starting. */
    private suspend fun prepare(dir: File) {
        val files = File(dir, "f").apply { mkdirs() }
        val temp = File(app.cacheDir, "t").apply { mkdirs() }
        val assets = File(dir, "a").apply { mkdirs() }
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

    /** What only the real profile knows about the decoy; sealed on disk under the real key. */
    data class Notes(val decoyKey: String = "", val duress: Boolean = false, val panic: Boolean = false)

    /** Real session only (null in the decoy, which knows nothing of this). */
    fun notes(): Notes? = sealed()?.read()?.let { text ->
        runCatching {
            val j = JSONObject(text.trim())
            Notes(j.optString("decoyKey"), j.optBoolean("duress"), j.optBoolean("panic"))
        }.getOrNull()
    }

    fun writeSettings(n: Notes) {
        val json = JSONObject().put("decoyKey", n.decoyKey).put("duress", n.duress).put("panic", n.panic).toString()
        // Padded to a fixed size, so the file's length says nothing either.
        sealed()?.write(json.padEnd(256))
    }

    /** Fills [to] with random bytes shaped like [from] (same file names and sizes, no write-ahead logs). */
    private fun junkLike(from: File, to: File) {
        to.deleteRecursively()
        to.mkdirs()
        val random = SecureRandom()
        for (f in from.listFiles().orEmpty()) {
            if (f.isDirectory) { File(to, f.name).mkdirs(); continue }
            if (!f.isFile || f.name.endsWith("-wal") || f.name.endsWith("-shm")) continue
            File(to, f.name).outputStream().use { out ->
                var left = f.length()
                val chunk = ByteArray(64 * 1024)
                while (left > 0) {
                    random.nextBytes(chunk)
                    val n = minOf(left, chunk.size.toLong()).toInt()
                    out.write(chunk, 0, n)
                    left -= n
                }
            }
        }
    }

    /**
     * Panic code: in the background, while the decoy is already open, the real profile is destroyed —
     * its Keystore key first (after that its database is unrecoverable noise), then its files — and
     * its folder is refilled with random bytes so the phone looks the same as before.
     */
    private fun wipeRealSilently() {
        vault.destroy(Slot.Real)
        junkLike(decoyDir, realDir)
    }

    private val _wiped = MutableStateFlow(false)
    /** True once [wipeAll] has run; the app then returns to the start. */
    val wiped: StateFlow<Boolean> = _wiped

    /** Emergency wipe: everything — both profiles, every key, the device key, all settings. Irreversible. */
    suspend fun wipeAll() {
        runCatching { lock() }
        withContext(Dispatchers.IO) {
            vault.destroyAll()
            runCatching { device.destroy() }
            realDir.deleteRecursively()
            decoyDir.deleteRecursively()
            File(app.cacheDir, "t").deleteRecursively()
            for (name in listOf("weft.security", "weft.profile", "weft.guard")) {
                app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
        _wiped.value = true
    }

    fun acknowledgeWipe() { _wiped.value = false }


    internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
