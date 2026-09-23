package app.weft.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A QR code as a square grid of modules; true is dark. */
class QrModules(val size: Int, private val dark: (x: Int, y: Int) -> Boolean) {
    operator fun get(x: Int, y: Int) = dark(x, y)
}

/**
 * `.qr-card` — white card, radius 26, 16 dp padding (it doubles as the QR quiet zone), modules in
 * #0B0B14 filling [codeSize]. Modules snap to whole pixels so the code stays crisp.
 */
@Composable
fun QrCard(modules: QrModules, modifier: Modifier = Modifier, codeSize: Dp = 230.dp, padding: Dp = 16.dp) {
    Box(modifier.background(Color.White, RoundedCornerShape(26.dp)).padding(padding)) {
        Canvas(Modifier.size(codeSize)) {
            val n = modules.size
            val cell = kotlin.math.floor(size.width / n)
            val inset = (size.width - cell * n) / 2
            for (y in 0 until n) for (x in 0 until n) {
                if (modules[x, y]) drawRect(WeftColors.onAccent, Offset(inset + x * cell, inset + y * cell), Size(cell, cell))
            }
        }
    }
}
