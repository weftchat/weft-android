package app.weft

import android.Manifest
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.weft.ui.call.newCallPage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * SimpleX's call page as Weft ships it, running in a real WebView: it answers, and what it would
 * send to the other side never contains this phone's own addresses (only TURN relay candidates),
 * even when asked for a direct connection and given no server at all.
 */
@RunWith(AndroidJUnit4::class)
class CallPageTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val replies = LinkedBlockingQueue<JSONObject>()
    private lateinit var page: WebView

    @Before
    fun open() {
        inst.uiAutomation.grantRuntimePermission(inst.targetContext.packageName, Manifest.permission.RECORD_AUDIO)
        val loaded = CountDownLatch(1)
        inst.runOnMainSync {
            page = newCallPage(inst.targetContext, onLoaded = { loaded.countDown() }, onMessage = { replies.add(JSONObject(it)) })
        }
        assertTrue("call page did not load", loaded.await(20, TimeUnit.SECONDS))
    }

    @After
    fun close() = inst.runOnMainSync { page.destroy() }

    private fun send(command: JSONObject) = inst.runOnMainSync {
        page.evaluateJavascript("processCommand(${JSONObject().put("corrId", 1).put("command", command)})", null)
    }

    private fun reply(type: String): JSONObject {
        while (true) {
            val r = replies.poll(30, TimeUnit.SECONDS) ?: error("no '$type' from the call page")
            if (r.optJSONObject("resp")?.optString("type") == type) return r.getJSONObject("resp")
        }
    }

    /** The page's own LZ-compressed JSON, decoded by the page (evaluateJavascript returns it quoted). */
    private fun decode(s: String): String {
        val out = LinkedBlockingQueue<String>()
        inst.runOnMainSync { page.evaluateJavascript("JSON.stringify(JSON.parse(LZString.decompressFromBase64(${JSONObject.quote(s)})))") { out.add(it) } }
        val quoted = out.poll(10, TimeUnit.SECONDS) ?: error("could not decode")
        return JSONArray("[$quoted]").getString(0)
    }

    @Test
    fun answersWithEncryption() {
        send(JSONObject().put("type", "capabilities").put("media", "audio"))
        assertTrue(reply("capabilities").getJSONObject("capabilities").getBoolean("encryption"))
    }

    @Test
    fun neverOffersThisPhonesAddresses() {
        val unreachableTurn = JSONArray().put(
            JSONObject().put("urls", JSONArray().put("turn:192.0.2.1:3478?transport=udp")).put("username", "u").put("credential", "p"),
        )
        // Asks for a direct connection on purpose: Weft's page must stay relay-only.
        send(JSONObject().put("type", "start").put("media", "audio").put("iceServers", unreachableTurn).put("relay", false))
        val offer = reply("offer")
        val sdp = decode(offer.getString("offer"))
        val candidates = decode(offer.getString("iceCandidates"))
        assertNotNull(sdp)
        for (text in listOf(sdp, candidates)) {
            assertFalse("host candidate leaked: $text", "typ host" in text)
            assertFalse("server-reflexive candidate leaked: $text", "typ srflx" in text)
        }
        assertEquals("[]", candidates) // an unreachable TURN gives no relay candidate either
    }
}
