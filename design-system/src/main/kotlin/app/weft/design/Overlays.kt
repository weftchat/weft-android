package app.weft.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/* ---------------------------------------------------------------- toast */

@Stable
class WeftToastState {
    internal var message by mutableStateOf<String?>(null)
    internal var icon by mutableStateOf(WeftIcon.Check)
    internal var serial by mutableIntStateOf(0)

    /** One at a time; a new one replaces the old. */
    fun show(message: String, icon: WeftIcon = WeftIcon.Check) {
        this.message = message
        this.icon = icon
        serial++
    }
}

/**
 * `.toast` — bottom-centre. In: rise 10 dp + scale 0.96 → 1, 320 ms EaseOut. Stays 2.3 s. Out: 200 ms.
 * [bottom] is the distance from the bottom edge (above the tab bar or the composer).
 */
@Composable
fun WeftToastHost(state: WeftToastState, bottom: Dp, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val progress = remember { Animatable(0f) }
    var outgoing by remember { mutableStateOf(false) }
    LaunchedEffect(state.serial) {
        if (state.serial == 0) return@LaunchedEffect
        outgoing = false
        progress.snapTo(0f)
        progress.animateTo(1f, WeftMotion.out(WeftMotion.TOAST_IN, reduce))
        delay(WeftMotion.TOAST_STAY.toLong())
        outgoing = true
        progress.animateTo(0f, if (reduce) tween(0) else tween(WeftMotion.TOAST_OUT, easing = WeftMotion.EaseIn))
        state.message = null
    }
    val msg = state.message ?: return
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val p = progress.value
        Row(
            Modifier
                .padding(bottom = bottom)
                .graphicsLayer {
                    alpha = p
                    val lift = if (outgoing) 6.dp.toPx() * (1 - p) else 10.dp.toPx() * (1 - p)
                    translationY = lift
                    val s = if (outgoing) 0.98f + 0.02f * p else 0.96f + 0.04f * p
                    scaleX = s; scaleY = s
                }
                .semantics { liveRegion = LiveRegionMode.Polite }
                .dropShadow(RoundedCornerShape(16.dp), Shadow(40.dp, Color.Black.copy(alpha = 0.7f), spread = (-12).dp, offset = DpOffset(0.dp, 18.dp)))
                .background(WeftColors.toast, RoundedCornerShape(16.dp))
                .border(1.dp, WeftColors.line2, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeftIconView(state.icon, 16.dp, WeftColors.safe, stroke = 2.4f)
            WeftText(msg, style = WeftType.secondary.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.W600, color = WeftColors.text), maxLines = 1)
        }
    }
}

/* ---------------------------------------------------------------- bottom sheet */

/**
 * `.sheet` — scrim fades to black @ 62 % in 300 ms; the sheet slides up from 115 %, 480 ms EaseNav,
 * closes in 300 ms. Drag the handle to dismiss: it follows the finger, dragging up resists
 * (square-root), dismisses past 90 dp or faster than 0.5 dp/ms, otherwise springs back in 360 ms.
 * Scrim tap and system back close it.
 */
