package app.weft.ui.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.weft.design.WeftColors
import app.weft.design.WeftType
import app.weft.nav.PinMode

/** PLACEHOLDER — screen 2 (PIN) is the next step; this only lets the push animation be tested. */
@Composable
fun PinScreen(mode: PinMode) {
    Box(Modifier.fillMaxSize().background(WeftColors.bg), contentAlignment = Alignment.Center) {
        BasicText("PIN · $mode — next screen", style = WeftType.monoCaption.copy(color = WeftColors.faint))
    }
}
