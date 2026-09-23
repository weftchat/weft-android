package app.weft.relay

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The relay in use, as the Chats pill shows it: "relay r2 · via Tor · 38 ms". */
data class RelayInUse(val name: String, val route: String, val latencyMs: Int)

interface Relays {
    /** Null while switching. */
    val inUse: StateFlow<RelayInUse?>
    /** Moves to the standby relay and returns it. */
    suspend fun rotate(): RelayInUse
}

/**
 * DEVELOPMENT ONLY — the mockup's relays (r2 in use, r3 on standby), so the pill and "Rotate" can
 * be tried. Real relay status arrives in Phase 3; this must not ship.
 */
object DemoRelays : Relays {
    private var standby = RelayInUse("r3", "via Tor", 61)
    override val inUse = MutableStateFlow<RelayInUse?>(RelayInUse("r2", "via Tor", 38))

    override suspend fun rotate(): RelayInUse {
        val old = inUse.value ?: return standby
        inUse.value = null
        delay(1300)
        val next = standby.copy(route = "via Tor", latencyMs = 40 + (0 until 20).random())
        standby = old.copy(route = "direct")
        inUse.value = next
        return next
    }
}
