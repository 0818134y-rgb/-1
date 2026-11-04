package com.zen.partygame.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random

class BgmTest {
    private var clock = 1_000_000L
    private lateinit var bank: TopicBank
    private val root = listOf(File("."), File("..")).first { File(it, "app/src/main/assets/topics.json").exists() }

    @Before fun setUp() {
        bank = TopicBank.parse(File(root, "app/src/main/assets/topics.json").readText())
    }

    private fun engine(game: GameType, n: Int = 4): Engine {
        val e = Engine("1", false, bank, Random(5)) { clock }
        e.join("h", "H", true); for (i in 1 until n) e.join("p$i", "P$i")
        e.apply("h", Intent.UpdateSettings(Settings(game = game)))
        return e
    }

    @Test fun outsideRoomAndLobby() {
        assertEquals(Bgm.TITLE, Bgm.track(null))
        assertEquals(Bgm.LOBBY, Bgm.track(engine(GameType.WORD_WOLF).viewFor("h")))
    }

    @Test fun eachGameStartsWithFittingMusic() {
        val want = mapOf(
            GameType.WORD_WOLF to Bgm.TALK, GameType.SPY to Bgm.TALK, GameType.NG_WORD to Bgm.TALK,
            GameType.SYNC to Bgm.TALK, GameType.GUESS to Bgm.TALK, GameType.QUESTION to Bgm.TALK,
            GameType.ARENA to Bgm.ARENA, GameType.DRAW to Bgm.DRAW,
        )
        want.forEach { (g, track) ->
            val e = engine(g); e.apply("h", Intent.Start)
            assertEquals("$g", track, Bgm.track(e.viewFor("h")))
        }
    }

    @Test fun everyScreenHasATrack() {
        // when が網羅的なので、新しい画面を足すとここでなくコンパイルで気づける。曲の名前が全部そろっているかだけ確かめる
        assertEquals(8, Bgm.ALL.toSet().size)
    }

    @Test fun musicFilesExist() {
        Bgm.ALL.forEach { id ->
            val ogg = File(root, "app/src/main/res/raw/bgm_$id.ogg")
            val mp3 = File(root, "pc/src/bgm/$id.mp3")
            assertTrue("$ogg", ogg.exists() && ogg.length() > 50_000)
            assertTrue("$mp3", mp3.exists() && mp3.length() > 50_000)
        }
    }
}
