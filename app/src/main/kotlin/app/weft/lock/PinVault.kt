package app.weft.lock

import java.security.MessageDigest

/** Holds the app PIN. The PIN is 6 digits; it is never kept in saved UI state. */
interface PinVault {
    val isSet: Boolean
    /** Sets the PIN. Returns false if it is refused (it would clash with the duress PIN). */
    suspend fun set(pin: String): Boolean
    /** True if [pin] opens Weft. */
    suspend fun unlock(pin: String): Boolean
}

/**
 * DEVELOPMENT ONLY — keeps the PIN in memory for this run, so the screens can be tried before the
 * real vault (SQLCipher key, Android Keystore, duress PIN) exists. Nothing is protected by it and
 * it must not ship.
 */
object DemoPinVault : PinVault {
    private var pin: ByteArray? = null
    override val isSet get() = pin != null
    override suspend fun set(pin: String): Boolean {
        this.pin = pin.toByteArray()
        return true
    }
    override suspend fun unlock(pin: String): Boolean =
        this.pin?.let { MessageDigest.isEqual(it, pin.toByteArray()) } ?: false
}
