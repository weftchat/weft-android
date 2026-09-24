package app.weft

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.weft.data.CoreRelays.State
import app.weft.data.CoreRelays.Status
import app.weft.data.LiveRelays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** When "Switch when blocked" acts, and when it must not. */
@RunWith(AndroidJUnit4::class)
class AutoSwitchTest {
    private val a = "smp://a@10.0.0.1"
    private val b = "smp://b@10.0.0.2"
    private val c = "smp://c@10.0.0.3"
    private fun decide(vararg s: Pair<String, State>, torWithoutOrbot: Boolean = false) =
        LiveRelays.standbyToTry(s.map { it.first }, s.associate { it.first to Status(it.second) }, torWithoutOrbot)

    @Test fun blockedWithStandbySwitches() = assertEquals(b, decide(a to State.Blocked, b to State.Standby))
    @Test fun skipsBlockedStandbys() = assertEquals(c, decide(a to State.Blocked, b to State.Blocked, c to State.Standby))
    @Test fun relayStillLiveStays() = assertNull(decide(a to State.InUse, b to State.Blocked, c to State.Standby))
    @Test fun nothingBlockedStays() = assertNull(decide(a to State.Standby, b to State.Standby))
    @Test fun singleRelayStays() = assertNull(decide(a to State.Blocked))
    @Test fun allBlockedStays() = assertNull(decide(a to State.Blocked, b to State.Blocked))
    @Test fun torWithoutOrbotStays() = assertNull(decide(a to State.Blocked, b to State.Standby, torWithoutOrbot = true))
}
