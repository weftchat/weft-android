package app.weft.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/**
 * Button press from DESIGN.md: scale 0.96 (full-width rows 0.985), 160 ms EaseOut. No ripple —
 * the prototype has none. Keeps the ≥ 44 dp target to the caller's size.
 */
fun Modifier.weftClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    role: Role = Role.Button,
    label: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = rememberReduceMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = if (reduce) tween(0) else tween(WeftMotion.PRESS, easing = WeftMotion.EaseOut),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(source, indication = null, enabled = enabled, onClickLabel = label, role = role, onClick = onClick)
}

@Composable
fun rememberPressSource() = remember { MutableInteractionSource() }
