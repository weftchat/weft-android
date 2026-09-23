package app.weft.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.StartOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/** `.b.them` / `.b.me` — radius 20 with the tail corner at 6. */
private fun bubbleShape(mine: Boolean) =
    if (mine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)

/**
 * New bubbles pop in: opacity 0 → 1, 10 dp → 0, scale 0.96 → 1, 340 ms EaseOut, growing from the
 * tail corner. [animate] false shows it at rest (bubbles that were already there).
 */
@Composable
fun Modifier.popIn(animate: Boolean, mine: Boolean): Modifier {
    val reduce = rememberReduceMotion()
    val a = remember { Animatable(if (animate && !reduce) 0f else 1f) }
    LaunchedEffect(Unit) { if (a.value < 1f) a.animateTo(1f, tween(WeftMotion.MESSAGE_IN, easing = WeftMotion.EaseOut)) }
    return graphicsLayer {
        val p = a.value
        alpha = p
        translationY = 10.dp.toPx() * (1f - p)
        val s = 0.96f + 0.04f * p
        scaleX = s; scaleY = s
        transformOrigin = TransformOrigin(if (mine) 1f else 0f, 1f)
    }
}

/**
 * A text message. Theirs: surface with a line border; mine: the accent gradient, 600 weight,
 * time and ticks in on-accent @ 62 %. [sender] (groups) is shown in its hue above the text.
 * [delivered] false shows one tick, true two; null shows none (their messages).
 */
@Composable
fun TextBubble(
    text: String,
    time: String,
    mine: Boolean,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    sender: String? = null,
    senderHue: Float = 262f,
    delivered: Boolean? = null,
) {
    val shape = bubbleShape(mine)
    Column(
        modifier
            .widthIn(max = maxWidth)
            .then(
                if (mine) Modifier.gradientBackground(shape)
                else Modifier.background(WeftColors.surface, shape).cssBorder(1.dp, WeftColors.line, shape),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (sender != null) {
            WeftText(sender, style = WeftType.fieldLabel.copy(color = WeftColors.hsl(senderHue, 0.80f, 0.78f)))
        }
        WeftText(
            text,
            style = WeftType.body.copy(
                color = if (mine) WeftColors.onAccent else WeftColors.text,
                fontWeight = if (mine) FontWeight.W600 else FontWeight.W500,
            ),
        )
        val metaInk = if (mine) WeftColors.onAccent.copy(alpha = 0.62f) else WeftColors.faint
        // With ticks the row is 16 dp: the icon sits on the text baseline, as inline SVG does.
        Row(
            Modifier.align(if (mine) Alignment.End else Alignment.Start).then(if (delivered != null) Modifier.heightIn(min = 16.dp) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeftText(time, style = WeftType.monoSmall.copy(color = metaInk))
            if (delivered != null) WeftIconView(if (delivered) WeftIcon.Checks else WeftIcon.Check, 13.dp, metaInk, stroke = 2.4f)
        }
    }
}

/**
 * `.b.photo` — 236 dp wide: the picture (148 dp), then "metadata stripped" in green and what was
 * removed. [picture] draws the image; without one, the mockup's placeholder is drawn.
 */
@Composable
fun PhotoBubble(
    stripped: String,
    removed: String,
    modifier: Modifier = Modifier,
    picture: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .width(236.dp)
            .clip(shape)
            .background(WeftColors.surface, shape)
            .cssBorder(1.dp, WeftColors.line, shape),
        verticalArrangement = Arrangement.spacedBy(3.dp), // `.b` gap, between the picture and its caption
    ) {
        Box(Modifier.fillMaxWidth().height(148.dp), contentAlignment = Alignment.Center) {
            if (picture != null) picture() else {
                Box(Modifier.matchParentSizePlaceholder())
                WeftIconView(WeftIcon.Image, 26.dp, WeftColors.text.copy(alpha = 0.55f), stroke = 1.6f)
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                WeftIconView(WeftIcon.Check, 12.dp, WeftColors.safe, stroke = 2.6f)
                WeftText(stripped, style = WeftType.monoSmall.copy(color = WeftColors.safe))
            }
            WeftText(removed, style = WeftType.monoSmall.copy(color = WeftColors.faint))
        }
    }
}

/** `.photo-ph` — two soft radial glows (violet top-left, green bottom-right) on #151522. */
private fun Modifier.matchParentSizePlaceholder() = this
    .fillMaxWidth()
    .height(148.dp)
    .drawBehind {
        drawRect(Color(0xFF151522))
        // CSS `radial-gradient(circle at x y, c, transparent n%)`: the radius runs to the farthest corner.
        fun glow(cx: Float, cy: Float, color: Color, stop: Float) {
            val c = Offset(size.width * cx, size.height * cy)
            val far = maxOf(
                hypot(c.x, c.y), hypot(size.width - c.x, c.y),
                hypot(c.x, size.height - c.y), hypot(size.width - c.x, size.height - c.y),
            )
            drawRect(Brush.radialGradient(0f to color, stop to color.copy(alpha = 0f), 1f to Color.Transparent, center = c, radius = far))
        }
        glow(0.78f, 0.72f, WeftColors.hsl(160f, 0.45f, 0.22f), 0.50f)
        glow(0.28f, 0.38f, WeftColors.hsl(262f, 0.45f, 0.32f), 0.55f)
    }

/** `.typing` — three 6 dp dots blinking over 1.1 s, 150 ms apart, in a "their" bubble. */
@Composable
fun TypingBubble(modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val shape = bubbleShape(mine = false)
    val t = rememberInfiniteTransition(label = "typing")
    val ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f) // CSS `ease`, per keyframe segment
    Row(
        modifier
            .background(WeftColors.surface, shape)
            .cssBorder(1.dp, WeftColors.line, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { i ->
            val p = if (reduce) 1f else t.animateFloat(
                0f, 0f,
                infiniteRepeatable(
                    keyframes {
                        durationMillis = 1100
                        0f at 0 using ease
                        1f at 330 using ease
                        0f at 660
                    },
                    RepeatMode.Restart,
                    StartOffset(i * 150),
                ),
                label = "dot$i",
            ).value
            Box(
                Modifier
                    .size(6.dp)
                    .graphicsLayer { alpha = 0.25f + 0.75f * p; translationY = -2.dp.toPx() * p }
                    .background(WeftColors.muted, CircleShape),
            )
        }
    }
}

/** `.sysnote` — dashed capsule with a padlock: "only you two · end-to-end encrypted". */
@Composable
fun SystemNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .drawBehind {
                val w = 1.dp.toPx()
                val dash = 3.dp.toPx()
                drawRoundRect(
                    WeftColors.line2,
                    topLeft = Offset(w / 2, w / 2),
                    size = size.copy(width = size.width - w, height = size.height - w),
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                )
            }
            .padding(1.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeftIconView(WeftIcon.Lock, 11.dp, WeftColors.faint, stroke = 2.4f)
        WeftText(text, style = WeftType.monoCaption.copy(color = WeftColors.faint))
    }
}
