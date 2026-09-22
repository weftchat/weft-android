package app.weft.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.weft.BuildConfig
import app.weft.R
import app.weft.design.DotState
import app.weft.design.StatusDot
import app.weft.design.WeftButton
import app.weft.design.WeftColors
import app.weft.design.WeftEyebrow
import app.weft.design.WeftFine
import app.weft.design.WeftIcon
import app.weft.design.WeftIconView
import app.weft.design.WeftLogo
import app.weft.design.WeftMotion
import app.weft.design.WeftPill
import app.weft.design.WeftText
import app.weft.design.WeftTextField
import app.weft.design.WeftType
import app.weft.design.gradientBackground
import app.weft.design.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private enum class Step { Intro, Forge, Name }
private enum class ForgeState { Idle, Run, Done }

/**
 * Screen 1 — Create identity (design/v3 §3.1): weave, intro → key forging → nickname.
 * [makeIdentity] does the real work and returns the 8 fingerprint groups; the forging animation
 * lasts at least as long as the work.
 */
@Composable
fun OnboardingScreen(
    makeIdentity: suspend () -> List<String>,
    onChoosePin: (nickname: String) -> Unit,
) {
    val reduce = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(Step.Intro) }
    // Coming back to this screen (back from PIN, or the process restored) shows the step as it was
    // left, without replaying its entrance. A forge cut short restarts from the intro.
    var seen by rememberSaveable { mutableStateOf(false) }
    val returning = remember { seen }
    SideEffect { seen = true }
    remember { if (step == Step.Forge) step = Step.Intro }
    val weave = remember { WeaveState().also { if (step == Step.Name) it.knot(0.35f, instant = true) } }
    var shown by remember { mutableStateOf(step) }
    val entered = remember { if (returning) step else null }
    val exit = remember { Animatable(0f) }
    var fingerprint by rememberSaveable { mutableStateOf(List(8) { "0000" }) }
    var nick by rememberSaveable { mutableStateOf("") }
    val forgeRows = remember { mutableStateOf(List(3) { ForgeState.Idle }) }
    var scrambleStart by remember { mutableStateOf<Long?>(null) }

    // Old step fades out and rises 8 dp (180 ms), then the new step's children rise in.
    LaunchedEffect(step) {
        if (shown == step) return@LaunchedEffect
        exit.animateTo(1f, if (reduce) tween(1) else tween(WeftMotion.STEP_OUT, easing = WeftMotion.EaseIn))
        shown = step
        exit.snapTo(0f)
    }

    fun forge() {
        step = Step.Forge
        weave.knot(1f, instant = reduce)
        scope.launch {
            val stepMs = if (reduce) 60L else 640L
            val work = scope.launch { fingerprint = makeIdentity() }
            scrambleStart = null
            forgeRows.value = List(3) { ForgeState.Idle }
            // Row i: run at i·640+120, done at (i+1)·640+60 (DESIGN.md §2, key forging).
            val t0 = System.currentTimeMillis()
            work.join()
            scrambleStart = System.currentTimeMillis()
            for (i in 0 until 3) {
                launch {
                    delay((i * stepMs + 120 - (System.currentTimeMillis() - t0)).coerceAtLeast(0))
                    forgeRows.value = forgeRows.value.toMutableList().also { it[i] = ForgeState.Run }
                    delay(stepMs - 60)
                    forgeRows.value = forgeRows.value.toMutableList().also { it[i] = ForgeState.Done }
                }
            }
            delay((3 * stepMs + 520 - (System.currentTimeMillis() - t0)).coerceAtLeast(0))
            weave.knot(0.35f, instant = reduce)
            step = Step.Name
        }
    }

    val density = LocalDensity.current
    val topInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    Box(Modifier.fillMaxSize().background(WeftColors.bg).imePadding()) {
        Weave(weave, reduce, Modifier.fillMaxWidth().height(540.dp).clipToBounds())
        // `.weave-fade`: top 260, height 290, transparent → bg at 78 %.
        Box(
            Modifier
                .padding(top = 260.dp)
                .fillMaxWidth()
                .height(290.dp)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.78f to WeftColors.bg, 1f to WeftColors.bg)),
        )
        Column(
            Modifier
                .fillMaxSize()
                // The mockup's 30 dp already covers the home-indicator area: only grow past it.
                .padding(start = 24.dp, end = 24.dp, bottom = maxOf(30.dp, bottomInset)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = maxOf(58.dp, topInset + 14.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WeftLogo(28.dp)
                WeftText("weft", style = WeftType.wordmark.copy(color = WeftColors.text))
                Spacer(Modifier.weight(1f))
                WeftPill(stringResource(R.string.onboard_version, BuildConfig.VERSION_NAME))
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.graphicsLayer {
                    alpha = 1f - exit.value
                    translationY = -8.dp.toPx() * exit.value
                },
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CompositionLocalProvider(LocalSkipEntrance provides (shown == entered)) {
                when (shown) {
                    Step.Intro -> IntroStep(reduce, onCreate = ::forge)
                    Step.Forge -> ForgeStep(reduce, fingerprint, scrambleStart, forgeRows.value)
                    Step.Name -> NameStep(reduce, fingerprint, nick, { nick = it }) { onChoosePin(nick.trim()) }
                }
                }
            }
        }
    }
}

