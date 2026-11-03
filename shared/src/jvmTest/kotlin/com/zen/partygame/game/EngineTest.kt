package com.zen.partygame.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random

class EngineTest {
    private var clock = 1_000_000L
    private lateinit var bank: TopicBank

    @Before fun setUp() {
        val f = listOf(File("src/main/assets/topics.json"), File("app/src/main/assets/topics.json"), File("../app/src/main/assets/topics.json")).first { it.exists() }
        bank = TopicBank.parse(f.readText())
    }

    private fun engine(n: Int, local: Boolean = false, seed: Int = 1): Engine {
        val e = Engine("1234", local, bank, Random(seed)) { clock }
        e.join("h", "ホスト", isHost = true)
        for (i in 1 until n) e.join("p$i", "P$i")
        return e
    }

    private fun Engine.wolvesNow(): List<String> {
        // 多数派と違うお題を持つ人がウルフ
        val words = players.associate { it.id to (viewFor(it.id).screen as Screen.WwReveal).word }
        val counts = words.values.groupingBy { it }.eachCount()
        val minority = counts.minByOrNull { it.value }!!.key
        return words.filterValues { it == minority }.keys.toList()
    }

    private fun Engine.confirmAll() = players.toList().forEach { apply(it.id, Intent.ConfirmWord()) }

    @Test fun duplicateNamesGetNumbered() {
        val e = engine(1)
        e.join("a", "ゆうき"); e.join("b", "ゆうき")
        assertEquals(listOf("ホスト", "ゆうき", "ゆうき2"), e.players.map { it.name })
    }

    @Test fun cannotJoinDuringGameOrWhenFull() {
        val e = engine(8)
        assertEquals(Engine.JoinResult.Full, e.join("x", "x"))
        val e2 = engine(3); e2.apply("h", Intent.Start)
        assertEquals(Engine.JoinResult.InGame, e2.join("x", "x"))
    }

    @Test fun onlyHostCanStartAndChangeSettings() {
        val e = engine(3)
        assertFalse(e.apply("p1", Intent.Start))
        assertFalse(e.apply("p1", Intent.UpdateSettings(Settings(talkSec = 60))))
        assertTrue(e.apply("h", Intent.UpdateSettings(Settings(talkSec = 60))))
        assertEquals(60, e.settings.talkSec)
    }

    @Test fun needsThreePlayers() {
        val e = engine(2); e.apply("h", Intent.Start)
        assertTrue(e.viewFor("h").screen is Screen.Lobby)
    }

    @Test fun wordWolfDealsOneMinorityWordAndHidesOthers() {
        val e = engine(5); e.apply("h", Intent.Start)
        val wolves = e.wolvesNow()
        assertEquals(1, wolves.size)
        val words = e.players.map { (e.viewFor(it.id).screen as Screen.WwReveal).word }.toSet()
        assertEquals(2, words.size)
    }

    @Test fun sevenPlayersGetTwoWolvesByDefault() {
        val e = engine(7); e.apply("h", Intent.Start)
        assertEquals(2, e.wolvesNow().size)
    }

    @Test fun talkStartsWhenAllConfirmedAndEndsByTimer() {
        val e = engine(4); e.apply("h", Intent.Start)
        e.apply("p1", Intent.ConfirmWord()); e.apply("p2", Intent.ConfirmWord())
        assertTrue(e.viewFor("h").screen is Screen.WwReveal)
        e.apply("h", Intent.ConfirmWord()); e.apply("p3", Intent.ConfirmWord())
        val v = e.viewFor("p1")
        assertTrue(v.screen is Screen.WwTalk)
        assertEquals(180_000L, v.remainingMs)
        clock += 179_999; assertFalse(e.tick())
        clock += 1; assertTrue(e.tick())
        assertTrue(e.viewFor("p1").screen is Screen.WwVote)
    }

