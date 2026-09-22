package app.weft.identity

import java.security.SecureRandom

/** Creates this phone's identity and returns its fingerprint as 8 groups of 4 hex digits. */
fun interface IdentityMaker {
    suspend fun create(): List<String>
}

/**
 * DEVELOPMENT ONLY — stands in until the SimpleX core (libsimplex.so) is wired in. It produces a
 * random fingerprint for the screen; it does not create keys and must not ship.
 */
object DemoIdentityMaker : IdentityMaker {
    override suspend fun create(): List<String> {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02X".format(it) }.chunked(4)
    }
}