private val LocalSkipEntrance = staticCompositionLocalOf { false }

/** Children rise 12 dp and fade in, 420 ms EaseOut, 45 ms apart. */
@Composable
private fun Modifier.stagger(index: Int, key: Any, reduce: Boolean): Modifier {
    val skip = LocalSkipEntrance.current
    val a = remember(key) { Animatable(if (reduce || skip) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduce) a.animateTo(1f, tween(WeftMotion.STEP_IN, delayMillis = index * WeftMotion.STEP_GAP, easing = WeftMotion.EaseOut))
    }
    return graphicsLayer {
        alpha = a.value
        translationY = 12.dp.toPx() * (1f - a.value)
    }
}

@Composable
private fun ColumnScope.IntroStep(reduce: Boolean, onCreate: () -> Unit) {
    val k = Step.Intro
    WeftEyebrow(stringResource(R.string.onboard_eyebrow), Modifier.stagger(0, k, reduce))
    WeftText(stringResource(R.string.onboard_display), style = WeftType.display.copy(color = WeftColors.text), modifier = Modifier.stagger(1, k, reduce))
    WeftText(stringResource(R.string.onboard_lede), style = WeftType.lede.copy(color = WeftColors.muted), modifier = Modifier.stagger(2, k, reduce))
    Column(Modifier.stagger(3, k, reduce), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(R.string.onboard_check_1, R.string.onboard_check_2, R.string.onboard_check_3).forEach {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(22.dp).background(WeftColors.safe.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    WeftIconView(WeftIcon.Check, 13.dp, WeftColors.safe, stroke = 3f)
                }
                WeftText(stringResource(it), style = WeftType.check.copy(color = WeftColors.text))
            }
        }
    }
    WeftButton(stringResource(R.string.onboard_create), onCreate, Modifier.stagger(4, k, reduce), trailing = WeftIcon.Arrow)
    WeftFine(stringResource(R.string.onboard_fine), Modifier.stagger(5, k, reduce))
}

@Composable
private fun ColumnScope.ForgeStep(reduce: Boolean, fingerprint: List<String>, scrambleStart: Long?, rows: List<ForgeState>) {
    val k = Step.Forge
    WeftEyebrow(stringResource(R.string.forge_eyebrow), Modifier.stagger(0, k, reduce))
    WeftText(stringResource(R.string.forge_title), style = WeftType.titleXl.copy(color = WeftColors.text), modifier = Modifier.stagger(1, k, reduce))
    ScrambledFingerprint(fingerprint, scrambleStart, if (reduce) 0 else 1900, Modifier.stagger(2, k, reduce))
    Column(Modifier.stagger(3, k, reduce).fillMaxWidth().topBorder()) {
        val names = listOf(R.string.forge_device_key to "Ed25519", R.string.forge_ratchet to "X25519 + sntrup761", R.string.forge_queues to "0 shared IDs")
        names.forEachIndexed { i, (label, alg) -> ForgeRow(stringResource(label), alg, rows[i], reduce) }
    }
    WeftFine(stringResource(R.string.forge_fine), Modifier.stagger(4, k, reduce))
}

@Composable
private fun ForgeRow(label: String, alg: String, state: ForgeState, reduce: Boolean) {
    val ink by animateColorAsState(if (state == ForgeState.Idle) WeftColors.faint else WeftColors.text, tween(if (reduce) 0 else 300), label = "forgeInk")
    Row(
        Modifier.fillMaxWidth().bottomBorder().padding(horizontal = 2.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ForgeStatus(state, reduce)
        WeftText(label, style = WeftType.body.copy(fontWeight = FontWeight.W700, color = ink, lineHeight = 1.2.em))
        Spacer(Modifier.weight(1f))
        WeftText(alg, style = WeftType.monoCaption.copy(color = WeftColors.faint))
    }
}

/** 22 dp ring: idle #34334A · running spinner (accent, top gap, 700 ms/turn) · done green tick. */
@Composable
private fun ForgeStatus(state: ForgeState, reduce: Boolean) {
    val spin = if (state == ForgeState.Run && !reduce) {
        rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart), label = "spinA").value
    } else 0f
    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        when (state) {
            ForgeState.Done -> Box(Modifier.size(22.dp).background(WeftColors.safe.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                WeftIconView(WeftIcon.Check, 12.dp, WeftColors.safe, stroke = 3f)
            }
            else -> Canvas(Modifier.size(22.dp).rotate(spin)) {
                val sw = 2.dp.toPx()
                val arc = Size(size.width - sw, size.height - sw)
                val tl = Offset(sw / 2, sw / 2)
                if (state == ForgeState.Idle) {
                    drawArc(WeftColors.forgeRing, 0f, 360f, false, tl, arc, style = Stroke(sw))
                } else {
                    // CSS border with a transparent top: three quarters of the ring.
                    drawArc(WeftColors.accent, -45f, 270f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Butt))
                }
            }
        }
    }
}