    @Test fun votingTheWolfLeadsToGuessAndJudge() {
        val e = engine(4); e.apply("h", Intent.Start)
        val wolf = e.wolvesNow().single()
        e.confirmAll(); e.apply("h", Intent.EndTalk)
        for (p in e.players.toList()) {
            val target = if (p.id == wolf) e.players.first { it.id != wolf }.id else wolf
            e.apply(p.id, Intent.Vote(target))
        }
        val g = e.viewFor(wolf).screen as Screen.WwGuess
        assertTrue(g.iAmWolf)
        assertFalse(e.apply("p1", Intent.Judge(true)))  // ホスト以外は判定できない
        e.apply("h", Intent.Judge(false))
        val r = e.viewFor("p1").screen as Screen.WwResult
        assertEquals(Team.CITIZEN, r.winner)
        assertEquals(ResultReason.WOLF_CAUGHT, r.reason)
        assertEquals(wolf, r.accused)
        assertEquals(3, r.tally.first { it.id == wolf }.votes)
    }

    @Test fun wolfGuessRightWins() {
        val e = engine(3); e.apply("h", Intent.Start)
        val wolf = e.wolvesNow().single()
        e.confirmAll(); e.apply("h", Intent.EndTalk)
        e.players.filter { it.id != wolf }.forEach { e.apply(it.id, Intent.Vote(wolf)) }
        e.apply("h", Intent.CloseVote)
        e.apply("h", Intent.Judge(true))
        val r = e.viewFor("h").screen as Screen.WwResult
        assertEquals(Team.WOLF, r.winner); assertEquals(ResultReason.WOLF_GUESSED, r.reason)
    }

    @Test fun accusingCitizenMeansWolfWins() {
        val e = engine(4); e.apply("h", Intent.Start)
        val wolf = e.wolvesNow().single()
        val citizen = e.players.first { it.id != wolf }.id
        e.confirmAll(); e.apply("h", Intent.EndTalk)
        e.players.filter { it.id != citizen }.forEach { e.apply(it.id, Intent.Vote(citizen)) }
        e.apply("h", Intent.CloseVote)
        val r = e.viewFor("h").screen as Screen.WwResult
        assertEquals(Team.WOLF, r.winner); assertEquals(ResultReason.CITIZEN_ACCUSED, r.reason)
    }

    @Test fun tieGoesToRunoffThenWolfWins() {
        val e = engine(4); e.apply("h", Intent.Start)
        e.confirmAll(); e.apply("h", Intent.EndTalk)
        // h↔p1, p2↔p3 で 2票ずつ…ではなく各1票の4人同票
        e.apply("h", Intent.Vote("p1")); e.apply("p1", Intent.Vote("p2"))
        e.apply("p2", Intent.Vote("p3")); e.apply("p3", Intent.Vote("h"))
        val v = e.viewFor("h").screen as Screen.WwVote
        assertTrue(v.runoff); assertEquals(4, v.candidates.size); assertNull(v.myVote)
        e.apply("h", Intent.Vote("p1")); e.apply("p1", Intent.Vote("h"))
        e.apply("p2", Intent.Vote("h")); e.apply("p3", Intent.Vote("p1"))
        val r = e.viewFor("h").screen as Screen.WwResult
        assertEquals(ResultReason.TIE, r.reason); assertEquals(Team.WOLF, r.winner)
    }

    @Test fun cannotVoteForSelf() {
        val e = engine(3); e.apply("h", Intent.Start); e.confirmAll(); e.apply("h", Intent.EndTalk)
        assertFalse(e.apply("p1", Intent.Vote("p1")))
    }

