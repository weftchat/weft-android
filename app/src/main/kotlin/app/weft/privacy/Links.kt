package app.weft.privacy

import android.content.Context

/**
 * Links in messages (BRIEF): tracking parameters come off before sending (on by default), and a
 * tapped link never opens directly — the user picks Tor Browser, the normal browser or copy.
 */
object Links {
    /** http(s) links, up to the first space; trailing punctuation belongs to the sentence. */
    val URL = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private const val TRAILING = ".,;:!?)]}'’\""

    /** Parameters that only identify who shared or clicked; removed everywhere. */
    private val TRACKERS = setOf(
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid", "ttclid", "li_fat_id",
        "igshid", "igsh", "mc_cid", "mc_eid", "_hsenc", "_hsmi", "mkt_tok", "oly_anon_id", "oly_enc_id",
        "vero_id", "wickedid", "rb_clickid", "s_cid", "ref_src", "ref_url",
    )
    /** "si" is a per-share tracker on these sites only (elsewhere it can be a real parameter). */
    private val SI_HOSTS = listOf("youtube.com", "youtu.be", "spotify.com", "instagram.com", "threads.net")

    private fun prefs(c: Context) = c.getSharedPreferences("weft.guard", Context.MODE_PRIVATE)
    fun cleanOn(c: Context) = prefs(c).getBoolean("cleanLinks", true)
    fun setCleanOn(c: Context, on: Boolean) = prefs(c).edit().putBoolean("cleanLinks", on).apply()

    /** Every link found in [text], without the sentence's trailing punctuation. */
    fun find(text: String): List<IntRange> = URL.findAll(text).map { m ->
        var end = m.range.last
        while (end > m.range.first && text[end] in TRAILING) end--
        m.range.first..end
    }.toList()

    /** [text] with tracking parameters removed from each link; everything else stays as typed. */
    fun clean(text: String): String {
        val out = StringBuilder()
        var at = 0
        for (r in find(text)) {
            out.append(text, at, r.first).append(cleanUrl(text.substring(r)))
            at = r.last + 1
        }
        return out.append(text, at, text.length).toString()
    }

    fun cleanUrl(url: String): String {
        val hashAt = url.indexOf('#')
        val fragment = if (hashAt >= 0) url.substring(hashAt) else ""
        val beforeHash = if (hashAt >= 0) url.substring(0, hashAt) else url
        val q = beforeHash.indexOf('?')
        if (q < 0) return url
        val base = beforeHash.substring(0, q)
        val host = base.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
        val siTracks = SI_HOSTS.any { host == it || host.endsWith(".$it") }
        val kept = beforeHash.substring(q + 1).split('&').filter { p ->
            val name = p.substringBefore('=').lowercase()
            p.isNotEmpty() && !name.startsWith("utm_") && name !in TRACKERS && !(siTracks && name == "si")
        }
        return base + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + fragment
    }
}
