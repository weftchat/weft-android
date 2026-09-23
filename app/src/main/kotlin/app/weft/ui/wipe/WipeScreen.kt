package app.weft.ui.wipe

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.weft.R
import app.weft.data.WeftSession
import app.weft.design.ButtonKind
import app.weft.design.WeftButton
import app.weft.design.WeftColors
import app.weft.design.WeftFine
import app.weft.design.WeftGhostButton
import app.weft.design.WeftHoldButton
import app.weft.design.WeftIcon
import app.weft.design.WeftIconView
import app.weft.design.WeftMotion
import app.weft.design.WeftText
import app.weft.design.WeftType
import app.weft.design.rememberReduceMotion
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Phase { Idle, Wiping, Clean }

/**
 * Screen 10 — Emergency wipe. Holding the red button for 2 s wipes everything (both profiles, every
 * key, the device key, all settings): the Keystore keys go first, so what is left on disk is noise.
 * The content then blurs away (520 ms) and "This phone is clean" rises in. No way back.
 */
@Composable
fun WipeScreen(onBack: () -> Unit, onStartOver: () -> Unit) {
    val reduce = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.Idle) }
    val fade = remember { Animatable(0f) }
    BackHandler(enabled = phase != Phase.Idle) { /* there is nothing to go back to */ }
    val bottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

    fun wipe() {
        if (phase != Phase.Idle) return
        phase = Phase.Wiping
        scope.launch {
            listOf(
                async { if (!reduce) fade.animateTo(1f, tween(WIPE_FADE, easing = WeftMotion.EaseIn)) },
                async { WeftSession.wipeAll() },
            ).awaitAll()
            phase = Phase.Clean
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(WeftColors.bg)
            // `#s-wipe` — a red-tinted glow from the top edge: ellipse 120 % × 55 %, #2A0E17 → bg at 62 %.
            .drawBehind {
                val sy = (0.55f * size.height) / (1.2f * size.width)
                withTransform({ scale(1f, sy, pivot = Offset(size.width / 2, 0f)) }) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color(0xFF2A0E17), WeftColors.bg),
                            center = Offset(size.width / 2, 0f),
                            radius = 1.2f * size.width * 0.62f,
                        ),
                        size = Size(size.width, size.height / sy),
                    )
                }
            },
    ) {
        if (phase != Phase.Clean) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 56.dp, bottom = 14.dp).graphicsLayer { alpha = if (phase == Phase.Idle) 1f else 0f }) {
                    WeftGhostButton(WeftIcon.Back, stringResource(R.string.wipe_cancel), onBack)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .graphicsLayer {
                            val p = fade.value
                            alpha = 1f - p
                            val s = 1f - 0.03f * p
                            scaleX = s; scaleY = s
                        }
                        .blur(8.dp * fade.value)
                        .padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = maxOf(34.dp, bottom)),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Box(Modifier.size(64.dp).background(WeftColors.danger.copy(alpha = 0.12f), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                        WeftIconView(WeftIcon.Trash, 28.dp, WeftColors.danger, stroke = 2f)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        WeftText(stringResource(R.string.wipe_title), style = WeftType.titleXl.copy(color = WeftColors.text))
                        WeftText(stringResource(R.string.wipe_lede), style = WeftType.lede.copy(color = WeftColors.muted))
                    }
                    Column {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(WeftColors.line))
                        for ((icon, label) in listOf(
                            WeftIcon.Key to R.string.wipe_keys,
                            WeftIcon.Chat to R.string.wipe_chats,
                            WeftIcon.Users to R.string.wipe_contacts,
                            WeftIcon.Server to R.string.wipe_relays,
                        )) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 13.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                WeftIconView(icon, 17.dp, WeftColors.danger, stroke = 2f)
                                WeftText(stringResource(label), style = WeftType.body.copy(color = WeftColors.text, fontWeight = FontWeight.W600))
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(WeftColors.line))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    WeftHoldButton(stringResource(R.string.wipe_hold), onComplete = ::wipe, enabled = phase == Phase.Idle)
                    WeftFine(stringResource(R.string.wipe_hint))
                }
            }
        } else {
            Clean(onStartOver)
        }
    }
}

private const val WIPE_FADE = 520

/** `.wiped` — the ring with a tick, "This phone is clean", what it means, and "Start over". Children rise in. */
@Composable
private fun Clean(onStartOver: () -> Unit) {
    val reduce = rememberReduceMotion()
    Column(
        Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        @Composable
        fun Rise(i: Int, content: @Composable () -> Unit) {
            val a = remember { Animatable(if (reduce) 1f else 0f) }
            LaunchedEffect(Unit) { if (!reduce) { delay(200L + i * 70L); a.animateTo(1f, tween(500, easing = WeftMotion.EaseOut)) } }
            Box(Modifier.graphicsLayer { alpha = a.value; translationY = 10.dp.toPx() * (1f - a.value) }) { content() }
        }
        Rise(0) {
            Box(Modifier.size(88.dp).border(1.dp, WeftColors.line2, CircleShape), contentAlignment = Alignment.Center) {
                WeftIconView(WeftIcon.Check, 34.dp, WeftColors.muted, stroke = 1.6f)
            }
        }
        Rise(1) { WeftText(stringResource(R.string.wipe_clean_title), style = WeftType.screenTitle.copy(fontSize = 28.sp, color = WeftColors.text, textAlign = TextAlign.Center)) }
        Rise(2) { WeftText(stringResource(R.string.wipe_clean_detail), style = WeftType.body.copy(color = WeftColors.muted, lineHeight = 1.6.em, fontWeight = FontWeight.W500, fontSize = 14.sp, textAlign = TextAlign.Center)) }
        Rise(3) { Box(Modifier.padding(top = 12.dp)) { WeftButton(stringResource(R.string.wipe_start_over), onStartOver, kind = ButtonKind.Ghost) } }
    }
}