    @Test fun localModePassAndPlay() {
        val e = engine(5, local = true); e.apply("h", Intent.Start)
        val s = e.viewFor("h").screen as Screen.WwRevealLocal
        assertEquals(5, s.entries.size)
        s.entries.forEach { e.apply("h", Intent.ConfirmWord(forPlayer = it.id)) }
        assertNull((e.viewFor("h").screen as Screen.WwTalk).word)
        e.apply("h", Intent.EndTalk)
        assertTrue(e.viewFor("h").screen is Screen.WwAccuse)
        val wolf = s.entries.groupBy { it.word }.minByOrNull { it.value.size }!!.value.single().id
        e.apply("h", Intent.Accuse(wolf))
        assertTrue(e.viewFor("h").screen is Screen.WwGuess)
    }

    @Test fun localModeIgnoresNgWord() {
        val e = engine(4, local = true)
        e.apply("h", Intent.UpdateSettings(Settings(game = GameType.NG_WORD)))
        assertEquals(GameType.WORD_WOLF, e.settings.game)
    }

    @Test fun ngWordHidesOwnWordAndEndsWithLastSurvivor() {
        val e = engine(3)
        e.apply("h", Intent.UpdateSettings(Settings(game = GameType.NG_WORD)))
        e.apply("h", Intent.Start)
        val mine = (e.viewFor("p1").screen as Screen.NgPlay).entries
        assertNull(mine.first { it.id == "p1" }.word)
        assertTrue(mine.filter { it.id != "p1" }.all { it.word != null })
        e.apply("h", Intent.ReportOut("p1"))
        val afterOut = e.viewFor("p1").screen as Screen.NgPlay
        assertTrue(afterOut.myOut)
        assertTrue(afterOut.entries.first { it.id == "p1" }.word != null)
        e.apply("h", Intent.UndoOut("p1"))
        assertFalse((e.viewFor("p1").screen as Screen.NgPlay).myOut)
        e.apply("p1", Intent.ReportOut("p2")); e.apply("p2", Intent.ReportOut("h"))
        val r = e.viewFor("h").screen as Screen.NgResult
        assertEquals(listOf("p1"), r.winners)
        assertEquals(2, r.entries.first { it.id == "h" }.outOrder)
    }

    @Test fun ngWordsAreUnique() {
        val e = engine(8)
        e.apply("h", Intent.UpdateSettings(Settings(game = GameType.NG_WORD, ngLevel = NgLevel.MIX)))
        e.apply("h", Intent.Start)
        val words = (e.viewFor("x").screen as Screen.NgPlay).entries.map { it.word }
        assertEquals(8, words.toSet().size)
    }

    @Test fun ngTimeUpEndsGame() {
        val e = engine(3)
        e.apply("h", Intent.UpdateSettings(Settings(game = GameType.NG_WORD, ngSec = 60)))
        e.apply("h", Intent.Start)
        clock += 60_000; assertTrue(e.tick())
        assertEquals(3, (e.viewFor("h").screen as Screen.NgResult).winners.size)
    }

    private fun Engine.playQuickWwRound() {
        apply("h", Intent.Start); confirmAll(); apply("h", Intent.EndTalk)
        apply("h", Intent.CloseVote)
        assertTrue(viewFor("h").screen is Screen.WwResult)
    }

    @Test fun adBreakOnlyAfterIntervalAndWaitsForEveryone() {
        val e = engine(3)
        e.playQuickWwRound()
        e.apply("h", Intent.Next)  // まだ3分たっていない → 広告なしで次のラウンド
        assertTrue(e.viewFor("h").screen is Screen.WwReveal)
        e.confirmAll(); e.apply("h", Intent.EndTalk); e.apply("h", Intent.CloseVote)
        clock += AD_MIN_INTERVAL_MS
        e.apply("h", Intent.Next)
        val ad = e.viewFor("p1").screen as Screen.AdBreak
        e.apply("h", Intent.AdDone(ad.breakId)); e.apply("p1", Intent.AdDone(ad.breakId))
        assertTrue(e.viewFor("h").screen is Screen.AdBreak)
        assertFalse(e.apply("p2", Intent.AdDone(ad.breakId + 1)))
        e.apply("p2", Intent.AdDone(ad.breakId))
        assertTrue(e.viewFor("h").screen is Screen.WwReveal)
    }

