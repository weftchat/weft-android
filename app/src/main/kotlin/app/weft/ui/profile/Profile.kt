package app.weft.ui.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The six avatar colours of the mockup, as hues. */
val SWATCH_HUES = listOf(262f, 212f, 160f, 32f, 330f, 196f)

/** This phone's own profile. [swatch] indexes [SWATCH_HUES]. */
data class MyProfile(val nickname: String = "", val fingerprint: List<String> = emptyList(), val swatch: Int = 0) {
    val hue get() = SWATCH_HUES[swatch]
    /**
     * The big avatar's letter: the nickname's first letter; without a nickname, the first character
     * of the fingerprint (the mockup's fixed "J" was sample data).
     */
    val initial get() = (nickname.trim().firstOrNull() ?: fingerprint.firstOrNull()?.firstOrNull() ?: '·').uppercaseChar().toString()
}

interface ProfileStore {
    val profile: StateFlow<MyProfile>
    fun setNickname(nickname: String)
    fun setSwatch(index: Int)
    fun setFingerprint(groups: List<String>)
}

/**
 * DEVELOPMENT ONLY — keeps the profile in memory for this run, until the SimpleX core stores it.
 * This must not ship.
 */
object DemoProfile : ProfileStore {
    override val profile = MutableStateFlow(MyProfile())
    override fun setNickname(nickname: String) = profile.update { it.copy(nickname = nickname.take(24)) }
    override fun setSwatch(index: Int) = profile.update { it.copy(swatch = index.coerceIn(SWATCH_HUES.indices)) }
    override fun setFingerprint(groups: List<String>) = profile.update { it.copy(fingerprint = groups) }
}
