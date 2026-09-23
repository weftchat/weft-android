package app.weft.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.weft.R
import app.weft.design.WeftColors
import app.weft.design.WeftIcon
import app.weft.design.WeftNavOption
import app.weft.design.WeftOption
import app.weft.design.WeftRadio
import app.weft.design.WeftSheetTitle
import app.weft.design.WeftText
import app.weft.design.WeftType

/** The bottom sheets of design v3. */
sealed interface Sheet {
    data object New : Sheet
    data class Timer(val chatId: String) : Sheet
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
