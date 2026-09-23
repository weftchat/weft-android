package app.weft.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * `.hold` — 62 dp danger pill: a red fill wipes left to right over [holdMs] (linear) while it is
 * held, and the label turns dark on red as the fill passes it. Letting go early rewinds four times
 * faster. Completing calls [onComplete] once. A long press from an accessibility service works too.
 */
@Composable
fun WeftHoldButton(text: String, onComplete: () -> Unit, modifier: Modifier = Modifier, holdMs: Int = 2000, enabled: Boolean = true) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val shape = CircleShape
    @Composable
    fun Label(ink: Color) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            WeftIconView(WeftIcon.Trash, 18.dp, ink, stroke = 2.2f)
            WeftText(text, style = WeftType.button.copy(color = ink))
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(62.dp)
            .graphicsLayer { val s = if (pressed) 0.985f else 1f; scaleX = s; scaleY = s }
            .clip(shape)
            .background(WeftColors.danger.copy(alpha = 0.08f), shape)
            .border(1.dp, WeftColors.danger.copy(alpha = 0.5f), shape)
            .semantics { onLongClick(label = text) { if (enabled && !done) { done = true; onComplete() }; true } }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val fill: Job = scope.launch {
                        progress.animateTo(1f, tween(((1f - progress.value) * holdMs).toInt().coerceAtLeast(1), easing = LinearEasing))
                        if (!done) { done = true; onComplete() }
                    }
                    waitForUpOrCancellation()
                    pressed = false
                    if (!done) {
                        fill.cancel()
                        scope.launch { progress.animateTo(0f, tween((progress.value * holdMs / 4).toInt().coerceAtLeast(1), easing = LinearEasing)) }
                    }
                }
            },
    ) {
        Label(WeftColors.danger)
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent { val content = this; clipRect(right = size.width * progress.value) { content.drawContent() } }
                .background(WeftColors.danger),
        ) { Label(Color(0xFF1B0308)) }
    }
}