    @Test fun adBreakTimesOut() {
        val e = engine(3)
        e.playQuickWwRound(); clock += AD_MIN_INTERVAL_MS
        e.apply("h", Intent.BackToLobby)
        assertTrue(e.viewFor("h").screen is Screen.AdBreak)
        clock += AD_WAIT_MS; assertTrue(e.tick())
        assertTrue(e.viewFor("h").screen is Screen.Lobby)
    }

    @Test fun leavingDuringAdBreakDoesNotBlock() {
        val e = engine(4)
        e.playQuickWwRound(); clock += AD_MIN_INTERVAL_MS
        e.apply("h", Intent.Next)
        val id = (e.viewFor("h").screen as Screen.AdBreak).breakId
        listOf("h", "p1", "p2").forEach { e.apply(it, Intent.AdDone(id)) }
        e.leave("p3")
        assertTrue(e.viewFor("h").screen is Screen.WwReveal)
    }

    @Test fun wolfLeavingSendsEveryoneToLobby() {
        val e = engine(4); e.apply("h", Intent.Start)
        val wolf = e.wolvesNow().single()
        if (wolf == "h") return
        e.leave(wolf)
        assertTrue(e.viewFor("h").screen is Screen.Lobby)
    }

    @Test fun lastUnconfirmedLeavingStartsTalk() {
        val e = engine(4); e.apply("h", Intent.Start)
        val wolf = e.wolvesNow().single()
        val leaver = e.players.first { it.id != wolf && it.id != "h" }.id
        e.players.filter { it.id != leaver }.forEach { e.apply(it.id, Intent.ConfirmWord()) }
        e.leave(leaver)
        assertTrue(e.viewFor("h").screen is Screen.WwTalk)
    }

    @Test fun topicsDoNotRepeatUntilExhausted() {
        val e = engine(3)
        e.apply("h", Intent.UpdateSettings(Settings(categories = listOf("動物"))))
        val seen = mutableSetOf<Set<String>>()
        repeat(17) {
            e.apply("h", Intent.Start)
            seen += e.players.map { (e.viewFor(it.id).screen as Screen.WwReveal).word }.toSet()
            e.apply("h", Intent.BackToLobby)
        }
        assertEquals(17, seen.size)
    }

    @Test fun viewSerializesRoundTrip() {
        val e = engine(4); e.apply("h", Intent.Start)
        val v = e.viewFor("p1")
        val s = WireJson.encodeToString(HostMsg.serializer(), HostMsg.ViewMsg(v))
        assertEquals(v, (WireJson.decodeFromString(HostMsg.serializer(), s) as HostMsg.ViewMsg).view)
        val i: Intent = Intent.Vote("p2")
        assertEquals(i, WireJson.decodeFromString(Intent.serializer(), WireJson.encodeToString(Intent.serializer(), i)))
        assertEquals(Intent.Start, WireJson.decodeFromString(Intent.serializer(), WireJson.encodeToString(Intent.serializer(), Intent.Start)))
    }

    // ================= 追加モード =================

    private fun Engine.startGame(g: GameType, settings: Settings = Settings()) {
        apply("h", Intent.UpdateSettings(settings.copy(game = g))); apply("h", Intent.Start)
    }

    @Test fun spyGetsNoPlaceOthersShareIt() {
        val e = engine(5); e.startGame(GameType.SPY)
        val views = e.players.associate { it.id to (e.viewFor(it.id).screen as Screen.WwReveal) }
        val spies = views.filterValues { it.spy }.keys
        assertEquals(1, spies.size)
        assertEquals("", views[spies.first()]!!.word)
        assertEquals(1, views.filterValues { !it.spy }.values.map { it.word }.toSet().size)
    }

