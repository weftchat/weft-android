package app.weft.ui.chats

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.weft.R
import app.weft.design.DotState
import app.weft.design.StatusDot
import app.weft.design.WeftAvatar
import app.weft.design.cssBorder
import app.weft.design.WeftColors
import app.weft.design.WeftIcon
import app.weft.design.WeftIconButton
import app.weft.design.WeftIconView
import app.weft.design.WeftLinkButton
import app.weft.design.WeftMotion
import app.weft.design.WeftSegmented
import app.weft.design.WeftText
import app.weft.design.WeftType
import app.weft.design.gradientBackground
import app.weft.design.rememberReduceMotion
import app.weft.design.weftClickable
import app.weft.relay.Relays
import kotlinx.coroutines.launch

private enum class Filter { All, Direct, Groups, Timed }

/**
 * Screen 3 — Chats. Title with the padlock (locks → PIN) and the gradient "+" (Start something
 * new), the relay pill with "Rotate", the All / Direct / Groups / Timed filter, and the list.
 * Rows rise in (420 ms, 80 ms + 30 ms apart) on arrival and on every filter change.
 */
@Composable
fun ChatsScreen(
    chatList: ChatList,
    relays: Relays,
    bottomPadding: Dp,
    toast: (String, WeftIcon) -> Unit,
    onLock: () -> Unit,
    onNew: () -> Unit,
    onOpen: (ChatSummary) -> Unit,
) {
    val chats by chatList.chats.collectAsState()
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    // Each arrival or filter change starts a new run of the row entrance.
    var run by remember { mutableIntStateOf(0) }
    var runStart by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val shown = chats.filter {
        when (filter) {
            Filter.All -> true
            Filter.Direct -> it.kind == ChatKind.Direct
            Filter.Groups -> it.kind == ChatKind.Group
            Filter.Timed -> it.timer != null
        }
    }

    Column(Modifier.fillMaxSize().background(WeftColors.bg)) {
        // `.bar` — 56 dp from the top, 20 dp sides, 14 dp below.
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WeftText(
                stringResource(R.string.chats_title),
                style = WeftType.screenTitle.copy(color = WeftColors.text),
                modifier = Modifier.padding(start = 2.dp).weight(1f),
            )
            WeftIconButton(WeftIcon.Lock, stringResource(R.string.chats_lock), onLock, iconSize = 19.dp)
            WeftIconButton(WeftIcon.Plus, stringResource(R.string.chats_new), onNew, accent = true, stroke = 2.6f)
        }
        RelayPill(relays, toast, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp))
        WeftSegmented(
            options = listOf(
                stringResource(R.string.filter_all),
                stringResource(R.string.filter_direct),
                stringResource(R.string.filter_groups),
                stringResource(R.string.filter_timed),
            ),
            selected = filter.ordinal,
            onSelect = {
                filter = Filter.entries[it]
                run++
                runStart = SystemClock.uptimeMillis()
            },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
        if (shown.isEmpty()) {
            WeftText(
                stringResource(R.string.chats_empty),
                style = WeftType.secondary.copy(color = WeftColors.faint, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(top = 44.dp),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 12.dp + bottomPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(shown, key = { _, c -> "$run/${c.id}" }) { i, chat ->
                    ChatRow(chat, Modifier.rise(i, runStart)) { onOpen(chat) }
                }
            }
        }
    }
}

/**
 * Row entrance: opacity 0 → 1 and 10 dp → 0, 420 ms EaseOut, starting 80 + 30·i ms into the run.
 * A row first composed after its slot has passed (scrolled into view later) just appears.
 */
