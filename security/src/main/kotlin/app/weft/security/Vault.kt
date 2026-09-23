package app.weft.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Which of the three codes matched. [Real] opens the real profile; [Duress] and [Panic] open the decoy. */
enum class Slot { Real, Duress, Panic }

/** A code that matched: which slot, and that profile's database key (32 bytes, kept in memory only while unlocked). */
class Opened(val slot: Slot, val profileKey: ByteArray)

/**
 * The three codes (BRIEF: real PIN, duress PIN, panic code). No code is ever stored, not even
 * encrypted. On disk there is one small file of the same size whatever is set up:
 *
 *   salt (16 bytes) | 3 slots × [ tag (32) | sealed profile key (32) ]
 *
 * For a code, `stretched = PBKDF2-HMAC-SHA256(code, salt, 100 000)`; then for each slot i,
 * `tag_i = HMAC[Keystore key i](stretched ‖ 1)` and `pad_i = HMAC[Keystore key i](stretched ‖ 2)`.
 * A slot matches when its tag equals `tag_i`; the profile key is its sealed half XOR `pad_i`.
 * The three Keystore HMAC keys never leave the chip, so a copied phone cannot be attacked off the
 * device — not even by trying all 10⁶ codes. Every attempt does the same work whatever matches,
 * and unused slots hold random bytes, so nothing tells which slots are in use.
 *
 * The profile keys are random, not derived from a code: changing a code only rewrites its slot,
 * and destroying a Keystore key makes that slot's profile key — and so its database — permanently
 * unrecoverable, even if the files are recovered.
 */
class Vault(context: Context) {
    private val file = File(context.filesDir, FILE)

    /** True once an identity exists on this phone. */
    val exists: Boolean get() = file.length() == SIZE.toLong()

    /** First run: makes the salt, the three Keystore keys and the real slot. Returns the real profile key. */
    fun create(realPin: CharArray): ByteArray {
        val salt = ByteArray(SALT).also { random.nextBytes(it) }
        val bytes = ByteArray(SIZE).also { random.nextBytes(it) }
        salt.copyInto(bytes)
        for (i in 0 until 3) { ks().deleteEntry(alias(i)); newHmacKey(i) }
        file.writeBytes(bytes)
        val key = ByteArray(32).also { random.nextBytes(it) }
        setSlot(Slot.Real, realPin, key)
        return key
    }

    /** Tries [pin] against all three slots. Always does the same work. Null: it matches none. */
    fun open(pin: CharArray): Opened? {
        val data = file.readBytes()
        if (data.size != SIZE) return null
        val stretched = stretch(pin, data.copyOfRange(0, SALT))
        var hit: Opened? = null
        for (i in 0 until 3) {
            val (tag, pad) = macs(i, stretched)
            val at = SALT + i * SLOT
            val same = MessageDigest.isEqual(tag, data.copyOfRange(at, at + 32))
            if (same) hit = Opened(Slot.entries[i], ByteArray(32) { j -> (data[at + 32 + j].toInt() xor pad[j].toInt()).toByte() })
        }
        stretched.fill(0)
        return hit
    }

    /** Writes [slot] so that [pin] opens the profile whose key is [profileKey]. */
    fun setSlot(slot: Slot, pin: CharArray, profileKey: ByteArray) {
        val data = file.readBytes()
        val stretched = stretch(pin, data.copyOfRange(0, SALT))
        val i = slot.ordinal
        if (!ks().containsAlias(alias(i))) newHmacKey(i)
        val (tag, pad) = macs(i, stretched)
        val at = SALT + i * SLOT
        tag.copyInto(data, at)
        for (j in 0 until 32) data[at + 32 + j] = (profileKey[j].toInt() xor pad[j].toInt()).toByte()
        file.writeBytes(data)
        stretched.fill(0)
    }

    /** Turns [slot] back into random bytes: no code opens it any more. */
    fun clearSlot(slot: Slot) {
        val data = file.readBytes()
        val at = SALT + slot.ordinal * SLOT
        ByteArray(SLOT).also { random.nextBytes(it) }.copyInto(data, at)
        file.writeBytes(data)
    }

    /** Destroys [slot]'s Keystore key and clears the slot: its profile can never be opened again. */
    fun destroy(slot: Slot) {
        ks().deleteEntry(alias(slot.ordinal))
        clearSlot(slot)
        newHmacKey(slot.ordinal) // a fresh key keeps every slot looking the same
    }

    /** Wipes everything the vault holds: all three Keystore keys and the keyring file. */
    fun destroyAll() {
        for (i in 0 until 3) ks().deleteEntry(alias(i))
        file.delete()
    }

    private fun stretch(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, ROUNDS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    /** (tag, pad) for slot [i]. A missing key still costs the same work and matches nothing. */
    private fun macs(i: Int, stretched: ByteArray): Pair<ByteArray, ByteArray> {
        val key = (ks().getKey(alias(i), null) as? SecretKey)
            ?: SecretKeySpec(ByteArray(32).also { random.nextBytes(it) }, "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
        return mac.doFinal(stretched + 1) to mac.doFinal(stretched + 2)
    }

    private fun newHmacKey(i: Int) {
        fun make(strongBox: Boolean) {
            val spec = KeyGenParameterSpec.Builder(alias(i), KeyProperties.PURPOSE_SIGN).apply {
                if (strongBox && Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(true)
            }.build()
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE).run { init(spec); generateKey() }
        }
        try { make(true) } catch (_: StrongBoxUnavailableException) { make(false) }
    }

    private fun ks() = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    private fun alias(i: Int) = "a$i"

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val FILE = "k"
        const val SALT = 16
        const val SLOT = 64
        const val SIZE = SALT + 3 * SLOT
        const val ROUNDS = 100_000
        val random = SecureRandom()
    }
}
