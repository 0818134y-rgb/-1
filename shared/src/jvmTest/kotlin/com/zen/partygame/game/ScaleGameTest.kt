package com.zen.partygame.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random

class ScaleGameTest {
    private var clock = 1_000_000L
    private lateinit var bank: TopicBank
    private val root = listOf(File("."), File("..")).first { File(it, "app/src/main/assets/topics.json").exists() }

    @Before fun setUp() {
        bank = TopicBank.parse(File(root, "app/src/main/assets/topics.json").readText())
    }

    private fun engine(n: Int, cards: Int = 0, seed: Int = 7): Engine {
        val e = Engine("1", false, bank, Random(seed)) { clock }
        e.join("h", "H", true); for (i in 1 until n) e.join("p$i", "P$i")
        e.apply("h", Intent.UpdateSettings(Settings(game = GameType.SCALE, scaleCards = cards)))
        e.apply("h", Intent.Start)
        return e
    }

    private fun scale(e: Engine, id: String) = e.viewFor(id).screen as Screen.Scale
    private fun ids(e: Engine) = e.players.map { it.id }

    /** 全員が全部のカードにヒントを書く */
    private fun hintAll(e: Engine) {
        for (id in ids(e)) for (c in scale(e, id).cards.filter { it.owner == id }) e.apply(id, Intent.ScaleHint(c.uid, "ヒント${c.uid}"))
    }

    /** 全員の数字（テスト用に、それぞれの端末の画面から自分の数字を集める） */
    private fun values(e: Engine): Map<Int, Pair<String, Int>> =
        ids(e).flatMap { id -> scale(e, id).cards.filter { it.owner == id }.map { it.uid to (id to it.value!!) } }.toMap()

    @Test fun dealsUniqueSecretNumbers() {
        val e = engine(4)
        val v = scale(e, "h")
        assertFalse(v.playing)
        assertEquals(SCALE_LIVES, v.lives)
        assertEquals(4, v.cards.size)                     // 4人なら1人1枚
        // 自分の数字だけ見える
        assertTrue(v.cards.filter { it.owner == "h" }.all { it.value != null })
        assertTrue(v.cards.filter { it.owner != "h" }.all { it.value == null })
        val all = values(e).values.map { it.second }
        assertEquals(all.size, all.toSet().size)
        assertTrue(all.all { it in 1..100 })
        assertTrue(v.topic.t.isNotBlank() && v.topic.lo.isNotBlank() && v.topic.hi.isNotBlank())
    }

    @Test fun fewerPlayersGetMoreCards() {
        assertEquals(6, scale(engine(2), "h").cards.size)
        assertEquals(6, scale(engine(3), "h").cards.size)
        assertEquals(10, scale(engine(5, cards = 2), "h").cards.size)
    }

    @Test fun hintsThenPlayInOrderClears() {
        val e = engine(4)
        // ほかの人のヒントは届いたら見える
        val mine = scale(e, "p1").cards.first { it.owner == "p1" }
        e.apply("p1", Intent.ScaleHint(mine.uid, "  ライオン  "))
        assertEquals("ライオン", scale(e, "h").cards.first { it.uid == mine.uid }.hint)
        // 他人のカードには書けない
        assertFalse(e.apply("h", Intent.ScaleHint(mine.uid, "x")))
        hintAll(e)
        assertTrue(scale(e, "h").playing)
        // 小さい順に出す
        values(e).values.sortedBy { it.second }.forEach { (owner, _) -> e.apply(owner, Intent.ScalePlay) }
        val r = e.viewFor("h").screen as Screen.ScaleResult
        assertTrue(r.cleared)
        assertEquals(SCALE_LIVES, r.lives)
        assertEquals(1, r.streak)
        assertEquals(r.cards.map { it.value }, r.cards.map { it.value }.sortedBy { it })
        assertTrue(r.cards.all { it.state == ScaleState.PLAYED })
    }

    @Test fun playingTooEarlyCostsALifeAndDiscardsLowerCards() {
        val e = engine(4, seed = 11)
        e.apply("h", Intent.ScaleBegin)                   // ヒントがそろわなくてもホストは始められる
        assertTrue(scale(e, "h").playing)
        val sorted = values(e).entries.sortedBy { it.value.second }
        // いちばん大きい人が最初に出す → 残り全部が捨てられ、ライフ -1、全部なくなったのでクリア（ライフ2）
        val biggest = sorted.last().value.first
        e.apply(biggest, Intent.ScalePlay)
        val r = e.viewFor("h").screen as Screen.ScaleResult
        assertTrue(r.cleared)
        assertEquals(SCALE_LIVES - 1, r.lives)
        assertEquals(3, r.cards.count { it.state == ScaleState.MISSED })
    }

