package app.weft.data

import app.weft.data.WeftSession.objects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * The user's own relays (BRIEF: no relay is built in; SimpleX's presets are off). The core needs
 * both a message relay (smp://) and a file relay (xftp://); with either missing it would silently
 * fall back to the preset servers, so Weft makes and accepts no invitations until both are set.
 */
object CoreRelays {
    /**
     * The core saves message and file relays only together: a tested address whose partner is still
     * missing waits here until the partner is added.
     */
    private var pending: String? = null

    /** Thrown when [address] works but its partner protocol is still missing; [need] is "smp" or "xftp". */
    class NeedPartner(val need: String) : Exception("add the $need:// address too")

    /**
     * Some servers print several ports ("host:5223,443"); this core accepts one, so the first (the
     * main one) is kept. Several hosts ("a,b:5223") are left as they are.
     */
    fun normalize(address: String): String = address.trim().replace(Regex(":(\\d+)(,\\d+)+$"), ":$1")

    private val _servers = MutableStateFlow<List<String>>(emptyList())
    /** The user's enabled relay addresses. */
    val servers: StateFlow<List<String>> = _servers

    val ready: Boolean get() = _servers.value.any { it.startsWith("smp://") } && _servers.value.any { it.startsWith("xftp://") }

    suspend fun load() {
        val uid = WeftSession.userId.value ?: return
        val groups = WeftSession.cmd("/_servers $uid").getJSONArray("userServers")
        _servers.value = custom(groups)?.let { g ->
            listOf("smpServers", "xftpServers").flatMap { k -> g.getJSONArray(k).objects() }
                .filter { it.optBoolean("enabled") && !it.optBoolean("deleted") }
                .map { it.getString("server") }
        } ?: emptyList()
    }

    /** Tests [address] against the real server; null when it works, else what failed. */
    suspend fun test(address: String): String? {
        val uid = WeftSession.userId.value ?: return "Weft is locked"
        val r = WeftSession.cmd("/_server test $uid ${normalize(address)}")
        val failure = r.optJSONObject("testFailure") ?: return null
        return failure.optString("testStep").ifEmpty { "test failed" }
    }

    /**
     * Adds [address] (smp:// or xftp://) to the user's own servers, together with a waiting partner.
     * Throws [NeedPartner] when the other kind is still missing (the address then waits).
     */
    suspend fun add(address: String) {
        val a = normalize(address)
        val both = listOfNotNull(pending, a).distinct()
        val hasSmp = both.any { it.startsWith("smp://") } || _servers.value.any { it.startsWith("smp://") }
        val hasXftp = both.any { it.startsWith("xftp://") } || _servers.value.any { it.startsWith("xftp://") }
        if (!hasSmp || !hasXftp) {
            pending = a
            throw NeedPartner(if (hasSmp) "xftp" else "smp")
        }
        edit { g -> both.forEach { put(g, it) } }
        pending = null
    }

    private fun put(g: JSONObject, a: String) {
        val key = if (a.startsWith("xftp://")) "xftpServers" else "smpServers"
        val list = g.getJSONArray(key)
        if (list.objects().none { it.getString("server") == a && !it.optBoolean("deleted") }) {
            list.put(JSONObject().put("server", a).put("preset", false).put("enabled", true).put("roles", JSONObject()).put("deleted", false))
        }
    }

    suspend fun remove(address: String) = edit { g ->
        for (key in listOf("smpServers", "xftpServers")) {
            g.getJSONArray(key).objects().filter { it.getString("server") == address }.forEach { it.put("deleted", true) }
        }
    }

    /** Puts [addresses] into another user's own servers (used to prepare the decoy profile). */
    suspend fun seed(uid: Long, addresses: List<String>) {
        val groups = WeftSession.cmd("/_servers $uid").getJSONArray("userServers")
        val group = custom(groups) ?: JSONObject()
            .put("smpServers", JSONArray()).put("xftpServers", JSONArray()).put("chatRelays", JSONArray())
            .also { groups.put(it) }
        addresses.forEach { put(group, it) }
        WeftSession.cmd("/_servers $uid $groups")
    }

    private suspend fun edit(change: (JSONObject) -> Unit) {
        val uid = WeftSession.userId.value ?: return
        val groups = WeftSession.cmd("/_servers $uid").getJSONArray("userServers")
        val group = custom(groups) ?: JSONObject()
            .put("smpServers", JSONArray()).put("xftpServers", JSONArray()).put("chatRelays", JSONArray())
            .also { groups.put(it) }
        change(group)
        WeftSession.cmd("/_servers $uid $groups")
        load()
    }

    /** The group of servers that belongs to no operator: the user's own. */
    private fun custom(groups: JSONArray): JSONObject? =
        groups.objects().firstOrNull { !it.has("operator") || it.isNull("operator") }
}
