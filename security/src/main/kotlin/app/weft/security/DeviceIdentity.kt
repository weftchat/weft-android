package app.weft.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec

/**
 * This phone's device key and its fingerprint (BRIEF, confirmed by the owner). The key is made in
 * the Android Keystore and never leaves it; it is never sent over the network, so the fingerprint
 * identifies nothing outside this phone. Ed25519 where the Keystore supports it (Android 13+),
 * P-256 before that. Fingerprint: the first 16 bytes of SHA-256 of the public key, as 8 groups of
 * 4 hex digits.
 */
class DeviceIdentity(context: Context) {
    private val prefs = context.getSharedPreferences("weft.security", Context.MODE_PRIVATE)

    /** The saved fingerprint, or null before the identity exists. */
    val fingerprint: List<String>? get() = prefs.getString(FINGERPRINT, null)?.chunked(4)

    /** Makes a new device key (replacing any old one) and returns its fingerprint. */
    fun create(): List<String> {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        val pair = if (Build.VERSION.SDK_INT >= 33) {
            KeyPairGenerator.getInstance("Ed25519", KEYSTORE).run {
                initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).setDigests(KeyProperties.DIGEST_NONE).build())
                generateKeyPair()
            }
        } else {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).run {
                initialize(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build(),
                )
                generateKeyPair()
            }
        }
        val hex = MessageDigest.getInstance("SHA-256").digest(pair.public.encoded)
            .copyOf(16).joinToString("") { "%02X".format(it) }
        prefs.edit().putString(FINGERPRINT, hex).commit()
        return hex.chunked(4)
    }

    /** Deletes the device key and its fingerprint. */
    fun destroy() {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS)
        prefs.edit().remove(FINGERPRINT).commit()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "weft.device"
        const val FINGERPRINT = "device.fingerprint"
    }
}