@Composable
fun WeftBottomSheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val reduce = rememberReduceMotion()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val open = remember { Animatable(0f) } // 0 closed, 1 open
    val drag = remember { Animatable(0f) } // px offset from the finger
    var height by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            shown = true
            drag.snapTo(0f)
            open.animateTo(1f, WeftMotion.nav(WeftMotion.SHEET_OPEN, reduce))
        } else if (shown) {
            open.animateTo(0f, if (reduce) tween(0) else tween(WeftMotion.SHEET_CLOSE, easing = WeftMotion.EaseNav))
            shown = false
        }
    }
    if (!shown) return
    BackHandler(enabled = visible, onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = open.value * (1f - (drag.value / (height.coerceAtLeast(1))).coerceIn(0f, 1f)) }
                .background(Color(0xFF030308).copy(alpha = 0.62f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close", role = Role.Button, onClick = onDismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(8.dp)
                .fillMaxWidth()
                .onSizeChanged { height = it.height }
                .graphicsLayer { translationY = (1f - open.value) * height * 1.15f + drag.value }
                .background(WeftColors.sheet, RoundedCornerShape(34.dp))
                .border(1.dp, WeftColors.line2, RoundedCornerShape(34.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) { /* swallow scrim taps */ }
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp)
                    .semantics { contentDescription = "Drag down to close" }
                    .pointerInput(Unit) {
                        val tracker = VelocityTracker()
                        var raw = 0f
                        var job: Job? = null
                        detectVerticalDragGestures(
                            onDragStart = { raw = 0f; tracker.resetTracking(); job?.cancel() },
                            onVerticalDrag = { change, dy ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                raw += dy
                                // Downwards follows the finger; upwards resists (square-root).
                                val shown2 = if (raw >= 0) raw else -sqrt(abs(raw)) * 4f
                                job = scope.launch { drag.snapTo(shown2) }
                            },
                            onDragEnd = {
                                val vy = tracker.calculateVelocity().y // px/s
                                val pxPerMs = vy / 1000f
                                val dpPerMs = with(density) { pxPerMs.toDp().value }
                                val dismiss = drag.value > with(density) { 90.dp.toPx() } || dpPerMs > 0.5f
                                if (dismiss) onDismiss()
                                else scope.launch { drag.animateTo(0f, WeftMotion.out(WeftMotion.SHEET_SPRING_BACK, reduce)) }
                            },
                            onDragCancel = { scope.launch { drag.animateTo(0f, WeftMotion.out(WeftMotion.SHEET_SPRING_BACK, reduce)) } },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(40.dp, 5.dp).background(Color.White.copy(alpha = 0.22f), CircleShape)) }
            content()
        }
    }
}

/* ---------------------------------------------------------------- tab bar */

data class TabItem(val label: String, val icon: WeftIcon)

/**
 * `.tabbar` — floating, 12 dp from the sides, 14 dp from the bottom, radius 26, #14141F @ 88 %.
 * Slides down (140 %) when leaving a tab screen, 420 ms EaseNav; the highlight slides between tabs,
 * 380 ms EaseOut. (The mockup's backdrop blur is not reproduced: Compose has no backdrop blur.)
 */
@Composable
fun WeftTabBar(items: List<TabItem>, selected: Int, visible: Boolean, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val hide = remember { Animatable(if (visible) 0f else 1f) }
    LaunchedEffect(visible) { hide.animateTo(if (visible) 0f else 1f, WeftMotion.nav(WeftMotion.TAB_BAR, reduce)) }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 14.dp)
            .graphicsLayer {
                translationY = hide.value * size.height * 1.4f
                alpha = (1f - hide.value / 0.57f).coerceIn(0f, 1f) // opacity fades in 240 ms of the 420
            }
            .background(WeftColors.tabBar, RoundedCornerShape(26.dp))
            .border(1.dp, WeftColors.line, RoundedCornerShape(26.dp))
            .padding(6.dp),
    ) {
        val cell = maxWidth / items.size
        val x by animateDpAsState(cell * selected.coerceAtLeast(0), WeftMotion.out(WeftMotion.TAB_INDICATOR, reduce), label = "tabInd")
        Box(Modifier.offset(x = x).width(cell).height(54.dp).background(WeftColors.accent.copy(alpha = 0.13f), RoundedCornerShape(20.dp)))
        Row(Modifier.fillMaxWidth().height(54.dp)) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { this.selected = on }
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab, enabled = visible) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    WeftIconView(item.icon, 22.dp, if (on) WeftColors.accent else WeftColors.faint)
                    WeftText(item.label, style = WeftType.tab.copy(color = if (on) WeftColors.text else WeftColors.faint))
                }
            }
        }
    }
}

