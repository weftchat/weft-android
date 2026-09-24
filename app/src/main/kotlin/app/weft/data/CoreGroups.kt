package app.weft.data

import app.weft.data.WeftSession.objects
import org.json.JSONObject

/**
 * SimpleX's own groups (BRIEF: up to 50 members). Weft makes groups with disappearing messages on
 * by default (1 h, BRIEF), adds contacts the user has already met, and shares one-time invitations
 * as a group link. Chat ids are the core's refs, "#<groupId>".
 */
object CoreGroups {
    const val MAX_MEMBERS = 50
    /** BRIEF: disappearing messages on by default, 1 h. */
    private const val DEFAULT_TTL = 3600

    data class Member(
        val id: Long,
        val name: String,
        val hue: Float,
        val role: String,
        val me: Boolean,
        /** Still being invited or connecting: not in the conversation yet. */
        val pending: Boolean,
    )

    data class Info(val members: List<Member>, val myRole: String, val invited: Boolean) {
        /** Everyone counted towards the cap, pending included. */
        val count: Int get() = members.size
        val canManage: Boolean get() = myRole == "owner" || myRole == "admin"
    }

    /** Thrown when a group already has [MAX_MEMBERS]. */
    class Full : Exception("group is full")

    private fun gid(chatId: String) = chatId.removePrefix("#").toLong()

    /** Makes the group and invites [contactIds]; returns its chat id. */
    suspend fun create(name: String, contactIds: List<Long>): String {
        require(contactIds.size < MAX_MEMBERS) { "at most ${MAX_MEMBERS - 1} people besides you" }
        val uid = WeftSession.userId.value ?: error("locked")
        val profile = JSONObject()
            .put("displayName", name.trim())
            .put("fullName", "")
            .put("groupPreferences", JSONObject().put("timedMessages", JSONObject().put("enable", "on").put("ttl", DEFAULT_TTL)))
        val info = WeftSession.cmd("/_group $uid incognito=off $profile").getJSONObject("groupInfo")
        val id = info.getLong("groupId")
        for (c in contactIds) runCatching { WeftSession.cmd("/_add #$id $c member") }
        CoreChats.refresh()
        return "#$id"
    }

    suspend fun info(chatId: String): Info {
        val group = WeftSession.cmd("/_members #${gid(chatId)}").getJSONObject("group")
        val me = group.getJSONObject("groupInfo").getJSONObject("membership")
        val members = listOf(me to true) + group.getJSONArray("members").objects().map { it to false }
        return Info(
            members = members
                .filter { (m, _) -> m.optString("memberStatus") !in GONE }
                .map { (m, mine) ->
                    Member(
                        id = m.getLong("groupMemberId"),
                        name = m.getJSONObject("memberProfile").optString("displayName"),
                        hue = hue(m.optString("memberId")),
                        role = m.optString("memberRole"),
                        me = mine,
                        pending = m.optString("memberStatus") in PENDING,
                    )
                },
            myRole = me.optString("memberRole"),
            invited = me.optString("memberStatus") == "invited",
        )
    }

    /** Accepts an invitation the user received. */
    suspend fun join(chatId: String) {
        WeftSession.cmd("/_join #${gid(chatId)}")
        CoreChats.refresh()
    }

    suspend fun add(chatId: String, contactId: Long) {
        if (info(chatId).count >= MAX_MEMBERS) throw Full()
        WeftSession.cmd("/_add #${gid(chatId)} $contactId member")
    }

    suspend fun remove(chatId: String, memberId: Long) {
        WeftSession.cmd("/_remove #${gid(chatId)} $memberId")
    }

    suspend fun leave(chatId: String) {
        WeftSession.cmd("/_leave #${gid(chatId)}")
        CoreChats.refresh()
    }

    /**
     * The group's invitation link (made the first time). Null once the group is full: the link is
     * deleted then, so nobody else can join through it.
     */
    suspend fun link(chatId: String): String? {
        val id = gid(chatId)
        if (info(chatId).count >= MAX_MEMBERS) {
            runCatching { WeftSession.cmd("/_delete link #$id") }
            return null
        }
        val r = runCatching { WeftSession.cmd("/_get link #$id") }.getOrNull()
            ?: WeftSession.cmd("/_create link #$id member")
        val l = r.getJSONObject("groupLink").getJSONObject("connLinkContact")
        return l.optString("connShortLink").ifEmpty { l.getString("connFullLink") }
    }

    /** A stable colour per member, for their name on the first bubble of a run. */
    fun hue(memberId: String) = ((memberId.hashCode() % 360 + 360) % 360).toFloat()

    private val GONE = setOf("rejected", "removed", "left", "deleted")
    private val PENDING = setOf("invited", "pending_approval", "pending_review", "introduced", "intro-inv", "accepted", "announced")
}
