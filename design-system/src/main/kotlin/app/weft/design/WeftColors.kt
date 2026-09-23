package app.weft.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Colour tokens from design/v3/DESIGN.md §1. Weft is dark only in v1. */
object WeftColors {
    val bg = Color(0xFF0A0A12)
    val surface = Color(0xFF12121C)
    val sheet = Color(0xFF161623)
    val toast = Color(0xFF20202F)
    val tabBar = Color(0xFF14141F).copy(alpha = 0.88f)

    val glass = Color.White.copy(alpha = 0.04f)
    val glassPressed = Color.White.copy(alpha = 0.08f)
    val line = Color.White.copy(alpha = 0.08f)
    val line2 = Color.White.copy(alpha = 0.14f)

    val text = Color(0xFFF4F3FF)
    val muted = Color(0xFFA3A1BE)
    val faint = Color(0xFF8886A8)

    val accent = Color(0xFFA78BFA)
    val accent2 = Color(0xFFC4B5FD)
    val onAccent = Color(0xFF0B0B14)
    /** The logo tile behind the bubble (design/logo: icon background). */
    val logoTile = Color(0xFF1B1530)

    /** Only for encrypted / verified / "always on" / live relay. */
    val safe = Color(0xFF5CF0B8)
    /** Only for wipe / delete / blocked relay / leave call. */
    val danger = Color(0xFFFF6B81)
    /** Relay switching. */
    val warn = Color(0xFFF7C86A)

    val switchOff = Color(0xFF2C2B3E)
    val emptyDot = Color(0xFF3A3950)
    val forgeRing = Color(0xFF34334A)
    val offDot = Color(0xFF57566E)

    /** Weave: end colour the threads lerp towards. */
    val weaveHighlight = Color(0xFFE4DCFF)
    val weaveCore = Color(0xFFB9A6FF)

    /**
     * The 135° accent gradient used by primary buttons, own bubbles, badges and the FAB.
     * CSS 135° runs from the top-left towards the bottom-right of the element's box.
     */
    fun gradient(width: Float, height: Float): Brush =
        Brush.linearGradient(listOf(accent, accent2), start = Offset.Zero, end = Offset(width, height))

    /** Avatars: `hsl(h, 32 %, 19 %)` fill with `hsl(h, 80 %, 84 %)` initials. */
    fun avatarFill(hue: Float) = hsl(hue, 0.32f, 0.19f)
    fun avatarInk(hue: Float) = hsl(hue, 0.80f, 0.84f)

    fun hsl(h: Float, s: Float, l: Float): Color = Color.hsl(((h % 360f) + 360f) % 360f, s, l)

    fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)
}
