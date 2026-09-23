package app.weft.ui.conversation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.weft.R
import app.weft.design.IncognitoKeyboard
import app.weft.design.PhotoBubble
import app.weft.design.SystemNote
import app.weft.design.TextBubble
import app.weft.design.TypingBubble
import app.weft.design.WeftAvatar
import app.weft.design.WeftColors
import app.weft.design.WeftGhostButton
import app.weft.design.WeftIcon
import app.weft.design.WeftIconButton
import app.weft.design.WeftIconView
import app.weft.design.WeftText
import app.weft.design.WeftType
import app.weft.design.cssBorder
import app.weft.design.gradientBackground
import app.weft.design.popIn
import app.weft.design.rememberReduceMotion
import app.weft.design.weftClickable
import app.weft.ui.chats.ChatList

/**
 * Screen 4 — Conversation (1:1). Header with back, avatar, name, verification line and the timer
 * pill (opens "Disappearing messages"); the thread under a dashed "only you two" note; the composer
 * with camera, an encrypted-message field and the gradient send button.
 */
@Composable
fun ConversationScreen(
    chatId: String,
    chatList: ChatList,
    conversations: Conversations,
    toast: (String, WeftIcon) -> Unit,
    onBack: () -> Unit,
    onTimer: () -> Unit,
) {
    val chats by chatList.chats.collectAsState()
    val chat = chats.firstOrNull { it.id == chatId } ?: return
    val thread by conversations.thread(chatId).collectAsState()
    val offTimer = stringResource(R.string.timer_off)
    val photoSent = stringResource(R.string.convo_photo_sent)

    Column(Modifier.fillMaxSize().background(WeftColors.bg).imePadding()) {
        // `.chat-head` — 52 dp top, 12 dp sides and bottom, a line underneath.
        Row(
            Modifier
                .fillMaxWidth()
                .bottomLine()
                .padding(start = 12.dp, end = 12.dp, top = 52.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WeftGhostButton(WeftIcon.Back, stringResource(R.string.convo_back), onBack)
            WeftAvatar(chat.initials, chat.hue, size = 40.dp, radius = 14.dp, fontSize = 14f)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                WeftText(
                    chat.name,
                    style = WeftType.headName.copy(color = WeftColors.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val ink = if (chat.verified) WeftColors.safe else WeftColors.muted
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    WeftIconView(if (chat.verified) WeftIcon.Shield else WeftIcon.Lock, 12.dp, ink, stroke = 2.2f)
                    WeftText(
                        stringResource(if (chat.verified) R.string.convo_verified else R.string.convo_not_verified),
                        style = WeftType.monoCaption.copy(color = ink),
                        maxLines = 1,
                    )
                }
            }
            val timer = chat.timer ?: offTimer
            val label = stringResource(R.string.convo_timer_label, timer)
            Row(
                Modifier
                    .heightIn(min = 40.dp)
                    .semantics { contentDescription = label }
                    .weftClickable(onClick = onTimer)
                    .background(Color.White.copy(alpha = 0.04f), CircleShape)
                    .cssBorder(1.dp, WeftColors.line, CircleShape)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WeftIconView(WeftIcon.Timer, 14.dp, WeftColors.accent, stroke = 2.2f)
                WeftText(timer, style = WeftType.monoBody.copy(color = WeftColors.text))
            }
        }

        ThreadList(
            thread = thread,
            note = chat.timer?.let { stringResource(R.string.convo_note_timer, it) } ?: stringResource(R.string.convo_note_plain),
            modifier = Modifier.weight(1f),
        )

        Composer(
            onSend = { conversations.send(chatId, it) },
            onPhoto = {
                conversations.sendPhoto(chatId)
                toast(photoSent, WeftIcon.Check)
            },
        )
    }
}

/** `.msgs` — 14 dp top/bottom, 16 dp sides, 6 dp apart, stacked from the bottom up. */
@Composable
private fun ThreadList(thread: ThreadState, note: String, modifier: Modifier) {
    val list = rememberLazyListState()
    // Messages already here when the screen opens sit still; later ones pop in.
    val initial = remember { thread.items.map { it.id }.toSet() }
    val reduce = rememberReduceMotion()
    // Opens at the latest message; afterwards each new one scrolls smoothly into view.
    val last = thread.items.size + (if (thread.typing) 1 else 0) + (if (thread.readNote != null) 1 else 0) // the note is item 0
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(last) {
        if (!opened || reduce) list.scrollToItem(last) else list.animateScrollToItem(last)
        opened = true
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val bubbleMax = (maxWidth - 32.dp) * 0.78f
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
        ) {
            item(key = "note") {
                Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) { SystemNote(note) }
            }
            items(thread.items, key = { it.id }) { item ->
                val i = thread.items.indexOf(item)
                val prev = thread.items.getOrNull(i - 1)
                // `.b.gap` — 6 dp more when the sender changes.
                val gap = if (prev != null && prev.mine != item.mine) 6.dp else 0.dp
                val animate = item.id !in initial
                Box(
                    Modifier.fillMaxWidth().padding(top = gap),
                    contentAlignment = if (item.mine) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    when (item) {
                        is TextItem -> TextBubble(
                            item.text, item.time, item.mine, bubbleMax,
                            modifier = Modifier.popIn(animate, item.mine),
                            delivered = if (item.mine) item.delivered else null,
                        )
                        is PhotoItem -> PhotoBubble(
                            stringResource(R.string.convo_photo_stripped),
                            stringResource(R.string.convo_photo_removed, item.time),
                            modifier = Modifier.popIn(animate, mine = true),
                        )
                    }
                }
            }
            thread.readNote?.let { note ->
                item(key = "read") {
                    Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.CenterEnd) {
                        WeftText(note, style = WeftType.monoSmall.copy(color = WeftColors.faint))
                    }
                }
            }
            if (thread.typing) {
                item(key = "typing") { TypingBubble(Modifier.popIn(animate = true, mine = false)) }
            }
        }
    }
}

