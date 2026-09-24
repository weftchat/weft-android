package app.weft

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.weft.data.CoreNetwork
import app.weft.data.CoreRelays
import app.weft.data.LiveRelays
import app.weft.data.WeftSession
import app.weft.relay.PillState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

/**
 * Tor through Orbot, on an emulator without Orbot: switching Tor on must send everything to
 * Orbot's port and, with Orbot absent, connect nowhere rather than directly.
 */
@RunWith(AndroidJUnit4::class)
class TorTest {
    private fun fp() = Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    @Before
    fun freshIdentity(): Unit = runBlocking {
        assumeFalse("an identity exists on this device; not touching it", WeftSession.hasIdentity)
        WeftSession.create("482913", "test")
        CoreRelays.clear()
    }

    @After
    fun wipe(): Unit = runBlocking {
        runCatching { CoreNetwork.setTor(false) }
        if (WeftSession.hasIdentity) WeftSession.wipeAll()
    }

    private suspend fun socks(): Any = WeftSession.cmd("/network").getJSONObject("networkConfig").opt("socksProxy") ?: JSONObject.NULL

    @Test
    fun torSendsEverythingToOrbot(): Unit = runBlocking {
        assertEquals(JSONObject.NULL, socks())
        CoreNetwork.setTor(true)
        assertEquals("127.0.0.1:9050", socks().toString().removePrefix("socks5://"))
        CoreNetwork.setTor(false)
        assertEquals(JSONObject.NULL, socks())
    }

    @Test
    fun withoutOrbotNothingConnects(): Unit = runBlocking {
        assertFalse(CoreNetwork.orbotRunning())
        // A real, reachable host with an identity it does not have: directly, the connection opens
        // and fails on the identity; through a SOCKS port nobody listens on, it never opens.
        val relay = "smp://${fp()}@159.69.21.145:5223"
        val uid = WeftSession.userId.value!!
        suspend fun failure() = WeftSession.cmd("/_server test $uid $relay").optJSONObject("testFailure")?.optJSONObject("testError").toString()
        val direct = failure()
        CoreNetwork.setTor(true)
        val viaTor = failure()
        assertTrue(direct, "unknownCAError" in direct) // reached the server
        assertTrue(viaTor, "connectError" in viaTor)   // never left the phone
        try { CoreRelays.add(relay) } catch (_: CoreRelays.NeedPartner) {}
        CoreRelays.add("xftp://${fp()}@127.0.0.3:3")
        LiveRelays.refresh()
        withTimeout(5_000) { LiveRelays.pill.first { it == PillState.OrbotOff } }
    }
}
