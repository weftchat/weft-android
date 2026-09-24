package app.weft.data

import app.weft.relay.PillState
import app.weft.relay.RotateResult
import app.weft.relay.Relays
import app.weft.ui.security.relayHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The user's relays as the Chats pill and "Rotate" see them, from [CoreRelays]. Only message
 * relays (smp://) count here: they hold the conversations; file relays are used per transfer.
 */
object LiveRelays : Relays {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val switching = MutableStateFlow(false)

    /** How the core reaches relays; "via Tor" once Tor is on (Phase 3, Orbot). */
    private val route: String get() = "direct"

    private fun messageRelays() = CoreRelays.servers.value.filter { it.startsWith("smp://") }

    /** The message relay in use: the one holding live queues, else the first that is not blocked. */
    private fun current(status: Map<String, CoreRelays.Status>): String? {
        val smp = messageRelays()
        return smp.firstOrNull { status[it]?.state == CoreRelays.State.InUse }
            ?: smp.firstOrNull { status[it]?.state != CoreRelays.State.Blocked }
    }

    override val pill: StateFlow<PillState> =
        combine(CoreRelays.servers, CoreRelays.status, switching) { servers, status, busy ->
            val smp = servers.filter { it.startsWith("smp://") }
            when {
                busy -> PillState.Switching
                smp.isEmpty() -> PillState.NoRelay
                else -> current(status)?.let { PillState.Live(relayHost(it), route, status[it]?.latencyMs) }
                    ?: PillState.Blocked(relayHost(smp.first()))
            }
        }.stateIn(scope, SharingStarted.Eagerly, PillState.NoRelay)

    override suspend fun refresh() {
        if (CoreRelays.servers.value.isEmpty()) runCatching { CoreRelays.load() }
        CoreRelays.refresh()
    }

    /**
     * Every 1:1 conversation gets a new receiving queue on another working message relay (the
     * core's queue switch; contacts are not asked anything). The relay being left may not take new
     * queues meanwhile, so the core has to choose another; it keeps forwarding for private routing.
     */
    override suspend fun rotate(): RotateResult {
        refresh()
        val status = CoreRelays.status.value
        val from = current(status) ?: messageRelays().firstOrNull() ?: return RotateResult.NoStandby
        val to = messageRelays().filter { it != from && status[it]?.state != CoreRelays.State.Blocked }
        if (to.isEmpty()) return RotateResult.NoStandby
        val uid = WeftSession.userId.value ?: return RotateResult.Failed
        switching.value = true
        try {
            CoreRelays.setStorage(from, false)
            val chats = WeftSession.cmd("/_get chats $uid pcc=on").getJSONArray("chats")
            for (i in 0 until chats.length()) {
                val contact = chats.getJSONObject(i).optJSONObject("chatInfo")?.optJSONObject("contact") ?: continue
                runCatching { WeftSession.cmd("/_switch @" + contact.getLong("contactId")) }
            }
            // With several standby relays the core picks one of them itself.
            return RotateResult.Switched(to.singleOrNull()?.let(::relayHost))
        } catch (_: Exception) {
            return RotateResult.Failed
        } finally {
            runCatching { CoreRelays.setStorage(from, true) }
            switching.value = false
            refresh()
        }
    }
}
