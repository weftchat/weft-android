package app.weft.nav

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.weft.design.WeftMotion
import app.weft.design.rememberReduceMotion
import kotlinx.coroutines.CancellationException

enum class NavMode { Push, Pop, Fade }

/**
 * A small back stack with the exact transitions of design/v3/DESIGN.md §2:
 * - Push: the new screen slides in from the right (100 % → 0, 460 ms EaseNav); the old one moves
 *   to −28 % and dims to 50 % brightness. Pop is the exact reverse.
 * - Fade (root change / tab switch): fade + scale 0.985 → 1, 280 ms EaseOut; the old screen fades
 *   out in ~200 ms.
 * System back and predictive back drive the same pop animation.
 */
@Stable
class WeftNavigator(start: Route) {
    val stack = mutableStateListOf(start)
    val current: Route get() = stack.last()

    internal var transition by mutableStateOf<NavTransition?>(null)
    internal val removed = mutableStateListOf<Route>()

    fun push(to: Route) = go(to, NavMode.Push, stack + to)
    fun pop() { if (stack.size > 1) go(stack[stack.size - 2], NavMode.Pop, stack.dropLast(1)) }
    fun root(to: Route, mode: NavMode = NavMode.Fade) = go(to, mode, listOf(to))

    private fun go(to: Route, mode: NavMode, newStack: List<Route>) {
        val from = current
        if (from == to && mode != NavMode.Fade) return
        removed += stack.filter { it !in newStack }
        stack.clear(); stack.addAll(newStack)
        transition = NavTransition(from, to, mode, Animatable(0f), manual = false)
    }

    /** Starts a pop driven by the predictive-back gesture; progress is set by the caller. */
    internal fun beginGesturePop(): NavTransition? {
        if (stack.size < 2 || transition != null) return null
        val from = current
        val to = stack[stack.size - 2]
        return NavTransition(from, to, NavMode.Pop, Animatable(0f), manual = true).also { transition = it }
    }

    internal fun commitGesturePop() {
        removed += stack.last()
        stack.removeAt(stack.lastIndex)
    }
}

internal class NavTransition(val from: Route, val to: Route, val mode: NavMode, val progress: Animatable<Float, *>, val manual: Boolean)

@Composable
fun WeftNavHost(nav: WeftNavigator, modifier: Modifier = Modifier, content: @Composable (Route) -> Unit) {
    val reduce = rememberReduceMotion()
    val holder = rememberSaveableStateHolder()
    val t = nav.transition

    LaunchedEffect(t) {
        if (t == null || t.manual) return@LaunchedEffect
        val spec = when {
            reduce -> tween<Float>(1)
            t.mode == NavMode.Fade -> tween(WeftMotion.FADE, easing = WeftMotion.EaseOut)
            else -> tween(WeftMotion.PUSH, easing = WeftMotion.EaseNav)
        }
        t.progress.animateTo(1f, spec)
        finish(nav, holder::removeState)
    }

    PredictiveBackHandler(enabled = nav.stack.size > 1 && t == null) { events ->
        val gesture = nav.beginGesturePop() ?: return@PredictiveBackHandler
        try {
            events.collect { e -> gesture.progress.snapTo(e.progress) }
            // Released: finish the pop from wherever the finger left it.
            val rest = ((1f - gesture.progress.value) * WeftMotion.PUSH).toInt().coerceAtLeast(1)
            gesture.progress.animateTo(1f, if (reduce) tween(1) else tween(rest, easing = WeftMotion.EaseNav))
            nav.commitGesturePop()
            finish(nav, holder::removeState)
        } catch (c: CancellationException) {
            gesture.progress.animateTo(0f, if (reduce) tween(1) else tween(WeftMotion.SHEET_SPRING_BACK, easing = WeftMotion.EaseOut))
            nav.transition = null
            throw c
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val p = t?.progress?.value ?: 0f
        // One keyed call site for every layer, so a screen keeps its state (and isn't recreated)
        // when its transition ends and it becomes the only screen on show.
        val layers: List<Pair<Route, Modifier>> = when (t?.mode) {
            null -> listOf(nav.current to Modifier)
            NavMode.Push -> listOf(t.from to Modifier.under(w, p), t.to to Modifier.over(w, 1f - p, shadow = p))
            NavMode.Pop -> listOf(t.to to Modifier.under(w, 1f - p), t.from to Modifier.over(w, p, shadow = 1f - p))
            NavMode.Fade -> listOf(
                t.from to Modifier.graphicsLayer { alpha = 1f - (p / 0.7f).coerceIn(0f, 1f) },
                t.to to Modifier.graphicsLayer { alpha = p; val s = 0.985f + 0.015f * p; scaleX = s; scaleY = s },
            )
        }
        for ((route, layer) in layers) {
            key(route.key) {
                Box(layer.fillMaxSize()) { holder.SaveableStateProvider(route.key) { content(route) } }
            }
        }
    }
}

private fun finish(nav: WeftNavigator, removeState: (Any) -> Unit) {
    nav.removed.forEach { removeState(it.key) }
    nav.removed.clear()
    nav.transition = null
}

/** The screen underneath: moves to −28 % and dims to 50 % brightness as [amount] goes 0 → 1. */
private fun Modifier.under(width: Float, amount: Float) = this
    .graphicsLayer { translationX = -0.28f * width * amount }
    .drawWithContent {
        drawContent()
        drawRect(Color.Black.copy(alpha = 0.5f * amount))
    }

/** The screen on top: offset by [slide] × width to the right, casting `-30px 0 60px rgba(0,0,0,.5)`. */
private fun Modifier.over(width: Float, slide: Float, shadow: Float) = this
    .graphicsLayer { translationX = width * slide }
    .drawWithContent {
        val sw = 90.dp.toPx()
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f * shadow)), startX = -sw, endX = 0f),
            topLeft = Offset(-sw, 0f),
            size = Size(sw, size.height),
        )
        drawContent()
    }
