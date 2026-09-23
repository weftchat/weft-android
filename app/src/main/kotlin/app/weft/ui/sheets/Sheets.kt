package app.weft.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.weft.R
import app.weft.data.AutoLock
import app.weft.design.ButtonKind
import app.weft.design.FingerprintGrid
import app.weft.design.QrCard
import app.weft.design.WeftButton
import app.weft.design.WeftColors
import app.weft.design.WeftIcon
import app.weft.design.WeftNavOption
import app.weft.design.WeftOption
import app.weft.design.WeftRadio
import app.weft.design.WeftSheetTitle
import app.weft.design.WeftText
import app.weft.design.WeftTextField
import app.weft.design.WeftType
import app.weft.ui.common.qrModules
import app.weft.ui.security.autoLockOption
import kotlinx.coroutines.launch

/** The bottom sheets of design v3. */
sealed interface Sheet {
    data object New : Sheet
    data class Timer(val chatId: String) : Sheet
    data object Fingerprint : Sheet
    data object AddRelay : Sheet
    data object AutoLock : Sheet
    data object UsbWipe : Sheet
}

/** "Your fingerprint" — a QR of it (200 dp, 14 dp card padding) and the groups, centred. */
@Composable
fun FingerprintSheet(fingerprint: List<String>) {
    WeftSheetTitle(stringResource(R.string.fp_sheet_title))
    WeftText(
        stringResource(R.string.fp_sheet_detail),
        style = WeftType.secondary.copy(color = WeftColors.muted, lineHeight = WeftType.itemDetail.lineHeight),
        modifier = Modifier.padding(horizontal = 4.dp).pullUp(6.dp),
    )
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        val modules = remember(fingerprint) { qrModules(fingerprint.joinToString(" ")) }
        QrCard(modules, codeSize = 200.dp, padding = 14.dp)
    }
    FingerprintGrid(fingerprint.map { it to true }, 14f, align = TextAlign.Center)
}

/** "Start something new" — New contact / New group. */
@Composable
fun NewSheet(onContact: () -> Unit, onGroup: () -> Unit) {
    WeftSheetTitle(stringResource(R.string.sheet_new_title))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WeftNavOption(WeftIcon.UserPlus, stringResource(R.string.sheet_new_contact), stringResource(R.string.sheet_new_contact_detail), onContact)
        WeftNavOption(WeftIcon.Users, stringResource(R.string.sheet_new_group), stringResource(R.string.sheet_new_group_detail), onGroup)
    }
}

/** The timer values, in order; null is off. */
val TIMERS: List<String?> = listOf(null, "5 min", "1 h", "24 h", "1 week")

/**
 * "Disappearing messages" — Off / 5 min / 1 h / 24 h / 1 week as radio options (54 dp).
 * [onPick] gets the new timer and the toast to show.
 */
