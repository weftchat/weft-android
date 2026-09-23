package app.weft.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The prototype's stroke icons (`I` table in design/v3/weft-v3.html), 24×24 viewBox, round caps and
 * joins. `<rect>` and `<circle>` elements are rewritten as path data so each icon is a single string.
 */
enum class WeftIcon(val path: String) {
    Back("M15 5l-7 7 7 7"),
    Down("M6 9l6 6 6-6"),
    Chevron("M9 6l6 6-6 6"),
    Plus("M12 5v14M5 12h14"),
    Arrow("M5 12h14M13 6l6 6-6 6"),
    Lock(rect(5f, 11f, 14f, 10f, 3f) + "M8 11V7a4 4 0 0 1 8 0v4"),
    Shield("M12 3L4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6zM9 12l2 2 4-4"),
    Chat("M4 5h16v11H9l-5 4z"),
    User(circle(12f, 8f, 4f) + "M4 21c0-4 3.5-7 8-7s8 3 8 7"),
    UserPlus(circle(9f, 8f, 4f) + "M2 20c0-4 3-6 7-6s7 2 7 6M19 8v6M16 11h6"),
    Users(circle(9f, 8f, 3.5f) + "M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.2c2.2.7 3.5 2.8 3.5 5.8"),
    Timer(circle(12f, 13f, 8f) + "M12 9v4l2 2M9 2h6"),
    Camera("M4 8h3l2-3h6l2 3h3v11H4z" + circle(12f, 13f, 3.5f)),
    Check("M20 6L9 17l-5-5"),
    Checks("M2 13l4 4L13 9M9 17l2 2 10-10"),
    Phone("M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"),
    Mic(rect(9f, 3f, 6f, 11f, 3f) + "M5 11a7 7 0 0 0 14 0M12 18v3"),
    MicOff("M15 9.4V6a3 3 0 0 0-5.7-1.3M9 9v2a3 3 0 0 0 5 2.2M5 11a7 7 0 0 0 11.4 5.4M19 11a7 7 0 0 1-.6 2.8M12 18v3M3 3l18 18"),
    Video(rect(3f, 6f, 13f, 12f, 3f) + "M16 10l5-3v10l-5-3"),
    Speaker("M4 9h4l5-4v14l-5-4H4zM16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12"),
    HangUp("M3 15.5c5-4.7 13-4.7 18 0l-2.3 2.6-3.7-1.6v-2.6a12 12 0 0 0-6 0v2.6l-3.7 1.6z"),
    Scan("M3 7V3h4M17 3h4v4M21 17v4h-4M7 21H3v-4M7 12h10"),
    Link("M10 14a4 4 0 0 0 6 0l3-3a4 4 0 0 0-6-6l-1 1M14 10a4 4 0 0 0-6 0l-3 3a4 4 0 0 0 6 6l1-1"),
    Copy(rect(8f, 8f, 12f, 12f, 3f) + "M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"),
    Refresh("M20 11a8 8 0 0 0-14.5-4.5M4 4v4h4M4 13a8 8 0 0 0 14.5 4.5M20 20v-4h-4"),
    Trash("M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"),
    Alert("M12 3L2 20h20zM12 10v4M12 17.5v.01"),
    Server(rect(4f, 4f, 16f, 7f, 2f) + rect(4f, 13f, 16f, 7f, 2f) + "M8 7.5h.01M8 16.5h.01"),
    Key(circle(8f, 15f, 4f) + "M11 12l9-9M17 6l3 3M14 9l2 2"),
    Delete("M21 5H9l-6 7 6 7h12zM17 9l-6 6M11 9l6 6"),
    Image(rect(3f, 4f, 18f, 16f, 3f) + circle(9f, 10f, 2f) + "M21 16l-5-5-9 9"),
    EyeOff("M3 3l18 18M10.6 5.1A10 10 0 0 1 12 5c6 0 9.5 7 9.5 7a17 17 0 0 1-3 3.7M6.6 6.6A17 17 0 0 0 2.5 12S6 19 12 19a9.6 9.6 0 0 0 4.4-1.1M9.9 9.9a3 3 0 0 0 4.2 4.2"),
    Mask("M3 7c3-1.5 6-2 9-2s6 .5 9 2c0 6-3.5 11-9 12C6.5 18 3 13 3 7zM7.5 10.5c1 .8 2.2.8 3 0M13.5 10.5c.8.8 2 .8 3 0"),
    Bolt("M13 2L4 14h7l-1 8 9-12h-7z"),
    PhoneBox(rect(6f, 2.5f, 12f, 19f, 3f) + "M11 18h2");

    private val vectors = HashMap<Float, ImageVector>()

    /** Stroke width is in viewBox units, exactly like the SVG `stroke-width`. */
    fun vector(strokeWidth: Float): ImageVector = vectors.getOrPut(strokeWidth) {
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = addPathNodes(path),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }
}

private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float): String {
    val r2 = r.coerceAtMost(minOf(w, h) / 2)
    return "M${x + r2} ${y}H${x + w - r2}A$r2 $r2 0 0 1 ${x + w} ${y + r2}V${y + h - r2}" +
        "A$r2 $r2 0 0 1 ${x + w - r2} ${y + h}H${x + r2}A$r2 $r2 0 0 1 $x ${y + h - r2}" +
        "V${y + r2}A$r2 $r2 0 0 1 ${x + r2} ${y}Z"
}

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0Z"

/** `ic(name, size, stroke)` from the prototype. */
@Composable
fun WeftIconView(icon: WeftIcon, size: Dp, tint: Color, modifier: Modifier = Modifier, stroke: Float = 2f) {
    Image(
        painter = rememberVectorPainter(icon.vector(stroke)),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}

// The Weft mark from design/logo (weft-app-icon.svg, viewBox 64): a solid violet speech bubble with
// two redaction bars, on a #1B1530 tile — the same as the launcher icon.
private val logoBubble = PathParser().parsePathString("M20 12H44A8 8 0 0 1 52 20V36A8 8 0 0 1 44 44H30L20 54V44A8 8 0 0 1 12 36V20A8 8 0 0 1 20 12Z").toPath()

/** The Weft mark: the app icon's tile (radius 14/64) with the bubble scaled 0.72 inside it. */
@Composable
fun WeftLogo(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 64f
        drawRoundRect(WeftColors.logoTile, cornerRadius = CornerRadius(14f * s), size = this.size)
        withTransform({
            scale(s, s, pivot = Offset.Zero)
            translate(8.96f, 8.24f)
            scale(0.72f, 0.72f, pivot = Offset.Zero)
        }) {
            drawPath(logoBubble, WeftColors.accent)
            drawPath(logoBubble, WeftColors.accent, style = Stroke(width = 5f, join = StrokeJoin.Round))
            drawRoundRect(WeftColors.logoTile, topLeft = Offset(19f, 22f), size = Size(26f, 7f), cornerRadius = CornerRadius(1.5f))
            drawLine(WeftColors.logoTile, Offset(21f, 35f), Offset(35f, 35f), strokeWidth = 3.5f, cap = StrokeCap.Round)
        }
    }
}
