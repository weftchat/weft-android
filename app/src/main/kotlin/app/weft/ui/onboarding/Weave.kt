package app.weft.ui.onboarding

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.weft.design.WeftColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * The 3D weave of design/v3 (the mockup's three.js `Weave` class), drawn with a plain Canvas:
 * 24 vertical + 24 horizontal threads of 160 points, additive, perspective FOV 36° from (0, 0.4, 14.5),
 * cloth rotated −1.05 rad on X and 0.5 rad on Z, a shuttle glow, and the "knot" used while keys are made.
 */
@Stable
class WeaveState {
    internal var t = 1.5f
    internal var k = 0f
    internal var kTarget = 0f
    internal var spin = 0f

    /** 1 while keys are being made, 0.35 afterwards, 0 at rest. Eased with factor 2.6/s. */
    fun knot(value: Float, instant: Boolean = false) {
        kTarget = value
        if (instant) k = value
    }

    internal fun update(dt: Float) {
        t += dt
        k += (kTarget - k) * min(1f, dt * 2.6f)
        spin += dt * 0.5f * k
    }
}

private const val N = 24
private const val SEG = 160
private const val SPAN = 7.2f
private const val CHUNK = 8
private val FOCAL = (1.0 / tan(Math.toRadians(18.0))).toFloat()

private class WeaveGeometry {
    val xs = FloatArray(SEG)
    val ys = FloatArray(SEG)
    val visible = BooleanArray(SEG)
    val seg = FloatArray(CHUNK * 4)
    // Per-thread, per-point colour factors do not change between frames.
    val colours = Array(2) { dir ->
        Array(N) { i ->
            val base = if (dir == 0) WeftColors.accent else WeftColors.safe
            val c = WeftColors.weaveHighlight
            val across = i / (N - 1f)
            IntArray(SEG) { s ->
                val u = s / (SEG - 1f)
                val edge = (sin(PI * u) * sin(PI * across)).toFloat().coerceAtLeast(0f).pow(1.2f)
                val f = edge * (if (dir == 0) 1f else 0.62f)
                val m = 0.25f * sin(PI * u).toFloat()
                val r = (base.red + (c.red - base.red) * m) * f
                val g = (base.green + (c.green - base.green) * m) * f
                val b = (base.blue + (c.blue - base.blue) * m) * f
                android.graphics.Color.argb(255, (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
            }
        }
    }
    val out = FloatArray(3)
}

/** Point on thread [i] of direction [dir] at [u] ∈ [0,1], in cloth space. Same maths as the mockup. */
private fun point(dir: Int, i: Int, u: Float, t: Float, k: Float, out: FloatArray) {
    val cell = 2 * SPAN / (N - 1)
    val a = (i / (N - 1f)) * 2 - 1
    val b = u * 2 - 1
    var x = if (dir == 0) a * SPAN else b * SPAN
    var y = if (dir == 0) b * SPAN else a * SPAN
    val along = if (dir == 0) y else x
    var z = 0.26f * sin(PI.toFloat() * along / cell + i * PI.toFloat() + if (dir == 1) PI.toFloat() else 0f)
    z += 0.6f * sin(x * 0.33f + t * 0.55f) * cos(y * 0.29f - t * 0.42f) + 0.25f * sin((x + y) * 0.5f + t * 0.8f)
    if (k > 0.001f) {
        val r = hypot(x, y)
        val ang = atan2(y, x)
        val fall = exp(-r * r / 38f)
        val nr = r * (1 - 0.62f * k * fall)
        val na = ang + k * 1.9f * exp(-r / 5.5f) + k * 0.35f * sin(t * 1.6f)
        x = nr * cos(na); y = nr * sin(na)
        z = z * (1 - 0.5f * k * fall) + k * 1.1f * exp(-r * r / 10f) * sin(ang * 3 + t * 2)
    }
    out[0] = x; out[1] = y; out[2] = z
}

@Composable
fun Weave(state: WeaveState, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val geo = remember { WeaveGeometry() }
    var frame by remember { mutableLongStateOf(0L) }
    val linePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
    }
    val glowPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Animate only while the screen is resumed; with "Remove animations" draw one still frame.
    LaunchedEffect(reduceMotion, lifecycle) {
        if (reduceMotion) { state.k = state.kTarget; frame++; return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            // Start after the first frame so the weave never delays cold start.
            withFrameNanos { last = it }
            while (true) {
                withFrameNanos { now ->
                    val dt = min(0.05f, (now - last) / 1e9f)
                    last = now
                    state.update(dt)
                    frame = now
                }
            }
        }
    }
    LaunchedEffect(reduceMotion, state.kTarget) { if (reduceMotion) { state.k = state.kTarget; frame++ } }

    Canvas(modifier) {
        frame // read to redraw on every tick
        val w = size.width
        val h = size.height
        val aspect = w / h
        val t = state.t
        val k = state.k
        val rz = 0.5f + state.spin + sin(t * 0.15f) * 0.05f
        val cz = cos(rz); val sz = sin(rz)
        val rx = -1.05f
        val cx = cos(rx); val sx = sin(rx)
        val o = geo.out

        fun project(px: Float, py: Float, pz: Float, idx: Int): Boolean {
            // Euler XYZ on the group: world = Rx · Rz · p (no Y rotation).
            val x1 = px * cz - py * sz
            val y1 = px * sz + py * cz
            val y2 = y1 * cx - pz * sx
            val z2 = y1 * sx + pz * cx
            val vx = x1
            val vy = y2 - 0.4f
            val vz = z2 - 14.5f
            if (vz > -0.1f) return false
            geo.xs[idx] = (vx * FOCAL / aspect / -vz + 1f) * 0.5f * w
            geo.ys[idx] = (1f - vy * FOCAL / -vz) * 0.5f * h
            return true
        }

        linePaint.strokeWidth = max(1f, density * 0.5f)
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            for (dir in 0..1) for (i in 0 until N) {
                for (s in 0 until SEG) {
                    point(dir, i, s / (SEG - 1f), t, k, o)
                    geo.visible[s] = project(o[0], o[1], o[2], s)
                }
                val cols = geo.colours[dir][i]
                var s = 0
                while (s < SEG - 1) {
                    val end = min(s + CHUNK, SEG - 1)
                    var n = 0
                    for (j in s until end) {
                        if (!geo.visible[j] || !geo.visible[j + 1]) continue
                        geo.seg[n++] = geo.xs[j]; geo.seg[n++] = geo.ys[j]
                        geo.seg[n++] = geo.xs[j + 1]; geo.seg[n++] = geo.ys[j + 1]
                    }
                    if (n > 0) {
                        linePaint.color = cols[(s + end) / 2]
                        nc.drawLines(geo.seg, 0, n, linePaint)
                    }
                    s = end
                }
            }

            // Shuttle: a glow running along one horizontal thread per 2.6 s, alternating direction.
            val pass = 2.6f
            val row = (floor(t / pass).toInt() % (N - 6)) + 3
            val u0 = (t % pass) / pass
            val u = if (row % 2 == 1) 1 - u0 else u0
            point(1, row, u, t, k, o)
            val shuttleAlpha = sin(PI.toFloat() * u) * (1 - k * 0.6f)
            glow(nc, glowPaint, o[0], o[1], o[2] + 0.05f, 1.1f, shuttleAlpha, null, ::project, geo, h)
            // Knot core: centre glow while keys are made.
            if (k > 0.01f) glow(nc, glowPaint, 0f, 0f, 0f, 5f, k * 0.55f, WeftColors.weaveCore.toArgb(), ::project, geo, h)
        }
    }
}

private fun glow(
    nc: android.graphics.Canvas,
    paint: Paint,
    x: Float, y: Float, z: Float,
    worldSize: Float,
    alpha: Float,
    tint: Int?,
    project: (Float, Float, Float, Int) -> Boolean,
    geo: WeaveGeometry,
    h: Float,
) {
    if (alpha <= 0.01f) return
    if (!project(x, y, z, 0)) return
    // Depth of the projected point, recomputed for the sprite's pixel size.
    val sx = geo.xs[0]; val sy = geo.ys[0]
    val approxDepth = 14.5f
    val radius = worldSize * FOCAL / approxDepth * h / 2f / 2f
    val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
    val centre = tint ?: android.graphics.Color.WHITE
    val mid = tint?.let { android.graphics.Color.argb((0.6f * 255).toInt(), android.graphics.Color.red(it), android.graphics.Color.green(it), android.graphics.Color.blue(it)) }
        ?: android.graphics.Color.argb((0.6f * 255).toInt(), 210, 195, 255)
    val edge = tint?.let { android.graphics.Color.argb(0, android.graphics.Color.red(it), android.graphics.Color.green(it), android.graphics.Color.blue(it)) }
        ?: android.graphics.Color.argb(0, 167, 139, 250)
    paint.shader = RadialGradient(sx, sy, radius.coerceAtLeast(1f), intArrayOf(centre, mid, edge), floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP)
    paint.alpha = a
    nc.drawCircle(sx, sy, radius, paint)
    paint.shader = null
}
