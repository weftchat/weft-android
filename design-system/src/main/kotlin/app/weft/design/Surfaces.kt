package app.weft.design

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fills the shape with the 135° accent gradient, sized to the element like CSS does. */
fun Modifier.gradientBackground(shape: Shape): Modifier = this.drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline, WeftColors.gradient(size.width, size.height))
}

/**
 * A CSS border: drawn like [border], but it also takes up its width, as the mockup's
 * `border: 1px solid` does (box-sizing: border-box). Use it where the size comes from the content;
 * fixed-size elements (buttons, keys, inputs) keep plain [border].
 */
fun Modifier.cssBorder(width: Dp, color: Color, shape: Shape): Modifier = border(width, color, shape).padding(width)

enum class DotState { Live, Switching, Off, Blocked }

/** `.dot` — 8 dp status light: safe with a glow, warn pulsing (800 ms), off, or danger. */
@Composable
fun StatusDot(state: DotState, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val target = when (state) {
        DotState.Live -> WeftColors.safe
        DotState.Switching -> WeftColors.warn
        DotState.Off -> WeftColors.offDot
        DotState.Blocked -> WeftColors.danger
    }
    val color by animateColorAsState(target, tween(if (reduce) 0 else 300), label = "dot")
    val pulse = if (state == DotState.Switching && !reduce) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "pulseA").value
    } else 1f
    val glow = when (state) {
        DotState.Off -> Color.Transparent
        DotState.Blocked -> WeftColors.danger.copy(alpha = 0.6f)
        else -> color
    }
    Box(
        modifier
            .size(8.dp)
            .alpha(pulse)
            .then(if (glow != Color.Transparent) Modifier.dropShadow(CircleShape, Shadow(if (state == DotState.Blocked) 8.dp else 10.dp, glow)) else Modifier)
            .background(color, CircleShape),
    )
}

/** Avatar: rounded square, `hsl(h 32% 19%)` fill with `hsl(h 80% 84%)` initials. */
@Composable
fun WeftAvatar(initials: String, hue: Float, size: Dp = 48.dp, radius: Dp = 16.dp, fontSize: Float = 15f, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(WeftColors.avatarFill(hue), RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) {
        WeftText(initials, style = WeftType.rowName.copy(fontSize = fontSize.sp, color = WeftColors.avatarInk(hue), letterSpacing = 0.sp))
    }
}