    @Test fun losingAllLivesFails() {
        val e = engine(4, cards = 3, seed = 3)            // 4人×3枚
        e.apply("h", Intent.ScaleBegin)
        val v = values(e)
        repeat(20) {
            val s = e.viewFor("h").screen as? Screen.Scale ?: return@repeat
            val held = s.cards.filter { it.state == ScaleState.HELD }.map { it.uid to v.getValue(it.uid) }
            val min = held.minBy { it.second.second }.second
            // 一番小さい数字を持っていない人が出す＝必ずミス
            val wrong = held.map { it.second.first }.distinct().firstOrNull { it != min.first } ?: min.first
            e.apply(wrong, Intent.ScalePlay)
        }
        val r = e.viewFor("h").screen as Screen.ScaleResult
        assertFalse(r.cleared)
        assertEquals(0, r.lives)
        assertEquals(0, r.streak)
        assertTrue(r.cards.any { it.state == ScaleState.HELD })   // 残っていたカードも結果では数字が見える
        assertTrue(r.cards.all { it.value != null })
    }

    @Test fun streakCountsAndResetsFromLobby() {
        val e = engine(2)
        repeat(2) {
            hintAll(e)
            values(e).values.sortedBy { it.second }.forEach { (o, _) -> e.apply(o, Intent.ScalePlay) }
            assertTrue((e.viewFor("h").screen as Screen.ScaleResult).cleared)
            assertEquals(it + 1, (e.viewFor("h").screen as Screen.ScaleResult).streak)
            e.apply("h", Intent.Next)
        }
        assertTrue(e.viewFor("h").screen is Screen.Scale)
        e.apply("h", Intent.BackToLobby); e.apply("h", Intent.Start)
        assertEquals(0, scale(e, "h").streak)
    }

    @Test fun leavingPlayerCardsAreRemoved() {
        val e = engine(3)
        val p2cards = scale(e, "p2").cards.filter { it.owner == "p2" }.map { it.uid }
        e.leave("p2")
        assertTrue(scale(e, "h").cards.none { it.uid in p2cards })
        hintAll(e)
        assertTrue(scale(e, "h").playing)
        values(e).values.sortedBy { it.second }.forEach { (o, _) -> e.apply(o, Intent.ScalePlay) }
        assertTrue((e.viewFor("h").screen as Screen.ScaleResult).cleared)
    }

    @Test fun soundsAndMusic() {
        val e = engine(3)
        assertEquals(Bgm.TALK, Bgm.track(e.viewFor("h")))
        var prev = e.viewFor("h")
        val c = scale(e, "h").cards.first { it.owner == "h" }
        e.apply("h", Intent.ScaleHint(c.uid, "a"))
        assertTrue(Sfx.SENT in Sfx.cues(prev, e.viewFor("h")))
        prev = e.viewFor("p1")
        e.apply("h", Intent.ScaleBegin)
        assertTrue(Sfx.ALERT in Sfx.cues(prev, e.viewFor("p1")))
        assertEquals(Bgm.TENSION, Bgm.track(e.viewFor("h")))
        val sorted = values(e).values.sortedBy { it.second }
        prev = e.viewFor("h")
        e.apply(sorted.last().first, Intent.ScalePlay)      // ミス
        val cues = Sfx.cues(prev, e.viewFor("h"))
        assertTrue(Sfx.BUZZER in cues)
        // 残りを小さい順に出し切る → 結果
        val v = values(e)
        while (e.viewFor("h").screen is Screen.Scale) {
            val c2 = scale(e, "h").cards.filter { it.state == ScaleState.HELD }.minBy { v.getValue(it.uid).second }
            e.apply(c2.owner, Intent.ScalePlay)
        }
        assertEquals(Bgm.RESULT, Bgm.track(e.viewFor("h")))
    }

    @Test fun everyLanguageHasScaleTopics() {
        for (l in listOf("", "_en", "_ko", "_zh")) {
            val f = File(root, "app/src/main/assets/topics$l.json")
            val b = TopicBank.parse(f.readText())
            val t = b.drawScale(Random(1))
            assertTrue("$l ${t.t}", t.t != "？" && t.lo != t.hi)
        }
        assertNull(null)
    }
}
