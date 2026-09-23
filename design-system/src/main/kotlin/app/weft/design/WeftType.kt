package app.weft.design

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Type from design/v3/DESIGN.md §1. Both families are bundled variable fonts (no downloadable
 * fonts): each weight is an instance of the same file picked through the `wght` axis.
 */
object WeftType {
    private fun manrope(weight: Int) = Font(
        R.font.manrope_variable,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    private fun mono(weight: Int) = Font(
        R.font.jetbrains_mono_variable,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    val Manrope = FontFamily(manrope(500), manrope(600), manrope(700), manrope(800))
    val JetBrainsMono = FontFamily(mono(400), mono(500), mono(600))

    /** Tabular numbers for times, counters and fingerprints. */
    const val TABULAR = "tnum"

    // Line boxes are placed CSS-style by WeftText; these keep plain BasicText close as a fallback.
    private val noFontPadding = PlatformTextStyle(includeFontPadding = false)
    private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    private fun sans(size: Float, weight: Int, tracking: Float = 0f, lineHeight: Float? = null) = TextStyle(
        fontFamily = Manrope,
        fontSize = size.sp,
        fontWeight = FontWeight(weight),
        letterSpacing = tracking.em,
        lineHeight = lineHeight?.em ?: TextStyle.Default.lineHeight,
        lineHeightStyle = tight,
        platformStyle = noFontPadding,
    )

    private fun monoStyle(size: Float, weight: Int, tracking: Float = 0f, lineHeight: Float? = null) = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = size.sp,
        fontWeight = FontWeight(weight),
        letterSpacing = tracking.em,
        lineHeight = lineHeight?.em ?: TextStyle.Default.lineHeight,
        fontFeatureSettings = TABULAR,
        lineHeightStyle = tight,
        platformStyle = noFontPadding,
    )

    val display = sans(50f, 800, -0.045f, 0.96f)
    val titleXl = sans(30f, 800, -0.03f, 1.05f)
    val screenTitle = sans(30f, 800, -0.03f)
    val pinTitle = sans(26f, 800, -0.02f)
    val pinKey = sans(28f, 600)
    val sheetTitle = sans(20f, 800, -0.02f)
    val wordmark = sans(18f, 800, 0.04f)
    val rowName = sans(15f, 800, -0.01f)
    /** `.head-name` — conversation header. */
    val headName = sans(16f, 800, -0.01f)
    val button = sans(15f, 800, -0.01f, 1f)
    val lede = sans(14.5f, 500, lineHeight = 1.6f)
    val body = sans(14f, 500, lineHeight = 1.45f)
    val check = sans(13.5f, 600)
    val secondary = sans(13f, 500)
    val fieldLabel = sans(12f, 700)
    val input = sans(15f, 600)
    val tab = sans(11f, 600)
    /** `.badge` — unread count. */
    val badge = sans(11f, 800)
    /** `.item-t` / `.item-d` — list and sheet option text. */
    val itemTitle = sans(14.5f, 700)
    val itemDetail = sans(12.5f, 500, lineHeight = 1.5f)

    /** Mono 11 / 500 / +0.16 em / UPPERCASE / accent. Callers upper-case the text. */
    val eyebrow = monoStyle(11f, 500, 0.16f, 1f)
    /** Mono 11 / 500 / +0.14 em / UPPERCASE / faint. */
    val sectionHeader = monoStyle(11f, 500, 0.14f)
    val monoCaption = monoStyle(11f, 400)
    val monoSmall = monoStyle(10.5f, 400)
    val pill = monoStyle(11f, 500)
    val fingerprint = monoStyle(18f, 500, 0.08f)
    val fine = monoStyle(11f, 400, lineHeight = 1.55f)
    /** `.pin-copy p` — mono 12 / 400 / 1.5. */
    val pinSub = monoStyle(12f, 400, lineHeight = 1.5f)
    /** `.relay` text and `.link-btn`. */
    val monoBody = monoStyle(12f, 400)
    val linkButton = monoStyle(12f, 600)
}