/**
 * `.fp-grid` — 4 columns, 18 sp mono. Groups scramble random hex and settle left to right over
 * [durationMs]; settled groups turn from muted to text, the last one to accent.
 */
@Composable
private fun ScrambledFingerprint(groups: List<String>, start: Long?, durationMs: Int, modifier: Modifier = Modifier, fontSize: Float = 18f) {
    var now by remember { mutableStateOf(0L) }
    LaunchedEffect(start) {
        if (start == null || durationMs == 0) return@LaunchedEffect
        while (System.currentTimeMillis() - start < durationMs * 1.1) { withFrameMillis { now = System.currentTimeMillis() } }
        now = System.currentTimeMillis()
    }
    val p = if (start == null) -1f else if (durationMs == 0) 2f else (now - start).toFloat() / durationMs
    val hex = "0123456789ABCDEF"
    FingerprintGrid(
        groups.mapIndexed { gi, g ->
            val settle = (gi + 1f) / groups.size
            val text = if (p < 0) "····" else buildString {
                for (ci in 0 until 4) append(if (p >= settle - 0.1f + ci * 0.025f) g[ci] else hex[Random.nextInt(16)])
            }
            text to (p >= settle)
        },
        fontSize, modifier,
    )
}

@Composable
private fun FingerprintGrid(groups: List<Pair<String, Boolean>>, fontSize: Float, modifier: Modifier = Modifier) {
    Column(modifier.semantics { contentDescription = "Your device fingerprint" }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.chunked(4).forEachIndexed { rowIdx, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { colIdx, (text, done) ->
                    val last = rowIdx == 1 && colIdx == 3
                    val target = when { !done -> WeftColors.muted; last -> WeftColors.accent; else -> WeftColors.text }
                    val ink by animateColorAsState(target, tween(300), label = "fp")
                    WeftText(text, style = WeftType.fingerprint.copy(color = ink, fontSize = androidx.compose.ui.unit.TextUnit(fontSize, androidx.compose.ui.unit.TextUnitType.Sp)), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.NameStep(reduce: Boolean, fingerprint: List<String>, nick: String, onNick: (String) -> Unit, onNext: () -> Unit) {
    val k = Step.Name
    WeftEyebrow(stringResource(R.string.name_eyebrow), Modifier.stagger(0, k, reduce))
    WeftText(stringResource(R.string.name_title), style = WeftType.titleXl.copy(color = WeftColors.text), modifier = Modifier.stagger(1, k, reduce))
    KeyCard(fingerprint, Modifier.stagger(2, k, reduce))
    Column(Modifier.stagger(3, k, reduce), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WeftText(
            buildAnnotatedString {
                append(stringResource(R.string.name_label))
                withStyle(SpanStyle(fontWeight = FontWeight.W500, color = WeftColors.faint)) { append(stringResource(R.string.name_label_hint)) }
            },
            style = WeftType.fieldLabel.copy(color = WeftColors.muted),
        )
        WeftTextField(nick, onNick, stringResource(R.string.name_placeholder), maxLength = 24)
    }
    WeftButton(stringResource(R.string.name_choose_pin), onNext, Modifier.stagger(4, k, reduce), trailing = WeftIcon.Arrow)
}

/** `.keycard` — 3.5 % white, radius 20, 2 dp gradient strip on top. */
@Composable
private fun KeyCard(fingerprint: List<String>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = 0.035f), shape).border(1.dp, WeftColors.line, shape)) {
        Box(Modifier.fillMaxWidth().height(2.dp).gradientBackground(RectangleShape))
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WeftText(stringResource(R.string.keycard_title).uppercase(), style = WeftType.pill.copy(color = WeftColors.muted, letterSpacing = 0.12.em))
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusDot(DotState.Live)
                    WeftText(stringResource(R.string.keycard_local).uppercase(), style = WeftType.pill.copy(color = WeftColors.safe, letterSpacing = 0.06.em))
                }
            }
            FingerprintGrid(fingerprint.map { it to true }, 18f)
            Row(Modifier.fillMaxWidth()) {
                WeftText("ed25519", style = WeftType.monoCaption.copy(color = WeftColors.faint))
                Spacer(Modifier.weight(1f))
                WeftText(stringResource(R.string.keycard_made), style = WeftType.monoCaption.copy(color = WeftColors.faint))
            }
        }
    }
}

private fun Modifier.topBorder() = hairline(top = true)
private fun Modifier.bottomBorder() = hairline(top = false)
private fun Modifier.hairline(top: Boolean) = this.drawBehind {
    val y = if (top) 0.5f else size.height - 0.5f
    drawLine(WeftColors.line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
}
