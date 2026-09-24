package app.weft.data

import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Group calls (BRIEF, Phase 4): SimpleX has none, so Weft runs a WebRTC mesh — one connection per
 * pair of participants — on its own page (assets/www/android/group-call.*), relay only, every frame
 * encrypted with the call's key. Voice up to 6 people, video up to 4; only between Weft users.
 *
 * Signalling rides on the group chat as Weft's own message type ("weft-call"): SimpleX apps show
 * only "Group call · Weft"; Weft hides them. They disappear after a minute. The call's key travels
 * in them, protected like any group message.
 *  start/here {id, media, key}  — a call exists (repeated every 20 s by everyone in it)
 *  join {id}                    — someone joins; everyone answers "here"
 *  sig {id, to, data}           — WebRTC offer / answer / ICE for one member
 *  leave {id}                   — someone left
 * In each pair the member with the smaller id makes the offer, so two offers never cross.
 */
object CoreGroupCalls {
    const val MAX_VOICE = 6
    const val MAX_VIDEO = 4
    private const val TYPE = "weft-call"
    private const val HEARTBEAT_MS = 20_000L
    private const val STALE_MS = 50_000L

    data class Person(val id: String, val name: String, val hue: Float, val connected: Boolean = false)

    /** A call someone is holding in a group, joined or not. */
    data class Available(val callId: String, val video: Boolean, val key: String, val people: Map<String, Long>) {
        val count: Int get() = people.size
    }

    data class Call(
        val chatId: String,
        val name: String,
        val callId: String,
        val video: Boolean,
        val key: String,
        val me: String,
        val people: Map<String, Person> = emptyMap(),
        val connectedAt: Long? = null,
        val muted: Boolean = false,
        val camera: Boolean = video,
        val speaker: Boolean = true,
    )

    sealed interface JoinResult {
        data object Ok : JoinResult
        data object NoServers : JoinResult
        data object Busy : JoinResult
        data object Full : JoinResult
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _call = MutableStateFlow<Call?>(null)
    val call: StateFlow<Call?> = _call
    private val _available = MutableStateFlow<Map<String, Available>>(emptyMap())
    /** Per group chat id, a call going on there (for "Join call"). */
    val available: StateFlow<Map<String, Available>> = _available
    private val _ended = Channel<Long?>(Channel.BUFFERED)
    val ended = _ended.receiveAsFlow()
    private val _toPage = Channel<JSONObject>(Channel.UNLIMITED)
    val toPage = _toPage.receiveAsFlow()
    private val offered = mutableSetOf<String>()
    private var heartbeat: Job? = null

    fun cap(video: Boolean) = if (video) MAX_VIDEO else MAX_VOICE

    // --- actions ---

    suspend fun start(chatId: String, name: String, video: Boolean): JoinResult {
        check()?.let { return it }
        // base64url with padding: SimpleX's callCrypto.decodeAesKey refuses it without.
        val key = Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_WRAP)
        val callId = Base64.encodeToString(ByteArray(9).also { SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_WRAP)
        enter(chatId, name, callId, video, key) ?: return JoinResult.Busy
        post(chatId, "start")
        return JoinResult.Ok
    }

    suspend fun join(chatId: String, name: String): JoinResult {
        check()?.let { return it }
        val a = _available.value[chatId] ?: return JoinResult.Busy
        if (a.count >= cap(a.video)) return JoinResult.Full
        enter(chatId, name, a.callId, a.video, a.key) ?: return JoinResult.Busy
        post(chatId, "join")
        return JoinResult.Ok
    }

    fun leave() {
        val c = _call.value ?: return
        _call.value = null
        heartbeat?.cancel()
        offered.clear()
        _toPage.trySend(JSONObject().put("type", "leave"))
        CoreCalls.audioMode(false)
        scope.launch { post(c, "leave") }
        _ended.trySend(c.connectedAt?.let { (SystemClock.elapsedRealtime() - it) / 1000 })
    }

    fun setMuted(muted: Boolean) = update { it.copy(muted = muted) }.also { media("mic", !muted) }
    fun setCamera(on: Boolean) = update { it.copy(camera = on) }.also { media("camera", on) }
    fun setSpeaker(on: Boolean) = update { it.copy(speaker = on) }.also { CoreCalls.speakerOn(on) }

    private fun check(): JoinResult? = when {
        !CallServers.ready -> JoinResult.NoServers
        _call.value != null || CoreCalls.call.value != null -> JoinResult.Busy
        else -> null
    }

