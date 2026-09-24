package app.weft.data

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import app.weft.core.ChatCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 1:1 calls (BRIEF, Phase 4) with SimpleX's protocol: the core carries the call's messages to the
 * contact; SimpleX's call page (assets/www, in a WebView) does WebRTC with end-to-end encryption.
 * Always through the user's own TURN servers ([CallServers]); without one there are no calls.
 *
 * Caller: page "capabilities" → /_call invite · callee accepts: page "start" → offer → /_call offer ·
 * caller: callOffer → page "offer" → answer → /_call answer · callee: callAnswer → page "answer" ·
 * ICE candidates both ways as /_call extra · /_call end or callEnded finishes it.
 */
object CoreCalls {
    enum class Phase { Calling, Incoming, Connecting, Connected }

    data class Call(
        val contactId: Long,
        val name: String,
        val video: Boolean,
        val phase: Phase,
        val sharedKey: String? = null,
        val connectedAt: Long? = null,
        val muted: Boolean = false,
        val camera: Boolean = video,
        val speaker: Boolean = video,
        /** The other side's camera is on (their video fills the call screen). */
        val peerVideo: Boolean = video,
    )

    sealed interface StartResult {
        data object Ok : StartResult
        data object NoServers : StartResult
        data object Busy : StartResult
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Context
    private val _call = MutableStateFlow<Call?>(null)
    val call: StateFlow<Call?> = _call
    private val _ended = Channel<Long?>(Channel.BUFFERED)
    /** A finished call's length in seconds (null when it never connected), for "Call ended · mm:ss". */
    val ended = _ended.receiveAsFlow()

    /** Commands for the call page, as `processCommand` takes them; the WebView drains this. */
    private val _toPage = Channel<String>(Channel.UNLIMITED)
    val toPage = _toPage.receiveAsFlow()
    private var corr = 0

    fun init(context: Context) {
        app = context.applicationContext
        scope.launch { ChatCore.events.collect { e -> e.optJSONObject("result")?.let(::onEvent) } }
    }

    // --- actions from the UI ---

    fun start(contactId: Long, name: String, video: Boolean): StartResult {
        if (_call.value != null) return StartResult.Busy
        if (!CallServers.ready) return StartResult.NoServers
        _call.value = Call(contactId, name, video, Phase.Calling)
        page(JSONObject().put("type", "capabilities").put("media", media(video)))
        audio(true)
        return StartResult.Ok
    }

    fun accept(): StartResult {
        val c = _call.value?.takeIf { it.phase == Phase.Incoming } ?: return StartResult.Busy
        if (!CallServers.ready) return StartResult.NoServers
        _call.value = c.copy(phase = Phase.Connecting)
        Background.cancelCallNotice()
        page(
            JSONObject().put("type", "start").put("media", media(c.video))
                .putOpt("aesKey", c.sharedKey).put("iceServers", CallServers.iceServers()).put("relay", true),
        )
        audio(true)
        return StartResult.Ok
    }

    fun reject() {
        val c = _call.value ?: return
        Background.cancelCallNotice()
        scope.launch { runCatching { WeftSession.cmd("/_call reject @${c.contactId}") } }
        finish(sendEnd = false)
    }

    fun hangUp() {
        page(JSONObject().put("type", "end"))
        finish(sendEnd = true)
    }

    fun setMuted(muted: Boolean) {
        val c = _call.value ?: return
        _call.value = c.copy(muted = muted)
        page(JSONObject().put("type", "media").put("source", "mic").put("enable", !muted))
    }

    fun setCamera(on: Boolean) {
        val c = _call.value ?: return
        _call.value = c.copy(camera = on)
        page(JSONObject().put("type", "media").put("source", "camera").put("enable", on))
    }

    fun setSpeaker(on: Boolean) {
        val c = _call.value ?: return
        _call.value = c.copy(speaker = on)
        speaker(on)
    }

    // --- the call page's replies ---

    fun onPage(message: String) {
        val msg = runCatching { JSONObject(message) }.getOrNull() ?: return
        val r = msg.optJSONObject("resp") ?: return
        val c = _call.value ?: return
        val id = c.contactId
        when (r.optString("type")) {
            "capabilities" -> send(
                "/_call invite @$id " + JSONObject().put("media", media(c.video)).put("capabilities", r.getJSONObject("capabilities")),
            )
            "offer" -> send(
                "/_call offer @$id " + JSONObject()
                    .put("callType", JSONObject().put("media", media(c.video)).put("capabilities", r.getJSONObject("capabilities")))
                    .put("rtcSession", session(r.getString("offer"), r.getString("iceCandidates"))),
            )
            "answer" -> send("/_call answer @$id " + session(r.getString("answer"), r.getString("iceCandidates")))
            "ice" -> send("/_call extra @$id " + JSONObject().put("rtcIceCandidates", r.getString("iceCandidates")))
            "connection" -> {
                val state = r.optJSONObject("state")?.optString("connectionState") ?: return
                if (state in setOf("connected", "connecting", "disconnected", "failed")) send("/_call status @$id $state")
                if (state == "connected" && c.connectedAt == null) _call.value = c.copy(phase = Phase.Connected, connectedAt = SystemClock.elapsedRealtime())
                if (state == "failed") hangUp()
            }
            "peerMedia" -> if (r.optString("source") == "camera") _call.value = c.copy(peerVideo = r.optBoolean("enabled"))
            "end", "ended" -> finish(sendEnd = r.optString("type") == "end")
        }
    }

