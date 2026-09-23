package app.weft.ui.add

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.weft.R
import app.weft.data.CoreConnect
import app.weft.data.JoinResult
import app.weft.design.ButtonKind
import app.weft.design.WeftButton
import app.weft.design.WeftColors
import app.weft.design.WeftGhostButton
import app.weft.design.WeftIcon
import app.weft.design.WeftText
import app.weft.design.WeftType
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * "Scan their code" (not drawn in the mockup, which only shows a toast): the camera full screen with
 * a rounded frame, back, and "Paste a one-time link" for a link sent instead of shown. QR codes are
 * read on the phone (ZXing); nothing leaves it until a real invitation is found.
 */
@Composable
fun ScanScreen(
    toast: (String, WeftIcon) -> Unit,
    onBack: () -> Unit,
    onJoined: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    // One code at a time: ignore what the camera sees while a code is being handled.
    var busy by remember { mutableStateOf(false) }

    val messages = mapOf(
        JoinResult.Connecting to stringResource(R.string.scan_connecting),
        JoinResult.OwnCode to stringResource(R.string.scan_own),
        JoinResult.AlreadyConnected to stringResource(R.string.scan_known),
        JoinResult.NotAnInvitation to stringResource(R.string.scan_not_invitation),
        JoinResult.NoRelay to stringResource(R.string.add_no_relay),
    )
    val failed = stringResource(R.string.scan_failed)
    val nothingToPaste = stringResource(R.string.scan_nothing_to_paste)

    fun handle(link: String) {
        if (busy) return
        busy = true
        scope.launch {
            val r = CoreConnect.join(link)
            toast(messages[r] ?: failed, if (r == JoinResult.Connecting) WeftIcon.Check else WeftIcon.Alert)
            if (r == JoinResult.Connecting) onJoined() else { delay(2500); busy = false }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) Camera(onCode = ::handle)
        // Dim everything but the frame.
        val frame = 250.dp
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawRect(Color.Black.copy(alpha = 0.55f))
                    val side = frame.toPx()
                    drawRoundRect(
                        Color.Transparent,
                        topLeft = Offset((size.width - side) / 2, (size.height - side) / 2),
                        size = Size(side, side),
                        cornerRadius = CornerRadius(26.dp.toPx()),
                        blendMode = BlendMode.Clear,
                    )
                },
        )
        Box(Modifier.align(Alignment.Center).size(frame).border(2.dp, WeftColors.accent, RoundedCornerShape(26.dp)))
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WeftGhostButton(WeftIcon.Back, stringResource(R.string.convo_back), onBack)
            WeftText(stringResource(R.string.add_scan), style = WeftType.headName.copy(color = WeftColors.text))
        }
        val bottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = maxOf(30.dp, bottom)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WeftText(
                stringResource(if (granted) R.string.scan_hint else R.string.scan_no_camera),
                style = WeftType.lede.copy(color = WeftColors.muted, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
            WeftButton(
                stringResource(R.string.scan_paste),
                onClick = {
                    val text = context.getSystemService(ClipboardManager::class.java).primaryClip
                        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                    if (text.isNullOrEmpty()) toast(nothingToPaste, WeftIcon.Alert) else handle(text)
                },
                kind = ButtonKind.Ghost,
                leading = WeftIcon.Link,
                leadingStroke = 2f,
            )
        }
    }
}

/** Back camera preview, with every frame offered to the QR reader. */
@Composable
private fun Camera(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var request by remember { mutableStateOf<SurfaceRequest?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply { setSurfaceProvider { request = it } }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val reader = QRCodeReader()
        val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
        analysis.setAnalyzer(executor) { image ->
            val text = image.use { decode(reader, hints, it) }
            if (text != null) context.mainExecutor.execute { onCode(text) }
        }
        provider.unbindAll()
        provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        try { awaitCancellation() } finally { provider.unbindAll() }
    }
    request?.let { CameraXViewfinder(it, Modifier.fillMaxSize()) }
}

private fun decode(reader: QRCodeReader, hints: Map<DecodeHintType, Any>, image: ImageProxy): String? {
    val plane = image.planes[0]
    val bytes = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
    val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
    return try {
        reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    } catch (_: ReaderException) {
        null
    } finally {
        reader.reset()
    }
}
