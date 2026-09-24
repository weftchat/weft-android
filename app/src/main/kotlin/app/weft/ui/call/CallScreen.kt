package app.weft.ui.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import app.weft.R
import app.weft.data.CoreCalls
import app.weft.data.CoreGroupCalls
import app.weft.design.ButtonKind
import app.weft.design.WeftAvatar
import app.weft.design.WeftButton
import app.weft.design.WeftColors
import app.weft.design.WeftGhostButton
import app.weft.design.WeftIcon
import app.weft.design.WeftIconView
import app.weft.design.WeftText
import app.weft.design.WeftType
import app.weft.design.weftClickable
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream

/** [seconds] as "mm:ss". */
fun callClock(seconds: Long) = "%02d:%02d".format(seconds / 60, seconds % 60)

/**
 * SimpleX's call page, served from the APK only (every other request is refused). [onMessage]
 * gets the page's replies on the main thread; [onLoaded] once `sendMessageToNative` is wired. The
 * microphone and camera are granted to the page only if Weft itself has them.
 */
@SuppressLint("SetJavaScriptEnabled")
fun newCallPage(ctx: Context, onLoaded: (WebView) -> Unit, onMessage: (String) -> Unit, url: String = CALL_PAGE): WebView {
    val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx)).build()
    val main = Handler(Looper.getMainLooper())
    return WebView(ctx).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        addJavascriptInterface(object {
            @JavascriptInterface
            fun postMessage(message: String) { main.post { onMessage(message) } }
        }, "WebRTCInterface")
        webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse =
                loader.shouldInterceptRequest(request.url)
                    ?: WebResourceResponse("text/plain", "utf-8", 403, "blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

            override fun onPageFinished(v: WebView, url: String?) {
                v.evaluateJavascript("sendMessageToNative = (msg) => WebRTCInterface.postMessage(JSON.stringify(msg))") { onLoaded(v) }
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val allowed = request.resources.filter { r ->
                    val needs = when (r) {
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                        else -> return@filter false
                    }
                    ContextCompat.checkSelfPermission(ctx, needs) == PackageManager.PERMISSION_GRANTED
                }
                if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
            }
        }
        loadUrl(url)
    }
}

const val CALL_PAGE = "https://appassets.androidplatform.net/assets/www/android/call.html"
const val GROUP_CALL_PAGE = "https://appassets.androidplatform.net/assets/www/android/group-call.html"

/** Weft's group call page for [CoreGroupCalls]; like [CallWebView], composed for the whole call. */
@Composable
fun GroupCallWebView(modifier: Modifier) {
    var view by remember { mutableStateOf<WebView?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx -> newCallPage(ctx, onLoaded = { view = it }, onMessage = CoreGroupCalls::onPage, url = GROUP_CALL_PAGE) },
        onRelease = { it.destroy() },
    )
    view?.let { v -> LaunchedEffect(v) { CoreGroupCalls.toPage.collect { json -> v.evaluateJavascript("processGroupCommand($json)", null) } } }
}

/**
 * The call page for [CoreCalls]. It must stay composed for the whole call: hidden, it is 1 dp;
 * with video, it fills [modifier].
 */
@Composable
fun CallWebView(modifier: Modifier) {
    var view by remember { mutableStateOf<WebView?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx -> newCallPage(ctx, onLoaded = { view = it }, onMessage = CoreCalls::onPage) },
        onRelease = { it.destroy() },
    )
    view?.let { v -> LaunchedEffect(v) { CoreCalls.toPage.collect { json -> v.evaluateJavascript("processCommand($json)", null) } } }
}

/**
 * Screen 6 for a 1:1 call: minimize, the contact's name, "E2E encrypted · mm:ss", two tiles (you
 * and them; their video instead when their camera is on) and Mute / Camera / Speaker / Leave.
 */
