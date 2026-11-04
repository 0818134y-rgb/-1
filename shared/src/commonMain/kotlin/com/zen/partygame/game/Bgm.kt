package com.zen.partygame.game

/**
 * 場面ごとのBGM。今の画面から流す曲の名前を返す（Android は res/raw/bgm_<名前>.ogg、ブラウザは bgm/<名前>.mp3）。
 * 曲は tools/bgm_scores.py の楽譜から tools/make_bgm.py で作ったもの。
 */
object Bgm {
    const val TITLE = "title"      // ホーム画面・部屋さがし
    const val LOBBY = "lobby"      // 部屋で待つ
    const val TALK = "talk"        // 話し合い（通話の声のじゃまをしない、ひかえめな曲）
    const val TENSION = "tension"  // 投票・犯人当て
    const val ARENA = "arena"      // なんでもアリーナ
    const val DRAW = "draw"        // お絵かき伝言で描いている間
    const val RESULT = "result"    // 結果発表
    const val BREAK = "break"      // 休憩（広告）

    val ALL = listOf(TITLE, LOBBY, TALK, TENSION, ARENA, DRAW, RESULT, BREAK)

    /** 部屋の外（view が null）ならタイトルの曲 */
    fun track(v: PlayerView?): String = when (val s = v?.screen) {
        null -> TITLE
        Screen.Lobby -> LOBBY
        is Screen.AdBreak -> BREAK
        is Screen.WwReveal, is Screen.WwRevealLocal, is Screen.WwTalk, is Screen.NgPlay,
        is Screen.SyncAnswer, is Screen.GuessPlay, is Screen.QVote -> TALK
        is Screen.WwVote, is Screen.WwAccuse, is Screen.WwGuess -> TENSION
        is Screen.Arena -> ARENA
        is Screen.DrawTask -> DRAW
        is Screen.Scale -> if (s.playing) TENSION else TALK
        is Screen.WwResult, is Screen.NgResult, is Screen.SyncReveal, is Screen.GuessResult,
        is Screen.QReveal, is Screen.ArenaResult, is Screen.DrawReveal, is Screen.ScaleResult -> RESULT
    }
}