    private fun Engine.catchSpy(): Pair<String, Screen.WwGuess> {
        val spy = players.first { (viewFor(it.id).screen as Screen.WwReveal).spy }.id
        confirmAll(); apply("h", Intent.EndTalk)
        players.filter { it.id != spy }.forEach { apply(it.id, Intent.Vote(spy)) }
        apply("h", Intent.CloseVote)
        return spy to (viewFor(spy).screen as Screen.WwGuess)
    }

    @Test fun spyGuessesPlaceFromOptions() {
        val e = engine(4); e.startGame(GameType.SPY)
        val place = (e.viewFor(e.players.first { !(e.viewFor(it.id).screen as Screen.WwReveal).spy }.id).screen as Screen.WwReveal).word
        val (spy, g) = e.catchSpy()
        assertEquals(SPY_OPTIONS, g.options.size); assertTrue(place in g.options)
        val other = e.players.first { it.id != spy }.id
        assertFalse(e.apply(other, Intent.SpyGuess(place)))       // スパイ以外は答えられない
        assertFalse(e.apply("h", Intent.Judge(true)))              // スパイモードでは口頭判定なし
        e.apply(spy, Intent.SpyGuess(place))
        val r = e.viewFor(other).screen as Screen.WwResult
        assertEquals(Team.WOLF, r.winner); assertEquals(place, r.spyGuess)
    }

    @Test fun spyWrongGuessLoses() {
        val e = engine(4); e.startGame(GameType.SPY)
        val place = e.players.map { (e.viewFor(it.id).screen as Screen.WwReveal) }.first { !it.spy }.word
        val (spy, g) = e.catchSpy()
        e.apply(spy, Intent.SpyGuess(g.options.first { it != place }))
        val r = e.viewFor("h").screen as Screen.WwResult
        assertEquals(Team.CITIZEN, r.winner); assertEquals(ResultReason.WOLF_CAUGHT, r.reason)
    }

    @Test fun syncGroupsSimilarAnswersAndScores() {
        val e = engine(4); e.startGame(GameType.SYNC)
        assertTrue(e.viewFor("p1").screen is Screen.SyncAnswer)
        e.apply("h", Intent.SyncAnswer("りんご")); e.apply("p1", Intent.SyncAnswer("リンゴ"))
        e.apply("p2", Intent.SyncAnswer(" りんご！ ")); e.apply("p3", Intent.SyncAnswer("いちご"))
        val r = e.viewFor("h").screen as Screen.SyncReveal
        assertEquals(2, r.groups.size); assertEquals(3, r.groups[0].ids.size)
        assertEquals(2, r.scores.first { it.id == "p1" }.votes); assertEquals(0, r.scores.first { it.id == "p3" }.votes)
        e.apply("h", Intent.Next)
        listOf("h", "p1").forEach { e.apply(it, Intent.SyncAnswer("海")) }
        e.apply("h", Intent.CloseVote)
        val r2 = e.viewFor("h").screen as Screen.SyncReveal
        assertEquals(3, r2.scores.first { it.id == "h" }.votes)   // 得点は持ち越し
        assertEquals("h", r2.scores.first().id)
    }

    @Test fun syncTimeUpReveals() {
        val e = engine(3); e.startGame(GameType.SYNC, Settings(syncSec = 30))
        e.apply("h", Intent.SyncAnswer("夏"))
        clock += 30_000; assertTrue(e.tick())
        assertTrue(e.viewFor("h").screen is Screen.SyncReveal)
    }

    @Test fun normalizeAnswerIgnoresWidthKanaAndSpaces() {
        assertEquals(Engine.normalizeAnswer("ﾘﾝｺﾞ"), Engine.normalizeAnswer("りんご"))
        assertEquals(Engine.normalizeAnswer("Ｃｏｋｅ"), Engine.normalizeAnswer("coke"))
        assertEquals(Engine.normalizeAnswer("お 寿司！"), Engine.normalizeAnswer("お寿司"))
    }

