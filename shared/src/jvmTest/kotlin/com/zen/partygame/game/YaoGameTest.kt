package com.zen.partygame.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class YaoGameTest {
    private fun game(n: Int = 3, seed: Int = 1, clock: LongArray = longArrayOf(0L)) =
        YaoGame((0 until n).map { "p$it" }, { it }, Random(seed), { clock[0] })

    private fun Screen.Yao.seat(id: String) = seats.first { it.id == id }

    @Test fun startsWithStatsAndHands() {
        val g = game()
        val v = g.view("p0")
        assertEquals(3, v.seats.size)
        v.seats.forEach { assertEquals(40, it.hp); assertEquals(10, it.mp); assertEquals(20, it.coin) }
        // 手番の人は最初の1枚を引かないので、全員7枚
        assertTrue(v.seats.all { it.hand == 7 })
        assertEquals(7, v.hand.size)
    }

    /** 手番の人に、指定のカードを持たせる（テスト用に山札から探す代わりに、カードを直接作る） */
    private fun YaoGame.give(id: String, cardId: String): Int {
        val f = YaoGame::class.java.getDeclaredField("seats").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val seats = f.get(this) as Map<String, Any>
        val seat = seats.getValue(id)
        @Suppress("UNCHECKED_CAST") val hand = seat.javaClass.getDeclaredMethod("getHand").invoke(seat) as MutableList<YaoCard>
        val uid = 100_000 + hand.size + cardId.hashCode().and(0xFFF) * 10
        hand += YaoCard(uid, cardId); return uid
    }

    @Test fun weaponPlusArmorDamage() {
        val g = game(seed = 3)
        val a = g.turn; val t = g.view(a).seats.first { it.id != a }.id
        val w = g.give(a, "bat"); val p = g.give(a, "kiai"); val arm = g.give(t, "nabebuta")
        assertTrue(g.act(a, "play", listOf(w, p), t, ""))
        assertEquals(YaoGame.Step.DEFEND, g.step)
        assertEquals(10, g.view(t).attack!!.atk)
        assertTrue(g.act(t, "defend", listOf(arm), null, ""))
        assertEquals(33, g.view(t).seat(t).hp)   // 10 - 3 = 7
        assertEquals(YaoGame.Step.TURN, g.step)
        assertTrue(g.turn != a)
    }

    @Test fun mirrorReflectsAndHarisenHalvesArmor() {
        val g = game(seed = 4)
        val a = g.turn; val t = g.view(a).seats.first { it.id != a }.id
        val w = g.give(a, "monohoshi"); val m = g.give(t, "kagami")
        g.act(a, "play", listOf(w), t, ""); g.act(t, "defend", listOf(m), null, "")
        assertEquals(34, g.view(a).seat(a).hp); assertEquals(40, g.view(a).seat(t).hp)
        // ハリセン（攻4）はなべのふた（防3）を半分（1）にする → 3ダメージ
        val g2 = game(seed = 5)
        val a2 = g2.turn; val t2 = g2.view(a2).seats.first { it.id != a2 }.id
        val h = g2.give(a2, "harisen"); val nb = g2.give(t2, "nabebuta")
        g2.act(a2, "play", listOf(h), t2, ""); g2.act(t2, "defend", listOf(nb), null, "")
        assertEquals(37, g2.view(a2).seat(t2).hp)
    }

    @Test fun miraclesNeedMpAndIgnoreArmor() {
        val g = game(seed = 6)
        val a = g.turn; val t = g.view(a).seats.first { it.id != a }.id
        val k = g.give(a, "kitsunebi")
        assertTrue(g.act(a, "play", listOf(k), t, ""))
        assertEquals(34, g.view(a).seat(t).hp); assertEquals(7, g.view(a).seat(a).mp)
        val g2 = game(seed = 7)
        val a2 = g2.turn
        val big = g2.give(a2, "tenpen")   // 霊力9 → 10 あるので使える
        assertTrue(g2.act(a2, "play", listOf(big), null, ""))
        assertTrue(g2.view(a2).seats.filter { it.id != a2 }.all { it.hp == 32 })
    }

    @Test fun allOthersAttackWaitsForEveryone() {
        val clock = longArrayOf(0L)
        val g = game(n = 4, seed = 8, clock = clock)
        val a = g.turn
        val d = g.give(a, "taiko")
        g.act(a, "play", listOf(d), null, "")
        assertEquals(3, g.view(a).attack!!.waiting.size)
        clock[0] += 26_000; g.tick()   // 誰も守らなかった → 全員4ダメージ
        assertTrue(g.view(a).seats.filter { it.id != a }.all { it.hp == 36 })
    }

    @Test fun killerGetsTheFuneralMoneyAndGameEnds() {
        val g = game(n = 2, seed = 9)
        var guard = 0
        while (g.step != YaoGame.Step.OVER && guard++ < 50) {
            val a = g.turn; val t = g.view(a).seats.first { it.id != a }.id
            val w = g.give(a, "otama")
            g.act(a, "play", listOf(w), t, "")
            g.act(t, "defend", emptyList(), null, "")
        }
        val v = g.view("p0")
        assertTrue(v.over)
        assertEquals(1, v.winners.size)
        val win = v.seat(v.winners[0])
        assertEquals(40, win.coin)   // 香典 20
    }

    @Test fun itemsAndExchange() {
        val g = game(seed = 10)
        val a = g.turn; val t = g.view(a).seats.first { it.id != a }.id
        val r = g.give(a, "ryogae")
        assertFalse(g.act(a, "play", listOf(r), null, "mp>mp"))
        assertTrue(g.act(a, "play", listOf(r), null, "coin>hp"))
        assertEquals(48, g.view(a).seat(a).hp); assertEquals(12, g.view(a).seat(a).coin)
        val b = g.turn
        val dor = g.give(b, "dorobo")
        assertTrue(g.act(b, "play", listOf(dor), a, ""))
        assertEquals(4, g.view(a).seat(a).coin)
    }

    @Test fun prayDiscardsAndDrawsOneMore() {
        val g = game(seed = 12)
        val a = g.turn
        val hand = g.view(a).hand
        assertTrue(g.act(a, "pray", hand.take(3).map { it.uid }, null, ""))
        assertEquals(8, g.view(a).seats.first { it.id == a }.hand)   // 7 - 3 + 4
    }

    @Test fun curseTicksOnTurnStart() {
        val g = game(n = 2, seed = 11)
        val a = g.turn; val t = g.view(a).seats.first { it.id != a }.id
        val c = g.give(a, "tatari")
        g.act(a, "play", listOf(c), t, "")
        // t の手番のはじめに3ダメージ
        assertEquals(t, g.turn)
        assertEquals(37, g.view(a).seat(t).hp)
        assertEquals(2, g.view(a).seat(t).curse)
    }

    private val dicts by lazy {
        val f = listOf(java.io.File("app/src/main/assets"), java.io.File("../app/src/main/assets")).first { it.exists() }
        listOf("en", "ko", "zh").map { lang ->
            Regex("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:").findAll(java.io.File(f, "i18n/$lang.json").readText()).map {
                it.groupValues[1].replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
            }.toSet()
        }
    }
    private val missing = mutableSetOf<String>()
    private fun check(k: String) { if (Regex("[ぁ-んァ-ヶ一-龠]").containsMatchIn(k) && dicts.any { k !in it }) missing += k }

    @Test fun randomGamesAlwaysFinishThroughTheEngine() {
        val root = listOf(java.io.File("."), java.io.File("..")).first { java.io.File(it, "app/src/main/assets/topics.json").exists() }
        val bank = TopicBank.parse(java.io.File(root, "app/src/main/assets/topics.json").readText())
        repeat(25) { seed ->
            val clock = longArrayOf(0L)
            val e = Engine("1", false, bank, Random(seed)) { clock[0] }
            e.join("h", "H", true); e.join("b", "B"); e.join("c", "C")
            e.apply("h", Intent.UpdateSettings(Settings(game = GameType.YAO))); e.apply("h", Intent.Start)
            val rng = Random(seed)
            var guard = 0
            while (e.viewFor("h").screen.let { it is Screen.Yao && !it.over } && guard++ < 2000) {
                val v = e.viewFor("h").screen as Screen.Yao
                val att = v.attack
                if (att != null) {
                    for (t in att.waiting) {
                        val hand = (e.viewFor(t).screen as Screen.Yao).hand
                        val armor = hand.filter { YaoCards.def(it.id).kind == YaoKind.ARMOR }.take(rng.nextInt(2))
                        e.apply(t, Intent.YaoAct("defend", armor.map { it.uid }))
                    }
                } else {
                    val me = v.turn
                    val hand = (e.viewFor(me).screen as Screen.Yao).hand
                    val others = v.seats.filter { it.alive && it.id != me }.map { it.id }
                    val c = hand.shuffled(rng).firstOrNull()
                    val ok = c != null && e.apply(me, Intent.YaoAct("play", listOf(c.uid), others.random(rng), listOf("hp>mp", "coin>hp", "mp>hp").random(rng)))
                    if (!ok) e.apply(me, Intent.YaoAct("pray", hand.filter { YaoCards.def(it.id).kind != YaoKind.ARMOR }.take(rng.nextInt(3)).map { it.uid }))
                }
                if (guard % 7 == 0) { clock[0] += 70_000; e.tick() }
            }
            val fv = e.viewFor("h").screen as Screen.Yao
            fv.log.forEach { line -> LogFmt.format(line, { k -> check(k); k }, "、") }
            assertTrue("missing: $missing", missing.isEmpty())
            assertTrue("seed $seed " + fv.seats + " turn=" + fv.turn + " att=" + fv.attack + " log=" + fv.log.map { LogFmt.format(it, { k -> k }, "、") }, fv.over)
        }
    }
}
