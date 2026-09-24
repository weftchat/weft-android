package app.weft.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.weft.R
import app.weft.data.CoreGroups
import app.weft.design.WeftAvatar
import app.weft.design.WeftButton
import app.weft.design.WeftCard
import app.weft.design.WeftCheckbox
import app.weft.design.WeftColors
import app.weft.design.WeftFieldLabel
import app.weft.design.WeftGhostButton
import app.weft.design.WeftIcon
import app.weft.design.WeftIconView
import app.weft.design.WeftSectionHeader
import app.weft.design.WeftText
import app.weft.design.WeftTextField
import app.weft.design.WeftType
import app.weft.design.weftClickable
import app.weft.ui.chats.ChatKind
import app.weft.ui.chats.ChatList
import kotlinx.coroutines.launch

/**
 * Screen 7 — New group: a name (28 characters, only members see it), the people the user has met
 * (existing contacts only), the note about nicknames, and "Create group · N people", enabled once
 * there is a name and at least one person. BRIEF: 50 members at most, the user included.
 */
@Composable
fun NewGroupScreen(
    chatList: ChatList,
    toast: (String, WeftIcon) -> Unit,
    onBack: () -> Unit,
    onCreated: (chatId: String, name: String) -> Unit,
) {
    val chats by chatList.chats.collectAsState()
    val contacts = chats.filter { it.kind == ChatKind.Direct }
    var name by rememberSaveable { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val full = stringResource(R.string.group_full, CoreGroups.MAX_MEMBERS)
    val failed = stringResource(R.string.group_create_failed)
    val bottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

    Column(Modifier.fillMaxSize().background(WeftColors.bg).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 52.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WeftGhostButton(WeftIcon.Back, stringResource(R.string.group_cancel), onBack)
            WeftText(stringResource(R.string.group_new_title), style = WeftType.screenTitle.copy(fontSize = 22.sp, color = WeftColors.text))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WeftFieldLabel(stringResource(R.string.group_name_label), stringResource(R.string.group_name_hint))
                WeftTextField(name, { name = it }, stringResource(R.string.group_name_placeholder), maxLength = 28)
            }
            Column {
                WeftSectionHeader(stringResource(R.string.group_add_people))
                WeftCard {
                    if (contacts.isEmpty()) {
                        WeftText(
                            stringResource(R.string.group_no_contacts),
                            style = WeftType.itemDetail.copy(color = WeftColors.muted),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    contacts.forEachIndexed { i, c ->
                        val on = c.id in picked
                        // `.member` — 62 dp, avatar 40/14, name, verified shield, checkbox.
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 62.dp)
                                .weftClickable {
                                    when {
                                        on -> picked.remove(c.id)
                                        picked.size + 1 >= CoreGroups.MAX_MEMBERS -> toast(full, WeftIcon.Alert)
                                        else -> picked.add(c.id)
                                    }
                                }
                                .then(if (i > 0) Modifier.topLine() else Modifier)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            WeftAvatar(c.initials, c.hue, size = 40.dp, radius = 14.dp, fontSize = 14f)
                            WeftText(
                                c.name,
                                style = WeftType.rowName.copy(color = WeftColors.text),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (c.verified) WeftIconView(WeftIcon.Shield, 14.dp, WeftColors.safe, stroke = 2.2f)
                            WeftCheckbox(on)
                        }
                    }
                }
            }
            WeftText(
                stringResource(R.string.group_nicknames_note),
                style = WeftType.monoCaption.copy(color = WeftColors.faint),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        // `.foot` — sticky, a line on top, 12/16/32 dp.
        Column(Modifier.fillMaxWidth().topLine().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = maxOf(32.dp, bottom))) {
            WeftButton(
                if (picked.isEmpty()) stringResource(R.string.group_create) else stringResource(R.string.group_create_n, picked.size + 1),
                onClick = {
                    busy = true
                    scope.launch {
                        val ids = picked.map { it.removePrefix("@").toLong() }
                        runCatching { CoreGroups.create(name, ids) }
                            .onSuccess { onCreated(it, name.trim()) }
                            .onFailure { toast(failed, WeftIcon.Alert) }
                        busy = false
                    }
                },
                enabled = name.isNotBlank() && picked.isNotEmpty() && !busy,
            )
        }
    }
}

/** A 1 dp line along the top edge. */
private fun Modifier.topLine() = drawBehind {
    drawRect(WeftColors.line, size = size.copy(height = 1.dp.toPx()))
}
