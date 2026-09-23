package app.weft.data

import app.weft.core.ChatCore
import app.weft.data.WeftSession.objects
import app.weft.ui.chats.ChatKind
import app.weft.ui.chats.ChatList
import app.weft.ui.chats.ChatSummary
import app.weft.ui.conversation.ChatItem
import app.weft.ui.conversation.Conversations
import app.weft.ui.conversation.PhotoItem
import app.weft.ui.conversation.TextItem
import app.weft.ui.conversation.ThreadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The chat list and the 1:1 threads, from the core. Chat ids are the core's refs: "@<contactId>"
 * for contacts, "#<groupId>" for groups. Kept current from the core's events.
 */
object CoreChats : ChatList, Conversations {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    override val chats: StateFlow<List<ChatSummary>> = _chats
    private val threads = mutableMapOf<String, MutableStateFlow<ThreadState>>()
    /** Last known contact JSON per chat, for its preferences. */
    private val contacts = mutableMapOf<String, JSONObject>()

    fun start() {
        scope.launch { WeftSession.userId.filterNotNull().collect { refresh() } }
        scope.launch {
            ChatCore.events.collect { event ->
                val r = event.optJSONObject("result") ?: return@collect
                when (r.optString("type")) {
                    "newChatItems", "chatItemsStatusesUpdated" -> itemsChanged(r.optJSONArray("chatItems"))
                    "chatItemUpdated" -> itemsChanged(JSONArray().put(r.optJSONObject("chatItem")))
                    "chatItemsDeleted", "contactConnecting", "contactSndReady", "contactConnected",
                    "contactUpdated", "contactDeletedByContact", "chatInfoUpdated", "contactPrefsUpdated" -> {
                        refresh()
                        threads.keys.toList().forEach { reload(it) }
                    }
                }
            }
        }
    }

    /** Reloads the list from the core. */
    fun refresh() = scope.launch {
        val uid = WeftSession.userId.value ?: return@launch
        val list = runCatching { WeftSession.cmd("/_get chats $uid pcc=on").getJSONArray("chats") }.getOrNull() ?: return@launch
        _chats.value = list.objects().mapNotNull(::summary)
    }

    override fun markRead(id: String) {
        _chats.value = _chats.value.map { if (it.id == id) it.copy(unread = 0) else it }
        scope.launch { runCatching { WeftSession.cmd("/_read chat $id") } }
    }

    override fun setTimer(id: String, timer: String?) {
        scope.launch {
            val contact = contacts[id] ?: return@launch
            val prefs = currentPreferences(contact)
            prefs.put("timedMessages", if (timer == null) JSONObject().put("allow", "no")
                else JSONObject().put("allow", "yes").put("ttl", TIMER_SECONDS.getValue(timer)))
            runCatching { WeftSession.cmd("/_set prefs $id $prefs") }
            refresh()
            reload(id)
        }
    }

    override fun thread(chatId: String): StateFlow<ThreadState> =
        threads.getOrPut(chatId) { MutableStateFlow(ThreadState()).also { reload(chatId) } }

    override fun send(chatId: String, text: String) {
        scope.launch {
            val msg = JSONArray().put(JSONObject().put("msgContent", JSONObject().put("type", "text").put("text", text)).put("mentions", JSONObject()))
            runCatching { WeftSession.cmd("/_send $chatId live=off ttl=default sign=off json $msg") }
                .onSuccess { itemsChanged(it.optJSONArray("chatItems")) }
        }
    }

    /** Photos need picking, metadata stripping and file transfer; not built yet. */
    override fun sendPhoto(chatId: String) = Unit

    private fun reload(chatId: String) = scope.launch {
        val chat = runCatching { WeftSession.cmd("/_get chat $chatId count=100").getJSONObject("chat") }.getOrNull() ?: return@launch
        chat.optJSONObject("chatInfo")?.optJSONObject("contact")?.let { contacts[chatId] = it }
        val items = chat.getJSONArray("chatItems").objects().mapNotNull(::item)
        threads.getOrPut(chatId) { MutableStateFlow(ThreadState()) }.value = ThreadState(items)
    }

    private fun itemsChanged(items: JSONArray?) {
        items ?: return
        val chats = items.objects().mapNotNull { ref(it.optJSONObject("chatInfo")) }.toSet()
        chats.filter { it in threads }.forEach { reload(it) }
        refresh()
    }

    private fun ref(info: JSONObject?): String? = when (info?.optString("type")) {
        "direct" -> "@" + info.getJSONObject("contact").getLong("contactId")
        "group" -> "#" + info.getJSONObject("groupInfo").getLong("groupId")
        else -> null
    }

