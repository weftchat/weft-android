package app.weft.design

import androidx.compose.foundation.background
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ItemTone { Accent, Danger, Safe }

/** `.item-ic` — 36 dp tile, radius 12, tinted accent (default), danger or safe. */
@Composable
fun ItemIcon(icon: WeftIcon, modifier: Modifier = Modifier, tone: ItemTone = ItemTone.Accent) {
    val (fill, ink) = when (tone) {
        ItemTone.Accent -> WeftColors.accent.copy(alpha = 0.12f) to WeftColors.accent
        ItemTone.Danger -> WeftColors.danger.copy(alpha = 0.12f) to WeftColors.danger
        ItemTone.Safe -> WeftColors.safe.copy(alpha = 0.10f) to WeftColors.safe
    }
    Box(modifier.size(36.dp).background(fill, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        WeftIconView(icon, 18.dp, ink, stroke = 2f)
    }
}

/** `.sheet h2` — 20 / 800, 4 dp in from the sheet's padding. */
@Composable
fun WeftSheetTitle(text: String) {
    WeftText(text, style = WeftType.sheetTitle.copy(color = WeftColors.text), modifier = Modifier.padding(horizontal = 4.dp))
}

/**
 * `.opt` — a glass option in a sheet: min 64 dp, radius 20, 14 dp gap; press scales to 0.985.
 * [selected] draws the chosen state (accent border @ 45 %, accent fill @ 8 %).
 */
@Composable
fun WeftOption(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 64.dp,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .weftClickable(pressedScale = 0.985f, onClick = onClick)
            .background(if (selected) WeftColors.accent.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f), shape)
            .cssBorder(1.dp, if (selected) WeftColors.accent.copy(alpha = 0.45f) else WeftColors.line, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** An option with an icon tile, a title and a line of detail, and a chevron: "New contact". */
@Composable
fun WeftNavOption(icon: WeftIcon, title: String, detail: String, onClick: () -> Unit) {
    WeftOption(onClick) {
        ItemIcon(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WeftText(title, style = WeftType.itemTitle.copy(color = WeftColors.text))
            WeftText(detail, style = WeftType.itemDetail.copy(color = WeftColors.muted))
        }
        WeftIconView(WeftIcon.Chevron, 16.dp, WeftColors.text, stroke = 2f)
    }
}

/** `.link-btn` — mono 12 / 600 in accent2 on an accent @ 10 % pill, 38 dp tall. */
@Composable
fun WeftLinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 38.dp)
            .weftClickable(onClick = onClick)
            .background(WeftColors.accent.copy(alpha = 0.10f), CircleShape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { WeftText(text, style = WeftType.linkButton.copy(color = WeftColors.accent2)) }
}

/** `.sec-h` — mono 11 / +0.14 em / uppercase / faint, 4 dp in, 8 dp above its card. */
@Composable
fun WeftSectionHeader(text: String, modifier: Modifier = Modifier) {
    WeftText(
        text.uppercase(),
        style = WeftType.sectionHeader.copy(color = WeftColors.faint),
        modifier = modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
    )
}

/** `.card` — surface, line border, radius 22; rows inside are separated by a line. */
@Composable
fun WeftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(WeftColors.surface, shape)
            .cssBorder(1.dp, WeftColors.line, shape),
        content = content,
    )
}

/**
 * `.item.center` — a row in a card: icon tile, title, optional detail, and on the right a value
 * and/or a chevron. [first] rows have no top line. Pressed: white @ 3 %.
 */
@Composable
fun WeftItem(
    icon: WeftIcon,
    title: String,
    first: Boolean,
    modifier: Modifier = Modifier,
    detail: String? = null,
    value: String? = null,
    chevron: Boolean = false,
    tone: ItemTone = ItemTone.Accent,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(source, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            .background(if (pressed) Color.White.copy(alpha = 0.03f) else Color.Transparent)
            .then(if (first) Modifier else Modifier.drawBehind { drawRect(WeftColors.line, size = size.copy(height = 1.dp.toPx())) }.padding(top = 1.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemIcon(icon, tone = tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WeftText(title, style = WeftType.itemTitle.copy(color = WeftColors.text))
            if (detail != null) WeftText(detail, style = WeftType.itemDetail.copy(color = WeftColors.muted))
        }
        trailing?.invoke()
        if (value != null || chevron) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (value != null) WeftText(value, style = WeftType.monoBody.copy(color = WeftColors.muted))
                if (chevron) WeftIconView(WeftIcon.Chevron, 16.dp, WeftColors.muted, stroke = 2f)
            }
        }
    }
}
