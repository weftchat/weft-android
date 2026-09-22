package app.weft.design

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Motion system from design/v3/DESIGN.md §2: fast, ease-out, interruptible, transform + opacity only. */
object WeftMotion {
    /** Screen push/pop, sheets, tab bar in/out. */
    val EaseNav = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    /** Everything else. */
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    /** CSS `ease-in`, used by the few exits the mockup animates that way. */
    val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)

    const val PUSH = 460
    const val FADE = 280
    const val TAB_BAR = 420
    const val TAB_INDICATOR = 380
    const val SEGMENT = 340
    const val STAGGER_ITEM = 420
    const val STAGGER_GAP = 30
    const val PRESS = 160
    const val SWITCH = 280
    const val SWITCH_STRETCH = 180
    const val PIN_DOT = 260
    const val MESSAGE_IN = 340
    const val SHEET_OPEN = 480
    const val SHEET_CLOSE = 300
    const val SCRIM = 300
    const val SHEET_SPRING_BACK = 360
    const val TOAST_IN = 320
    const val TOAST_STAY = 2300
    const val TOAST_OUT = 200
    const val STEP_OUT = 180
    const val STEP_IN = 420
    const val STEP_GAP = 45

    fun <T> nav(ms: Int = PUSH, reduce: Boolean): AnimationSpec<T> = if (reduce) snap() else tween(ms, easing = EaseNav)
    fun <T> out(ms: Int, reduce: Boolean, delay: Int = 0): AnimationSpec<T> =
        if (reduce) snap() else tween(ms, delayMillis = delay, easing = EaseOut)
}

/**
 * True when the user turned on Android's "Remove animations" (animator duration scale 0).
 * Every movement then becomes an instant change or a short fade.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
