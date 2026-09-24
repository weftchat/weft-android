package app.weft

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.weft.data.CallServers
import app.weft.data.CallServers.Problem
import app.weft.data.WeftSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The user's call servers: checked, sealed in the profile, and handed to the call page. */
@RunWith(AndroidJUnit4::class)
class CallServersTest {
    private val pin = "482913"
    private val turn = "turn:weft:s3cret0pass@203.0.113.7:3478?transport=udp"

    @Before
    fun freshIdentity(): Unit = runBlocking {
        assumeFalse("an identity exists on this device; not touching it", WeftSession.hasIdentity)
        WeftSession.create(pin, "test")
    }

    @After
    fun wipe(): Unit = runBlocking { if (WeftSession.hasIdentity) WeftSession.wipeAll() }

    @Test
    fun checksLines() {
        assertNull(CallServers.check(turn))
        assertNull(CallServers.check("stun:203.0.113.7:3478"))
        assertEquals(Problem.Domain, CallServers.check("turn:weft:x@turn.example.com:3478"))
        assertEquals(Problem.NoCredentials, CallServers.check("turn:203.0.113.7:3478"))
        assertEquals(Problem.Format, CallServers.check("https://203.0.113.7"))
    }

    @Test
    fun sealedInTheProfileAndKeptAcrossALock(): Unit = runBlocking {
        assertFalse(CallServers.ready)
        assertNull(CallServers.add("stun:203.0.113.7:3478 $turn"))
        assertTrue(CallServers.ready)
        WeftSession.lock()
        assertTrue(CallServers.list.value.isEmpty())
        assertEquals(WeftSession.Unlock.Ok, WeftSession.unlock(pin))
        assertEquals(listOf("stun:203.0.113.7:3478", turn), CallServers.list.value)
    }

    @Test
    fun iceServersForThePageAndLabelsWithoutPassword(): Unit = runBlocking {
        CallServers.add(turn)
        val o = CallServers.iceServers().getJSONObject(0)
        assertEquals("turn:203.0.113.7:3478?transport=udp", o.getJSONArray("urls").getString(0))
        assertEquals("weft", o.getString("username"))
        assertEquals("s3cret0pass", o.getString("credential"))
        assertFalse("s3cret0pass" in CallServers.label(turn))
    }
}
