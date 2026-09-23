package app.weft.ui.conversation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** One item of a conversation, formatted for display. */
sealed interface ChatItem {
    val id: Long
    val mine: Boolean
    val time: String
}

/** [delivered]: for my messages, false = sent (one tick), true = delivered (two ticks). */
data class TextItem(
    override val id: Long,
    override val mine: Boolean,
    val text: String,
    override val time: String,
    val delivered: Boolean = true,
    val sender: String? = null,
) : ChatItem

/** A photo, always sent with its metadata stripped. */
data class PhotoItem(override val id: Long, override val mine: Boolean, override val time: String) : ChatItem

data class ThreadState(
    val items: List<ChatItem> = emptyList(),
    val typing: Boolean = false,
    /** "read 21:12 · disappears in 58 min" under the last message, until something new arrives. */
    val readNote: String? = null,
)

interface Conversations {
    fun thread(chatId: String): StateFlow<ThreadState>
    fun send(chatId: String, text: String)
    fun sendPhoto(chatId: String)
}

/**
 * DEVELOPMENT ONLY — the mockup's sample threads and canned replies, so the screen can be built and
 * compared before the SimpleX core is wired in. Messages here go nowhere; this must not ship.
 */
object DemoConversations : Conversations {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var nextId = 1000L
    private val replies = listOf("understood. reading and wiping", "copy that", "ok — same place as last time", "noted. talk tomorrow")
    private val threads = mutableMapOf<String, MutableStateFlow<ThreadState>>()

    private fun them(t: String, at: String) = TextItem(nextId++, false, t, at)
    private fun me(t: String, at: String) = TextItem(nextId++, true, t, at)

    private fun sample(chatId: String): ThreadState = when (chatId) {
        "nomad" -> ThreadState(
            listOf(
                them("did you get there ok?", "21:02"),
                me("yes. sending you the address here, not on the other app", "21:03"),
                them("perfect. delete it as soon as you have it", "21:04"),
                PhotoItem(nextId++, true, "21:06"),
                them("got it. wiping the thread once read", "21:12"),
            ),
            readNote = "read 21:12 · disappears in 58 min",
        )
        "kestrel" -> ThreadState(listOf(them("the documents are in the usual folder", "18:51"), me("on my way. I’ll send a photo when I have them", "18:58"), PhotoItem(nextId++, true, "19:05")))
        "tadeo" -> ThreadState(listOf(them("r1 is blocked on the office network again", "Yest."), them("did you rotate the relay yet?", "Yest.")))
        "vela" -> ThreadState(listOf(me("my fingerprint ends in 58AD", "Mon"), them("matches. verified in person", "Mon")))
        "orion" -> ThreadState(listOf(me("thanks for tonight", "Sep 12"), them("see you", "Sep 12")))
        else -> ThreadState()
    }

    override fun thread(chatId: String): StateFlow<ThreadState> = threads.getOrPut(chatId) { MutableStateFlow(sample(chatId)) }

    private fun now() = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    private fun add(chatId: String, item: ChatItem) =
        (thread(chatId) as MutableStateFlow).update { it.copy(items = it.items + item, readNote = null) }

    override fun send(chatId: String, text: String) {
        val flow = thread(chatId) as MutableStateFlow
        val item = TextItem(nextId++, true, text, now(), delivered = false)
        add(chatId, item)
        scope.launch {
            delay(700)
            flow.update { t -> t.copy(items = t.items.map { if (it.id == item.id) item.copy(delivered = true) else it }) }
        }
        scope.launch {
            delay(900)
            flow.update { it.copy(typing = true) }
            delay(1300)
            flow.update { it.copy(typing = false) }
            add(chatId, TextItem(nextId++, false, replies.random(), now()))
        }
    }

    override fun sendPhoto(chatId: String) = add(chatId, PhotoItem(nextId++, true, now()))
}
