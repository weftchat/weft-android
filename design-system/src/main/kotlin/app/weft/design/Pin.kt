package app.weft.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * `.dots` — one ring per digit (14 dp, 16 dp apart). A filled dot turns accent with a 12 dp glow
 * (140 ms) and pops 0.6 → 1.15 → 1 over 260 ms. [error] turns the rings danger and empties them.
 * [shake] is the progress (0..1) of [pinShake].
 */
@Composable
fun PinDots(filled: Int, length: Int, error: Boolean, shake: Float, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    Row(
        modifier
            .height(16.dp)
            .graphicsLayer { translationX = along(SHAKE, shake) * density }
            .semantics { contentDescription = "$filled of $length digits entered" },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(length) { i -> PinDot(on = i < filled && !error, error = error, reduce = reduce) }
    }
}

@Composable
private fun PinDot(on: Boolean, error: Boolean, reduce: Boolean) {
    val spec = tween<Color>(if (reduce) 0 else 140)
    val fill by animateColorAsState(if (on) WeftColors.accent else Color.Transparent, spec, label = "dotFill")
    val ring by animateColorAsState(
        when {
            error -> WeftColors.danger
            on -> WeftColors.accent
            else -> WeftColors.emptyDot
        },
        spec, label = "dotRing",
    )
    val glow by animateFloatAsState(if (on) 1f else 0f, tween(if (reduce) 0 else 140), label = "dotGlow")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(on) {
        if (on && !reduce) {
            pop.snapTo(0f)
            pop.animateTo(1f, tween(WeftMotion.PIN_DOT, easing = WeftMotion.EaseOut))
        }
    }
    Box(
        Modifier
            .size(14.dp)
            .graphicsLayer { val k = along(POP, pop.value); scaleX = k; scaleY = k }
            .dropShadow(CircleShape, Shadow(12.dp, WeftColors.accent.copy(alpha = glow)))
            .background(fill, CircleShape)
            .border(2.dp, ring, CircleShape),
    )
}

/** Wrong PIN: the dots row shakes −10, +9, −6, +4, 0 dp over 420 ms (CSS `ease-out`). */
suspend fun Animatable<Float, *>.pinShake(reduce: Boolean) {
    snapTo(0f)
    if (!reduce) animateTo(1f, tween(420, easing = CssEaseOut))
    snapTo(0f)
}

// Web Animations ease the whole run and go linearly between evenly spaced keyframes; so do we.
private val POP = floatArrayOf(0.6f, 1.15f, 1f)
private val SHAKE = floatArrayOf(0f, -10f, 9f, -6f, 4f, 0f)
private val CssEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

private fun along(frames: FloatArray, p: Float): Float {
    val x = p.coerceIn(0f, 1f) * (frames.size - 1)
    val i = x.toInt().coerceAtMost(frames.size - 2)
    return frames[i] + (frames[i + 1] - frames[i]) * (x - i)
}

/**
 * `.keys` — 3 × 4 grid of 78 dp round keys, 26 dp across and 16 dp down; the bottom row is
 * spacer · 0 · delete. [onKey] receives "0".."9" or [PIN_DELETE].
 */
@Composable
fun PinKeypad(onKey: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", PIN_DELETE))) {
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                for (k in row) when (k) {
                    "" -> Box(Modifier.size(78.dp))
                    PIN_DELETE -> Box(
                        Modifier
                            .size(78.dp)
                            .semantics { contentDescription = "Delete digit" }
                            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button) { onKey(k) },
                        contentAlignment = Alignment.Center,
                    ) { WeftIconView(WeftIcon.Delete, 26.dp, WeftColors.muted, stroke = 2f) }
                    else -> PinKey(k, enabled) { onKey(k) }
                }
            }
        }
    }
}

const val PIN_DELETE = "del"

/** `.key` — glass circle; pressed: scale 0.93 and accent @ 24 % fill, 60 ms down; back in 140/220 ms. */
@Composable
private fun PinKey(digit: String, enabled: Boolean, onClick: () -> Unit) {
    val reduce = rememberReduceMotion()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.93f else 1f,
        if (reduce) tween(0) else tween(if (pressed) 60 else 140, easing = WeftMotion.EaseOut),
        label = "keyScale",
    )
    val fill by animateColorAsState(
        if (pressed) WeftColors.accent.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.045f),
        tween(if (reduce) 0 else if (pressed) 60 else 220),
        label = "keyFill",
    )
    Box(
        Modifier
            .size(78.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .background(fill, CircleShape)
            .border(1.dp, WeftColors.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) { WeftText(digit, style = WeftType.pinKey.copy(color = WeftColors.text)) }
}