@Composable
fun CallScreen(call: CoreCalls.Call, myInitials: String, onMinimize: () -> Unit, withCamera: (() -> Unit) -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); delay(1000) } }
    val showVideo = call.peerVideo && call.phase == CoreCalls.Phase.Connected
    Column(Modifier.fillMaxSize().background(if (showVideo) Color.Transparent else WeftColors.bg)) {
        // `.call-head` — 52 dp top, 14 dp sides.
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 52.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WeftGhostButton(WeftIcon.Down, stringResource(R.string.call_minimize), onMinimize)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WeftText(call.name, style = WeftType.rowName.copy(fontSize = 16.sp, color = WeftColors.text))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    WeftIconView(WeftIcon.Lock, 11.dp, WeftColors.safe, stroke = 2.4f)
                    WeftText(
                        when (call.phase) {
                            CoreCalls.Phase.Connected -> stringResource(R.string.call_e2e, callClock((now - (call.connectedAt ?: now)) / 1000))
                            CoreCalls.Phase.Calling -> stringResource(R.string.call_calling)
                            else -> stringResource(R.string.call_connecting)
                        },
                        style = WeftType.monoSmall.copy(color = WeftColors.safe),
                    )
                }
            }
            Spacer(Modifier.width(44.dp))
        }
        // `.tiles` — 2 columns, 10 dp gaps, 10/14 dp padding.
        if (showVideo) Spacer(Modifier.weight(1f)) else Row(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Tile(myInitials, stringResource(R.string.call_you), 262f, call.muted, Modifier.weight(1f))
            Tile(call.name.take(1).uppercase().ifEmpty { "?" }, call.name, 200f, false, Modifier.weight(1f))
        }
        // `.controls`
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 40.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Control(if (call.muted) WeftIcon.MicOff else WeftIcon.Mic, stringResource(R.string.call_mute), pressed = call.muted) { CoreCalls.setMuted(!call.muted) }
            Control(WeftIcon.Video, stringResource(R.string.call_camera), pressed = call.camera) { if (call.camera) CoreCalls.setCamera(false) else withCamera { CoreCalls.setCamera(true) } }
            Control(WeftIcon.Speaker, stringResource(R.string.call_speaker), pressed = call.speaker) { CoreCalls.setSpeaker(!call.speaker) }
            Control(WeftIcon.HangUp, stringResource(R.string.call_leave), end = true) { CoreCalls.hangUp() }
        }
    }
}

/**
 * Screen 6 — Group call: minimize, the group's name, "E2E encrypted · mm:ss", a 2-column grid of up
 * to 6 tiles (you first; someone still connecting is dimmed), Mute / Camera / Speaker / Leave. With
 * video the page's own grid of videos shows instead of the tiles.
 */
@Composable
fun GroupCallScreen(call: CoreGroupCalls.Call, myInitials: String, onMinimize: () -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); delay(1000) } }
    Column(Modifier.fillMaxSize().background(if (call.video) Color.Transparent else WeftColors.bg)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 52.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WeftGhostButton(WeftIcon.Down, stringResource(R.string.call_minimize), onMinimize)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WeftText(call.name, style = WeftType.rowName.copy(fontSize = 16.sp, color = WeftColors.text))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    WeftIconView(WeftIcon.Lock, 11.dp, WeftColors.safe, stroke = 2.4f)
                    WeftText(
                        call.connectedAt?.let { stringResource(R.string.call_e2e, callClock((now - it) / 1000)) } ?: stringResource(R.string.call_connecting),
                        style = WeftType.monoSmall.copy(color = WeftColors.safe),
                    )
                }
            }
            Spacer(Modifier.width(44.dp))
        }
        if (call.video) Spacer(Modifier.weight(1f)) else {
            // You first, then everyone else in their own colour; someone still connecting is dimmed.
            val you = GroupTile(myInitials, stringResource(R.string.call_you), 262f, connected = true, muted = call.muted)
            val tiles = listOf(you) + call.people.values.map { GroupTile(it.name.take(1).uppercase().ifEmpty { "?" }, it.name, it.hue, it.connected, muted = false) }
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in tiles.chunked(2)) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (t in row) Tile(t.initials, t.name, t.hue, t.muted, Modifier.weight(1f).alpha(if (t.connected) 1f else 0.45f))
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 40.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Control(if (call.muted) WeftIcon.MicOff else WeftIcon.Mic, stringResource(R.string.call_mute), pressed = call.muted) { CoreGroupCalls.setMuted(!call.muted) }
            if (call.video) Control(WeftIcon.Video, stringResource(R.string.call_camera), pressed = call.camera) { CoreGroupCalls.setCamera(!call.camera) }
            Control(WeftIcon.Speaker, stringResource(R.string.call_speaker), pressed = call.speaker) { CoreGroupCalls.setSpeaker(!call.speaker) }
            Control(WeftIcon.HangUp, stringResource(R.string.call_leave), end = true) { CoreGroupCalls.leave() }
        }
    }
}

