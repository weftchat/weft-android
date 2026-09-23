package app.weft.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Text laid out like a CSS line box. Every line takes exactly the style's line-height and the glyphs
 * are centred in it (half-leading), overflowing when the line-height is below the font's natural
 * height — display is 0.96, titles 1.05, mono labels 1.0. Android keeps lines at least as tall as
 * the font, so a plain BasicText would be ~20 % taller than the mockup; here each line is measured
 * once and drawn at its CSS position.
 */
@Composable
fun WeftText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val measurer = rememberTextMeasurer()
    // Written while measuring and read while drawing, so new text always redraws — even when it
    // measures to the same size as the old.
    var lines by remember { mutableStateOf(emptyList<Pair<TextLayoutResult, Offset>>()) }
    // Measure without the line-height: we place the lines ourselves.
    val natural = style.copy(lineHeight = TextUnit.Unspecified)
    Layout(
        content = {},
        modifier = modifier
            .semantics { this.text = text }
            .drawBehind { lines.forEach { (layout, at) -> drawText(layout, topLeft = at) } },
    ) { _, constraints ->
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val full = measurer.measure(text, natural, overflow, softWrap = true, maxLines = maxLines, constraints = Constraints(maxWidth = maxWidth))
        val count = full.lineCount
        val lineHeightPx = lineHeightPx(style, full)
        var widest = 0
        val measured = (0 until count).map { i ->
            val last = i == count - 1
            // A cut-off last line takes the rest of the text, so the ellipsis lands where it should.
            val end = if (last && full.hasVisualOverflow) text.length else full.getLineEnd(i, visibleEnd = true)
            val slice = text.subSequence(full.getLineStart(i), end)
            val one = measurer.measure(
                slice, natural,
                overflow = if (last && full.hasVisualOverflow) overflow else TextOverflow.Clip,
                softWrap = false, maxLines = 1,
                constraints = Constraints(maxWidth = maxWidth),
            )
            widest = maxOf(widest, one.size.width)
            one
        }
        val width = widest.coerceIn(constraints.minWidth, maxWidth.coerceAtLeast(constraints.minWidth))
        val placed = measured.mapIndexed { i, one ->
            val y = i * lineHeightPx + (lineHeightPx - one.size.height) / 2f
            val x = when (style.textAlign) {
                TextAlign.Center -> (width - one.size.width) / 2f
                TextAlign.End, TextAlign.Right -> (width - one.size.width).toFloat()
                else -> 0f
            }
            one to Offset(x, y)
        }
        val height = ceil(count * lineHeightPx).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        // Baselines, so rows can align text like CSS `align-items: baseline`.
        val baselines: Map<AlignmentLine, Int> = if (placed.isEmpty()) emptyMap() else mapOf(
            FirstBaseline to (placed.first().second.y + placed.first().first.firstBaseline).roundToInt(),
            LastBaseline to (placed.last().second.y + placed.last().first.lastBaseline).roundToInt(),
        )
        lines = placed
        layout(width, height, baselines) {}
    }
}

@Composable
fun WeftText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) = WeftText(AnnotatedString(text), style, modifier, maxLines, overflow)

/** CSS `line-height`: em × font-size, sp, or "normal" (the font's own height). */
private fun androidx.compose.ui.unit.Density.lineHeightPx(style: TextStyle, natural: TextLayoutResult): Float {
    val lh = style.lineHeight
    return when (lh.type) {
        TextUnitType.Em -> lh.value * style.fontSize.toPx()
        TextUnitType.Sp -> lh.toPx()
        else -> if (natural.lineCount > 0) natural.getLineBottom(0) - natural.getLineTop(0) else natural.size.height.toFloat()
    }
}
