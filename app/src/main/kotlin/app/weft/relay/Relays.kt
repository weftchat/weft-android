package app.weft.relay

import kotlinx.coroutines.flow.StateFlow

/** What the Chats pill shows: "relay host · direct · 38 ms", "switching relay…", or a problem. */
sealed interface PillState {
    /** No relay of the user's own yet. */
    data object NoRelay : PillState
    data object Switching : PillState
    /** [latencyMs] is known only after a test. */
    data class Live(val name: String, val route: String, val latencyMs: Int?) : PillState
    /** Tor is on but Orbot is not running: nothing connects until it is. */
    data object OrbotOff : PillState
    /** Every message relay failed to answer. */
    data class Blocked(val name: String) : PillState
}

sealed interface RotateResult {
    data class Switched(val name: String) : RotateResult
    /** No other message relay answers a test now: there is nowhere to move to. */
    data object NoStandby : RotateResult
    data object Failed : RotateResult
}

interface Relays {
    val pill: StateFlow<PillState>
    /** Re-reads the core's view of each relay (local, no network). */
    suspend fun refresh()
    /** Moves every conversation's receiving queue off the relay in use, to a working standby one. */
    suspend fun rotate(): RotateResult
}
