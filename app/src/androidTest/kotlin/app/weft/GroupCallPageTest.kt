package app.weft

import android.Manifest
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.weft.ui.call.GROUP_CALL_PAGE
import app.weft.ui.call.newCallPage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Weft's group call page, two copies in real WebViews ("a" and "b"), signalling passed by hand:
 * it starts encrypted, a and b negotiate (offer, answer), and nothing either would send contains
 * this phone's own addresses. (Media needs a reachable TURN server: manual test.)
 */
@RunWith(AndroidJUnit4::class)
class GroupCallPageTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val pages = mutableMapOf<String, WebView>()
    private val out = mutableMapOf<String, LinkedBlockingQueue<JSONObject>>()
    private val key = "q4dUdhBRPbBy9CjTJS4aV1-SCs4v1V1qM5yTHbv3oms=" // 32 bytes, base64url with padding
    private val servers = JSONArray().put(JSONObject().put("urls", JSONArray().put("turn:192.0.2.1:3478?transport=udp")).put("username", "u").put("credential", "p"))

    @Before
    fun open() {
        inst.uiAutomation.grantRuntimePermission(inst.targetContext.packageName, Manifest.permission.RECORD_AUDIO)
        for (name in listOf("a", "b")) {
            val loaded = CountDownLatch(1)
            val q = LinkedBlockingQueue<JSONObject>().also { out[name] = it }
            inst.runOnMainSync {
                pages[name] = newCallPage(inst.targetContext, onLoaded = { loaded.countDown() }, onMessage = { q.add(JSONObject(it)) }, url = GROUP_CALL_PAGE)
            }
            assertTrue("group page $name did not load", loaded.await(20, TimeUnit.SECONDS))
        }
    }

    @After
    fun close() = inst.runOnMainSync { pages.values.forEach { it.destroy() } }

    private fun send(page: String, cmd: JSONObject) = inst.runOnMainSync { pages.getValue(page).evaluateJavascript("processGroupCommand($cmd)", null) }

    private fun next(page: String, type: String): JSONObject {
        while (true) {
            val m = out.getValue(page).poll(30, TimeUnit.SECONDS) ?: error("no '$type' from $page")
            if (m.optString("type") == "error") error("$page: ${m.optString("message")}")
            if (m.optString("type") == type) return m
        }
    }

    private fun decoded(page: String, packed: String): String {
        val q = LinkedBlockingQueue<String>()
        inst.runOnMainSync { pages.getValue(page).evaluateJavascript("JSON.stringify(JSON.parse(LZString.decompressFromBase64(${JSONObject.quote(packed)})))") { q.add(it) } }
        return JSONArray("[${q.poll(10, TimeUnit.SECONDS)}]").getString(0)
    }

    @Test
    fun twoPagesNegotiateEncryptedAndRelayOnly() {
        for (p in listOf("a", "b")) {
            send(p, JSONObject().put("type", "start").put("media", "audio").put("aesKey", key).put("iceServers", servers))
            assertTrue("$p should encrypt", next(p, "ready").getBoolean("encrypted"))
        }
        send("a", JSONObject().put("type", "offerTo").put("peer", "b"))
        val offer = next("a", "signal").getJSONObject("data")
        assertTrue(offer.getString("kind") == "offer")
        send("b", JSONObject().put("type", "signal").put("peer", "a").put("data", offer))
        val answer = next("b", "signal").getJSONObject("data")
        assertTrue(answer.getString("kind") == "answer")
        send("a", JSONObject().put("type", "signal").put("peer", "b").put("data", answer))
        for ((page, sdp) in listOf("a" to offer.getString("sdp"), "b" to answer.getString("sdp"))) {
            val text = decoded(page, sdp)
            assertFalse("host candidate from $page: $text", "typ host" in text)
            assertFalse("srflx candidate from $page: $text", "typ srflx" in text)
        }
        // Any ICE either page tries to send must be relay-only too.
        for (p in listOf("a", "b")) {
            while (true) {
                val m = out.getValue(p).poll(3, TimeUnit.SECONDS) ?: break
                if (m.optString("type") == "signal" && m.getJSONObject("data").optString("kind") == "ice") {
                    val ice = decoded(p, m.getJSONObject("data").getString("ice"))
                    assertFalse("host candidate from $p: $ice", "typ host" in ice)
                }
            }
        }
    }
}
