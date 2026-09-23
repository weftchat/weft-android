package app.weft.lock

import java.security.MessageDigest

/** Result of setting a code. */
enum class SetResult { Ok, Taken, NeedRelay }

/** Holds a code. Codes are 6 digits; they are never kept in saved UI state. */
interface PinVault {
    /** Sets the code. */
    suspend fun set(pin: String): SetResult
    /** True if [pin] opens Weft. */
    suspend fun unlock(pin: String): Boolean
}

/** The real vault: the PIN opens (or, the first time, creates) the encrypted store. */
class CorePinVault(private val nickname: () -> String) : PinVault {
    override suspend fun set(pin: String): SetResult {
        val session = app.weft.data.WeftSession
        if (!session.hasIdentity) { session.create(pin, nickname()); return SetResult.Ok }
        return if (session.changePin(pin)) SetResult.Ok else SetResult.Taken
    }
    override suspend fun unlock(pin: String): Boolean =
        app.weft.data.WeftSession.unlock(pin) == app.weft.data.WeftSession.Unlock.Ok
}

/** A second or third code (duress PIN, panic code), chosen from inside Security. */
class SecondCodeVault(private val panic: Boolean) : PinVault {
    override suspend fun set(pin: String): SetResult {
        val session = app.weft.data.WeftSession
        return when (if (panic) session.enablePanic(pin) else session.enableDuress(pin)) {
            app.weft.data.WeftSession.SetCode.Ok -> SetResult.Ok
            app.weft.data.WeftSession.SetCode.NeedRelay -> SetResult.NeedRelay
            else -> SetResult.Taken
        }
    }
    override suspend fun unlock(pin: String): Boolean = false
}
