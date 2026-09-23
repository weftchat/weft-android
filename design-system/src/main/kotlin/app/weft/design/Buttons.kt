package app.weft.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

enum class ButtonKind { Primary, Ghost, Danger }

/**
 * `.btn` — 56 dp, full width, pill. Primary uses the accent gradient with its glow
 * (`0 14px 34px -16px accent`); disabled drops to 38 % opacity with no glow.
 */
@Composable
fun WeftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    trailing: WeftIcon? = null,
    leading: WeftIcon? = null,
    leadingStroke: Float = 2.4f,
    iconSize: Dp = 18.dp,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    textStyle: TextStyle = WeftType.button,
) {
    val ink = when (kind) {
        ButtonKind.Primary -> WeftColors.onAccent
        ButtonKind.Ghost -> WeftColors.text
        ButtonKind.Danger -> WeftColors.danger
    }
    val shaped = when (kind) {
        ButtonKind.Primary -> Modifier
            .then(if (enabled) Modifier.dropShadow(CircleShape, Shadow(34.dp, WeftColors.accent, spread = (-16).dp, offset = DpOffset(0.dp, 14.dp))) else Modifier)
            .gradientBackground(CircleShape)
        ButtonKind.Ghost -> Modifier.background(WeftColors.glass, CircleShape).border(1.dp, WeftColors.line, CircleShape)
        ButtonKind.Danger -> Modifier.background(WeftColors.danger.copy(alpha = 0.08f), CircleShape)
            .border(1.dp, WeftColors.danger.copy(alpha = 0.38f), CircleShape)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (enabled) 1f else 0.38f)
            .weftClickable(enabled = enabled, onClick = onClick)
            .then(shaped),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) WeftIconView(leading, iconSize, ink, stroke = leadingStroke)
            WeftText(text, style = textStyle.copy(color = ink, fontWeight = if (kind == ButtonKind.Ghost) androidx.compose.ui.text.font.FontWeight.W700 else textStyle.fontWeight))
            if (trailing != null) WeftIconView(trailing, iconSize, ink, stroke = 2.6f)
        }
    }
}

/** `.icon-btn` (glass, 44 dp) and `.icon-btn.accent` (gradient with glow `0 8px 24px -10px`). */
@Composable
fun WeftIconButton(
    icon: WeftIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    iconSize: Dp = 20.dp,
    stroke: Float = 2f,
    tint: Color = if (accent) WeftColors.onAccent else WeftColors.text,
) {
    val look = if (accent) {
        Modifier
            .dropShadow(CircleShape, Shadow(24.dp, WeftColors.accent, spread = (-10).dp, offset = DpOffset(0.dp, 8.dp)))
            .gradientBackground(CircleShape)
    } else {
        Modifier.background(WeftColors.glass, CircleShape).border(1.dp, WeftColors.line, CircleShape)
    }
    Box(
        modifier
            .size(44.dp)
            .semantics { this.contentDescription = contentDescription }
            .weftClickable(onClick = onClick)
            .then(look),
        contentAlignment = Alignment.Center,
    ) { WeftIconView(icon, iconSize, tint, stroke = stroke) }
}

/** `.ghost` — 44 dp transparent icon button (back, minimize). */
@Composable
fun WeftGhostButton(icon: WeftIcon, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, iconSize: Dp = 22.dp, stroke: Float = 2.2f) {
    Box(
        modifier.size(44.dp).semantics { this.contentDescription = contentDescription }.weftClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { WeftIconView(icon, iconSize, WeftColors.text, stroke = stroke) }
}

/** `.pill` — mono 11 caption in a glass capsule. */
@Composable
fun WeftPill(text: String, modifier: Modifier = Modifier) {
    WeftText(
        text,
        style = WeftType.pill.copy(color = WeftColors.muted),
        maxLines = 1,
        modifier = modifier
            .background(WeftColors.glass, CircleShape)
            .cssBorder(1.dp, WeftColors.line, CircleShape)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

/** `.eyebrow` — mono 11 / +0.16 em / uppercase / accent. */
@Composable
fun WeftEyebrow(text: String, modifier: Modifier = Modifier, color: Color = WeftColors.accent) {
    WeftText(text.uppercase(), style = WeftType.eyebrow.copy(color = color), modifier = modifier)
}
