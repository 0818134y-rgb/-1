package com.zen.partygame.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ArenaGameTest {
    private var clock = 0L
    private val ids = listOf("a", "b", "c")
    private var uid = 1000

    private fun card(id: String, suit: Suit = Suit.SUN) = ArenaCard(uid++, id, suit)

    /**
     * 山札を指定して始める。配る順：a,b,c,a,b,c → a の手番で a が1枚引く（7枚目）。
     * 残りは「手札こうかん」で埋める（ゲームに影響しにくい）。
     */
    private fun game(deck: List<ArenaCard>, chaos: Chaos? = null, players: List<String> = ids, turnSec: Int = 60): ArenaGame {
        val filler = (0 until 40).map { card("swap") }
        return ArenaGame(players, { it.uppercase() }, Random(3), { clock }, turnSec, useChaos = chaos != null,
            presetDeck = deck + filler, presetChaos = chaos, presetFirst = players.first())
    }

    /** a に hand、ほかは swap を配る（3人用）。a は7枚目も引く */
    private fun withHand(vararg aCards: String, chaos: Chaos? = null, others: Map<String, List<String>> = emptyMap()): ArenaGame {
        val b = others["b"] ?: listOf("swap", "swap"); val c = others["c"] ?: listOf("swap", "swap")
        val a = aCards.toList() + List(3 - aCards.size.coerceAtMost(3)) { "swap" }
        val deck = listOf(card(a[0]), card(b[0]), card(c[0]), card(a[1]), card(b[1]), card(c[1]), card(a[2]))
        return game(deck, chaos)
    }

    private fun ArenaGame.cardOf(p: String, id: String) = hand(p).first { it.id == id }
    private fun ArenaGame.mini() = (view("a") as Screen.Arena).mini

    @Test fun dealsTwoAndTurnPlayerDrawsOne() {
        val g = game(emptyList())
        assertEquals("a", g.turn)
        ids.forEach { assertEquals(if (it == g.turn) 3 else 2, g.hand(it).size) }
        assertEquals(ArenaGame.Step.TURN, g.step)
    }

    @Test fun richNightDealsThree() {
        val g = game(emptyList(), Chaos.RICH)
        assertEquals(3, g.hand("b").size)
    }

    @Test fun onlyTurnPlayerCanPlayAndTargetIsRequired() {
        val g = withHand("pitfall")
        assertFalse(g.play("b", g.hand("b")[0].uid, "a"))
        assertFalse(g.play("a", g.cardOf("a", "pitfall").uid, null))
        assertFalse(g.play("a", g.cardOf("a", "pitfall").uid, "a"))
        assertTrue(g.play("a", g.cardOf("a", "pitfall").uid, "b"))
    }

    @Test fun trapLosesAtEndOfTargetsTurn() {
        val g = withHand("pitfall")
        g.play("a", g.cardOf("a", "pitfall").uid, "b")
        assertEquals("b", g.turn)
        assertTrue(g.seats["b"]!!.alive)
        assertEquals("pitfall", g.seatsView().first { it.id == "b" }.front.single().id)
        g.play("b", g.hand("b")[0].uid, "c")       // b の手番が終わる
        assertFalse(g.seats["b"]!!.alive)
        assertEquals("c", g.turn)
    }

    @Test fun charmSavesFromTrapOnce() {
        val g = withHand("pitfall", others = mapOf("b" to listOf("charm", "coin")))
        g.play("a", g.cardOf("a", "pitfall").uid, "b")
        g.play("b", g.cardOf("b", "coin").uid, null)
        assertTrue(g.seats["b"]!!.alive)
        assertTrue(g.hand("b").none { it.id == "charm" })
        assertTrue(g.seatsView().first { it.id == "b" }.front.none { it.id == "pitfall" })
    }

    @Test fun dinosaurBlocksTraps() {
        val g = withHand("dino", "pitfall", others = mapOf("b" to listOf("pitfall", "swap")))
        g.play("a", g.cardOf("a", "dino").uid, null)
        assertEquals(listOf("c"), g.validTargets("b", ArenaCards.def("pitfall")))
        assertFalse(g.play("b", g.cardOf("b", "pitfall").uid, "a"))
    }

    @Test fun theEndRingsAfterOwnersNextTurn() {
        val g = withHand("theend", "swap", "swap")
        g.play("a", g.cardOf("a", "theend").uid, null)
        g.play("b", g.hand("b")[0].uid, "c")
        g.play("c", g.hand("c")[0].uid, "b")
        assertEquals(ArenaGame.Step.TURN, g.step)
        g.play("a", g.hand("a")[0].uid, "b")
        assertEquals(ArenaGame.Step.OVER, g.step)
        assertTrue(g.winners.isEmpty())
    }

    @Test fun pointsWinAtFifteenAndVaultRaisesGoal() {
        val g = game(listOf(card("coin"), card("swap"), card("swap"), card("coin"), card("swap"), card("swap"), card("coin")) +
            List(6) { card("swap") } + listOf(card("coin")))
        g.play("a", g.cardOf("a", "coin").uid, null); g.play("b", g.hand("b")[0].uid, "c"); g.play("c", g.hand("c")[0].uid, "b")
        g.play("a", g.cardOf("a", "coin").uid, null); g.play("b", g.hand("b")[0].uid, "c"); g.play("c", g.hand("c")[0].uid, "b")
        assertEquals(10, g.points("a"))
        g.play("a", g.cardOf("a", "coin").uid, null)
        assertEquals(ArenaGame.Step.OVER, g.step)
        assertEquals(listOf("a"), g.winners)

        val v = withHand("vault", "coin", "coin")
        v.play("a", v.cardOf("a", "vault").uid, null)
        assertEquals(100, v.pointGoal)
        assertEquals(90, v.points("a"))
        assertEquals(ArenaGame.Step.TURN, v.step)
    }

    @Test fun fourBombsEndForEveryone() {
        val g = game(listOf(card("bomb"), card("bomb"), card("bomb"), card("bomb"), card("swap"), card("swap"), card("swap")))
        g.play("a", g.cardOf("a", "bomb").uid, null)
        g.play("b", g.cardOf("b", "bomb").uid, null)
        g.play("c", g.cardOf("c", "bomb").uid, null)
        assertEquals(ArenaGame.Step.TURN, g.step)
        g.play("a", g.cardOf("a", "bomb").uid, null)
        assertEquals(ArenaGame.Step.OVER, g.step)
        assertTrue(g.winners.isEmpty())
    }

    @Test fun rpsLoserHandIsHiddenUntilReveal() {
        val g = withHand("rps_g")
        g.play("a", g.cardOf("a", "rps_g").uid, null)
        val vb = g.view("b") as Screen.Arena
        assertTrue(vb.topHidden)
        assertFalse((g.view("a") as Screen.Arena).topHidden)
        assertEquals("rps", vb.mini!!.kind)
        g.input("a", "p"); g.input("b", "g"); g.input("c", "c")
        assertFalse(g.seats["b"]!!.alive)
        assertTrue(g.seats["a"]!!.alive && g.seats["c"]!!.alive)
    }

    @Test fun rpsDiffAndTimeoutPicksRandom() {
        val g = withHand("rps_diff")
        g.play("a", g.cardOf("a", "rps_diff").uid, null)
        g.input("a", "g"); g.input("b", "g")
        clock += 16_000; g.tick()
        assertNull(g.mini())
        assertTrue(g.seats["b"]!!.alive)
    }

    @Test fun fingerRules() {
        val odd = withHand("finger_odd")
        odd.play("a", odd.cardOf("a", "finger_odd").uid, null)
        odd.input("a", "2"); odd.input("b", "3"); odd.input("c", "4")
        assertFalse(odd.seats["b"]!!.alive)

        val dup = withHand("finger_dup")
        dup.play("a", dup.cardOf("a", "finger_dup").uid, null)
        dup.input("a", "2"); dup.input("b", "2"); dup.input("c", "4")
        assertFalse(dup.seats["a"]!!.alive); assertFalse(dup.seats["b"]!!.alive)
        assertEquals(ArenaGame.Step.OVER, dup.step)

        val prime = withHand("finger_prime")
        prime.play("a", prime.cardOf("a", "finger_prime").uid, null)
        prime.input("a", "1"); prime.input("b", "2"); prime.input("c", "4")
        assertEquals(listOf("a"), prime.winners)
    }

    @Test fun catCancelsCardAndDraws() {
        val g = withHand("pitfall", others = mapOf("c" to listOf("cat", "swap")))
        g.play("a", g.cardOf("a", "pitfall").uid, "b")
        assertEquals("cancel", g.mini()!!.kind)
        assertEquals(listOf("c"), g.mini()!!.involved)
        assertFalse(g.input("b", "use"))
        g.input("c", "use")
        assertNull(g.mini())
        assertTrue(g.seatsView().first { it.id == "b" }.front.isEmpty())
        assertEquals(2, g.hand("c").size)          // ネコを捨てて1枚引いた
        assertEquals("b", g.turn)
    }

    @Test fun catPassLetsCardResolve() {
        val g = withHand("pitfall", others = mapOf("c" to listOf("cat", "swap")))
        g.play("a", g.cardOf("a", "pitfall").uid, "b")
        clock += 7_000; g.tick()
        assertEquals("pitfall", g.seatsView().first { it.id == "b" }.front.single().id)
    }

    @Test fun spiteDragsWinnerDown() {
        val g = withHand("birthday", others = mapOf("b" to listOf("doom", "swap")))
        g.play("a", g.cardOf("a", "birthday").uid, null)
        assertEquals("yesno", g.mini()!!.kind)
        g.input("a", "yes")
        assertFalse(g.seats["a"]!!.alive); assertFalse(g.seats["b"]!!.alive)
        assertEquals(listOf("c"), g.winners)
    }

    @Test fun presentNoLoses() {
        val g = withHand("present")
        g.play("a", g.cardOf("a", "present").uid, null)
        assertEquals(listOf("b", "c"), g.mini()!!.involved)
        g.input("b", "want"); g.input("c", "no")
        assertFalse(g.seats["c"]!!.alive)
        assertTrue(g.seats["b"]!!.alive)
    }

    @Test fun noseMustAndMustNot() {
        val g = withHand("nose")
        g.play("a", g.cardOf("a", "nose").uid, null)
        assertNull(g.mini()!!.goInMs)
        assertFalse(g.input("b", "press:100"))        // 合図の前は押せない
        g.input("a", "go")
        assertEquals(0L, g.mini()!!.goInMs)
        g.input("b", "press:800")
        clock += 6_000; g.tick()
        assertTrue(g.seats["b"]!!.alive)
        assertFalse(g.seats["c"]!!.alive)

        val h = withHand("nonose")
        h.play("a", h.cardOf("a", "nonose").uid, null)
        h.input("a", "go"); h.input("b", "press:300")
        clock += 6_000; h.tick()
        assertFalse(h.seats["b"]!!.alive)
        assertTrue(h.seats["c"]!!.alive)
    }

    @Test fun laughDrawsWhenOthersLose() {
        val g = withHand("laugh", "vote")
        g.play("a", g.cardOf("a", "laugh").uid, null)
        g.play("b", g.hand("b")[0].uid, "c"); g.play("c", g.hand("c")[0].uid, "b")
        val before = g.hand("a").size
        g.play("a", g.cardOf("a", "vote").uid, null)
        g.input("a", "b"); g.input("b", "c"); g.input("c", "b")
        assertFalse(g.seats["b"]!!.alive)
        assertEquals(before, g.hand("a").size)   // 投票で1枚出して、高笑いで1枚引いた
    }

    @Test fun socialRuleReport() {
        val g = withHand("silence")
        assertFalse(g.report("b", "c"))
        g.play("a", g.cardOf("a", "silence").uid, null)
        assertEquals("silence", (g.view("b") as Screen.Arena).rules.single().id)
        assertTrue(g.report("b", "c"))
        assertFalse(g.seats["c"]!!.alive)
    }

    @Test fun noRefillStopsDrawingAndEmptyHandLoses() {
        val g = withHand("norefill", "swap", "swap", others = mapOf("b" to listOf("swap", "swap")))
        g.play("a", g.cardOf("a", "norefill").uid, null)
        assertTrue((g.view("a") as Screen.Arena).noDraw)
        assertEquals(2, g.hand("b").size)          // b は引けなかった
    }

    @Test fun suddenDeathTakesNeighbor() {
        val four = game(listOf(card("vote"), card("swap"), card("swap"), card("swap"), card("swap"), card("swap"), card("swap"), card("swap"), card("swap")),
            Chaos.SUDDEN, players = listOf("a", "b", "c", "d"))
        four.play("a", four.cardOf("a", "vote").uid, null)
        listOf("a", "c", "d").forEach { four.input(it, "b") }; four.input("b", "a")
        assertFalse(four.seats["b"]!!.alive); assertFalse(four.seats["c"]!!.alive)
        assertTrue(four.seats["d"]!!.alive)
    }

    @Test fun timeoutAutoPlays() {
        val g = withHand("swap", "swap", "swap")
        clock += 61_000; g.tick()
        assertEquals("b", g.turn)
    }

    @Test fun forfeitOnTurnAdvances() {
        val g = game(emptyList())
        g.forfeit("a")
        assertEquals("b", g.turn)
    }

    @Test fun deckIsBuiltFromAllCards() {
        val d = ArenaCards.buildDeck()
        assertEquals(ArenaCards.all.sumOf { it.copies }, d.size)
        assertEquals(50, d.size)
        assertEquals(d.size, d.map { it.uid }.toSet().size)
        assertEquals(14, ArenaCards.all.filter { it.star }.sumOf { it.copies })
    }

    @Test fun randomGamesAlwaysFinish() {
        // ランダムに遊び続けても、止まらずに決着がつくこと
        repeat(300) { seed ->
            clock = 0
            val rng = Random(seed)
            val players = (0 until 2 + seed % 7).map { "p$it" }
            val g = ArenaGame(players, { it }, Random(seed), { clock }, 60, useChaos = true)
            var steps = 0
            while (g.step != ArenaGame.Step.OVER && steps < 5000) {
                steps++
                when (g.step) {
                    ArenaGame.Step.TURN -> {
                        val h = g.hand(g.turn)
                        if (h.isEmpty() || rng.nextInt(10) == 0) { clock += 61_000; g.tick() }
                        else {
                            val c = h[rng.nextInt(h.size)]
                            g.play(g.turn, c.uid, g.validTargets(g.turn, ArenaCards.def(c.id)).randomOrNull(rng))
                        }
                        if (rng.nextInt(30) == 0) g.report(players.random(rng), players.random(rng))
                    }
                    ArenaGame.Step.MINI -> {
                        val m = (g.view(players[0]) as Screen.Arena).mini!!
                        if (m.kind == "nose" && m.goInMs == null && rng.nextInt(4) > 0) g.input(m.owner, "go")
                        m.involved.forEach { p ->
                            val v = when (m.kind) {
                                "cancel" -> listOf("use", "pass").random(rng); "yesno" -> listOf("yes", "no").random(rng)
                                "rps" -> listOf("g", "c", "p").random(rng); "fingers" -> (1 + rng.nextInt(5)).toString()
                                "vote" -> players.random(rng); "present" -> listOf("want", "no").random(rng)
                                else -> "press:${rng.nextInt(4000)}"
                            }
                            if (rng.nextInt(5) > 0) g.input(p, v)
                        }
                        if (g.step == ArenaGame.Step.MINI) { clock += 30_000; g.tick() }
                    }
                    ArenaGame.Step.OVER -> Unit
                }
            }
            assertEquals("seed $seed did not finish", ArenaGame.Step.OVER, g.step)
        }
    }
}
