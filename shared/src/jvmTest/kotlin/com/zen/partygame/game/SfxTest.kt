package com.zen.partygame.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random

class SfxTest {
    private var clock = 1_000_000L
    private lateinit var bank: TopicBank

    @Before fun setUp() {
        bank = TopicBank.parse(listOf(File("app/src/main/assets/topics.json"), File("../app/src/main/assets/topics.json")).first { it.exists() }.readText())
    }

    private fun engine(n: Int = 3): Engine {
        val e = Engine("1", false, bank, Random(3)) { clock }
        e.join("h", "H", true); for (i in 1 until n) e.join("p$i", "P$i")
        return e
    }

    @Test fun startJoinAndVotes() {
        val e = engine(2)
        var prev = e.viewFor("h")
        e.join("p2", "P2")
        assertEquals(listOf(Sfx.JOIN), Sfx.cues(prev, e.viewFor("h")))
        prev = e.viewFor("h")
        e.apply("h", Intent.Start)
        assertTrue(Sfx.START in Sfx.cues(prev, e.viewFor("h")))
        // 最初の画面には音なし
        assertTrue(Sfx.cues(null, e.viewFor("h")).isEmpty())
    }

    @Test fun drawGameSounds() {
        val e = engine()
        e.apply("h", Intent.UpdateSettings(Settings(game = GameType.DRAW))); e.apply("h", Intent.Start)
        var prev = e.viewFor("p1")
        e.apply("p1", Intent.DrawSubmit(0, text = "a"))
        assertEquals(listOf(Sfx.SENT), Sfx.cues(prev, e.viewFor("p1")))
        prev = e.viewFor("p1")
        e.apply("h", Intent.DrawSubmit(0, text = "b"))
        assertEquals(listOf(Sfx.VOTE), Sfx.cues(prev, e.viewFor("p1")))
        prev = e.viewFor("p1")
        e.apply("p2", Intent.DrawSubmit(0, text = "c"))
        assertEquals(listOf(Sfx.PAGE), Sfx.cues(prev, e.viewFor("p1")))
    }

    @Test fun arenaNeverCrashesAndPlaysCardSounds() {
        val seen = mutableSetOf<String>()
        repeat(30) { seed ->
            val e = Engine("1", false, bank, Random(seed)) { clock }
            e.join("h", "H", true); e.join("a", "A"); e.join("b", "B"); e.join("c", "C")
            e.apply("h", Intent.UpdateSettings(Settings(game = GameType.ARENA))); e.apply("h", Intent.Start)
            var prev = e.players.associate { it.id to e.viewFor(it.id) }
            var guard = 0
            while (e.viewFor("h").screen is Screen.Arena && guard++ < 400) {
                clock += 61_000; e.tick()
                for (p in e.players) { val v = e.viewFor(p.id); seen += Sfx.cues(prev[p.id], v); }
                prev = e.players.associate { it.id to e.viewFor(it.id) }
            }
        }
        assertTrue("seen=$seen", seen.containsAll(listOf(Sfx.CARD_PLAY, Sfx.YOUR_TURN)))
        assertTrue("seen=$seen", Sfx.WIN in seen || Sfx.LOSE in seen)
        assertTrue(seen.all { it in Sfx.ALL })
    }

    @Test fun soundFilesExistForEveryCue() {
        val root = listOf(File("app"), File("../app")).first { it.exists() }.parentFile ?: File("..")
        for (n in Sfx.ALL) {
            assertTrue("ogg $n", File(root, "app/src/main/res/raw/sfx_$n.ogg").exists())
            assertTrue("mp3 $n", File(root, "pc/src/sfx/$n.mp3").exists())
        }
    }
}
