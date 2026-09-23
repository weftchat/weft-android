package app.weft.data

import app.weft.ui.add.INVITATION_LIFETIME_MS
import app.weft.ui.add.Invitation
import app.weft.ui.add.Invitations

/** No relay of the user's own yet: invitations would go through SimpleX's preset servers. */
class NoRelayException : Exception("add your own relay first")

/**
 * One-time invitations from the core. Each new code deletes the previous unused one, so an old
 * code shown on screen stops working, as the screen promises.
 */
object CoreInvitations : Invitations {
    private var pendingConnId: Long? = null

    override suspend fun renew(): Invitation {
        if (!CoreRelays.ready) CoreRelays.load()
        if (!CoreRelays.ready) throw NoRelayException()
        val uid = WeftSession.userId.value ?: throw IllegalStateException("locked")
        pendingConnId?.let { runCatching { WeftSession.cmd("/_delete :$it full notify=off") } }
        val r = WeftSession.cmd("/_connect $uid incognito=off")
        pendingConnId = r.getJSONObject("connection").getLong("pccConnId")
        val links = r.getJSONObject("connLinkInvitation")
        val link = links.optString("connShortLink").ifEmpty { links.getString("connFullLink") }
        return Invitation(link, System.currentTimeMillis() + INVITATION_LIFETIME_MS)
    }
}
