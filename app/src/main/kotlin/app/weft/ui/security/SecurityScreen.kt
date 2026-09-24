package app.weft.ui.security

import app.weft.design.WeftLinkButton
import app.weft.data.CallServers
import app.weft.privacy.Links
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
import app.weft.BuildConfig
import app.weft.R
import app.weft.data.Background
import app.weft.data.CoreNetwork
import app.weft.data.CoreRelays
import app.weft.data.LiveRelays
import app.weft.data.UsbGuard
import app.weft.data.WeftSession
import app.weft.data.WipePolicy
import app.weft.design.ButtonKind
import app.weft.design.ItemIcon
import app.weft.design.ItemTone
import app.weft.design.TagTone
import app.weft.design.WeftButton
import app.weft.design.WeftCard
import app.weft.design.WeftColors
import app.weft.design.WeftIcon
import app.weft.design.WeftIconView
import app.weft.design.WeftItem
import app.weft.design.WeftSectionHeader
import app.weft.design.WeftSubRow
import app.weft.design.WeftSwitch
import app.weft.design.WeftTag
import app.weft.design.WeftText
import app.weft.design.WeftType
import app.weft.nav.PinPurpose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    onRelay: (String) -> Unit,
    onAddCallServer: () -> Unit,
    onChoose: (PinPurpose) -> Unit,
    onWipe: () -> Unit,
    autoLockSeconds: Int,
    onAutoLock: () -> Unit,
    usbWipe: Boolean,
    onUsbWipe: (Boolean) -> Unit,
) {
    val servers by CoreRelays.servers.collectAsState()
    val status by CoreRelays.status.collectAsState()
    val tor by CoreNetwork.tor.collectAsState()
    val callServers by CallServers.list.collectAsState()
    val callRemoved = stringResource(R.string.calls_removed)
    val route = if (tor) "via Tor" else "direct"
    val autoSwitch by LiveRelays.autoSwitch.collectAsState()
    val autoOn = stringResource(R.string.relay_auto_on)
    val autoOff = stringResource(R.string.relay_auto_off)
    val torOn = stringResource(R.string.tor_on)
    val torOff = stringResource(R.string.tor_off)
    val torNeedOrbot = stringResource(R.string.tor_need_orbot)
    val scope = rememberCoroutineScope()
    // Only the real profile knows whether a duress PIN exists; the decoy shows the feature off.
    var duress by remember { mutableStateOf(false) }
    var panic by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var wipe10 by remember { mutableStateOf(WipePolicy.wipeOnLimit(context)) }
    var cleanLinks by remember { mutableStateOf(Links.cleanOn(context)) }
    val linksOn = stringResource(R.string.toast_links_on)
    val linksOff = stringResource(R.string.toast_links_off)
    LaunchedEffect(Unit) {
        runCatching { CoreRelays.load() }
        runCatching { CoreRelays.refresh() }
        withContext(Dispatchers.IO) { WeftSession.notes() }?.let { duress = it.duress; panic = it.panic }
    }
    val duressOff = stringResource(R.string.toast_duress_off)
    val panicOff = stringResource(R.string.toast_panic_off)
    val wipe10On = stringResource(R.string.toast_wipe10_on)
    val listening by Background.listening.collectAsState()
    val listenOn = stringResource(R.string.toast_listen_on)
    val listenOff = stringResource(R.string.toast_listen_off)
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
                    WeftItem(
                        WeftIcon.Timer, stringResource(R.string.autolock_title), first = false,
                        detail = stringResource(R.string.autolock_detail),
                        value = stringResource(autoLockValue(autoLockSeconds)), chevron = true,
                        onClick = onAutoLock,
                    )
                    WeftItem(
                        WeftIcon.Chat, stringResource(R.string.listen_title), first = false,
                        detail = stringResource(R.string.listen_detail),
                        topAligned = true,
                        trailing = {
                            WeftSwitch(
                                checked = listening,
                                onCheckedChange = { on ->
                                    Background.setListen(on)
                                    toast(if (on) listenOn else listenOff, if (on) WeftIcon.Check else WeftIcon.Lock)
                                },
                                contentDescription = stringResource(R.string.listen_title),
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        },
                    )
                    WeftItem(
                        WeftIcon.Bolt, stringResource(R.string.usb_title), first = false,
                        detail = stringResource(R.string.usb_detail),
                        topAligned = true,
                        trailing = { WeftTag(stringResource(R.string.always_on), TagTone.On, Modifier.align(Alignment.CenterVertically)) },
                    )
                    WeftItem(
                        WeftIcon.Alert, stringResource(R.string.usb_wipe_title), first = false,
                        detail = stringResource(R.string.usb_wipe_detail),
                        tone = ItemTone.Danger,
                        topAligned = true,
                        trailing = {
                            WeftSwitch(
                                checked = usbWipe,
                                onCheckedChange = onUsbWipe,
                                contentDescription = stringResource(R.string.usb_wipe_title),
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        },
                    )
                    if (BuildConfig.DEBUG) {
                        val go = stringResource(R.string.toast_usb_debug)
                        WeftItem(
                            WeftIcon.Bolt, stringResource(R.string.usb_debug_simulate), first = false,
                            detail = stringResource(R.string.usb_debug_detail),
                            chevron = true,
                            onClick = {
                                toast(go, WeftIcon.Alert)
                                scope.launch { delay(5000); UsbGuard.onDataConnection() }
                            },
                        )
                    }
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
                    WeftItem(
                        WeftIcon.Link, stringResource(R.string.privacy_links), first = false,
                        detail = stringResource(R.string.privacy_links_detail),
                        trailing = {
                            WeftSwitch(
                                checked = cleanLinks,
                                onCheckedChange = { on ->
                                    Links.setCleanOn(context, on)
                                    cleanLinks = on
                                    toast(if (on) linksOn else linksOff, WeftIcon.Link)
                                },
                                contentDescription = stringResource(R.string.privacy_links),
                            )
                        },
                    )
                    WeftItem(
                        WeftIcon.Mask, stringResource(R.string.privacy_tor), first = false,
                        detail = stringResource(R.string.privacy_tor_detail),
                        trailing = {
                            WeftSwitch(
                                checked = tor,
                                onCheckedChange = { on ->
                                    when {
                                        on && !CoreNetwork.orbotInstalled() -> toast(torNeedOrbot, WeftIcon.Alert)
                                        else -> scope.launch {
                                            CoreNetwork.setTor(on)
                                            toast(if (on) torOn else torOff, if (on) WeftIcon.Mask else WeftIcon.Alert)
                                        }
                                    }
                                },
                                contentDescription = stringResource(R.string.privacy_tor),
                            )
                        },
                    )
                }
            }
            Column {
                WeftSectionHeader(stringResource(R.string.security_relays))
                WeftCard {
                    servers.forEachIndexed { i, address ->
                        val st = status[address]
                        val kind = stringResource(if (address.startsWith("xftp://")) R.string.relay_kind_files_short else R.string.relay_kind_messages_short)
                        WeftItem(
                            WeftIcon.Server,
                            relayHost(address),
                            first = i == 0,
                            detail = when {
                                st?.state == CoreRelays.State.Blocked -> stringResource(R.string.relay_blocked_detail)
                                st?.latencyMs != null -> stringResource(R.string.relay_route_ms, kind, route, st.latencyMs)
                                else -> stringResource(R.string.relay_route, kind, route)
                            },
                            tone = when (st?.state) {
                                CoreRelays.State.InUse -> ItemTone.Safe
                                CoreRelays.State.Blocked -> ItemTone.Danger
                                else -> ItemTone.Accent
                            },
                            titleStyle = WeftType.relayName,
                            textGap = 2.dp,
                            onClick = { onRelay(address) },
                            trailing = {
                                when (st?.state) {
                                    CoreRelays.State.InUse -> WeftTag(stringResource(R.string.relay_tag_in_use), TagTone.On)
                                    CoreRelays.State.Blocked -> WeftTag(stringResource(R.string.relay_tag_blocked), TagTone.Bad)
                                    else -> WeftTag(stringResource(R.string.relay_tag_standby), TagTone.Standby)
                                }
                            },
                        )
                    }
                    WeftItem(
                        WeftIcon.Refresh, stringResource(R.string.relay_auto), first = servers.isEmpty(),
                        detail = stringResource(R.string.relay_auto_detail),
                        trailing = {
                            WeftSwitch(
                                checked = autoSwitch,
                                onCheckedChange = { on ->
                                    LiveRelays.setAutoSwitch(on)
                                    toast(if (on) autoOn else autoOff, WeftIcon.Refresh)
                                },
                                contentDescription = stringResource(R.string.relay_auto),
                            )
                        },
                    )
                    WeftItem(
                        WeftIcon.Plus,
                        stringResource(R.string.relay_add),
                        first = false,
                        detail = stringResource(R.string.relay_add_detail),
                        chevron = true,
                        onClick = onAddRelay,
                    )
                }
            }
            Column {
                WeftSectionHeader(stringResource(R.string.security_calls))
                WeftCard {
                    callServers.forEachIndexed { i, line ->
                        WeftItem(
                            WeftIcon.Phone, CallServers.label(line), first = i == 0,
                            titleStyle = WeftType.relayName, textGap = 2.dp, tone = ItemTone.Safe,
                            trailing = {
                                WeftLinkButton(stringResource(R.string.group_remove), onClick = {
                                    CallServers.remove(line)
                                    toast(callRemoved, WeftIcon.Trash)
                                })
                            },
                        )
                    }
                    WeftItem(
                        WeftIcon.Plus, stringResource(R.string.calls_add), first = callServers.isEmpty(),
                        detail = stringResource(if (callServers.isEmpty()) R.string.calls_none else if (tor) R.string.calls_tor_note else R.string.calls_add_detail),
                        chevron = true,
                        onClick = onAddCallServer,
                    )
                }
            }
            Column {
                WeftSectionHeader(stringResource(R.string.graphene_header))
                WeftCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            ItemIcon(WeftIcon.Shield, tone = ItemTone.Safe)
                            WeftText(stringResource(R.string.graphene_title), style = WeftType.itemTitle.copy(color = WeftColors.text), modifier = Modifier.weight(1f))
                        }
                        WeftText(stringResource(R.string.graphene_detail), style = WeftType.itemDetail.copy(color = WeftColors.muted))
                        for (line in listOf(R.string.graphene_usb, R.string.graphene_duress, R.string.graphene_reboot)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                WeftIconView(WeftIcon.Check, 14.dp, WeftColors.safe, Modifier.padding(top = 3.dp), stroke = 2.6f)
                                WeftText(stringResource(line), style = WeftType.itemDetail.copy(color = WeftColors.text), modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            WeftButton(stringResource(R.string.security_wipe_now), onWipe, kind = ButtonKind.Danger, leading = WeftIcon.Trash, leadingStroke = 2f)
        }
    }
}

/** "smp://fingerprint:password@host:5223" → "host:5223". Never shows the password. */
fun relayHost(address: String): String =
    address.substringAfter("://").substringAfter('@').substringBefore(',').ifEmpty { address.substringBefore(':') }
