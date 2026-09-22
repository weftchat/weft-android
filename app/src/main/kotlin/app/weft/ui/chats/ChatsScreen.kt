package app.weft.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.weft.R
import app.weft.design.WeftColors
import app.weft.design.WeftIcon
import app.weft.design.WeftIconButton
import app.weft.design.WeftText
import app.weft.design.WeftType

/** PLACEHOLDER — screen 3 (Chats) comes next; for now only the title and the padlock work. */
@Composable
fun ChatsScreen(onLock: () -> Unit) {
    val top = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    Box(Modifier.fillMaxSize().background(WeftColors.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = maxOf(54.dp, top + 10.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WeftText(stringResource(R.string.chats_title), style = WeftType.screenTitle.copy(color = WeftColors.text))
            Spacer(Modifier.weight(1f))
            WeftIconButton(WeftIcon.Lock, stringResource(R.string.chats_lock), onLock, iconSize = 19.dp)
        }
    }
}