@Composable
private fun Modifier.rise(index: Int, runStart: Long): Modifier {
    val reduce = rememberReduceMotion()
    val delayMs = 80 + index * 30
    val late = SystemClock.uptimeMillis() - runStart
    val a = remember { Animatable(if (reduce || late > delayMs + WeftMotion.STAGGER_ITEM) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (a.value < 1f) {
            a.animateTo(1f, tween(WeftMotion.STAGGER_ITEM, delayMillis = (delayMs - late).toInt().coerceAtLeast(0), easing = WeftMotion.EaseOut))
        }
    }
    return graphicsLayer {
        alpha = a.value
        translationY = 10.dp.toPx() * (1f - a.value)
    }
}

/** `.relay` — status dot, "relay r2 · via Tor · 38 ms" and "Rotate". */
@Composable
private fun RelayPill(relays: Relays, toast: (String, WeftIcon) -> Unit, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val inUse by relays.inUse.collectAsState()
    val textIn = remember { Animatable(1f) }
    var busy by remember { mutableStateOf(false) }
    val switched = stringResource(R.string.relay_switched)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .background(Color.White.copy(alpha = 0.04f), CircleShape)
            .cssBorder(1.dp, WeftColors.line, CircleShape)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(if (inUse == null) DotState.Switching else DotState.Live)
        val relay = inUse
        val line = if (relay == null) {
            buildAnnotatedString { append(stringResource(R.string.relay_switching)) }
        } else {
            val full = stringResource(R.string.relay_in_use, relay.name, relay.route, relay.latencyMs)
            val at = full.indexOf(relay.name)
            buildAnnotatedString {
                append(full)
                if (at >= 0) addStyle(SpanStyle(color = WeftColors.text, fontWeight = FontWeight.W600), at, at + relay.name.length)
            }
        }
        WeftText(
            line,
            style = WeftType.monoBody.copy(color = WeftColors.muted),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).graphicsLayer {
                alpha = textIn.value
                translationY = 4.dp.toPx() * (1f - textIn.value)
            },
        )
        WeftLinkButton(stringResource(R.string.relay_rotate), onClick = {
            if (busy) return@WeftLinkButton
            busy = true
            scope.launch {
                val next = relays.rotate()
                if (!reduce) {
                    textIn.snapTo(0f)
                    launch { textIn.animateTo(1f, tween(300, easing = WeftMotion.EaseOut)) }
                }
                toast(switched.format(next.name), WeftIcon.Refresh)
                busy = false
            }
        })
    }
}

/**
 * `.row` — 74 dp min, padding 12, radius 18; unread rows sit on white @ 4 %. Avatar (groups get a
 * people badge), name with a green shield when verified, time (accent2 when unread), last message,
 * timer chip and unread badge.
 */
@Composable
private fun ChatRow(chat: ChatSummary, modifier: Modifier, onClick: () -> Unit) {
    val unread = chat.unread > 0
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .weftClickable(pressedScale = 0.985f, onClick = onClick)
            .background(if (unread) Color.White.copy(alpha = 0.04f) else Color.Transparent, RoundedCornerShape(18.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            WeftAvatar(chat.initials, chat.hue)
            if (chat.kind == ChatKind.Group) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(20.dp).background(WeftColors.bg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { WeftIconView(WeftIcon.Users, 11.dp, WeftColors.muted, stroke = 2.4f) }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.weight(1f).alignByBaseline(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WeftText(
                        chat.name,
                        style = WeftType.rowName.copy(color = WeftColors.text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (chat.verified) {
                        val verified = stringResource(R.string.chats_verified)
                        WeftIconView(WeftIcon.Shield, 12.dp, WeftColors.safe, Modifier.semantics { contentDescription = verified }, stroke = 2.4f)
                    }
                }
                WeftText(
                    chat.time,
                    style = WeftType.monoCaption.copy(color = if (unread) WeftColors.accent2 else WeftColors.faint),
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                WeftText(
                    chat.last,
                    style = WeftType.secondary.copy(color = WeftColors.muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                chat.timer?.let { TimerChip(it) }
                if (unread) UnreadBadge(chat.unread)
            }
        }
    }
}

/** `.chip-timer` — timer icon 12 dp + mono 11 muted. */
@Composable
private fun RowScope.TimerChip(timer: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        WeftIconView(WeftIcon.Timer, 12.dp, WeftColors.muted, stroke = 2.2f)
        WeftText(timer, style = WeftType.monoCaption.copy(color = WeftColors.muted))
    }
}

/** `.badge` — gradient pill, min 22 dp, 7 dp sides, 11 / 800. */
@Composable
private fun UnreadBadge(count: Int) {
    Box(
        Modifier
            .height(22.dp)
            .widthIn(min = 22.dp)
            .gradientBackground(CircleShape)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) { WeftText(count.toString(), style = WeftType.badge.copy(color = WeftColors.onAccent)) }
}
