package app.weft.data

import app.weft.data.WeftSession.objects
import android.os.SystemClock
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

    fun clear() {
        pending = null
        _servers.value = emptyList()
        _status.value = emptyMap()
        tested.clear()
    }

    enum class State { InUse, Standby, Blocked }
    /** [latencyMs] is known only once the relay has been tested from this phone. */
    data class Status(val state: State, val latencyMs: Int? = null)

    private val _status = MutableStateFlow<Map<String, Status>>(emptyMap())
    /** Per address in [servers]; missing until the first [refresh]. */
    val status: StateFlow<Map<String, Status>> = _status
    /** The last test of each relay, by [fingerprint]: the time it took, or null when it failed. */
    private val tested = mutableMapOf<String, Int?>()

    /**
     * The server's identity in an address (no password, no host). The core writes it back with
     * base64 padding ("…=") whatever was pasted, so the padding is ignored.
     */
    fun fingerprint(address: String): String =
        address.substringAfter("://").substringBefore('@').substringBefore(':').trimEnd('=')

    /**
     * Re-reads how the core sees each relay, from its own connection summary (no network traffic,
     * so it never holds up other commands): in use = connected or holding live queues; blocked =
     * only errors, or the last test failed; otherwise standby.
     */
    suspend fun refresh() {
        val uid = WeftSession.userId.value ?: return
        val summary = runCatching { WeftSession.cmd("/get servers summary $uid").getJSONObject("serversSummary") }.getOrNull() ?: return
        val seen = mutableMapOf<String, JSONObject>()
        summary.optJSONObject("currentUserSMP")?.let { smp ->
            for (k in listOf("currentlyUsedSMPServers", "previouslyUsedSMPServers", "onlyProxiedSMPServers"))
                smp.optJSONArray(k)?.objects()?.forEach { seen.putIfAbsent(fingerprint(it.getString("smpServer")), it) }
        }
        summary.optJSONObject("currentUserXFTP")?.let { xftp ->
            for (k in listOf("currentlyUsedXFTPServers", "previouslyUsedXFTPServers"))
                xftp.optJSONArray(k)?.objects()?.forEach { seen.putIfAbsent(fingerprint(it.getString("xftpServer")), it) }
        }
        _status.value = _servers.value.associateWith { a ->
            val fp = fingerprint(a)
            val s = seen[fp]
            val connected = s?.optJSONObject("sessions")?.optInt("ssConnected") ?: 0
            val errors = s?.optJSONObject("sessions")?.optInt("ssErrors") ?: 0
            val active = s?.optJSONObject("subs")?.optInt("ssActive") ?: 0
            val busy = s != null && (s.optBoolean("rcvInProgress") || s.optBoolean("sndInProgress"))
            val state = when {
                connected > 0 || active > 0 || busy -> State.InUse
                (fp in tested && tested[fp] == null) || errors > 0 -> State.Blocked
                else -> State.Standby
            }
            Status(state, tested[fp])
        }
    }

    /** Tests [address] now (this holds up other commands until it answers); null when it works. */
    suspend fun check(address: String): String? {
        val start = SystemClock.elapsedRealtime()
        val failure = runCatching { test(address) }.getOrElse { it.message ?: "error" }
        tested[fingerprint(address)] = if (failure == null) (SystemClock.elapsedRealtime() - start).toInt() else null
        refresh()
        return failure
    }

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

    /** Thrown by [remove] for the only relay of its kind: without it the core would fall back to presets. */
    class LastOfKind : Exception("the only relay of its kind")

    suspend fun remove(address: String) {
        val kind = address.substringBefore("://")
        val fp = fingerprint(address)
        if (_servers.value.none { fingerprint(it) != fp && it.startsWith("$kind://") }) throw LastOfKind()
        edit { g ->
            for (key in listOf("smpServers", "xftpServers")) {
                g.getJSONArray(key).objects().filter { fingerprint(it.getString("server")) == fp }.forEach { it.put("deleted", true) }
            }
        }
        tested.remove(fp)
        refresh()
    }

    /**
     * Whether new queues may be made on message relay [address]. [LiveRelays] turns it off for the
     * relay being left, so the core has to choose another one; the relay still forwards (proxy role).
     */
    suspend fun setStorage(address: String, on: Boolean) = edit { g ->
        g.getJSONArray("smpServers").objects().filter { fingerprint(it.getString("server")) == fingerprint(address) }.forEach {
            it.put("roles", (it.optJSONObject("roles") ?: JSONObject()).put("storage", on))
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
