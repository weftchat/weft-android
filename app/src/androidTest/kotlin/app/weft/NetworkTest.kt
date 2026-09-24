package app.weft

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.weft.data.WeftSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 3 network settings, checked against the real core on an emulator. Each test makes a
 * throwaway identity and wipes it after, so it never runs where an identity already exists.
 */
@RunWith(AndroidJUnit4::class)
class NetworkTest {
    @Before
    fun freshIdentity() = runBlocking {
        assumeFalse("an identity exists on this device; not touching it", WeftSession.hasIdentity)
        WeftSession.create("482913", "test")
    }

    @After
    fun wipe() = runBlocking { if (WeftSession.hasIdentity) WeftSession.wipeAll() }

    @Test
    fun privateRoutingIsAlwaysOn() = runBlocking {
        val cfg = WeftSession.cmd("/network").getJSONObject("networkConfig")
        assertEquals("always", cfg.getString("smpProxyMode"))
        assertEquals("allowProtected", cfg.getString("smpProxyFallback"))
    }
}
