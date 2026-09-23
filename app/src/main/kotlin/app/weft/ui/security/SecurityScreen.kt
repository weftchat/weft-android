package app.weft.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.weft.R
import app.weft.data.CoreRelays
import app.weft.data.WipePolicy
import app.weft.data.WeftSession
import app.weft.design.ButtonKind
import app.weft.design.WeftButton
import app.weft.design.ItemTone
import app.weft.design.TagTone
import app.weft.design.WeftCard
import app.weft.design.WeftColors
import app.weft.design.WeftIcon
import app.weft.design.WeftItem
import app.weft.design.WeftSectionHeader
import app.weft.design.WeftSubRow
import app.weft.design.WeftSwitch
import app.weft.design.WeftTag
import app.weft.design.WeftText
import app.weft.design.WeftType
import app.weft.nav.PinPurpose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen 9 — Security. Phase 1 has the Relays section only: the user's own relays and "Add your own
 * relay". The phone-seizure and privacy sections come with Phase 2.
 */
@Composable
fun SecurityScreen(
    bottomPadding: Dp,
    toast: (String, WeftIcon) -> Unit,
    onAddRelay: () -> Unit,
    onChoose: (PinPurpose) -> Unit,
    onWipe: () -> Unit,
) {
    val servers by CoreRelays.servers.collectAsState()
    val scope = rememberCoroutineScope()
    // Only the real profile knows whether a duress PIN exists; the decoy shows the feature off.
    var duress by remember { mutableStateOf(false) }
    var panic by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var wipe10 by remember { mutableStateOf(WipePolicy.wipeOnLimit(context)) }
    LaunchedEffect(Unit) {
        runCatching { CoreRelays.load() }
        withContext(Dispatchers.IO) { WeftSession.notes() }?.let { duress = it.duress; panic = it.panic }
    }
    val duressOff = stringResource(R.string.toast_duress_off)
    val panicOff = stringResource(R.string.toast_panic_off)
    val wipe10On = stringResource(R.string.toast_wipe10_on)
    val wipe10Off = stringResource(R.string.toast_wipe10_off)
    Column(Modifier.fillMaxSize().background(WeftColors.bg)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 14.dp)) {
            WeftText(stringResource(R.string.security_title), style = WeftType.screenTitle.copy(color = WeftColors.text), modifier = Modifier.padding(start = 2.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 28.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column {
                WeftSectionHeader(stringResource(R.string.security_phone))
                WeftCard {
                    WeftItem(
                        WeftIcon.Mask, stringResource(R.string.duress_title), first = true,
                        detail = stringResource(R.string.duress_detail),
                        topAligned = true,
                        below = if (duress) ({
                            WeftSubRow(stringResource(R.string.duress_pin), "••••••", stringResource(R.string.duress_change), onAction = { onChoose(PinPurpose.Duress) })
                        }) else null,
                        trailing = {
                            WeftSwitch(
                                checked = duress,
                                onCheckedChange = { on ->
                                    if (on) onChoose(PinPurpose.Duress)
                                    else scope.launch {
                                        WeftSession.disableDuress()
                                        duress = false
                                        toast(duressOff, WeftIcon.Alert)
                                    }
                                },
                                contentDescription = stringResource(R.string.duress_title),
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        },
                    )
                    WeftItem(
                        WeftIcon.Bolt, stringResource(R.string.panic_title), first = false,
                        detail = stringResource(R.string.panic_detail),
                        tone = ItemTone.Danger,
                        topAligned = true,
                        below = if (panic) ({
                            WeftSubRow(stringResource(R.string.panic_code), "••••••", stringResource(R.string.duress_change), onAction = { onChoose(PinPurpose.Panic) })
                        }) else null,
                        trailing = {
                            WeftSwitch(
                                checked = panic,
                                onCheckedChange = { on ->
                                    if (on) onChoose(PinPurpose.Panic)
                                    else scope.launch {
                                        WeftSession.disablePanic()
                                        panic = false
                                        toast(panicOff, WeftIcon.Alert)
                                    }
                                },
                                contentDescription = stringResource(R.string.panic_title),
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        },
                    )
                    WeftItem(
                        WeftIcon.Alert, stringResource(R.string.wipe10_title), first = false,
                        detail = stringResource(R.string.wipe10_detail),
                        tone = ItemTone.Danger,
                        topAligned = true,
                        trailing = {
                            WeftSwitch(
                                checked = wipe10,
                                onCheckedChange = { on ->
                                    WipePolicy.setWipeOnLimit(context, on)
                                    wipe10 = on
                                    toast(if (on) wipe10On else wipe10Off, if (on) WeftIcon.Check else WeftIcon.Alert)
                                },
                                contentDescription = stringResource(R.string.wipe10_title),
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        },
                    )
                }
            }
            Column {
                WeftSectionHeader(stringResource(R.string.security_privacy))
                WeftCard {
                    WeftItem(
                        WeftIcon.Lock, stringResource(R.string.privacy_e2e), first = true,
                        detail = stringResource(R.string.privacy_e2e_detail), tone = ItemTone.Safe,
                        trailing = { WeftTag(stringResource(R.string.always_on), TagTone.On) },
                    )
                    WeftItem(
                        WeftIcon.Image, stringResource(R.string.privacy_strip), first = false,
                        detail = stringResource(R.string.privacy_strip_detail), tone = ItemTone.Safe,
                        trailing = { WeftTag(stringResource(R.string.always_on), TagTone.On) },
                    )
                    WeftItem(
                        WeftIcon.EyeOff, stringResource(R.string.privacy_screens), first = false,
                        detail = stringResource(R.string.privacy_screens_detail),
                        trailing = { WeftTag(stringResource(R.string.always_on), TagTone.On) },
                    )
                }
            }
            Column {
                WeftSectionHeader(stringResource(R.string.security_relays))
                WeftCard {
                    servers.forEachIndexed { i, address ->
                        WeftItem(
                            WeftIcon.Server,
                            relayHost(address),
                            first = i == 0,
                            detail = stringResource(if (address.startsWith("xftp://")) R.string.relay_kind_files else R.string.relay_kind_messages),
                            tone = ItemTone.Safe,
                            titleStyle = WeftType.relayName,
                            textGap = 2.dp,
                            trailing = { WeftTag(stringResource(R.string.relay_tag_in_use), TagTone.On) },
                        )
                    }
                    WeftItem(
                        WeftIcon.Plus,
                        stringResource(R.string.relay_add),
                        first = servers.isEmpty(),
                        detail = stringResource(R.string.relay_add_detail),
                        chevron = true,
                        onClick = onAddRelay,
                    )
                }
            }
            WeftButton(stringResource(R.string.security_wipe_now), onWipe, kind = ButtonKind.Danger, leading = WeftIcon.Trash, leadingStroke = 2f)
        }
    }
}

/** "smp://fingerprint:password@host:5223" → "host:5223". Never shows the password. */
fun relayHost(address: String): String =
    address.substringAfter("://").substringAfter('@').substringBefore(',').ifEmpty { address.substringBefore(':') }
