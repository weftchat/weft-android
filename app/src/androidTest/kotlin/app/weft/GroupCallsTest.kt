package app.weft

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.weft.data.CallServers
import app.weft.data.CoreChats
import app.weft.data.CoreGroupCalls
import app.weft.data.CoreGroups
import app.weft.data.WeftSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The group call's rules against the real core (the user alone in a group; others simulated). */
@RunWith(AndroidJUnit4::class)
class GroupCallsTest {
    private lateinit var group: String

    @Before
    fun freshIdentity(): Unit = runBlocking {
        assumeFalse("an identity exists on this device; not touching it", WeftSession.hasIdentity)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(InstrumentationRegistry.getInstrumentation().targetContext.packageName, Manifest.permission.RECORD_AUDIO)
        WeftSession.create("482913", "test")
        group = CoreGroups.create("night desk", emptyList())
    }

    @After
    fun wipe(): Unit = runBlocking {
        CoreGroupCalls.drop()
        if (WeftSession.hasIdentity) WeftSession.wipeAll()
    }

    private fun here(from: String, id: String, video: Boolean = false) =
        JSONObject().put("kind", "here").put("id", id).put("media", if (video) "video" else "audio").put("key", "k")

    @Test
    fun noCallServerNoCall(): Unit = runBlocking {
        assertEquals(CoreGroupCalls.JoinResult.NoServers, CoreGroupCalls.start(group, "night desk", video = false))
    }

    @Test
    fun smallerIdOffers(): Unit = runBlocking {
        CallServers.add("turn:u:p@192.0.2.1:3478")
        assertEquals(CoreGroupCalls.JoinResult.Ok, CoreGroupCalls.start(group, "night desk", video = false))
        val c = CoreGroupCalls.call.value!!
        val bigger = "~" + c.me   // sorts after me: I make the offer
        val smaller = "!" + c.me  // sorts before me: they make it
        val commands = mutableListOf<JSONObject>()
        CoreGroupCalls.onMessage(group, bigger, "ana", 30f, here(bigger, c.callId))
        CoreGroupCalls.onMessage(group, smaller, "bo", 90f, here(smaller, c.callId))
        CoreGroupCalls.onMessage(group, bigger, "ana", 30f, here(bigger, c.callId)) // heartbeat: no second offer
        withTimeout(5_000) { CoreGroupCalls.toPage.first { it.optString("type") == "start"; true } }.let { commands += it }
        while (true) commands += runCatching { withTimeout(500) { CoreGroupCalls.toPage.first() } }.getOrNull() ?: break
        val offers = commands.filter { it.optString("type") == "offerTo" }.map { it.getString("peer") }
        assertEquals(listOf(bigger), offers)
        assertEquals(setOf(bigger, smaller), CoreGroupCalls.call.value!!.people.keys)
    }

    @Test
    fun seenCallsCanBeJoinedUntilFull(): Unit = runBlocking {
        CallServers.add("turn:u:p@192.0.2.1:3478")
        repeat(CoreGroupCalls.MAX_VIDEO) { i -> CoreGroupCalls.onMessage(group, "m$i", "p$i", 0f, here("m$i", "call1", video = true)) }
        assertEquals(CoreGroupCalls.MAX_VIDEO, CoreGroupCalls.available.value[group]?.count)
        assertEquals(CoreGroupCalls.JoinResult.Full, CoreGroupCalls.join(group, "night desk"))
        CoreGroupCalls.onMessage(group, "m0", "p0", 0f, JSONObject().put("kind", "leave").put("id", "call1"))
        assertEquals(CoreGroupCalls.JoinResult.Ok, CoreGroupCalls.join(group, "night desk"))
    }

    @Test
    fun signallingNeverShowsInTheChat(): Unit = runBlocking {
        CallServers.add("turn:u:p@192.0.2.1:3478")
        CoreGroupCalls.start(group, "night desk", video = false)
        kotlinx.coroutines.delay(1_500)
        val items = WeftSession.cmd("/_get chat $group count=20").getJSONObject("chat").getJSONArray("chatItems")
        val sent = (0 until items.length()).map { items.getJSONObject(it) }
            .count { it.optJSONObject("content")?.optJSONObject("msgContent")?.optString("type") == "weft-call" }
        assertTrue("the start message was not sent", sent >= 1)
        CoreChats.refresh()
        val thread = CoreChats.thread(group)
        kotlinx.coroutines.delay(500)
        assertTrue(thread.value.items.isEmpty())
    }
}
