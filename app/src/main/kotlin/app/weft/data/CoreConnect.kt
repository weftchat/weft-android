package app.weft.data

/** What happened when joining through someone's one-time code or link. */
sealed interface JoinResult {
    /** The request went out; the contact appears in Chats once their phone answers. */
    data object Connecting : JoinResult
    data object OwnCode : JoinResult
    data object AlreadyConnected : JoinResult
    /** Not a one-time invitation (a group link, an address, or not a SimpleX link at all). */
    data object NotAnInvitation : JoinResult
    data object NoRelay : JoinResult
    data class Failed(val detail: String) : JoinResult
}

/** Joins a contact from the one-time code they show (or the link they send). */
object CoreConnect {
    suspend fun join(link: String): JoinResult {
        if (!CoreRelays.ready) runCatching { CoreRelays.load() }
        // Joining makes our reply queue on our own relays; without them the core would use presets.
        if (!CoreRelays.ready) return JoinResult.NoRelay
        val uid = WeftSession.userId.value ?: return JoinResult.Failed("locked")
        val plan = try {
            WeftSession.cmd("/_connect plan $uid ${link.trim()}")
        } catch (e: CoreException) {
            return JoinResult.NotAnInvitation
        }
        val cp = plan.getJSONObject("connectionPlan")
        if (cp.optString("type") != "invitationLink") return JoinResult.NotAnInvitation
        when (cp.optJSONObject("invitationLinkPlan")?.optString("type")) {
            "ownLink" -> return JoinResult.OwnCode
            "connecting", "known" -> return JoinResult.AlreadyConnected
            "ok" -> Unit
            else -> return JoinResult.NotAnInvitation
        }
        val links = plan.getJSONObject("connLink")
        val full = links.getString("connFullLink")
        val short = links.optString("connShortLink").takeIf { it.isNotEmpty() }
        return try {
            WeftSession.cmd("/_connect $uid incognito=off $full" + (short?.let { " $it" } ?: ""))
            CoreChats.refresh()
            JoinResult.Connecting
        } catch (e: CoreException) {
            JoinResult.Failed(e.error.optString("type"))
        }
    }
}
