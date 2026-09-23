package app.weft.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.weft.design.WeftColors
import app.weft.design.WeftGhostButton
import app.weft.design.WeftIcon
import app.weft.design.WeftText
import app.weft.design.WeftType

/** PLACEHOLDER for a screen that is not built yet: its title, and a back button if it was pushed. */
@Composable
fun PlaceholderScreen(title: String, onBack: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().background(WeftColors.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = if (onBack != null) 12.dp else 20.dp, end = 20.dp, top = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onBack != null) WeftGhostButton(WeftIcon.Back, "Back", onBack)
            WeftText(title, style = WeftType.screenTitle.copy(color = WeftColors.text), modifier = Modifier.padding(start = 2.dp))
        }
        WeftText(
            "Coming in a later step",
            style = WeftType.monoCaption.copy(color = WeftColors.faint),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