    // --- the core's call messages ---

    private fun onEvent(r: JSONObject) {
        val type = r.optString("type")
        if (!type.startsWith("call")) return
        val contact = (r.optJSONObject("callInvitation") ?: r).optJSONObject("contact") ?: return
        val id = contact.getLong("contactId")
        val c = _call.value
        when (type) {
            "callInvitation" -> {
                if (c != null) return // busy: the caller's invitation simply times out
                val inv = r.getJSONObject("callInvitation")
                val name = contact.optJSONObject("profile")?.optString("displayName").orEmpty()
                val video = inv.optJSONObject("callType")?.optString("media") == "video"
                _call.value = Call(id, name, video, Phase.Incoming, sharedKey = inv.optString("sharedKey").ifEmpty { null })
                if (!(AutoLock.isForeground && WeftSession.isUnlocked)) Background.incomingCall()
            }
            "callOffer" -> if (c?.contactId == id) {
                _call.value = c.copy(phase = Phase.Connecting, sharedKey = r.optString("sharedKey").ifEmpty { null })
                val offer = r.getJSONObject("offer")
                page(
                    JSONObject().put("type", "offer")
                        .put("offer", offer.getString("rtcSession")).put("iceCandidates", offer.getString("rtcIceCandidates"))
                        .put("media", r.optJSONObject("callType")?.optString("media") ?: media(c.video))
                        .putOpt("aesKey", r.optString("sharedKey").ifEmpty { null })
                        .put("iceServers", CallServers.iceServers()).put("relay", true),
                )
            }
            "callAnswer" -> if (c?.contactId == id) {
                val a = r.getJSONObject("answer")
                page(JSONObject().put("type", "answer").put("answer", a.getString("rtcSession")).put("iceCandidates", a.getString("rtcIceCandidates")))
            }
            "callExtraInfo" -> if (c?.contactId == id) {
                page(JSONObject().put("type", "ice").put("iceCandidates", r.getJSONObject("extraInfo").getString("rtcIceCandidates")))
            }
            "callEnded" -> if (c?.contactId == id) {
                Background.cancelCallNotice()
                page(JSONObject().put("type", "end"))
                finish(sendEnd = false)
            }
        }
    }

    /** Ends the call here; [sendEnd] tells the contact too. Also on lock and wipe. */
    fun finish(sendEnd: Boolean) {
        val c = _call.value ?: return
        _call.value = null
        audio(false)
        if (sendEnd) scope.launch { runCatching { WeftSession.cmd("/_call end @${c.contactId}") } }
        _ended.trySend(c.connectedAt?.let { (SystemClock.elapsedRealtime() - it) / 1000 })
    }

    private fun page(command: JSONObject) {
        _toPage.trySend(JSONObject().put("corrId", ++corr).put("command", command).toString())
    }

    private fun send(cmd: String) = scope.launch { runCatching { WeftSession.cmd(cmd) } }

    private fun session(sdp: String, ice: String) = JSONObject().put("rtcSession", sdp).put("rtcIceCandidates", ice)
    private fun media(video: Boolean) = if (video) "video" else "audio"

    /** Call audio (communication mode) on or off; also used by [CoreGroupCalls]. */
    fun audioMode(on: Boolean) = audio(on)
    fun speakerOn(on: Boolean) = speaker(on)

    private fun audio(on: Boolean) {
        val am = app.getSystemService(AudioManager::class.java)
        am.mode = if (on) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
        if (!on) {
            if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice() else @Suppress("DEPRECATION") { am.isSpeakerphoneOn = false }
            am.isMicrophoneMute = false
        } else speaker(_call.value?.speaker == true)
    }

    private fun speaker(on: Boolean) {
        val am = app.getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= 31) {
            val want = if (on) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            am.availableCommunicationDevices.firstOrNull { it.type == want }?.let { am.setCommunicationDevice(it) }
        } else @Suppress("DEPRECATION") { am.isSpeakerphoneOn = on }
    }
}