@Composable
fun TimerSheet(current: String?, onPick: (timer: String?, message: String) -> Unit) {
    WeftSheetTitle(stringResource(R.string.timer_title))
    // `.sheet p.sd` — `margin-top: -6px`, so 8 dp under the title instead of 14.
    WeftText(
        stringResource(R.string.timer_detail),
        style = WeftType.secondary.copy(color = WeftColors.muted, lineHeight = WeftType.itemDetail.lineHeight),
        modifier = Modifier.padding(horizontal = 4.dp).pullUp(6.dp),
    )
    val kept = stringResource(R.string.timer_kept)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (t in TIMERS) {
            val message = if (t == null) kept else stringResource(R.string.timer_set, t)
            WeftOption(onClick = { onPick(t, message) }, minHeight = 54.dp, selected = t == current) {
                WeftRadio(checked = t == current)
                WeftText(
                    if (t == null) stringResource(R.string.timer_off_option) else t,
                    style = WeftType.itemTitle.copy(color = WeftColors.text),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Like a negative CSS top margin: moves this up by [by] and pulls what follows up with it. */
private fun Modifier.pullUp(by: Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val d = by.roundToPx()
    layout(p.width, (p.height - d).coerceAtLeast(0)) { p.place(0, -d) }
}

/** What happened to an address pasted in "Add your own relay". */
sealed interface RelayAdd {
    data object Added : RelayAdd
    /** Tested and working, waiting for its partner ("smp" or "xftp"). */
    data class NeedPartner(val need: String) : RelayAdd
    data class Failed(val why: String) : RelayAdd
}

/**
 * "Add your own relay" — paste an smp:// or xftp:// address; it is tested against the real server
 * before it is saved. The core keeps message and file relays together, so after one works the
 * sheet asks for the other.
 */
@Composable
fun AddRelaySheet(onAdd: suspend (String) -> RelayAdd, onDone: (String) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var partner by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val valid = address.trim().let { it.startsWith("smp://") || it.startsWith("xftp://") }
    WeftSheetTitle(stringResource(R.string.relay_add))
    WeftText(
        partner?.let { stringResource(R.string.relay_need_partner, it) } ?: stringResource(R.string.relay_add_sheet_detail),
        style = WeftType.secondary.copy(color = if (partner != null) WeftColors.safe else WeftColors.muted, lineHeight = WeftType.itemDetail.lineHeight),
        modifier = Modifier.padding(horizontal = 4.dp).pullUp(6.dp),
    )
    WeftTextField(address, { address = it; problem = null }, stringResource(R.string.relay_add_placeholder))
    problem?.let { WeftText(it, style = WeftType.secondary.copy(color = WeftColors.danger), modifier = Modifier.padding(horizontal = 4.dp)) }
    val added = stringResource(R.string.relay_added)
    WeftButton(
        stringResource(if (busy) R.string.relay_testing else R.string.relay_add_button),
        onClick = {
            busy = true
            scope.launch {
                val outcome = onAdd(address.trim())
                busy = false
                when (outcome) {
                    RelayAdd.Added -> { address = ""; partner = null; onDone(added) }
                    is RelayAdd.NeedPartner -> { address = ""; partner = outcome.need }
                    is RelayAdd.Failed -> problem = outcome.why
                }
            }
        },
        enabled = valid && !busy,
    )
}

/** "Lock automatically" — the times after which Weft locks once the user has left it. */
@Composable
fun AutoLockSheet(current: Int, onPick: (seconds: Int) -> Unit) {
    WeftSheetTitle(stringResource(R.string.autolock_sheet_title))
    WeftText(
        stringResource(R.string.autolock_sheet_detail),
        style = WeftType.secondary.copy(color = WeftColors.muted, lineHeight = WeftType.itemDetail.lineHeight),
        modifier = Modifier.padding(horizontal = 4.dp).pullUp(6.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (seconds in AutoLock.OPTIONS) {
            WeftOption(onClick = { onPick(seconds) }, minHeight = 54.dp, selected = seconds == current) {
                WeftRadio(checked = seconds == current)
                WeftText(stringResource(autoLockOption(seconds)), style = WeftType.itemTitle.copy(color = WeftColors.text), modifier = Modifier.weight(1f))
            }
        }
    }
}

/** "Wipe on USB data?" — the clear warning before the advanced option goes on. */
@Composable
fun UsbWipeSheet(onTurnOn: () -> Unit, onCancel: () -> Unit) {
    WeftSheetTitle(stringResource(R.string.usb_wipe_sheet_title))
    WeftText(
        stringResource(R.string.usb_wipe_sheet_detail),
        style = WeftType.secondary.copy(color = WeftColors.muted, lineHeight = WeftType.itemDetail.lineHeight),
        modifier = Modifier.padding(horizontal = 4.dp).pullUp(6.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WeftButton(stringResource(R.string.usb_wipe_turn_on), onTurnOn, kind = ButtonKind.Danger, leading = WeftIcon.Trash, leadingStroke = 2f)
        WeftButton(stringResource(R.string.usb_wipe_cancel), onCancel, kind = ButtonKind.Ghost)
    }
}