    private suspend fun enter(chatId: String, name: String, callId: String, video: Boolean, key: String): Call? {
        val me = runCatching {
            WeftSession.cmd("/_members $chatId").getJSONObject("group").getJSONObject("groupInfo").getJSONObject("membership").getString("memberId")
        }.getOrNull() ?: return null
        val c = Call(chatId, name, callId, video, key, me)
        _call.value = c
        _toPage.trySend(JSONObject().put("type", "start").put("media", if (video) "video" else "audio").put("aesKey", key).put("iceServers", CallServers.iceServers()))
        CoreCalls.audioMode(true)
        CoreCalls.speakerOn(true)
        heartbeat = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                _call.value?.let { post(it, "here") }
            }
        }
        return c
    }

    // --- the page's replies ---

    fun onPage(message: String) {
        val m = runCatching { JSONObject(message) }.getOrNull() ?: return
        val c = _call.value ?: return
        when (m.optString("type")) {
            "signal" -> scope.launch { post(c, "sig", JSONObject().put("to", m.getString("peer")).put("data", m.getJSONObject("data"))) }
            "state" -> {
                val id = m.getString("peer")
                val on = m.optString("state") == "connected"
                update { call ->
                    val p = call.people[id] ?: Person(id, "", 200f)
                    call.copy(
                        people = call.people + (id to p.copy(connected = on)),
                        connectedAt = call.connectedAt ?: if (on) SystemClock.elapsedRealtime() else null,
                    )
                }
                if (m.optString("state") == "failed") peerGone(id)
            }
        }
    }

    // --- group messages ---

    /** Called by [CoreChats] for every received "weft-call" item. */
    fun onMessage(chatId: String, from: String, fromName: String, fromHue: Float, payload: JSONObject) {
        val kind = payload.optString("kind")
        val id = payload.optString("id")
        val now = SystemClock.elapsedRealtime()
        // Keep track of calls in every group, for "Join call".
        if (kind in setOf("start", "here", "join")) {
            val prev = _available.value[chatId]?.takeIf { it.callId == id }
            val people = (prev?.people ?: emptyMap()).filterValues { now - it < STALE_MS } + (from to now)
            _available.value = _available.value + (chatId to Available(id, payload.optString("media") == "video", payload.optString("key"), people))
        } else if (kind == "leave") {
            _available.value[chatId]?.takeIf { it.callId == id }?.let { a ->
                val left = a.people - from
                _available.value = if (left.isEmpty()) _available.value - chatId else _available.value + (chatId to a.copy(people = left))
            }
        }
        val c = _call.value?.takeIf { it.chatId == chatId && it.callId == id } ?: return
        if (from == c.me) return
        when (kind) {
            "start", "here", "join" -> {
                update { it.copy(people = it.people + (from to (it.people[from] ?: Person(from, fromName, fromHue)))) }
                if (kind == "join") scope.launch { post(c, "here") }
                if (c.me < from && offered.add(from)) _toPage.trySend(JSONObject().put("type", "offerTo").put("peer", from))
            }
            "sig" -> if (payload.optString("to") == c.me) {
                _toPage.trySend(JSONObject().put("type", "signal").put("peer", from).put("data", payload.getJSONObject("data")))
            }
            "leave" -> peerGone(from)
        }
    }

    private fun peerGone(id: String) {
        offered.remove(id)
        update { it.copy(people = it.people - id) }
        _toPage.trySend(JSONObject().put("type", "peerLeft").put("peer", id))
    }

    /** Ends everything here without telling anyone (lock, wipe). */
    fun drop() {
        if (_call.value == null) return
        leave()
    }

    private suspend fun post(c: Call, kind: String, extra: JSONObject = JSONObject()) =
        post(c.chatId, kind, extra.put("id", c.callId).put("media", if (c.video) "video" else "audio").put("key", c.key))

    private suspend fun post(chatId: String, kind: String, extra: JSONObject = JSONObject()) {
        val c = _call.value
        val body = extra.put("kind", kind)
        if (c != null && !body.has("id")) body.put("id", c.callId).put("media", if (c.video) "video" else "audio").put("key", c.key)
        val msg = JSONArray().put(
            JSONObject().put("msgContent", JSONObject().put("type", TYPE).put("text", "Group call · Weft").put("call", body)).put("mentions", JSONObject()),
        )
        // Signalling disappears after a minute; groups without disappearing messages get it as is.
        runCatching { WeftSession.cmd("/_send $chatId live=off ttl=60 sign=off json $msg") }
            .onFailure { runCatching { WeftSession.cmd("/_send $chatId live=off ttl=default sign=off json $msg") } }
    }

    private fun media(source: String, enable: Boolean) {
        _toPage.trySend(JSONObject().put("type", "media").put("source", source).put("enable", enable))
    }

    private fun update(change: (Call) -> Call) {
        _call.value = _call.value?.let(change)
    }

    /** Whether a chat item's content is one of these messages. */
    fun isSignal(msgContent: JSONObject?) = msgContent?.optString("type") == TYPE
}
