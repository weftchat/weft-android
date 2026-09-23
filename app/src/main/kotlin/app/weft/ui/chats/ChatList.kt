package app.weft.ui.chats

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class ChatKind { Direct, Group }

/** One row of the chat list, already formatted for display. */
data class ChatSummary(
    val id: String,
    val kind: ChatKind,
    val initials: String,
    val hue: Float,
    val name: String,
    val last: String,
    val time: String,
    val unread: Int,
    /** Disappearing-message timer, e.g. "1 h"; null when off. */
    val timer: String?,
    val verified: Boolean = false,
)

/** The chat list, newest first. */
interface ChatList {
    val chats: StateFlow<List<ChatSummary>>
    fun markRead(id: String)
}

/**
 * DEVELOPMENT ONLY — the mockup's sample chats, so the screen can be built and compared before the
 * SimpleX core is wired in. The real list comes from the core; this must not ship.
 */
object DemoChatList : ChatList {
    override val chats = MutableStateFlow(
        listOf(
            ChatSummary("g30", ChatKind.Group, "30", 262f, "circle·30", "lena: tomorrow at 8pm, usual place", "21:48", 4, null),
            ChatSummary("nomad", ChatKind.Direct, "N", 212f, "nomad", "got it. wiping the thread once read", "21:12", 0, "1 h", verified = true),
            ChatSummary("kestrel", ChatKind.Direct, "K", 160f, "kestrel", "Photo · metadata stripped", "19:05", 0, null, verified = true),
            ChatSummary("desk", ChatKind.Group, "PD", 32f, "press desk", "tadeo: source confirmed, publishing 7am", "18:40", 2, "24 h"),
            ChatSummary("tadeo", ChatKind.Direct, "T", 38f, "tadeo", "did you rotate the relay yet?", "Yesterday", 1, null),
            ChatSummary("vela", ChatKind.Direct, "V", 230f, "vela", "matches. verified in person", "Mon", 0, "24 h", verified = true),
            ChatSummary("orion", ChatKind.Direct, "O", 292f, "orion", "see you", "Sep 12", 0, null),
        ),
    )

    override fun markRead(id: String) = chats.update { list -> list.map { if (it.id == id) it.copy(unread = 0) else it } }
}
