package app.weft.data

import android.content.Context
import app.weft.ui.profile.MyProfile
import app.weft.ui.profile.ProfileStore
import app.weft.ui.profile.SWATCH_HUES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The profile: the nickname lives in the core (it is what contacts see), the fingerprint is the
 * device key's (security module), the avatar colour is a local preference.
 */
object CoreProfile : ProfileStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Context
    private val prefs by lazy { app.getSharedPreferences("weft.profile", Context.MODE_PRIVATE) }
    private val _profile = MutableStateFlow(MyProfile())
    override val profile: StateFlow<MyProfile> = _profile
    private var coreProfile: JSONObject? = null
    private var saving: Job? = null

    fun start(context: Context) {
        app = context.applicationContext
        _profile.update { it.copy(swatch = prefs.getInt(SWATCH, 0), fingerprint = WeftSession.device.fingerprint ?: emptyList()) }
        scope.launch {
            WeftSession.userId.filterNotNull().collect {
                val p = runCatching { WeftSession.cmd("/u").getJSONObject("user").getJSONObject("profile") }.getOrNull() ?: return@collect
                coreProfile = p
                _profile.update { it.copy(nickname = p.optString("displayName"), fingerprint = WeftSession.device.fingerprint ?: emptyList()) }
            }
        }
    }

    fun clear() {
        saving?.cancel()
        coreProfile = null
        _profile.value = MyProfile()
    }

    /** Shown at once; saved to the core after typing pauses (a blank name is never saved). */
    override fun setNickname(nickname: String) {
        _profile.update { it.copy(nickname = nickname.take(24)) }
        saving?.cancel()
        saving = scope.launch {
            delay(800)
            val name = _profile.value.nickname.trim()
            val uid = WeftSession.userId.value ?: return@launch
            val p = coreProfile ?: return@launch
            if (name.isEmpty() || name == p.optString("displayName")) return@launch
            val updated = JSONObject(p.toString()).put("displayName", name)
            runCatching { WeftSession.cmd("/_profile $uid $updated") }.onSuccess { coreProfile = updated }
        }
    }

    override fun setSwatch(index: Int) {
        val i = index.coerceIn(SWATCH_HUES.indices)
        prefs.edit().putInt(SWATCH, i).apply()
        _profile.update { it.copy(swatch = i) }
    }

    override fun setFingerprint(groups: List<String>) = _profile.update { it.copy(fingerprint = groups) }

    private const val SWATCH = "swatch"
}
