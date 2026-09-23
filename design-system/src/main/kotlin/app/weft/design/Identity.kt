package app.weft.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * `.fp-grid` — the fingerprint as 2 rows of 4 hex groups (8 dp down, 10 dp across), mono 500
 * +0.08 em. Groups still being worked out are muted; settled ones are text, the last one accent
 * (300 ms colour change).
 */
@Composable
fun FingerprintGrid(
    groups: List<Pair<String, Boolean>>,
    fontSize: Float,
    modifier: Modifier = Modifier,
    label: String = "Your device fingerprint",
    align: TextAlign = TextAlign.Start,
) {
    Column(modifier.semantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.chunked(4).forEachIndexed { rowIdx, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { colIdx, (text, done) ->
                    val last = rowIdx == 1 && colIdx == 3
                    val target = when { !done -> WeftColors.muted; last -> WeftColors.accent; else -> WeftColors.text }
                    val ink by animateColorAsState(target, tween(300), label = "fp")
                    WeftText(text, style = WeftType.fingerprint.copy(color = ink, fontSize = fontSize.sp, textAlign = align), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** `.field-label` — 12 / 700 muted, with a 500 faint hint after it ("· optional, …"). */
@Composable
fun WeftFieldLabel(label: String, hint: String, modifier: Modifier = Modifier) {
    WeftText(
        buildAnnotatedString {
            append(label)
            withStyle(SpanStyle(fontWeight = FontWeight.W500, color = WeftColors.faint)) { append(hint) }
        },
        style = WeftType.fieldLabel.copy(color = WeftColors.muted),
        modifier = modifier,
    )
}
