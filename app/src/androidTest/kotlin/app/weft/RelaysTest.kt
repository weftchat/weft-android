package app.weft

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.weft.data.CoreRelays
import app.weft.data.LiveRelays
import app.weft.data.WeftSession
import app.weft.relay.PillState
import app.weft.relay.RotateResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

/**
 * The user's relay list against the real core, with relays that do not exist (nothing listens on
 * 127.0.0.x at those ports), so nothing leaves the emulator. The core refuses two relays on one
 * host, hence three addresses. It also writes fingerprints back with padding: compare those.
 */
@RunWith(AndroidJUnit4::class)
class RelaysTest {
    private fun fp() = Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    private val smp1 = "smp://${fp()}@127.0.0.1:1"
    private val smp2 = "smp://${fp()}@127.0.0.2:2"
    private val xftp = "xftp://${fp()}@127.0.0.3:3"

    @Before
    fun freshIdentity(): Unit = runBlocking {
        assumeFalse("an identity exists on this device; not touching it", WeftSession.hasIdentity)
        WeftSession.create("482913", "test")
        CoreRelays.clear()
    }

    @After
    fun wipe(): Unit = runBlocking { if (WeftSession.hasIdentity) WeftSession.wipeAll() }

    private fun fps(vararg a: String) = a.map(CoreRelays::fingerprint).toSet()

    private suspend fun pill() = withTimeout(5_000) { LiveRelays.pill.first { it != PillState.Switching } }

    @Test
    fun noRelayYet(): Unit = runBlocking {
        LiveRelays.refresh()
        assertEquals(PillState.NoRelay, pill())
    }

    @Test
    fun onlyRelayOfItsKindCannotBeRemoved(): Unit = runBlocking {
        try { CoreRelays.add(smp1); fail("needs its partner") } catch (_: CoreRelays.NeedPartner) {}
        CoreRelays.add(xftp)
        assertEquals(fps(smp1, xftp), fps(*CoreRelays.servers.value.toTypedArray()))
        try { CoreRelays.remove(smp1); fail("last message relay removed") } catch (_: CoreRelays.LastOfKind) {}
        try { CoreRelays.remove(xftp); fail("last file relay removed") } catch (_: CoreRelays.LastOfKind) {}
        CoreRelays.add(smp2)
        CoreRelays.remove(smp1)
        assertEquals(fps(smp2, xftp), fps(*CoreRelays.servers.value.toTypedArray()))
    }

    @Test
    fun relayThatDoesNotAnswerIsBlocked(): Unit = runBlocking {
        try { CoreRelays.add(smp1) } catch (_: CoreRelays.NeedPartner) {}
        CoreRelays.add(xftp)
        assertNotNull(CoreRelays.check(smp1))
        val saved = CoreRelays.servers.value.first { it.startsWith("smp://") }
        assertEquals(CoreRelays.State.Blocked, CoreRelays.status.value[saved]?.state)
        withTimeout(5_000) { LiveRelays.pill.first { it is PillState.Blocked } }
    }

    @Test
    fun rotateNeedsAWorkingStandby(): Unit = runBlocking {
        try { CoreRelays.add(smp1) } catch (_: CoreRelays.NeedPartner) {}
        CoreRelays.add(xftp)
        assertEquals(RotateResult.NoStandby, LiveRelays.rotate())
        CoreRelays.add(smp2)
        CoreRelays.check(smp2) // fails: a blocked relay is no standby either
        assertEquals(RotateResult.NoStandby, LiveRelays.rotate())
    }
}
