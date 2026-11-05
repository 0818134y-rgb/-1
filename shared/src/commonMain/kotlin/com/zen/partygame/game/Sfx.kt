package com.zen.partygame.game

/**
 * 効果音を鳴らすきっかけ。前に見ていた画面と、届いた画面を比べて、鳴らす音の名前を返す。
 * Android もブラウザも同じこれを使う（音のファイルは sfx_<名前>.ogg / sfx/<名前>.mp3）。
 * 画面のタップ音や、残り時間のカウント音は、それぞれの画面側で鳴らす。
 */
object Sfx {
    const val TAP = "tap"
    const val CARD_PLAY = "card_play"
    const val CARD_DRAW = "card_draw"
    const val YOUR_TURN = "your_turn"
    const val TICK = "tick"
    const val TICK_LAST = "tick_last"
    const val BUZZER = "buzzer"
    const val WIN = "win"
    const val LOSE = "lose"
    const val REVEAL = "reveal"
    const val PAGE = "page"
    const val SENT = "sent"
    const val JOIN = "join"
    const val BOMB = "bomb"
    const val ALERT = "alert"
    const val SYNC = "sync"
    const val START = "start"
    const val VOTE = "vote"
    const val OUT = "out"

    val ALL = listOf(TAP, CARD_PLAY, CARD_DRAW, YOUR_TURN, TICK, TICK_LAST, BUZZER, WIN, LOSE, REVEAL, PAGE, SENT, JOIN, BOMB,
        ALERT, SYNC, START, VOTE, OUT)

    /** 遊んでいる最中の画面（ロビー・結果・休憩以外） */
    private fun isPlay(s: Screen) = when (s) {
        Screen.Lobby, is Screen.AdBreak, is Screen.WwResult, is Screen.NgResult, is Screen.SyncReveal, is Screen.GuessResult,
        is Screen.QReveal, is Screen.ArenaResult, is Screen.DrawReveal, is Screen.ScaleResult -> false
        is Screen.Table -> s.stage != "result"
        else -> true
    }

    /** 残り時間を数える画面か（残り10秒からカウント音を鳴らす） */
    fun countsDown(v: PlayerView): Boolean = v.remainingMs != null && when (val s = v.screen) {
        is Screen.WwTalk, is Screen.NgPlay, is Screen.SyncAnswer, is Screen.GuessPlay -> true
        is Screen.DrawTask -> !s.submitted
        is Screen.Arena -> s.turn == v.me && s.mini == null
        is Screen.Table -> s.actions.isNotEmpty()
        else -> false
    }

    fun cues(prev: PlayerView?, next: PlayerView): List<String> {
        val out = mutableListOf<String>()
        val p = prev?.screen
        val s = next.screen
        val me = next.me
        if (prev == null || p == null) return out
        val sameKind = p::class == s::class

        // ゲームが始まった（ロビー・結果・休憩から遊ぶ画面へ）
        if (!isPlay(p) && isPlay(s)) out += START

        when (s) {
            Screen.Lobby -> if (p == Screen.Lobby && next.players.size > prev.players.size) out += JOIN

            is Screen.WwVote -> when {
                p !is Screen.WwVote -> out += VOTE
                s.runoff && !p.runoff -> out += ALERT
                s.voted.size > p.voted.size -> out += VOTE
            }
            is Screen.WwReveal -> if (p is Screen.WwReveal && s.confirmed.size > p.confirmed.size) out += VOTE
            is Screen.WwGuess -> if (p !is Screen.WwGuess) out += ALERT
            is Screen.WwResult -> if (p !is Screen.WwResult) out += when {
                next.localMode -> REVEAL
                (if (me in s.wolves) Team.WOLF else Team.CITIZEN) == s.winner -> WIN
                else -> LOSE
            }

            is Screen.NgPlay -> if (p is Screen.NgPlay) {
                val outs = s.entries.count { it.out }; val before = p.entries.count { it.out }
                if (outs > before) out += if (s.myOut && !p.myOut) LOSE else OUT
                else if (outs < before) out += TAP
            }
            is Screen.NgResult -> if (p !is Screen.NgResult) out += if (me in s.winners) WIN else REVEAL

            is Screen.SyncAnswer -> if (p is Screen.SyncAnswer && s.answered.size > p.answered.size) out += VOTE
            is Screen.SyncReveal -> if (p !is Screen.SyncReveal) out += if (s.groups.any { me in it.ids && it.ids.size >= 2 }) SYNC else REVEAL

            is Screen.GuessResult -> if (p !is Screen.GuessResult) out += when {
                s.guesser == null -> BUZZER
                s.guesser == me || s.asker == me -> WIN
                else -> SYNC
            }
            is Screen.QVote -> if (p is Screen.QVote && s.voted.size > p.voted.size) out += VOTE
            is Screen.QReveal -> if (p !is Screen.QReveal) out += REVEAL

            is Screen.Arena -> if (p is Screen.Arena) arena(p, s, me, out) else if (s.turn == me) out += YOUR_TURN
            is Screen.ArenaResult -> if (p !is Screen.ArenaResult) {
                if (p is Screen.Arena && newLines(p.log, s.log).any { tplOf(it).startsWith("ばくだんが4つ") }) out += BOMB
                out += if (me in s.winners) WIN else LOSE
            }

            is Screen.DrawTask -> when {
                p !is Screen.DrawTask -> Unit
                s.step != p.step -> out += PAGE
                s.submitted && !p.submitted -> out += SENT
                s.done.size > p.done.size -> out += VOTE
            }
            is Screen.DrawReveal -> when {
                p !is Screen.DrawReveal -> out += REVEAL
                s.finished && !p.finished -> out += WIN
                s.book != p.book || s.entries.size != p.entries.size -> out += PAGE
            }
            is Screen.Scale -> if (p is Screen.Scale) {
                val played = s.cards.count { it.state == ScaleState.PLAYED }; val before = p.cards.count { it.state == ScaleState.PLAYED }
                val hints = s.cards.count { it.hint.isNotEmpty() }; val hintsBefore = p.cards.count { it.hint.isNotEmpty() }
                when {
                    s.lives < p.lives -> out += BUZZER
                    played > before -> out += CARD_PLAY
                    s.playing && !p.playing -> out += ALERT
                    hints > hintsBefore -> out += if (s.cards.count { it.owner == me && it.hint.isNotEmpty() } > p.cards.count { it.owner == me && it.hint.isNotEmpty() }) SENT else VOTE
                }
            }
            is Screen.ScaleResult -> if (p !is Screen.ScaleResult) out += if (s.cleared) WIN else LOSE
            is Screen.Table -> if (p is Screen.Table) table(p, s, me, out)
            else -> Unit
        }
        if (!sameKind && out.isEmpty() && isPlay(s) && isPlay(p)) out += PAGE
        return out.distinct().take(2)
    }

