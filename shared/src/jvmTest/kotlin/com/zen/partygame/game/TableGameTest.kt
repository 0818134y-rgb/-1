package com.zen.partygame.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TableGameTest {
    private fun c(s: String): PCard {
        val suit = "♠♥♦♣".indexOf(s[0])
        val r = when (val x = s.substring(1)) { "J" -> 11; "Q" -> 12; "K" -> 13; "A" -> 14; else -> x.toInt() }
        return PCard(r, suit)
    }
    private fun h(vararg s: String) = s.map { c(it) }

    @Test fun blackjackTotals() {
        assertEquals(21, TableGame.bjTotal(h("♠A", "♥K")))
        assertTrue(TableGame.isBlackjack(h("♠A", "♥K")))
        assertEquals(12, TableGame.bjTotal(h("♠A", "♥A")))
        assertEquals(21, TableGame.bjTotal(h("♠A", "♥A", "♦9")))
        assertEquals(22, TableGame.bjTotal(h("♠K", "♥Q", "♦2")))
        assertTrue(TableGame.bjSoft(h("♠A", "♥6")))
        assertFalse(TableGame.bjSoft(h("♠A", "♥6", "♣K")))
        assertFalse(TableGame.isBlackjack(h("♠7", "♥7", "♣7")))
    }

    @Test fun pokerHands() {
        fun name(vararg s: String) = TableGame.handName(h(*s))
        assertEquals("ロイヤルストレートフラッシュ", name("♠A", "♠K", "♠Q", "♠J", "♠10"))
        assertEquals("ストレートフラッシュ", name("♥5", "♥4", "♥3", "♥2", "♥A"))
        assertEquals("フォーカード", name("♠9", "♥9", "♦9", "♣9", "♠2"))
        assertEquals("フルハウス", name("♠9", "♥9", "♦9", "♣2", "♠2"))
        assertEquals("フラッシュ", name("♦2", "♦7", "♦9", "♦J", "♦K"))
        assertEquals("ストレート", name("♠5", "♥4", "♦3", "♣2", "♠A"))
        assertEquals("ストレート", name("♠10", "♥J", "♦Q", "♣K", "♠A"))
        assertEquals("スリーカード", name("♠9", "♥9", "♦9", "♣2", "♠3"))
        assertEquals("ツーペア", name("♠9", "♥9", "♦3", "♣3", "♠K"))
        assertEquals("ワンペア", name("♠9", "♥9", "♦3", "♣4", "♠K"))
        assertEquals("ハイカード", name("♠9", "♥7", "♦3", "♣4", "♠K"))
        // 同じ役どうしの強さ
        val s = { x: List<PCard> -> TableGame.pokerScore(x) }
        assertTrue(s(h("♠A", "♥A", "♦3", "♣4", "♠5")) > s(h("♠K", "♥K", "♦Q", "♣J", "♠9")))
        assertTrue(s(h("♠9", "♥9", "♦3", "♣4", "♠K")) > s(h("♣9", "♦9", "♥3", "♠4", "♠Q")))
        assertTrue(s(h("♠6", "♥5", "♦4", "♣3", "♠2")) > s(h("♠5", "♥4", "♦3", "♣2", "♠A")))   // A から始まるストレートは一番弱い
        assertEquals(s(h("♠9", "♥9", "♦3", "♣4", "♠K")), s(h("♣9", "♦9", "♥3", "♠4", "♥K")))
    }

    private fun engine(game: GameType, n: Int = 3, seed: Int = 1, clock: LongArray = longArrayOf(0L)): Engine {
        val root = listOf(java.io.File("."), java.io.File("..")).first { java.io.File(it, "app/src/main/assets/topics.json").exists() }
        val e = Engine("123456", false, TopicBank.parse(java.io.File(root, "app/src/main/assets/topics.json").readText()), Random(seed)) { clock[0] }
        e.join("h", "ホスト", true)
        (1 until n).forEach { e.join("p$it", "P$it") }
        e.apply("h", Intent.UpdateSettings(Settings(game = game)))
        e.apply("h", Intent.Start)
        return e
    }
    private fun Engine.table(id: String = "h") = viewFor(id).screen as Screen.Table
    private fun Engine.ids() = players.map { it.id }

    @Test fun blackjackRoundSettlesAndChipsCarryOver() {
        repeat(30) { seed ->
            val e = engine(GameType.BLACKJACK, seed = seed)
            assertEquals("bet", e.table().stage)
            e.ids().forEach { assertTrue(e.apply(it, Intent.TableAct("bet20"))) }
            var guard = 0
            while (e.table().stage == "play" && guard++ < 50) {
                e.ids().forEach { id -> if (e.table(id).actions.isNotEmpty()) e.apply(id, Intent.TableAct(if (guard < 2) "hit" else "stand")) }
            }
            val t = e.table()
            assertEquals("result", t.stage)
            // ディーラーの手はすべて見える
            assertTrue(t.center.all { it != null })
            for (s in t.seats) {
                assertTrue(s.delta in listOf(-20, 0, 20, 30))
                assertEquals(100 + s.delta, s.chips)
            }
            // 次のラウンドもチップを持ち越す
            val before = t.seats.associate { it.id to it.chips }
            e.apply("h", Intent.Next)
            if (e.viewFor("h").screen is Screen.Table) {
                assertEquals(before, e.table().seats.associate { it.id to it.chips })
                assertEquals(2, e.table().round)
            }
        }
    }

    @Test fun blackjackDoubleAndTimeout() {
        val clock = longArrayOf(0L)
        val e = engine(GameType.BLACKJACK, clock = clock, seed = 5)
        clock[0] += 31_000; e.tick()   // 賭けなかった人は自動で10枚
        val t = e.table()
        if (t.stage == "play") {
            assertTrue(t.seats.all { it.bet == 10 })
            val me = t.seats.first { it.id == "h" }
            if ("double" in t.actions) {
                e.apply("h", Intent.TableAct("double"))
                assertEquals(20, e.table().seats.first { it.id == "h" }.bet)
                assertEquals(3, e.table().seats.first { it.id == "h" }.cards.size)
            } else assertTrue(me.done)
            clock[0] += 46_000; e.tick()
        }
        assertEquals("result", e.table().stage)
    }

    @Test fun hiloTenCardsWithScores() {
        val clock = longArrayOf(0L)
        val e = engine(GameType.HILO, clock = clock)
        var reveals = 0
        var guard = 0
        while (e.table().stage != "result" && guard++ < 100) {
            val t = e.table()
            when (t.stage) {
                "guess" -> {
                    assertEquals(1, t.center.size)
                    e.apply("h", Intent.TableAct("high")); e.apply("p1", Intent.TableAct("low"))
                    // p2 は答えない → 時間切れ
                    clock[0] += 21_000; e.tick()
                    assertEquals("reveal", e.table().stage); reveals++
                    assertEquals(2, e.table().center.size)
                    assertEquals(0, e.table().seats.first { it.id == "p2" }.delta)
                }
                "reveal" -> { clock[0] += 3_100; e.tick() }
            }
        }
        assertEquals(10, reveals)
        val t = e.table()
        assertEquals(0, t.seats.first { it.id == "p2" }.chips)
        assertTrue(t.seats.first { it.id == "h" }.chips + t.seats.first { it.id == "p1" }.chips in 1..10)
    }

    @Test fun pokerHidesOthersCardsAndPaysThePot() {
        val e = engine(GameType.POKER, n = 4)
        val t = e.table("p1")
        assertEquals("swap", t.stage)
        assertEquals(40, t.pot)
        assertTrue(t.seats.first { it.id == "p1" }.cards.all { it != null })
        assertTrue(t.seats.filter { it.id != "p1" }.all { s -> s.cards.all { it == null } })
        assertTrue(t.seats.first { it.id == "p1" }.label.isNotEmpty())
        e.apply("h", Intent.TableAct("swap", listOf(0, 1, 2)))
        e.ids().drop(1).forEach { e.apply(it, Intent.TableAct("swap")) }
        assertEquals("decide", e.table().stage)
        e.apply("h", Intent.TableAct("call")); e.apply("p1", Intent.TableAct("call"))
        e.apply("p2", Intent.TableAct("fold")); e.apply("p3", Intent.TableAct("fold"))
        val r = e.table("p2")
        assertEquals("result", r.stage)
        // 勝負した人の手は見える、降りた人の手は見えない
        assertTrue(r.seats.first { it.id == "p1" }.cards.all { it != null })
        assertTrue(r.seats.first { it.id == "p3" }.cards.all { it == null })
        assertEquals(400, r.seats.sumOf { it.chips })   // チップは増えも減りもしない
        val ds = r.seats.filter { it.id in setOf("h", "p1") }.map { it.delta }.sorted()
        assertTrue(ds.toString(), ds == listOf(-20, 40) || ds == listOf(10, 10))
    }

    @Test fun everybodyFoldsCarriesThePot() {
        val e = engine(GameType.POKER)
        e.ids().forEach { e.apply(it, Intent.TableAct("swap")) }
        e.ids().forEach { e.apply(it, Intent.TableAct("fold")) }
        val r = e.table()
        assertEquals(30, r.pot)
        assertTrue(r.seats.all { it.chips == 90 })
        e.apply("h", Intent.Next)
        assertEquals(60, e.table().pot)
    }

    @Test fun indianHidesOnlyYourOwnCard() {
        val e = engine(GameType.INDIAN, n = 3)
        val t = e.table("p1")
        assertEquals("decide", t.stage)
        assertNull(t.seats.first { it.id == "p1" }.cards[0])
        assertTrue(t.seats.filter { it.id != "p1" }.all { it.cards[0] != null })
        e.ids().forEach { e.apply(it, Intent.TableAct("call")) }
        val r = e.table("p1")
        assertEquals("result", r.stage)
        assertTrue(r.seats.all { it.cards[0] != null })
        assertEquals(300, r.seats.sumOf { it.chips })
        val best = r.seats.maxOf { it.cards[0]!!.r }
        r.seats.filter { it.cards[0]!!.r == best }.forEach { assertTrue(it.delta > 0) }
    }

    @Test fun brokePlayersGetPityChips() {
        val e = engine(GameType.INDIAN, n = 2)
        // 何回も勝負して、だれかが0枚になるまで
        var guard = 0
        while (guard++ < 200) {
            e.ids().forEach { e.apply(it, Intent.TableAct("call")) }
            val t = e.table()
            if (t.seats.any { it.chips <= 0 }) {
                e.apply("h", Intent.Next)
                assertTrue(e.table().seats.all { it.chips >= 0 })
                assertTrue(e.table().log.any { it.contains("おなさけチップ") })
                return
            }
            e.apply("h", Intent.Next)
        }
    }

    @Test fun leavingDoesNotStall() {
        val e = engine(GameType.POKER, n = 3)
        e.apply("h", Intent.TableAct("swap")); e.apply("p1", Intent.TableAct("swap"))
        e.leave("p2")
        assertEquals("decide", e.table().stage)
        e.apply("h", Intent.TableAct("call")); e.apply("p1", Intent.TableAct("fold"))
        assertEquals("result", e.table().stage)
        e.leave("p1")
        assertTrue(e.viewFor("h").screen is Screen.Lobby)
    }

    @Test fun everyTableLogLineIsTranslatable() {
        val dicts = listOf("en", "ko", "zh").associateWith { lang ->
            val f = listOf(java.io.File("app/src/main/assets"), java.io.File("../app/src/main/assets")).first { it.exists() }
            Regex("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:").findAll(java.io.File(f, "i18n/$lang.json").readText()).map {
                it.groupValues[1].replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
            }.toSet()
        }
        val jp = Regex("[ぁ-んァ-ヶ一-龠]")
        val missing = mutableSetOf<String>()
        fun check(k: String) { if (jp.containsMatchIn(k) && dicts.values.any { k !in it }) missing += k }
        for (g in TableGame.GAMES) repeat(15) { seed ->
            val clock = longArrayOf(0L)
            val e = engine(g, n = 3, seed = seed, clock = clock)
            var guard = 0
            while (guard++ < 400) {
                val v = e.table()
                v.seats.forEach { check(it.status); check(it.label) }; check(v.centerLabel)
                v.log.forEach { line -> LogFmt.format(line, { k -> check(k); k }, "、") }
                if (v.stage == "result") { if (guard > 300) break; e.apply("h", Intent.Next); if (e.viewFor("h").screen !is Screen.Table) break; continue }
                e.ids().forEach { id -> e.table(id).actions.randomOrNull(Random(seed + guard))?.let { a -> e.apply(id, Intent.TableAct(a, listOf(0, 2))) } }
                clock[0] += 100_000; e.tick()
            }
        }
        assertTrue("missing: $missing", missing.isEmpty())
    }
}