private data class GroupTile(val initials: String, val name: String, val hue: Float, val connected: Boolean, val muted: Boolean)

/** `.tile` — radius 24, a radial glow in the person's hue, 74 dp avatar, name bottom-left. */
@Composable
private fun Tile(initials: String, name: String, hue: Float, muted: Boolean, modifier: Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(WeftColors.hsl(hue, 0.30f, 0.15f), Color(0xFF101019))), RoundedCornerShape(24.dp))
            .border(1.dp, WeftColors.line, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        WeftAvatar(initials, hue, size = 74.dp, radius = 37.dp, fontSize = 27f)
        WeftText(name, style = WeftType.itemTitle.copy(fontSize = 12.5.sp, color = WeftColors.text), modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 10.dp))
        if (muted) Box(
            Modifier.align(Alignment.TopEnd).padding(10.dp).size(28.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { WeftIconView(WeftIcon.MicOff, 14.dp, WeftColors.danger, stroke = 2.2f) }
    }
}

/** `.ctrl` — 60 dp round; pressed toggles invert (light fill, dark icon); Leave is danger. */
@Composable
private fun Control(icon: WeftIcon, label: String, pressed: Boolean = false, end: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val fill = when { end -> WeftColors.danger; pressed -> WeftColors.text; else -> Color.White.copy(alpha = 0.08f) }
        val ink = when { end -> Color(0xFF1B0308); pressed -> WeftColors.bg; else -> WeftColors.text }
        Box(
            Modifier.size(60.dp).background(fill, CircleShape).semantics { contentDescription = label }.weftClickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { WeftIconView(icon, if (end) 26.dp else 24.dp, ink, stroke = if (end) 2.2f else 2f) }
        WeftText(label, style = WeftType.itemDetail.copy(fontSize = 11.sp, color = WeftColors.muted))
    }
}

/** An incoming call while Weft is open and unlocked: who, "Incoming call · E2E", Decline / Answer. */
@Composable
fun IncomingCallScreen(call: CoreCalls.Call, onAnswer: () -> Unit, onDecline: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(WeftColors.bg).padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        WeftAvatar(call.name.take(1).uppercase().ifEmpty { "?" }, 200f, size = 96.dp, radius = 48.dp, fontSize = 34f)
        WeftText(call.name, style = WeftType.screenTitle.copy(color = WeftColors.text, textAlign = TextAlign.Center))
        WeftText(
            stringResource(if (call.video) R.string.call_incoming_video else R.string.call_incoming_audio),
            style = WeftType.monoBody.copy(color = WeftColors.safe),
        )
        Spacer(Modifier.size(24.dp))
        WeftButton(stringResource(R.string.call_answer), onAnswer, leading = WeftIcon.Phone, leadingStroke = 2f)
        WeftButton(stringResource(R.string.call_decline), onDecline, kind = ButtonKind.Danger, leading = WeftIcon.HangUp, leadingStroke = 2f)
    }
}