    private fun arena(p: Screen.Arena, s: Screen.Arena, me: String, out: MutableList<String>) {
        val lines = newLines(p.log, s.log)
        val tpls = lines.map { tplOf(it) }
        val meBefore = p.seats.firstOrNull { it.id == me }; val meNow = s.seats.firstOrNull { it.id == me }
        when {
            tpls.any { it.startsWith("ばくだんが4つ") } -> out += BOMB
            meBefore?.alive == true && meNow?.alive == false -> out += LOSE
            s.seats.count { it.alive } < p.seats.count { it.alive } -> out += OUT
        }
        if (tpls.any { it.endsWith("の違反を報告！") }) out += BUZZER
        // 勝負（じゃんけん・指・投票・鼻タッチ…）が始まった / 鼻タッチの合図
        val pm = p.mini; val sm = s.mini
        if (sm != null && (pm == null || pm.kind != sm.kind || pm.owner != sm.owner)) out += ALERT
        else if (sm?.kind == "nose" && sm.goInMs != null && pm?.goInMs == null) out += ALERT
        // カードが出た
        if (s.discardTop != null && s.discardTop.uid != p.discardTop?.uid) out += CARD_PLAY
        if (s.turn == me && p.turn != me && meNow?.alive == true) out += YOUR_TURN
        else if (s.hand.size > p.hand.size && out.isEmpty()) out += CARD_DRAW
    }

    private fun table(p: Screen.Table, s: Screen.Table, me: String, out: MutableList<String>) {
        val mineBefore = p.seats.firstOrNull { it.id == me }; val mine = s.seats.firstOrNull { it.id == me }
        when {
            s.stage == "result" && p.stage != "result" -> out += if ((mine?.delta ?: 0) > 0) WIN else if ((mine?.delta ?: 0) < 0) LOSE else REVEAL
            s.stage == "reveal" && p.stage != "reveal" -> out += if ((mine?.delta ?: 0) > 0) WIN else REVEAL
            s.stage != p.stage -> out += CARD_DRAW
            (mine?.cards?.size ?: 0) > (mineBefore?.cards?.size ?: 0) -> out += CARD_DRAW
            s.seats.count { it.done } > p.seats.count { it.done } -> out += if (mine?.done == true && mineBefore?.done == false) SENT else VOTE
        }
        if (mine?.status == "バースト" && mineBefore?.status != "バースト") out += BUZZER
    }

    /** 記録の、新しく増えた行 */
    private fun newLines(before: List<String>, after: List<String>): List<String> {
        for (k in minOf(before.size, after.size) downTo 1) {
            if (before.takeLast(k) == after.take(k)) return after.drop(k)
        }
        return if (before == after) emptyList() else after
    }

    /** 記録1行の型（日本語）。訳す前の型で判定する */
    private fun tplOf(line: String): String {
        if (!LogFmt.isEntry(line)) return line
        val end = line.indexOfAny(charArrayOf(LogFmt.SEP, LogFmt.END), 1)
        return if (end < 0) line.substring(1) else line.substring(1, end)
    }
}
