package app.weft

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.weft.data.CoreChats
import app.weft.data.CoreGroups
import app.weft.data.WeftSession
import app.weft.ui.chats.ChatKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Groups against the real core, with the user alone in them (inviting needs a second identity). */
@RunWith(AndroidJUnit4::class)
class GroupsTest {
    @Before
    fun freshIdentity(): Unit = runBlocking {
        assumeFalse("an identity exists on this device; not touching it", WeftSession.hasIdentity)
        WeftSession.create("482913", "test")
    }

    @After
    fun wipe(): Unit = runBlocking { if (WeftSession.hasIdentity) WeftSession.wipeAll() }

    @Test
    fun createdGroupHasDisappearingMessagesAndAnOwner(): Unit = runBlocking {
        val id = CoreGroups.create("night desk", emptyList())
        val info = CoreGroups.info(id)
        assertEquals(1, info.count)
        assertEquals("owner", info.myRole)
        assertTrue(info.canManage)
        val prefs = WeftSession.cmd("/_members $id").getJSONObject("group").getJSONObject("groupInfo")
            .getJSONObject("fullGroupPreferences").getJSONObject("timedMessages")
        assertEquals("on", prefs.getString("enable"))
        assertEquals(3600, prefs.getInt("ttl"))
    }

    @Test
    fun groupShowsInTheChatList(): Unit = runBlocking {
        val id = CoreGroups.create("night desk", emptyList())
        CoreChats.refresh()
        val row = withTimeout(5_000) { CoreChats.chats.first { list -> list.any { it.id == id } } }.first { it.id == id }
        assertEquals(ChatKind.Group, row.kind)
        assertEquals("night desk", row.name)
    }

    @Test
    fun cannotCreateAboveTheCap(): Unit = runBlocking {
        val tooMany = (1L..CoreGroups.MAX_MEMBERS.toLong()).toList()
        assertTrue(runCatching { CoreGroups.create("big", tooMany) }.isFailure)
    }

    @Test
    fun leavingWorks(): Unit = runBlocking {
        val id = CoreGroups.create("night desk", emptyList())
        CoreGroups.leave(id)
        val status = WeftSession.cmd("/_members $id").getJSONObject("group").getJSONObject("groupInfo")
            .getJSONObject("membership").getString("memberStatus")
        assertEquals("left", status)
    }
}
