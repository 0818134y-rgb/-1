package com.zen.partygame.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class AdminTest {
    private var clock = 1_000_000L
    private val bank = TopicBank.parse(listOf(File("app/src/main/assets/topics.json"), File("../app/src/main/assets/topics.json")).first { it.exists() }.readText())

    private fun engine(): Engine {
        val e = Engine("123456", false, bank, Random(1)) { clock }
        e.join("h", "H", true); e.join("p1", "A"); e.join("p2", "B")
        return e
    }

    @Test fun roomShowsPlayersDevicesAndWatchedView() {
        val e = engine()
        val r = Admin.room(e, "h", "p1", mapOf("p1" to "aa", "h" to "hh"), listOf("bb"), "android")
        assertEquals("123456", r.roomCode)
        assertEquals("lobby", r.screen)
        assertEquals(listOf("h", "p1", "p2"), r.players.map { it.id })
        assertEquals("aa", r.players[1].device)
        assertEquals("p1", r.watching); assertEquals("p1", r.view!!.me)
        // いない人を見ようとしたらホストの画面
        assertEquals("h", Admin.room(e, "h", "zz", emptyMap(), emptyList(), "web").watching)
        // HostMsg として JSON にできる（ブラウザ版のホストも同じ形で送る）
        val json = WireJson.encodeToString(HostMsg.serializer(), HostMsg.AdminViewMsg(r))
        assertTrue(json.contains("\"t\":\"adminView\""))
        assertEquals(r, (WireJson.decodeFromString(HostMsg.serializer(), json) as HostMsg.AdminViewMsg).room)
    }

    @Test fun skipMovesTheGameForward() {
        val e = engine()
        e.apply("h", Intent.UpdateSettings(Settings(game = GameType.WORD_WOLF))); e.apply("h", Intent.Start)
        e.apply("h", Intent.ConfirmWord())
        assertTrue(e.viewFor("h").screen is Screen.WwReveal)
        assertTrue(Admin.skip(e, "h"))   // まだ確認していない人の分も確認
        assertTrue(e.viewFor("h").screen is Screen.WwTalk)
        assertTrue(Admin.skip(e, "h"))
        assertTrue(e.viewFor("h").screen is Screen.WwVote)
        assertTrue(Admin.skip(e, "h"))   // 投票を締め切る
        assertTrue(e.viewFor("h").screen !is Screen.WwVote)
    }

    @Test fun adminOpIsIgnoredByTheGame() {
        val e = engine()
        assertEquals(false, e.apply("p1", Intent.AdminOp(Admin.Op.KICK, "p2")))
        assertEquals(3, e.players.size)
        val op = WireJson.encodeToString(Intent.serializer(), Intent.AdminOp(Admin.Op.NOTICE, text = "x"))
        assertTrue(op.contains("\"t\":\"adminOp\""))
    }

    @Test fun playersCannotPoseAsAdmin() {
        val e = engine()
        e.join("p3", "🛡管理者")
        assertEquals("管理者", e.players.last().name)
    }

    @Test fun banList() {
        val l = WireJson.decodeFromString(BanList.serializer(), """{"v":1,"bans":[{"h":"abc","at":"2026-11-04"}]}""")
        assertTrue(l.has("abc")); assertTrue(!l.has("")); assertTrue(!l.has("x"))
    }
}
