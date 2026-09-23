package app.weft.ui.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.weft.R
import app.weft.design.ButtonKind
import app.weft.design.FingerprintGrid
import app.weft.design.WeftButton
import app.weft.design.WeftCard
import app.weft.design.WeftColors
import app.weft.design.WeftFieldLabel
import app.weft.design.WeftIcon
import app.weft.design.WeftItem
import app.weft.design.WeftMotion
import app.weft.design.WeftSectionHeader
import app.weft.design.WeftText
import app.weft.design.WeftTextField
import app.weft.design.WeftType
import app.weft.design.rememberReduceMotion
import app.weft.privacy.SensitiveClipboard
import kotlinx.coroutines.launch

/**
 * Screen 11 — Profile. Big avatar (the nickname's initial) with six colours, the nickname, the
 * fingerprint card (Show as QR, Copy), "This phone" (Linked devices, Change PIN, Security and
 * duress) and "Delete identity from this phone".
 */
@Composable
fun ProfileScreen(
    store: ProfileStore,
    bottomPadding: Dp,
    toast: (String, WeftIcon) -> Unit,
    onShowQr: () -> Unit,
    onChangePin: () -> Unit,
    onSecurity: () -> Unit,
    onDelete: () -> Unit,
) {
    val profile by store.profile.collectAsState()
    val reduce = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Picking a colour: the avatar dips to 0.92 and back, 320 ms EaseOut; colours change over 300 ms.
    val dip = remember { Animatable(1f) }
    val fill by animateColorAsState(WeftColors.avatarFill(profile.hue), tween(if (reduce) 0 else 300), label = "avFill")
    val ink by animateColorAsState(WeftColors.avatarInk(profile.hue), tween(if (reduce) 0 else 300), label = "avInk")
    val copied = stringResource(R.string.profile_fp_copied)
    val linkedToast = stringResource(R.string.profile_linked_toast)

    Column(Modifier.fillMaxSize().background(WeftColors.bg)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 14.dp)) {
            WeftText(stringResource(R.string.profile_title), style = WeftType.screenTitle.copy(color = WeftColors.text), modifier = Modifier.padding(start = 2.dp))
        }
        // `.stack` — 22 dp between blocks, 2 dp top, 16 dp sides, 28 dp bottom (plus the tab bar).
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 28.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // `.prof-top`
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .graphicsLayer { scaleX = dip.value; scaleY = dip.value }
                        .background(fill, RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center,
                ) { WeftText(profile.initial, style = WeftType.rowName.copy(fontSize = 38.sp, letterSpacing = 0.sp, color = ink)) }
                val coloursLabel = stringResource(R.string.profile_colours)
                Row(Modifier.semantics { contentDescription = coloursLabel }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SWATCH_HUES.forEachIndexed { i, hue ->
                        val on = i == profile.swatch
                        val label = stringResource(R.string.profile_colour, i + 1)
                        Box(
                            Modifier
                                .size(30.dp)
                                .semantics { contentDescription = label; selected = on }
                                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) {
                                    store.setSwatch(i)
                                    if (!reduce) scope.launch {
                                        dip.snapTo(0.92f)
                                        dip.animateTo(1f, tween(320, easing = WeftMotion.EaseOut))
                                    }
                                }
                                // Selected: a 2 dp ring of background, then 2 dp of accent, outside the dot.
                                .drawBehind {
                                    if (on) {
                                        drawCircle(WeftColors.accent, radius = size.minDimension / 2 + 4.dp.toPx())
                                        drawCircle(WeftColors.bg, radius = size.minDimension / 2 + 2.dp.toPx())
                                    }
                                }
                                .background(WeftColors.hsl(hue, 0.45f, 0.45f), CircleShape),
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WeftFieldLabel(stringResource(R.string.profile_nick_label), stringResource(R.string.profile_nick_hint))
                WeftTextField(profile.nickname, store::setNickname, stringResource(R.string.name_placeholder), maxLength = 24)
            }
            Column {
                WeftSectionHeader(stringResource(R.string.profile_fp_header))
                WeftCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FingerprintGrid(profile.fingerprint.map { it to true }, 15f)
                        WeftText(stringResource(R.string.profile_fp_detail), style = WeftType.itemDetail.copy(color = WeftColors.muted))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WeftButton(
                                stringResource(R.string.profile_show_qr), onShowQr, Modifier.weight(1f),
                                kind = ButtonKind.Ghost, leading = WeftIcon.Scan, leadingStroke = 2.2f, iconSize = 16.dp,
                                height = 44.dp, textStyle = WeftType.button.copy(fontSize = 13.sp),
                            )
                            WeftButton(
                                stringResource(R.string.profile_copy),
                                {
                                    SensitiveClipboard.copy(context, profile.fingerprint.joinToString(" "))
                                    toast(copied, WeftIcon.Copy)
                                },
                                Modifier.weight(1f),
                                kind = ButtonKind.Ghost, leading = WeftIcon.Copy, leadingStroke = 2f, iconSize = 16.dp,
                                height = 44.dp, textStyle = WeftType.button.copy(fontSize = 13.sp),
                            )
                        }
                    }
                }
            }
            Column {
                WeftSectionHeader(stringResource(R.string.profile_phone_header))
                WeftCard {
                    WeftItem(WeftIcon.PhoneBox, stringResource(R.string.profile_linked), first = true, value = "1", chevron = true, onClick = { toast(linkedToast, WeftIcon.Check) })
                    WeftItem(WeftIcon.Key, stringResource(R.string.profile_change_pin), first = false, chevron = true, onClick = onChangePin)
                    WeftItem(WeftIcon.Shield, stringResource(R.string.profile_security), first = false, chevron = true, onClick = onSecurity)
                }
            }
            WeftButton(stringResource(R.string.profile_delete), onDelete, kind = ButtonKind.Danger, leading = WeftIcon.Trash, leadingStroke = 2f)
        }
    }
}
