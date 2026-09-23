package app.weft.security

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A small file only the profile's own key can read (AES-GCM, key = SHA-256(profile key ‖ purpose)).
 * Holds the things that must not be visible on disk, such as whether a duress PIN exists.
 */
class Sealed(private val file: File, profileKey: ByteArray, purpose: String) {
    private val key = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(profileKey + purpose.toByteArray()), "AES")

    fun read(): String? {
        val data = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (data.size < 13) return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12)) }
            String(c.doFinal(data, 12, data.size - 12))
        }.getOrNull()
    }

    fun write(text: String) {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        file.parentFile?.mkdirs()
        file.writeBytes(iv + c.doFinal(text.toByteArray()))
    }
}
