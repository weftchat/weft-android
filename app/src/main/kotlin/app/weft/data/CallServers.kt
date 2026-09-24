package app.weft.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * The user's own call servers (BRIEF, Phase 4), in SimpleX's format, one per line:
 *   stun:IP:3478 · turn:user:password@IP:3478?transport=udp · turns:user:password@IP:5349?transport=tcp
 * No server is built in and SimpleX's are never used. Only IP addresses are accepted (BRIEF: no DNS
 * lookups). Kept sealed in the open profile ([WeftSession.callServers]).
 */
object CallServers {
    private val _list = MutableStateFlow<List<String>>(emptyList())
    val list: StateFlow<List<String>> = _list

    /** Why a line was refused. */
    enum class Problem { Format, Domain, NoCredentials }

    fun load() { _list.value = WeftSession.callServers() }
    fun clear() { _list.value = emptyList() }

    /** Calls need at least one TURN server: with the relay-only policy nothing else connects. */
    val ready: Boolean get() = _list.value.any { it.startsWith("turn:") || it.startsWith("turns:") }

    /** Adds every valid line of [text] (several may be pasted at once); returns the first problem, if any. */
    fun add(text: String): Problem? {
        val lines = text.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return Problem.Format
        for (l in lines) check(l)?.let { return it }
        save((_list.value + lines).distinct())
        return null
    }

    fun remove(line: String) = save(_list.value - line)

    private fun save(lines: List<String>) {
        WeftSession.setCallServers(lines)
        _list.value = lines
    }

    fun check(line: String): Problem? {
        val u = uri(line) ?: return Problem.Format
        if (!isIp(u.host)) return Problem.Domain
        if (u.scheme.startsWith("turn") && u.userInfo?.contains(':') != true) return Problem.NoCredentials
        return null
    }

    /** The servers as the call page wants them: `[{urls, username, credential}]`. */
    fun iceServers(): JSONArray = JSONArray().apply {
        for (line in _list.value) {
            val u = uri(line) ?: continue
            val query = u.rawQuery?.let { "?$it" } ?: ""
            val o = JSONObject().put("urls", JSONArray().put("${u.scheme}:${u.host}:${u.port}$query"))
            u.userInfo?.split(":", limit = 2)?.let { (user, pass) -> o.put("username", user).put("credential", pass) }
            put(o)
        }
    }

    /** "turn:user:pass@1.2.3.4:3478" → "1.2.3.4:3478 · TURN udp". Never shows the password. */
    fun label(line: String): String {
        val u = uri(line) ?: return line.substringAfter('@')
        val transport = u.rawQuery?.substringAfter("transport=", "")?.takeIf { it.isNotEmpty() }?.let { " $it" } ?: ""
        return "${u.host}:${u.port} · ${u.scheme.uppercase()}$transport"
    }

    private fun uri(line: String): URI? {
        val scheme = listOf("stun", "stuns", "turn", "turns").firstOrNull { line.startsWith("$it:") } ?: return null
        val u = runCatching { URI(line.replaceFirst("$scheme:", "$scheme://")) }.getOrNull() ?: return null
        return u.takeIf { it.host != null && it.port > 0 && (it.path == null || it.path.isEmpty()) }
    }

    private fun isIp(host: String): Boolean {
        val h = host.removePrefix("[").removeSuffix("]")
        val v4 = h.split('.').let { p -> p.size == 4 && p.all { it.toIntOrNull() in 0..255 } }
        val v6 = ':' in h && h.all { it.isLetterOrDigit() || it == ':' || it == '.' } && h.none { it in 'g'..'z' || it in 'G'..'Z' }
        return v4 || v6
    }
}