    @Test fun guessOnlyAskerSeesAnswerAndAskerRotates() {
        val e = engine(3); e.startGame(GameType.GUESS)
        val g = e.viewFor("h").screen as Screen.GuessPlay
        val asker = g.asker
        e.players.forEach { p -> assertEquals(p.id == asker, (e.viewFor(p.id).screen as Screen.GuessPlay).answer != null) }
        val guesser = e.players.first { it.id != asker }.id
        val other = e.players.first { it.id != asker && it.id != guesser && it.id != "h" }.id.takeIf { asker != "h" }
        if (other != null) assertFalse(e.apply(other, Intent.GuessSolved(guesser)))
        e.apply(asker, Intent.GuessSolved(guesser))
        val r = e.viewFor("h").screen as Screen.GuessResult
        assertEquals(guesser, r.guesser)
        assertEquals(1, r.scores.first { it.id == guesser }.votes); assertEquals(1, r.scores.first { it.id == asker }.votes)
        e.apply("h", Intent.Next)
        val asker2 = (e.viewFor("h").screen as Screen.GuessPlay).asker
        assertTrue(asker2 != asker)
        clock += 180_000; e.tick()
        assertNull((e.viewFor("h").screen as Screen.GuessResult).guesser)
    }

    @Test fun guessWorksWithTwoPlayers() {
        val e = engine(2); e.startGame(GameType.GUESS)
        assertTrue(e.viewFor("h").screen is Screen.GuessPlay)
    }

    @Test fun questionVotesIncludingSelfAreRevealed() {
        val e = engine(3); e.startGame(GameType.QUESTION)
        val q = e.viewFor("p1").screen as Screen.QVote
        assertTrue(q.question.startsWith("この中で一番"))
        e.apply("h", Intent.QVote("h")); e.apply("p1", Intent.QVote("h")); e.apply("p2", Intent.QVote("p1"))
        val r = e.viewFor("p2").screen as Screen.QReveal
        assertEquals(listOf(VoteCount("h", 2), VoteCount("p1", 1)), r.tally)
    }

    @Test fun localModeAllowsOnlyLocalGames() {
        val e = engine(4, local = true)
        e.apply("h", Intent.UpdateSettings(Settings(game = GameType.SYNC)))
        assertEquals(GameType.WORD_WOLF, e.settings.game)
        e.apply("h", Intent.UpdateSettings(Settings(game = GameType.SPY)))
        assertEquals(GameType.SPY, e.settings.game)
        e.apply("h", Intent.Start)
        val s = e.viewFor("h").screen as Screen.WwRevealLocal
        assertEquals(1, s.entries.count { it.spy })
        s.entries.forEach { e.apply("h", Intent.ConfirmWord(it.id)) }
        e.apply("h", Intent.EndTalk)
        val spy = s.entries.first { it.spy }.id
        e.apply("h", Intent.Accuse(spy))
        val g = e.viewFor("h").screen as Screen.WwGuess
        assertTrue(e.apply("h", Intent.SpyGuess(g.options.first())))   // 1台モードではホストが代わりに選べる
    }

    @Test fun leavingDuringSyncRevealsWhenEveryoneElseAnswered() {
        val e = engine(3); e.startGame(GameType.SYNC)
        e.apply("h", Intent.SyncAnswer("a")); e.apply("p1", Intent.SyncAnswer("a"))
        e.leave("p2")
        assertTrue(e.viewFor("h").screen is Screen.SyncReveal)
    }

    @Test fun allTopicListsLoaded() {
        val e = engine(3)
        for (g in listOf(GameType.SPY, GameType.SYNC, GameType.GUESS, GameType.QUESTION)) {
            e.startGame(g); assertFalse(e.viewFor("h").screen is Screen.Lobby); e.apply("h", Intent.BackToLobby)
        }
    }
}
