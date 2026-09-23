package app.weft.ui.add

import java.security.SecureRandom

/** A one-time invitation: the link the other phone opens or scans, and when this code stops working. */
data class Invitation(val link: String, val expiresAtMs: Long)

interface Invitations {
    /** Makes a new one-time invitation; the previous one stops working. */
    suspend fun renew(): Invitation
}

/** How long a code stays on screen before it is replaced (the mockup's 10:00). */
const val INVITATION_LIFETIME_MS = 10 * 60 * 1000L

/**
 * DEVELOPMENT ONLY — a random placeholder link (the mockup's `weft://invite#…`), so the QR, the
 * countdown and "Copy one-time link" can be tried. The real link is a SimpleX one-time invitation
 * made by the core; this must not ship.
 */
object DemoInvitations : Invitations {
    override suspend fun renew(): Invitation {
        val bytes = ByteArray(10).also { SecureRandom().nextBytes(it) }
        val link = "weft://invite#" + bytes.joinToString("") { "%02x".format(it) }
        return Invitation(link, System.currentTimeMillis() + INVITATION_LIFETIME_MS)
    }
}