/**
 * `.composer` — a line on top, 10 dp top, 12 dp sides, 30 dp bottom (clear of the system bar; 10 dp
 * while the keyboard is up). Camera · field (46 dp, padlock, "Encrypted message…") · send.
 */
@Composable
private fun Composer(onSend: (String) -> Unit, onPhoto: () -> Unit) {
    val reduce = rememberReduceMotion()
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val border by animateColorAsState(if (focused) WeftColors.accent.copy(alpha = 0.55f) else WeftColors.line, tween(if (reduce) 0 else 200), label = "fieldBorder")
    val density = LocalDensity.current
    val keyboardUp = WindowInsets.ime.getBottom(density) > 0
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

    fun send() {
        val t = text.trim()
        if (t.isEmpty()) { focus.requestFocus(); return }
        text = ""
        onSend(t)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(WeftColors.bg)
            .topLine()
            .padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = if (keyboardUp) 10.dp else maxOf(30.dp, navBottom)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeftIconButton(WeftIcon.Camera, stringResource(R.string.convo_photo), onPhoto)
        Row(
            Modifier
                .weight(1f)
                .height(46.dp)
                .background(Color.White.copy(alpha = 0.04f), CircleShape)
                .cssBorder(1.dp, border, CircleShape)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeftIconView(WeftIcon.Lock, 14.dp, WeftColors.safe, stroke = 2.4f)
            IncognitoKeyboard {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    interactionSource = source,
                    textStyle = WeftType.body.copy(color = WeftColors.text, lineHeight = TextUnit.Unspecified),
                    cursorBrush = SolidColor(WeftColors.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) WeftText(stringResource(R.string.convo_placeholder), style = WeftType.body.copy(color = WeftColors.faint), maxLines = 1)
                            inner()
                        }
                    },
                )
            }
        }
        val sendLabel = stringResource(R.string.convo_send)
        Box(
            Modifier
                .size(46.dp)
                .semantics { contentDescription = sendLabel }
                .weftClickable(onClick = ::send)
                .dropShadow(CircleShape, Shadow(24.dp, WeftColors.accent, spread = (-10).dp, offset = DpOffset(0.dp, 8.dp)))
                .gradientBackground(CircleShape),
            contentAlignment = Alignment.Center,
        ) { WeftIconView(WeftIcon.Arrow, 20.dp, WeftColors.onAccent, stroke = 2.6f) }
    }
}

/** A 1 dp line along the bottom that takes up its space, like `border-bottom: 1px solid`. */
private fun Modifier.bottomLine() = drawBehind {
    val w = 1.dp.toPx()
    drawRect(WeftColors.line, topLeft = Offset(0f, size.height - w), size = size.copy(height = w))
}

/** A 1 dp line along the top (the caller adds 1 dp to its top padding). */
private fun Modifier.topLine() = drawBehind {
    drawRect(WeftColors.line, size = size.copy(height = 1.dp.toPx()))
}
