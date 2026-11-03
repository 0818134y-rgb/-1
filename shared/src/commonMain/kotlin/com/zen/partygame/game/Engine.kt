package com.zen.partygame.game

import kotlin.random.Random

/** 前回の広告からこれだけたっていて、1ラウンド以上遊んでいれば広告休憩に入る */
const val AD_MIN_INTERVAL_MS = 180_000L
/** 広告休憩で全員の「閉じた」を待つ上限 */
const val AD_WAIT_MS = 25_000L
/** スパイが選ぶ場所の候補数 */
const val SPY_OPTIONS = 12
const val MAX_ANSWER_LEN = 20

/**
 * ゲームの進行（ホストの端末だけで動く）。Android に依存しない純Kotlin。
 * 操作は [apply]、時間経過は [tick]、各端末に見せる内容は [viewFor]。
 * 状態が変わるたびに [version] が増える。
 */
class Engine(
    val roomCode: String,
    val localMode: Boolean,
    private val topics: TopicBank,
    private val rng: Random = Random.Default,
    private val now: () -> Long,
) {
    private enum class Phase {
        LOBBY,
        WW_REVEAL, WW_TALK, WW_VOTE, WW_GUESS, WW_RESULT,   // ワードウルフとスパイ
        NG_PLAY, NG_RESULT,
        SYNC_ANSWER, SYNC_REVEAL,
        GUESS_PLAY, GUESS_RESULT,
        Q_VOTE, Q_REVEAL,
        ARENA, ARENA_RESULT,
        AD_BREAK,
    }
    private enum class AfterAd { NEXT_ROUND, LOBBY }

    val players = mutableListOf<PlayerInfo>()
    var settings = Settings()
        private set
    var version = 0
        private set
    val hostId: String? get() = players.firstOrNull { it.isHost }?.id
    val maxPlayers get() = if (localMode) MAX_PLAYERS_LOCAL else MAX_PLAYERS_NEARBY
    val inLobby get() = phase == Phase.LOBBY

    private var phase = Phase.LOBBY
    private var deadline: Long? = null
    /** 今のラウンドのゲーム（ロビーで設定を変えても、遊んでいる最中のゲームは変わらない） */
    private var current = GameType.WORD_WOLF
    /** 得点（シンクロ・質問で当てろ）。ロビーから始めるたびにリセット */
    private val scores = linkedMapOf<String, Int>()

    // --- ワードウルフ／スパイ ---
    private var citizenWord = ""
    private var wolfWord = ""
    private var wolves = setOf<String>()
    private val confirmed = linkedSetOf<String>()
    private val votes = linkedMapOf<String, String>()
    private var candidates = listOf<String>()
    private var runoff = false
    private var wwResult: Screen.WwResult? = null
    private var accused: String? = null
    private var lastTally = listOf<VoteCount>()
    private var spyOptions = listOf<String>()
    private val isSpy get() = current == GameType.SPY

    // --- NGワード ---
    private val ngWords = linkedMapOf<String, String>()
    private val ngOut = linkedMapOf<String, Int>()
    private var ngWinners = listOf<String>()

    // --- シンクロ ---
    private var prompt = ""
    private val answers = linkedMapOf<String, String>()
    private var syncReveal: Screen.SyncReveal? = null

    // --- 質問で当てろ ---
    private var askerTurn = -1
    private var asker = ""
    private var guessWord = ""
    private var guesser: String? = null

    // --- ぶっちゃけ質問 ---
    private var question = ""
    private val qVotes = linkedMapOf<String, String>()

    // --- なんでもアリーナ ---
    private var arena: ArenaGame? = null

    // --- 広告 ---
    private var lastAdAt = now()
    private var roundsSinceAd = 0
    private var breakId = 0
    private val adPending = mutableSetOf<String>()
    private var afterAd = AfterAd.LOBBY

    private fun changed() { version++ }

    // ================= 参加・退出 =================

    sealed interface JoinResult {
        data class Ok(val player: PlayerInfo) : JoinResult
        data object Full : JoinResult
        data object InGame : JoinResult
    }

    fun join(id: String, rawName: String, isHost: Boolean = false): JoinResult {
        if (phase != Phase.LOBBY) return JoinResult.InGame
        if (players.size >= maxPlayers) return JoinResult.Full
        players.removeAll { it.id == id }
        val p = PlayerInfo(id, uniqueName(rawName), isHost)
        if (isHost) players.add(0, p) else players.add(p)
        changed()
        return JoinResult.Ok(p)
    }

    private fun uniqueName(raw: String): String {
        val base = raw.trim().replace("\n", " ").take(MAX_NAME_LEN).ifBlank { "プレイヤー${players.size + 1}" }
        var name = base; var n = 2
        while (players.any { it.name == name }) name = "$base${n++}"
        return name
    }

    /** 抜けた人を取り除き、進行が止まらないように整える */
    fun leave(id: String) {
        if (players.none { it.id == id }) return
        players.removeAll { it.id == id }
        confirmed.remove(id); votes.remove(id); adPending.remove(id); answers.remove(id); qVotes.remove(id); scores.remove(id)
        votes.entries.removeAll { it.value == id }
        qVotes.entries.removeAll { it.value == id }
        changed()
        when (phase) {
            Phase.LOBBY, Phase.WW_RESULT, Phase.NG_RESULT, Phase.SYNC_REVEAL, Phase.GUESS_RESULT, Phase.Q_REVEAL, Phase.ARENA_RESULT -> Unit
            Phase.ARENA -> { arena?.forfeit(id); syncArena() }
            Phase.AD_BREAK -> if (adPending.isEmpty()) finishAdBreak()
            Phase.NG_PLAY -> {
                ngWords.remove(id); ngOut.remove(id)
                if (players.size < 2 || alive().size <= 1) finishNg()
            }
            Phase.SYNC_ANSWER -> when {
                players.size < 2 -> toLobby()
                answers.size >= players.size -> revealSync()
            }
            Phase.GUESS_PLAY -> if (id == asker || players.size < 2) toLobby()
            Phase.Q_VOTE -> when {
                players.size < 2 -> toLobby()
                qVotes.size >= players.size -> revealQ()
            }
            Phase.WW_REVEAL, Phase.WW_TALK, Phase.WW_VOTE, Phase.WW_GUESS -> {
                wolves = wolves - id
                if (players.size < MIN_PLAYERS || wolves.isEmpty()) { toLobby(); return }
                candidates = candidates - id
                when (phase) {
                    Phase.WW_REVEAL -> if (confirmed.containsAll(ids())) startTalk()
                    Phase.WW_VOTE -> if (candidates.isEmpty()) toLobby() else if (votes.size >= players.size) resolveVotes()
                    else -> Unit
                }
            }
        }
    }

    private fun ids() = players.map { it.id }

    // ================= 操作 =================

    /** @return 状態が変わったら true */
    fun apply(from: String, intent: Intent): Boolean {
        if (players.none { it.id == from }) return false
        val isHost = from == hostId
        val before = version
        when (intent) {
            is Intent.UpdateSettings -> if (isHost && phase == Phase.LOBBY) {
                settings = intent.settings.sanitized().let { if (localMode && !it.game.localOk) it.copy(game = GameType.WORD_WOLF) else it }
                changed()
            }
            is Intent.Kick -> if (isHost && phase == Phase.LOBBY && intent.target != from) leave(intent.target)
            Intent.Start -> if (isHost && phase == Phase.LOBBY) {
                scores.clear(); askerTurn = -1; startRound()
            }

            // --- ワードウルフ／スパイ ---
            is Intent.ConfirmWord -> if (phase == Phase.WW_REVEAL) {
                val who = if (localMode && isHost && intent.forPlayer != null) intent.forPlayer else from
                if (who in ids() && confirmed.add(who)) {
                    changed()
                    if (confirmed.containsAll(ids())) startTalk()
                }
            }
            Intent.EndTalk -> if (isHost && phase == Phase.WW_TALK) startVote()
            is Intent.Vote -> if (phase == Phase.WW_VOTE && !localMode &&
                intent.target in candidates && intent.target != from && votes[from] != intent.target
            ) {
                votes[from] = intent.target; changed()
                if (votes.size >= players.size) resolveVotes()
            }
            Intent.CloseVote -> if (isHost) when (phase) {
                Phase.WW_VOTE -> resolveVotes()
                Phase.SYNC_ANSWER -> revealSync()
                Phase.Q_VOTE -> revealQ()
                else -> Unit
            }
            is Intent.Accuse -> if (isHost && phase == Phase.WW_VOTE && localMode && intent.target in candidates) {
                lastTally = emptyList(); accusedIs(intent.target)
            }
            is Intent.Judge -> if (isHost && phase == Phase.WW_GUESS && !isSpy) {
                finishWw(if (intent.correct) Team.WOLF else Team.CITIZEN,
                    if (intent.correct) ResultReason.WOLF_GUESSED else ResultReason.WOLF_CAUGHT)
            }
            is Intent.SpyGuess -> if (phase == Phase.WW_GUESS && isSpy && (from in wolves || (localMode && isHost)) &&
                intent.answer in spyOptions
            ) {
                val ok = intent.answer == citizenWord
                finishWw(if (ok) Team.WOLF else Team.CITIZEN, if (ok) ResultReason.WOLF_GUESSED else ResultReason.WOLF_CAUGHT, intent.answer)
            }

            // --- NGワード ---
            is Intent.ReportOut -> if (phase == Phase.NG_PLAY && intent.target in ngWords && intent.target !in ngOut) {
                ngOut[intent.target] = ngOut.size + 1; changed()
                if (alive().size <= 1) finishNg()
            }
            is Intent.UndoOut -> if (isHost && phase == Phase.NG_PLAY && ngOut.remove(intent.target) != null) {
                ngOut.keys.toList().forEachIndexed { i, k -> ngOut[k] = i + 1 }; changed()
            }
            Intent.EndNg -> if (isHost && phase == Phase.NG_PLAY) finishNg()

            // --- シンクロ ---
            is Intent.SyncAnswer -> if (phase == Phase.SYNC_ANSWER) {
                val text = intent.text.trim().replace("\n", " ").take(MAX_ANSWER_LEN)
                if (text.isNotEmpty() && answers[from] != text) {
                    answers[from] = text; changed()
                    if (answers.size >= players.size) revealSync()
                }
            }

            // --- 質問で当てろ ---
            is Intent.GuessSolved -> if (phase == Phase.GUESS_PLAY && (from == asker || isHost) &&
                intent.guesser in ids() && intent.guesser != asker
            ) {
                guesser = intent.guesser
                addScore(intent.guesser, 1); addScore(asker, 1)
                finishGuess()
            }
            Intent.GuessGiveUp -> if (phase == Phase.GUESS_PLAY && (from == asker || isHost)) {
                guesser = null; finishGuess()
            }

            // --- ぶっちゃけ質問 ---
            is Intent.QVote -> if (phase == Phase.Q_VOTE && !localMode && intent.target in ids() && qVotes[from] != intent.target) {
                qVotes[from] = intent.target; changed()
                if (qVotes.size >= players.size) revealQ()
            }

            // --- なんでもアリーナ ---
            is Intent.ArenaPlay -> if (phase == Phase.ARENA && arena!!.play(from, intent.uid, intent.target)) { changed(); syncArena() }
            is Intent.ArenaInput -> if (phase == Phase.ARENA && arena!!.input(from, intent.value)) { changed(); syncArena() }
            is Intent.ArenaReport -> if (phase == Phase.ARENA && arena!!.report(from, intent.target)) { changed(); syncArena() }

            // --- 共通 ---
            Intent.Next -> if (isHost && isResult()) {
                if (adDue()) startAdBreak(AfterAd.NEXT_ROUND) else startRound()
            }
            Intent.BackToLobby -> if (isHost && phase != Phase.LOBBY && phase != Phase.AD_BREAK) {
                if (isResult() && adDue()) startAdBreak(AfterAd.LOBBY) else toLobby()
            }
            is Intent.AdDone -> if (phase == Phase.AD_BREAK && intent.breakId == breakId && adPending.remove(from)) {
                changed(); if (adPending.isEmpty()) finishAdBreak()
            }
        }
        return version != before
    }

    private fun isResult() = phase in setOf(Phase.WW_RESULT, Phase.NG_RESULT, Phase.SYNC_REVEAL, Phase.GUESS_RESULT, Phase.Q_REVEAL, Phase.ARENA_RESULT)

    /** 時間切れの処理。@return 状態が変わったら true */
    fun tick(): Boolean {
        if (phase == Phase.ARENA) {
            if (arena!!.tick()) { changed(); syncArena(); return true }
            return false
        }
        val d = deadline ?: return false
        if (now() < d) return false
        val before = version
        when (phase) {
            Phase.WW_TALK -> startVote()
            Phase.NG_PLAY -> finishNg()
            Phase.SYNC_ANSWER -> revealSync()
            Phase.GUESS_PLAY -> { guesser = null; finishGuess() }
            Phase.AD_BREAK -> finishAdBreak()
            else -> { deadline = null; changed() }
        }
        return version != before
    }

    // ================= ラウンドの進行 =================

    private fun startRound() {
        val g = settings.game.let { if (localMode && !it.localOk) GameType.WORD_WOLF else it }
        if (players.size < g.minPlayers) return
        current = g
        when (g) {
            GameType.WORD_WOLF, GameType.SPY -> startWw()
            GameType.NG_WORD -> startNg()
            GameType.SYNC -> startSync()
            GameType.GUESS -> startGuess()
            GameType.QUESTION -> startQ()
            GameType.ARENA -> startArena()
        }
    }

    private fun toLobby() {
        phase = Phase.LOBBY; deadline = null; changed()
    }

    private fun addScore(id: String, n: Int) { if (id in ids()) scores[id] = (scores[id] ?: 0) + n }

    private fun scoreList() = players.map { VoteCount(it.id, scores[it.id] ?: 0) }.sortedByDescending { it.votes }

    private fun startWw() {
        if (isSpy) {
            citizenWord = topics.drawSpyPlace(rng); wolfWord = ""
        } else {
            val pair = topics.drawPair(settings.categories, rng)
            val (cw, ww) = if (rng.nextBoolean()) pair.a to pair.b else pair.b to pair.a
            citizenWord = cw; wolfWord = ww
        }
        val n = players.size
        val wanted = if (settings.wolves == 0) (if (n >= 7) 2 else 1) else settings.wolves
        val count = wanted.coerceIn(1, maxOf(1, (n - 1) / 2))
        wolves = ids().shuffled(rng).take(count).toSet()
        confirmed.clear(); votes.clear(); runoff = false; wwResult = null; accused = null; lastTally = emptyList(); spyOptions = emptyList()
        candidates = ids()
        phase = Phase.WW_REVEAL; deadline = null; changed()
    }

    private fun startTalk() {
        phase = Phase.WW_TALK; deadline = now() + settings.talkSec * 1000L; changed()
    }

    private fun startVote() {
        votes.clear(); candidates = ids(); runoff = false
        phase = Phase.WW_VOTE; deadline = null; changed()
    }

    private fun resolveVotes() {
        val tally = candidates.map { c -> VoteCount(c, votes.values.count { it == c }) }
        lastTally = tally.sortedByDescending { it.votes }
        val max = tally.maxOfOrNull { it.votes } ?: 0
        val tops = tally.filter { it.votes == max && max > 0 }.map { it.id }
        when {
            tops.size == 1 -> accusedIs(tops[0])
            !runoff && tops.size >= 2 -> {
                candidates = tops; votes.clear(); runoff = true; changed()
            }
            else -> finishWw(Team.WOLF, ResultReason.TIE)
        }
    }

    private fun accusedIs(id: String) {
        accused = id
        if (id in wolves) {
            if (isSpy) spyOptions = topics.spyOptions(citizenWord, SPY_OPTIONS, rng)
            phase = Phase.WW_GUESS; deadline = null; changed()
        } else {
            finishWw(Team.WOLF, ResultReason.CITIZEN_ACCUSED)
        }
    }

    private fun finishWw(winner: Team, reason: ResultReason, spyGuess: String? = null) {
        wwResult = Screen.WwResult(
            citizenWord = citizenWord, wolfWord = wolfWord, wolves = wolves.toList(), accused = accused,
            tally = lastTally, winner = winner, reason = reason, myWord = null, spyGuess = spyGuess,
        )
        roundsSinceAd++
        phase = Phase.WW_RESULT; deadline = null; changed()
    }

    private fun startNg() {
        val words = topics.drawNg(players.size, settings.ngLevel, rng)
        ngWords.clear(); ngOut.clear(); ngWinners = emptyList()
        players.forEachIndexed { i, p -> ngWords[p.id] = words[i % words.size] }
        phase = Phase.NG_PLAY; deadline = now() + settings.ngSec * 1000L; changed()
    }

    private fun alive() = ngWords.keys.filter { it !in ngOut }

    private fun finishNg() {
        ngWinners = alive()
        roundsSinceAd++
        phase = Phase.NG_RESULT; deadline = null; changed()
    }

    // --- シンクロ ---

    private fun startSync() {
        prompt = topics.drawSync(rng); answers.clear(); syncReveal = null
        phase = Phase.SYNC_ANSWER
        deadline = if (settings.syncSec > 0) now() + settings.syncSec * 1000L else null
        changed()
    }

    private fun revealSync() {
        val groups = answers.entries.groupBy { normalizeAnswer(it.value) }.values
            .map { g -> SyncGroup(g.first().value, g.map { it.key }) }
            .sortedWith(compareByDescending<SyncGroup> { it.ids.size }.thenBy { it.answer })
        val gained = players.map { p ->
            val size = groups.firstOrNull { p.id in it.ids }?.ids?.size ?: 0
            VoteCount(p.id, if (size >= 2) size - 1 else 0)
        }
        gained.forEach { addScore(it.id, it.votes) }
        syncReveal = Screen.SyncReveal(prompt, groups, gained, emptyList())
        roundsSinceAd++
        phase = Phase.SYNC_REVEAL; deadline = null; changed()
    }

    // --- 質問で当てろ ---

    private fun startGuess() {
        askerTurn = (askerTurn + 1) % players.size
        asker = players[askerTurn].id
        guessWord = topics.drawGuess(rng); guesser = null
        phase = Phase.GUESS_PLAY; deadline = now() + settings.guessSec * 1000L; changed()
    }

    private fun finishGuess() {
        roundsSinceAd++
        phase = Phase.GUESS_RESULT; deadline = null; changed()
    }

    // --- ぶっちゃけ質問 ---

    private fun startQ() {
        question = topics.drawQuestion(rng); qVotes.clear()
        phase = Phase.Q_VOTE; deadline = null; changed()
    }

    private fun revealQ() {
        roundsSinceAd++
        phase = Phase.Q_REVEAL; deadline = null; changed()
    }

    // --- なんでもアリーナ ---

    /** テスト用 */
    /** テスト用 */
    fun arenaForTest() = arena

    private fun startArena() {
        val names = players.associate { it.id to it.name }   // 途中で抜けた人の名前もログに残せるように
        arena = ArenaGame(ids(), { id -> names[id] ?: "?" }, rng, now, settings.arenaTurnSec, settings.arenaChaos)
        phase = Phase.ARENA; deadline = null; changed(); syncArena()
    }

    /** アリーナの決着がついたら結果画面へ */
    private fun syncArena() {
        val a = arena ?: return
        if (phase == Phase.ARENA && a.step == ArenaGame.Step.OVER) {
            roundsSinceAd++
            phase = Phase.ARENA_RESULT; deadline = null; changed()
        }
    }

    // ================= 広告休憩 =================

    private fun adDue() = roundsSinceAd >= 1 && now() - lastAdAt >= AD_MIN_INTERVAL_MS

    private fun startAdBreak(after: AfterAd) {
        breakId++; afterAd = after
        adPending.clear()
        // 1台モードでは広告を出すのはホストの端末だけ
        adPending += if (localMode) listOfNotNull(hostId) else ids()
        phase = Phase.AD_BREAK; deadline = now() + AD_WAIT_MS; changed()
    }

    private fun finishAdBreak() {
        lastAdAt = now(); roundsSinceAd = 0; adPending.clear()
        if (afterAd == AfterAd.NEXT_ROUND) startRound() else toLobby()
        if (phase == Phase.AD_BREAK) toLobby()
    }

    // ================= 各端末に見せる内容 =================

    fun viewFor(id: String): PlayerView {
        val screen: Screen = when (phase) {
            Phase.LOBBY -> Screen.Lobby
            Phase.WW_REVEAL -> if (localMode) {
                Screen.WwRevealLocal(players.map { RevealEntry(it.id, wordOf(it.id), isSpy && it.id in wolves) }, confirmed.toList())
            } else Screen.WwReveal(wordOf(id), confirmed.toList(), isSpy && id in wolves)
            Phase.WW_TALK -> Screen.WwTalk(if (localMode) null else wordOf(id), !localMode && isSpy && id in wolves)
            Phase.WW_VOTE -> if (localMode) Screen.WwAccuse(candidates)
            else Screen.WwVote(candidates, votes[id], votes.keys.toList(), runoff)
            Phase.WW_GUESS -> Screen.WwGuess(wolves.toList(), id in wolves, spyOptions)
            Phase.WW_RESULT -> wwResult!!.copy(myWord = if (localMode || (isSpy && id in wolves)) null else wordOf(id))
            Phase.NG_PLAY -> {
                val meOut = id in ngOut
                Screen.NgPlay(ngEntries(hideFor = if (meOut) null else id), meOut)
            }
            Phase.NG_RESULT -> Screen.NgResult(ngEntries(hideFor = null), ngWinners)
            Phase.SYNC_ANSWER -> Screen.SyncAnswer(prompt, answers[id], answers.keys.toList())
            Phase.SYNC_REVEAL -> syncReveal!!.copy(scores = scoreList())
            Phase.GUESS_PLAY -> Screen.GuessPlay(asker, if (localMode || id == asker) guessWord else null)
            Phase.GUESS_RESULT -> Screen.GuessResult(guessWord, asker, guesser, scoreList())
            Phase.Q_VOTE -> Screen.QVote(question, qVotes[id], qVotes.keys.toList())
            Phase.Q_REVEAL -> Screen.QReveal(
                question,
                players.map { p -> VoteCount(p.id, qVotes.values.count { it == p.id }) }.filter { it.votes > 0 }.sortedByDescending { it.votes },
            )
            Phase.ARENA, Phase.ARENA_RESULT -> arena!!.view(id)
            Phase.AD_BREAK -> Screen.AdBreak(breakId)
        }
        return PlayerView(
            me = id, isHost = id == hostId, roomCode = roomCode, localMode = localMode,
            players = players.toList(), settings = settings.copy(game = if (phase == Phase.LOBBY) settings.game else current),
            categories = topics.categories,
            screen = screen,
            remainingMs = (if (phase == Phase.ARENA) arena?.deadline else deadline)?.let { (it - now()).coerceAtLeast(0) },
        )
    }

    private fun wordOf(id: String) = if (id in wolves) wolfWord else citizenWord

    private fun ngEntries(hideFor: String?) = players.filter { it.id in ngWords }.map {
        NgEntry(it.id, if (it.id == hideFor) null else ngWords[it.id], it.id in ngOut, ngOut[it.id] ?: 0)
    }

    companion object {
        /** シンクロの答え合わせ用：全角半角・カタカナひらがな・空白・大文字小文字の違いを無視する */
        fun normalizeAnswer(s: String): String {
            val n = normalizeNfkc(s).lowercase().filterNot { it.isWhitespace() }
            return buildString { for (c in n) append(if (c in 'ァ'..'ヶ') (c - 0x60) else c) }
                .trimEnd('。', '!', '！', '?', '？', '〜', '~')
        }
    }
}
