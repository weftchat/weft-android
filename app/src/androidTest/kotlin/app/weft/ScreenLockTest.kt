package app.weft

import android.app.ActivityManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.weft.core.ChatCore
import app.weft.data.Background
import app.weft.data.WeftSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** "Receive messages while locked": the PIN covers the app while the core keeps listening. */
@RunWith(AndroidJUnit4::class)
class ScreenLockTest {
    private val pin = "482913"
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun freshIdentity(): Unit = runBlocking {
        assumeFalse("an identity exists on this device; not touching it", WeftSession.hasIdentity)
        WeftSession.create(pin, "test")
    }

    @After
    fun wipe(): Unit = runBlocking { if (WeftSession.hasIdentity) WeftSession.wipeAll() }

    @Suppress("DEPRECATION") // still lists the app's own services
    private suspend fun serviceRunning(): Boolean {
        delay(2_500) // the service follows the session after a second
        return context.getSystemService(ActivityManager::class.java).getRunningServices(20)
            .any { it.service.className == WeftService::class.java.name }
    }

    @Test
    fun screenLockKeepsListening(): Unit = runBlocking {
        assertTrue(Background.listen(context)) // on by default
        assertTrue(serviceRunning())
        WeftSession.lockScreen()
        assertTrue(WeftSession.screenLocked.value)
        assertFalse(WeftSession.isUnlocked)
        assertTrue(ChatCore.isOpen)
        assertNotNull(WeftSession.userId.value)
        assertTrue(serviceRunning())
    }

    @Test
    fun wrongPinKeepsListeningRightPinUnlocks(): Unit = runBlocking {
        WeftSession.lockScreen()
        assertEquals(WeftSession.Unlock.WrongPin, WeftSession.unlock("000001"))
        assertTrue(ChatCore.isOpen)
        assertTrue(WeftSession.screenLocked.value)
        assertEquals(WeftSession.Unlock.Ok, WeftSession.unlock(pin))
        assertFalse(WeftSession.screenLocked.value)
        assertTrue(WeftSession.isUnlocked)
    }

    @Test
    fun padlockClosesEverything(): Unit = runBlocking {
        WeftSession.lockScreen()
        WeftSession.lock()
        assertFalse(ChatCore.isOpen)
        assertNull(WeftSession.userId.value)
        assertFalse(WeftSession.screenLocked.value)
        assertFalse(serviceRunning())
    }
}
