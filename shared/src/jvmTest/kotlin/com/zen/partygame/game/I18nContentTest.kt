package com.zen.partygame.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class I18nContentTest {
    private fun assets() = listOf(File("app/src/main/assets"), File("../app/src/main/assets")).first { it.exists() }

    @Test fun everyLanguageHasPlayableTopics() {
        for (lang in listOf("en", "ko", "zh")) {
            val bank = TopicBank.parse(File(assets(), "topics_$lang.json").readText())
            val rng = Random(1)
            repeat(30) {
                val p = bank.drawPair(emptyList(), rng); assertTrue(p.a != p.b)
                assertEquals(8, bank.drawNg(8, NgLevel.MIX, rng).toSet().size)
                assertEquals(SPY_OPTIONS, bank.spyOptions(bank.drawSpyPlace(rng), SPY_OPTIONS, rng).toSet().size)
                assertTrue(bank.drawSync(rng).isNotBlank()); assertTrue(bank.drawGuess(rng).isNotBlank()); assertTrue(bank.drawQuestion(rng).isNotBlank())
                assertTrue(bank.drawPrompt(rng).length > 3)
            }
            // 全部のゲームが、その言語のお題で最後まで回る
            val e = Engine("1", false, bank, rng) { 0L }
            e.join("h", "A", true); e.join("b", "B"); e.join("c", "C")
            for (g in GameType.entries) {
                e.apply("h", Intent.UpdateSettings(Settings(game = g))); e.apply("h", Intent.Start)
                assertTrue(e.viewFor("b").screen !is Screen.Lobby)
                e.apply("h", Intent.BackToLobby)
            }
        }
    }

    @Test fun koreanParticleAndOtherJoins() {
        assertEquals("고양이가 서핑하고 있다", TopicBank.joinDrawPrompt("ko", "고양이", "서핑하고 있다"))
        assertEquals("곰이 서핑하고 있다", TopicBank.joinDrawPrompt("ko", "곰", "서핑하고 있다"))
        assertEquals("A cat surfing", TopicBank.joinDrawPrompt("en", "A cat", "surfing"))
        assertEquals("猫在冲浪", TopicBank.joinDrawPrompt("zh", "猫", "在冲浪"))
        assertEquals("ねこが寝ている", TopicBank.joinDrawPrompt("ja", "ねこ", "寝ている"))
    }

    @Test fun logEntriesFormatInAnyLanguage() {
        val dict = mapOf("{0}が「{1}」を出した{2}" to "{0} played \"{1}\"{2}", "金貨" to "Gold Coin", "指：" to "Fingers: ", "{0} {1}本" to "{0} {1}")
        val tr: (String) -> String = { dict[it] ?: it }
        val e = LogFmt.entry("{0}が「{1}」を出した{2}", listOf("Aki", LogFmt.entry("金貨", emptyList()), " → Bo"))
        assertEquals("Aki played \"Gold Coin\" → Bo", LogFmt.format(e, tr, ", "))
        assertEquals("Akiが「金貨」を出した → Bo", LogFmt.format(e, { it }, "、"))
        val list = LogFmt.entry(LogFmt.LIST, listOf(LogFmt.entry("指：", emptyList()), LogFmt.entry("{0} {1}本", listOf("A", 3)), LogFmt.entry("{0} {1}本", listOf("B", 5))))
        assertEquals("Fingers: A 3, B 5", LogFmt.format(list, tr, ", "))
        assertEquals("plain", LogFmt.format("plain", tr, ", "))
        val rule = LogFmt.entry(LogFmt.CARD_RULE + "rps_g", emptyList())
        assertTrue(LogFmt.format(rule, { it }, "、").isNotBlank())
    }

    @Test fun everyArenaLogLineIsTranslatable() {
        // アリーナを何回も遊んで、出てきた記録の型がすべて訳の辞書にある
        val dicts = listOf("en", "ko", "zh").associateWith { lang ->
            Regex("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:").findAll(File(assets(), "i18n/$lang.json").readText()).map {
                it.groupValues[1].replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
            }.toSet()
        }
        val missing = mutableSetOf<String>()
        repeat(60) { seed ->
            val rng = Random(seed); var now = 0L
            val g = ArenaGame(listOf("a", "b", "c", "d"), { it }, rng, { now }, 60, true)
            var guard = 0
            while (g.step != ArenaGame.Step.OVER && guard++ < 300) {
                now += 61_000; g.tick()
            }
            for (line in g.log) LogFmt.format(line, { k -> if (dicts.values.any { k !in it } && !k.startsWith(LogFmt.CARD_RULE) && k != LogFmt.LIST && Regex("[ぁ-んァ-ヶ一-龠]").containsMatchIn(k)) missing += k; k }, "、")
        }
        assertTrue("missing: $missing", missing.isEmpty())
    }
}
