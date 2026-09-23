package app.weft.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Turns the PIN into the SQLCipher key (BRIEF, confirmed by the owner). The PIN is never stored:
 *
 *   key = HMAC-SHA256[Keystore key]( PBKDF2-HMAC-SHA256(PIN, salt, 100 000 rounds) )
 *
 * The HMAC key is made inside the Android Keystore (StrongBox when the phone has it) and can
 * never be read out, so a copied database cannot be attacked off the phone, not even by trying
 * all 10⁶ PINs. A wrong PIN gives a different key and the database simply does not open.
 * The salt is random and not secret; it lives in the app's private preferences.
 */
class DatabaseKey(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Derives the database key for [pin]. Slow on purpose (~0.1–0.3 s): call off the main thread. */
    fun derive(pin: CharArray): String {
        val stretched = PBEKeySpec(pin, salt(), ROUNDS, 256).let { spec ->
            try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        }
        val mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256).apply { init(hmacKey()) }
        val key = mac.doFinal(stretched)
        stretched.fill(0)
        return key.joinToString("") { "%02x".format(it) }.also { key.fill(0) }
    }

    /** Forgets the salt and the Keystore key: the old database can never be opened again. */
    fun destroy() {
        prefs.edit().remove(SALT).commit()
        keyStore().deleteEntry(ALIAS)
    }

    private fun salt(): ByteArray {
        prefs.getString(SALT, null)?.let { return hexToBytes(it) }
        val fresh = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(SALT, fresh.joinToString("") { "%02x".format(it) }).commit()
        return fresh
    }

    private fun hmacKey(): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        fun generate(strongBox: Boolean): SecretKey {
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).apply {
                if (strongBox && Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(true)
            }.build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE).run { init(spec); generateKey() }
        }
        return try {
            generate(strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }
    }

    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "weft.database.hmac"
        const val PREFS = "weft.security"
        const val SALT = "db.salt"
        const val ROUNDS = 100_000

        fun hexToBytes(s: String) = ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
