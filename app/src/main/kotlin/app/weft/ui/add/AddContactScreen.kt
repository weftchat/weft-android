package app.weft.ui.add

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.weft.R
import app.weft.privacy.SensitiveClipboard
import app.weft.design.ButtonKind
import app.weft.design.QrCard
import app.weft.design.QrModules
import app.weft.design.WeftButton
import app.weft.design.WeftColors
import app.weft.design.WeftIcon
import app.weft.design.WeftIconButton
import app.weft.design.WeftIconView
import app.weft.design.WeftMotion
import app.weft.design.WeftText
import app.weft.design.WeftType
import app.weft.design.cssBorder
import app.weft.design.gradientBackground
import app.weft.design.rememberReduceMotion
import io.nayuki.qrcodegen.QrCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen 8 — Add contact. A one-time QR code of the invitation link on a white card with an accent
 * glow, a live "expires in mm:ss" countdown (a new code at 0 or with the refresh button), "Scan
 * their code" and "Copy one-time link".
 */
@Composable
fun AddContactScreen(
    invitations: Invitations,
    bottomPadding: Dp,
    toast: (String, WeftIcon) -> Unit,
) {
    val reduce = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var invitation by remember { mutableStateOf<Invitation?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // New code: the card flashes from 20 % and 0.97 back to full, 380 ms EaseOut.
    val flash = remember { Animatable(1f) }
    val renewed = stringResource(R.string.add_renewed)
    val scanSoon = stringResource(R.string.add_scan_soon)
    val copied = stringResource(R.string.add_copied)

    suspend fun renew(animate: Boolean) {
        invitation = invitations.renew()
        now = System.currentTimeMillis()
        if (animate && !reduce) {
            flash.snapTo(0f)
            flash.animateTo(1f, tween(380, easing = WeftMotion.EaseOut))
        }
    }

    LaunchedEffect(Unit) {
        renew(animate = false)
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
            if ((invitation?.expiresAtMs ?: 0) <= now) renew(animate = true)
        }
    }

    Column(Modifier.fillMaxSize().background(WeftColors.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WeftText(
                stringResource(R.string.add_title),
                style = WeftType.screenTitle.copy(color = WeftColors.text),
                modifier = Modifier.padding(start = 2.dp).weight(1f),
            )
            WeftIconButton(WeftIcon.Refresh, stringResource(R.string.add_new_code), onClick = {
                scope.launch {
                    renew(animate = true)
                    toast(renewed, WeftIcon.Refresh)
                }
            }, iconSize = 19.dp)
        }
        // `.add-body` — 4 dp top, 24 dp sides and bottom (plus the tab bar), 20 dp between.
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp + bottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WeftText(
                buildAnnotatedString {
                    append(stringResource(R.string.add_lede_before))
                    withStyle(SpanStyle(color = WeftColors.text, fontWeight = FontWeight.W700)) { append(stringResource(R.string.add_lede_once)) }
                    append(stringResource(R.string.add_lede_after))
                },
                style = WeftType.lede.copy(color = WeftColors.muted, textAlign = TextAlign.Center),
            )
            val qrLabel = stringResource(R.string.add_qr)
            Box(contentAlignment = Alignment.Center) {
                // `.qr-wrap::before` — the accent gradient, 14 dp in, radius 40, 35 %, blurred 32 px.
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(14.dp)
                        .blur(32.dp, BlurredEdgeTreatment.Unbounded)
                        .alpha(0.35f)
                        .gradientBackground(RoundedCornerShape(40.dp)),
                )
                val modules = remember(invitation?.link) { invitation?.link?.let(::qrModules) }
                Box(
                    Modifier
                        .semantics { contentDescription = qrLabel }
                        .graphicsLayer {
                            alpha = 0.2f + 0.8f * flash.value
                            val s = 0.97f + 0.03f * flash.value
                            scaleX = s; scaleY = s
                        },
                ) {
                    if (modules != null) QrCard(modules)
                    else Box(Modifier.background(Color.White, RoundedCornerShape(26.dp)).padding(131.dp))
                }
            }
            ExpiryPill(((invitation?.expiresAtMs ?: now) - now).coerceAtLeast(0))
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WeftButton(stringResource(R.string.add_scan), onClick = { toast(scanSoon, WeftIcon.Scan) }, leading = WeftIcon.Scan)
                WeftButton(
                    stringResource(R.string.add_copy),
                    onClick = {
                        val link = invitation?.link ?: return@WeftButton
                        SensitiveClipboard.copy(context, link)
                        toast(copied, WeftIcon.Copy)
                    },
                    kind = ButtonKind.Ghost,
                    leading = WeftIcon.Link,
                    leadingStroke = 2f,
                )
            }
        }
    }
}

/** `.pill.expiry` — timer icon, "expires in " and the time in bold, mono 12 tabular. */
@Composable
private fun ExpiryPill(leftMs: Long) {
    val secs = (leftMs + 999) / 1000
    val time = "%02d:%02d".format(secs / 60, secs % 60)
    Row(
        Modifier
            .background(Color.White.copy(alpha = 0.04f), CircleShape)
            .cssBorder(1.dp, WeftColors.line, CircleShape)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeftIconView(WeftIcon.Timer, 14.dp, WeftColors.accent, stroke = 2.2f)
        WeftText(
            buildAnnotatedString {
                append(stringResource(R.string.add_expires))
                withStyle(SpanStyle(color = WeftColors.text, fontWeight = FontWeight.W600)) { append(time) }
            },
            style = WeftType.monoBody.copy(color = WeftColors.muted),
        )
    }
}

private fun qrModules(link: String): QrModules {
    val qr = QrCode.encodeText(link, QrCode.Ecc.MEDIUM)
    return QrModules(qr.size) { x, y -> qr.getModule(x, y) }
}