    private fun summary(chat: JSONObject): ChatSummary? {
        val info = chat.getJSONObject("chatInfo")
        val id = ref(info) ?: return null
        val (name, kind) = when (info.getString("type")) {
            "direct" -> {
                val c = info.getJSONObject("contact")
                contacts[id] = c
                displayName(c.getJSONObject("profile")) to ChatKind.Direct
            }
            else -> displayName(info.getJSONObject("groupInfo").getJSONObject("groupProfile")) to ChatKind.Group
        }
        val contact = info.optJSONObject("contact")
        val last = chat.optJSONArray("chatItems")?.let { if (it.length() > 0) it.getJSONObject(it.length() - 1) else null }
        return ChatSummary(
            id = id,
            kind = kind,
            initials = initials(name),
            hue = hueFor(id),
            name = name,
            last = last?.let(::previewText) ?: "",
            time = last?.getJSONObject("meta")?.optString("itemTs")?.let(::listTime) ?: "",
            unread = chat.optJSONObject("chatStats")?.optInt("unreadCount") ?: 0,
            timer = contact?.let(::timerOf),
            verified = contact?.optJSONObject("activeConn")?.has("connectionCode") == true,
        )
    }

    private fun item(ci: JSONObject): ChatItem? {
        val meta = ci.getJSONObject("meta")
        val mine = ci.getJSONObject("chatDir").getString("type").endsWith("Snd")
        val content = ci.getJSONObject("content")
        val mc = content.optJSONObject("msgContent") ?: return null // features, connection notes, …
        val id = meta.getLong("itemId")
        val time = itemTime(meta.getString("itemTs"))
        return when (mc.optString("type")) {
            "image" -> PhotoItem(id, mine, time)
            else -> TextItem(
                id, mine, mc.optString("text"), time,
                delivered = meta.optJSONObject("itemStatus")?.optString("type") == "sndRcvd",
            )
        }
    }

    private fun previewText(ci: JSONObject): String {
        val mc = ci.getJSONObject("content").optJSONObject("msgContent")
        return when {
            mc == null -> ci.getJSONObject("meta").optString("itemText")
            mc.optString("type") == "image" -> "Photo · metadata stripped"
            else -> mc.optString("text")
        }
    }

    private fun displayName(profile: JSONObject) =
        profile.optString("localAlias").ifBlank { profile.optString("displayName") }

    private fun initials(name: String): String {
        val words = name.split(Regex("[\\s·_.-]+")).filter { it.isNotEmpty() }
        return when {
            words.size >= 2 -> "${words[0][0]}${words[1][0]}"
            name.isNotEmpty() -> name.substring(0, 1)
            else -> "?"
        }.uppercase()
    }

    /** A stable avatar hue per chat. */
    private fun hueFor(id: String) = ((id.hashCode() % 360 + 360) % 360).toFloat()

    /** Effective only when both sides allow it; the value is the user's own choice. */
    private fun timerOf(contact: JSONObject): String? {
        val tm = contact.optJSONObject("mergedPreferences")?.optJSONObject("timedMessages") ?: return null
        val on = tm.optJSONObject("enabled")?.let { it.optBoolean("forUser") && it.optBoolean("forContact") } ?: false
        if (!on) return null
        val ttl = tm.optJSONObject("userPreference")?.optJSONObject("preference")?.optLong("ttl") ?: return null
        if (ttl <= 0) return null
        return TIMER_SECONDS.entries.firstOrNull { it.value == ttl }?.key ?: when {
            ttl < 3600 -> "${ttl / 60} min"
            ttl < 86400 -> "${ttl / 3600} h"
            ttl < 604800 -> "${ttl / 86400} d"
            else -> "${ttl / 604800} w"
        }
    }

    /** The contact's own preference overrides, so changing one feature keeps the others. */
    private fun currentPreferences(contact: JSONObject): JSONObject {
        val merged = contact.optJSONObject("mergedPreferences") ?: return JSONObject()
        val out = JSONObject()
        merged.keys().forEach { feature ->
            merged.optJSONObject(feature)?.optJSONObject("userPreference")?.let { up ->
                if (up.optString("type") == "contact") up.optJSONObject("preference")?.let { out.put(feature, it) }
            }
        }
        return out
    }

    private val zone get() = ZoneId.systemDefault()
    private fun itemTime(ts: String) = Instant.parse(ts).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))

    /** Today "21:48" · yesterday "Yesterday" · this week "Mon" · older "Sep 12". */
    private fun listTime(ts: String): String {
        val t = Instant.parse(ts).atZone(zone)
        val today = LocalDate.now(zone)
        val day = t.toLocalDate()
        return when {
            day == today -> t.format(DateTimeFormatter.ofPattern("HH:mm"))
            day == today.minusDays(1) -> "Yesterday"
            day.isAfter(today.minusDays(7)) -> t.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            else -> t.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
        }
    }

    /** The mockup's timer choices, in seconds. */
    val TIMER_SECONDS = linkedMapOf("5 min" to 300L, "1 h" to 3600L, "24 h" to 86400L, "1 week" to 604800L)
}
