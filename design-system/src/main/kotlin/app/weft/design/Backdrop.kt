package app.weft.design

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

/**
 * CSS `backdrop-filter: blur()` for the floating bars. The screen content is recorded once into
 * [content] as it is drawn; a bar then draws that recording again, blurred and clipped to its
 * shape, under its own translucent fill. Android 12+ only (RenderEffect); older versions keep the
 * plain translucent fill.
 */
@Stable
class Backdrop internal constructor(internal val content: GraphicsLayer, internal val blurred: GraphicsLayer) {
    internal var origin = Offset.Zero
    internal var size = IntSize.Zero
}

@Composable
fun rememberBackdrop(): Backdrop {
    val content = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    return remember(content, blurred) { Backdrop(content, blurred) }
}

/** Marks the content that bars blur behind them. */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot(); backdrop.size = it.size }
    .drawWithContent {
        backdrop.content.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.content)
    }

private val canBlur = Build.VERSION.SDK_INT >= 31

/** Draws the blurred backdrop inside [shape]; put it before the bar's own background. */
fun Modifier.backdropBlur(backdrop: Backdrop?, radius: Dp, shape: Shape): Modifier {
    if (backdrop == null || !canBlur) return this
    var at = Offset.Zero
    return this
        .onGloballyPositioned { at = it.positionInRoot() }
        .drawWithContent {
            val r = radius.toPx()
            backdrop.blurred.renderEffect = BlurEffect(r, r, TileMode.Clamp)
            backdrop.blurred.record(backdrop.size) { drawLayer(backdrop.content) }
            val outline = shape.createOutline(size, layoutDirection, this)
            clipPath(Path().apply { addOutline(outline) }) {
                translate(backdrop.origin.x - at.x, backdrop.origin.y - at.y) { drawLayer(backdrop.blurred) }
            }
            drawContent()
        }
}
