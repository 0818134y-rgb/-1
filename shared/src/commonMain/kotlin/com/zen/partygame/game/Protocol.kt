package com.zen.partygame.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 通信の版。違う版の端末どうしは同じ部屋に入れない */
const val PROTO = 9
const val MIN_PLAYERS = 3
const val MAX_PLAYERS_NEARBY = 8
const val MAX_PLAYERS_LOCAL = 12
const val MAX_NAME_LEN = 10

val WireJson = Json {
    classDiscriminator = "t"
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** ゲームの種類。localOk = スマホ1台でも遊べる、minPlayers = 必要な人数 */
enum class GameType(val label: String, val short: String, val localOk: Boolean, val minPlayers: Int) {
    ARENA("なんでもアリーナ", "アリーナ", false, 2),
    YAO("やおよろずバトル", "やおよろず", false, 2),
    SCALE("みんなのものさし", "ものさし", false, 2),
    WORD_WOLF("ワードウルフ", "ワードウルフ", true, 3),
    NG_WORD("NGワード", "NGワード", false, 3),
    SPY("スパイはだれだ", "スパイ", true, 3),
    SYNC("みんなでシンクロ", "シンクロ", false, 2),
    GUESS("質問で当てろ", "質問当て", true, 2),
    QUESTION("ぶっちゃけ質問", "ぶっちゃけ", false, 3),
    DRAW("お絵かき伝言", "お絵かき", false, 3),
    BLACKJACK("ブラックジャック", "ブラックジャック", false, 2),
    HILO("ハイ＆ロー", "ハイ＆ロー", false, 2),
    POKER("ポーカー", "ポーカー", false, 2),
    INDIAN("インディアンポーカー", "インディアン", false, 2),
}

/** みんなのものさし：ライフの数・ヒントの長さ */
const val SCALE_LIVES = 3
const val MAX_SCALE_HINT_LEN = 20

/** みんなのものさしのお題。t = お題、lo = 1 のときの言葉、hi = 100 のときの言葉 */
@Serializable
data class ScaleTopic(val t: String, val lo: String, val hi: String)

/** みんなのものさしのカード：手元にある／出した／小さいのに出しそびれた（ミスで捨てられた） */
enum class ScaleState { HELD, PLAYED, MISSED }

@Serializable
data class ScaleCard(
    val uid: Int,
    val owner: String,
    /** 途中で抜けた人の名前も出せるように */
    val ownerName: String,
    val hint: String = "",
    /** 自分のカードか、もう場に出たカードだけ数字が入る */
    val value: Int? = null,
    val state: ScaleState = ScaleState.HELD,
    /** 出した順番（1始まり）。出していなければ 0 */
    val order: Int = 0,
)

/** お絵かき伝言の各手番でやること */
enum class DrawKind { WRITE, DRAW, GUESS }

/** 絵のデータ（DrawingCodec の形式）の上限文字数 */
const val MAX_DRAWING_CHARS = 16000
const val MAX_DRAW_TEXT_LEN = 40
enum class NgLevel { NORMAL, HARD, MIX }
enum class Team { CITIZEN, WOLF }

enum class ResultReason {
    /** ウルフが最多票 → 市民のお題を答えられなかった */
    WOLF_CAUGHT,
    /** ウルフが最多票 → 市民のお題を当てて逆転 */
    WOLF_GUESSED,
    /** 市民が最多票になってしまった */
    CITIZEN_ACCUSED,
    /** 決選投票でも同票 */
    TIE,
}

@Serializable
data class PlayerInfo(val id: String, val name: String, val isHost: Boolean = false)

@Serializable
data class Settings(
    val game: GameType = GameType.WORD_WOLF,
    /** 話し合いの秒数 */
    val talkSec: Int = 180,
    /** ウルフの人数。0 = 人数に合わせて自動 */
    val wolves: Int = 0,
    /** 使うお題のジャンル。空 = すべて */
    val categories: List<String> = emptyList(),
    /** NGワードの制限秒数 */
    val ngSec: Int = 300,
    val ngLevel: NgLevel = NgLevel.NORMAL,
    /** シンクロの回答時間（秒）。0 = 制限なし */
    val syncSec: Int = 60,
    /** 質問で当てろの制限時間（秒） */
    val guessSec: Int = 180,
    /** なんでもアリーナの1手番の制限（秒）。0 = 制限なし */
    val arenaTurnSec: Int = 60,
    /** なんでもアリーナで「今夜のルール」を入れるか */
    val arenaChaos: Boolean = true,
    /** お絵かき伝言：絵を描く時間（秒） */
    val drawSec: Int = 90,
    /** お絵かき伝言：お題を書く・絵を見て答える時間（秒） */
    val writeSec: Int = 45,
    /** みんなのものさし：1人に配る数字の枚数。0 = 人数に合わせて自動 */
    val scaleCards: Int = 0,
) {
    fun sanitized(): Settings = copy(
        talkSec = talkSec.coerceIn(60, 600),
        wolves = wolves.coerceIn(0, 3),
        ngSec = ngSec.coerceIn(60, 900),
        syncSec = syncSec.coerceIn(0, 300),
        guessSec = guessSec.coerceIn(60, 600),
        arenaTurnSec = arenaTurnSec.coerceIn(0, 300),
        drawSec = drawSec.coerceIn(30, 300),
        writeSec = writeSec.coerceIn(15, 180),
        scaleCards = scaleCards.coerceIn(0, 3),
    )
}

/** 参加者 → ホストへ送る操作 */
@Serializable
sealed interface Intent {
    @Serializable @SerialName("settings") data class UpdateSettings(val settings: Settings) : Intent
    @Serializable @SerialName("start") data object Start : Intent
    @Serializable @SerialName("kick") data class Kick(val target: String) : Intent

    /** お題を確認した。1台モードでは forPlayer にその人のIDを入れてホストが代わりに送る */
    @Serializable @SerialName("confirm") data class ConfirmWord(val forPlayer: String? = null) : Intent
    @Serializable @SerialName("endTalk") data object EndTalk : Intent
    @Serializable @SerialName("vote") data class Vote(val target: String) : Intent
    @Serializable @SerialName("closeVote") data object CloseVote : Intent
    /** 1台モード：口頭の投票でいちばん票を集めた人をホストが選ぶ */
    @Serializable @SerialName("accuse") data class Accuse(val target: String) : Intent
    /** ウルフの逆転回答の判定（ホスト） */
    @Serializable @SerialName("judge") data class Judge(val correct: Boolean) : Intent

    @Serializable @SerialName("out") data class ReportOut(val target: String) : Intent
    @Serializable @SerialName("undoOut") data class UndoOut(val target: String) : Intent
    @Serializable @SerialName("endNg") data object EndNg : Intent

    /** スパイが場所を答える（1台モードではホストが代わりに押す） */
    @Serializable @SerialName("spyGuess") data class SpyGuess(val answer: String) : Intent
    /** シンクロの回答 */
    @Serializable @SerialName("syncAnswer") data class SyncAnswer(val text: String) : Intent
    /** 質問で当てろ：当てた人（出題者かホストが押す） */
    @Serializable @SerialName("guessSolved") data class GuessSolved(val guesser: String) : Intent
    /** 質問で当てろ：降参（出題者かホスト） */
    @Serializable @SerialName("guessGiveUp") data object GuessGiveUp : Intent
    /** ぶっちゃけ質問の投票（自分にも投票できる） */
    @Serializable @SerialName("qVote") data class QVote(val target: String) : Intent

    /** なんでもアリーナ：手札を出す */
    @Serializable @SerialName("arenaPlay") data class ArenaPlay(val uid: Int, val target: String? = null) : Intent
    /** なんでもアリーナ：同時勝負の入力（ArenaGame.input を参照） */
    @Serializable @SerialName("arenaInput") data class ArenaInput(val value: String) : Intent
    /** なんでもアリーナ：場のルール違反を報告 */
    @Serializable @SerialName("arenaReport") data class ArenaReport(val target: String) : Intent

    /** お絵かき伝言：書いた文・描いた絵を出す（step は今の手番の番号。古い手番の送信は無視される） */
    @Serializable @SerialName("drawSubmit") data class DrawSubmit(val step: Int, val text: String = "", val drawing: String = "") : Intent

    /** みんなのものさし：自分のカードのヒント（場に出すまでは書き直せる） */
    @Serializable @SerialName("scaleHint") data class ScaleHint(val uid: Int, val text: String) : Intent
    /** みんなのものさし：自分の一番小さい数字を出す */
    @Serializable @SerialName("scalePlay") data object ScalePlay : Intent
    /** みんなのものさし：ヒントがそろっていなくても並べ始める（ホスト） */
    @Serializable @SerialName("scaleBegin") data object ScaleBegin : Intent

    /** トランプのゲーム：bet10 / hit / stand / double / high / low / same / swap（cards = 交換するカードの番号） / call / fold */
    @Serializable @SerialName("tableAct") data class TableAct(val act: String, val cards: List<Int> = emptyList()) : Intent

    /** やおよろずバトル：play（cards・target・opt）/ pray / defend（cards = 防具） */
    @Serializable @SerialName("yaoAct") data class YaoAct(val act: String, val cards: List<Int> = emptyList(), val target: String? = null, val opt: String = "") : Intent

    /** 管理者の操作（ホストの端末が、署名を確かめた管理者からのものだけ実行する。ゲームの進行には渡さない） */
    @Serializable @SerialName("adminOp") data class AdminOp(val op: String, val target: String = "", val text: String = "") : Intent

    @Serializable @SerialName("next") data object Next : Intent
    @Serializable @SerialName("lobby") data object BackToLobby : Intent
    @Serializable @SerialName("adDone") data class AdDone(val breakId: Int) : Intent
}

@Serializable
data class VoteCount(val id: String, val votes: Int)

@Serializable
data class ArenaCard(val uid: Int, val id: String, val suit: Suit)

@Serializable
data class ArenaSeat(
    val id: String,
    val alive: Boolean,
    val hand: Int,
    /** 前に置かれたカード（落とし穴・金貨・ばくだん・恐竜など） */
    val front: List<ArenaCard> = emptyList(),
    val points: Int = 0,
)

/** なんでもアリーナの同時勝負 */
@Serializable
data class ArenaMini(
    /** cancel / yesno / rps / fingers / vote / present / nose */
    val kind: String,
    val owner: String,
    val involved: List<String>,
    /** 入力し終えた人 */
    val done: List<String>,
    val myInput: String? = null,
    /** 勝負を起こしたカード（横やり待ちなら、打ち消されるかもしれないカード） */
    val card: String? = null,
    /** 鼻タッチ：合図ずみなら 0、まだなら null */
    val goInMs: Long? = null,
)

/** トランプ1枚。r = 2〜14（11=J 12=Q 13=K 14=A）、s = 0♠ 1♥ 2♦ 3♣ */
@Serializable
data class PCard(val r: Int, val s: Int)

/** トランプのゲームの席 */
@Serializable
data class TableSeat(
    val id: String,
    /** null = 裏向き（見えないカード） */
    val cards: List<PCard?> = emptyList(),
    /** チップ（ハイローは点数） */
    val chips: Int = 0,
    val bet: Int = 0,
    /** 今の様子（日本語の型。端末で訳す）。{0} には statusArg が入る */
    val status: String = "",
    val done: Boolean = false,
    /** 手の強さ（ポーカーの役名・ブラックジャックの合計） */
    val label: String = "",
    /** このラウンドで増えた・減ったチップ */
    val delta: Int = 0,
    val statusArg: String = "",
)

/** やおよろずバトルのカード */
@Serializable
data class YaoCard(val uid: Int, val id: String)

@Serializable
data class YaoSeat(val id: String, val hp: Int, val mp: Int, val coin: Int, val hand: Int, val alive: Boolean, val curse: Int = 0)

/** 守りを待っている攻撃 */
@Serializable
data class YaoAttackView(val by: String, val targets: List<String>, val atk: Int, val cards: List<String>, val waiting: List<String>)

/** お絵かき伝言の1コマ（文か絵） */
@Serializable
data class DrawEntry(
    val by: String,
    /** 途中で抜けた人の名前も出せるように */
    val byName: String,
    val kind: DrawKind,
    val text: String = "",
    val drawing: String = "",
)

/** シンクロで同じ答えだった人たち */
@Serializable
data class SyncGroup(val answer: String, val ids: List<String>)

@Serializable
data class RevealEntry(val id: String, val word: String, val spy: Boolean = false)

@Serializable
data class NgEntry(
    val id: String,
    /** 自分がまだアウトでないときは null（自分のNGワードは見えない） */
    val word: String?,
    val out: Boolean,
    /** アウトになった順番（1始まり）。アウトでなければ 0 */
    val outOrder: Int = 0,
)

/** 画面ごとの内容。ホストが各端末向けに作って送る */
@Serializable
sealed interface Screen {
    @Serializable @SerialName("lobby") data object Lobby : Screen

    @Serializable @SerialName("wwReveal")
    data class WwReveal(val word: String, val confirmed: List<String>, val spy: Boolean = false) : Screen

    /** 1台モードのお題確認（端末を順番に渡す） */
    @Serializable @SerialName("wwRevealLocal")
    data class WwRevealLocal(val entries: List<RevealEntry>, val confirmed: List<String>) : Screen

    @Serializable @SerialName("wwTalk")
    data class WwTalk(val word: String?, val spy: Boolean = false) : Screen

    @Serializable @SerialName("wwVote")
    data class WwVote(
        val candidates: List<String>,
        val myVote: String?,
        val voted: List<String>,
        val runoff: Boolean,
    ) : Screen

    @Serializable @SerialName("wwAccuse")
    data class WwAccuse(val candidates: List<String>) : Screen

    @Serializable @SerialName("wwGuess")
    data class WwGuess(
        val wolves: List<String>,
        val iAmWolf: Boolean,
        /** スパイモードで、スパイが選ぶ場所の候補 */
        val options: List<String> = emptyList(),
    ) : Screen

    @Serializable @SerialName("wwResult")
    data class WwResult(
        val citizenWord: String,
        val wolfWord: String,
        val wolves: List<String>,
        val accused: String?,
        val tally: List<VoteCount>,
        val winner: Team,
        val reason: ResultReason,
        val myWord: String?,
        /** スパイモードで、スパイが答えた場所 */
        val spyGuess: String? = null,
    ) : Screen

    @Serializable @SerialName("ngPlay")
    data class NgPlay(val entries: List<NgEntry>, val myOut: Boolean) : Screen

    @Serializable @SerialName("ngResult")
    data class NgResult(val entries: List<NgEntry>, val winners: List<String>) : Screen

    @Serializable @SerialName("syncAnswer")
    data class SyncAnswer(val prompt: String, val myAnswer: String?, val answered: List<String>) : Screen

    @Serializable @SerialName("syncReveal")
    data class SyncReveal(
        val prompt: String,
        val groups: List<SyncGroup>,
        val gained: List<VoteCount>,
        val scores: List<VoteCount>,
    ) : Screen

    /** 質問で当てろ。answer は出題者（1台モードでは全員分の端末）にだけ入る */
    @Serializable @SerialName("guessPlay")
    data class GuessPlay(val asker: String, val answer: String?) : Screen

    @Serializable @SerialName("guessResult")
    data class GuessResult(val answer: String, val asker: String, val guesser: String?, val scores: List<VoteCount>) : Screen

    @Serializable @SerialName("qVote")
    data class QVote(val question: String, val myVote: String?, val voted: List<String>) : Screen

    @Serializable @SerialName("qReveal")
    data class QReveal(val question: String, val tally: List<VoteCount>) : Screen

    @Serializable @SerialName("arena")
    data class Arena(
        val seats: List<ArenaSeat>,
        val turn: String,
        val deck: Int,
        val discardTop: ArenaCard?,
        /** 結果が出るまで discardTop の中身を見せない */
        val topHidden: Boolean = false,
        /** 場に残っているルール */
        val rules: List<ArenaCard>,
        val chaos: Chaos?,
        val hand: List<ArenaCard>,
        val mini: ArenaMini?,
        val log: List<String>,
        val pointGoal: Int = 15,
        /** 閉幕のベルを出した人 */
        val endOwner: String? = null,
        val noDraw: Boolean = false,
    ) : Screen

    @Serializable @SerialName("arenaResult")
    data class ArenaResult(val winners: List<String>, val seats: List<ArenaSeat>, val log: List<String>, val chaos: Chaos?) : Screen

    /** お絵かき伝言の手番。text は描くお題、drawing は当てる絵、suggestion は書くときのおまかせお題 */
    @Serializable @SerialName("drawTask")
    data class DrawTask(
        val step: Int,
        val steps: Int,
        val kind: DrawKind,
        val text: String? = null,
        val drawing: String? = null,
        val suggestion: String? = null,
        val submitted: Boolean = false,
        val done: List<String> = emptyList(),
    ) : Screen

    /** お絵かき伝言の発表。ホストが1コマずつめくる */
    @Serializable @SerialName("drawReveal")
    data class DrawReveal(
        val book: Int,
        val books: Int,
        val owner: String,
        val ownerName: String,
        val entries: List<DrawEntry>,
        val total: Int,
        val finished: Boolean,
    ) : Screen

    /** みんなのものさし。playing = false はヒントを書く時間、true は小さい順に出していく時間 */
    @Serializable @SerialName("scale")
    data class Scale(
        val topic: ScaleTopic,
        val playing: Boolean,
        val lives: Int,
        val maxLives: Int,
        val cards: List<ScaleCard>,
        /** 直前の「出す」で捨てられたカード（ミスの演出用） */
        val lastMiss: List<Int> = emptyList(),
        /** 連続クリアの回数 */
        val streak: Int = 0,
    ) : Screen

    @Serializable @SerialName("scaleResult")
    data class ScaleResult(
        val topic: ScaleTopic,
        val cleared: Boolean,
        val lives: Int,
        val maxLives: Int,
        /** 全部の数字入り、小さい順 */
        val cards: List<ScaleCard>,
        val streak: Int = 0,
    ) : Screen

    /** トランプのゲーム。stage = bet / play / swap / decide / guess / reveal / result */
    @Serializable @SerialName("table")
    data class Table(
        val game: GameType,
        val stage: String,
        /** 何ラウンド目か（ハイローは何枚目か） */
        val round: Int,
        /** ハイローの枚数。0 = 決まりなし */
        val rounds: Int = 0,
        val seats: List<TableSeat>,
        /** ブラックジャックのディーラーの手・ハイローの場のカード */
        val center: List<PCard?> = emptyList(),
        val centerLabel: String = "",
        val pot: Int = 0,
        /** 今の自分が押せる操作 */
        val actions: List<String> = emptyList(),
        val log: List<String> = emptyList(),
    ) : Screen

    /** やおよろずバトル。over = 決着（結果画面） */
    @Serializable @SerialName("yao")
    data class Yao(
        val seats: List<YaoSeat>,
        val turn: String,
        val hand: List<YaoCard>,
        val attack: YaoAttackView? = null,
        val log: List<String> = emptyList(),
        val over: Boolean = false,
        val winners: List<String> = emptyList(),
        val deck: Int = 0,
    ) : Screen

    @Serializable @SerialName("adBreak")
    data class AdBreak(val breakId: Int) : Screen
}

@Serializable
data class PlayerView(
    val me: String,
    val isHost: Boolean,
    val roomCode: String,
    val localMode: Boolean,
    val players: List<PlayerInfo>,
    val settings: Settings,
    val categories: List<String>,
    val screen: Screen,
    /** タイマーの残り（送った時点）。受け取った端末が自分の時計で数える */
    val remainingMs: Long? = null,
) {
    fun nameOf(id: String?): String = players.firstOrNull { it.id == id }?.name ?: "?"
}

/** ホスト → 参加者 */
@Serializable
sealed interface HostMsg {
    @Serializable @SerialName("view") data class ViewMsg(val view: PlayerView) : HostMsg
    @Serializable @SerialName("bye") data class Bye(val reason: String) : HostMsg
    /** 管理者からのお知らせ（全員に表示）。text は日本語の型（受け取った端末がその言語に訳す）、args は {0} などに入る値 */
    @Serializable @SerialName("notice") data class Notice(val text: String, val args: List<String> = emptyList()) : HostMsg
    /** 管理者アプリだけに送る、部屋の様子 */
    @Serializable @SerialName("adminView") data class AdminViewMsg(val room: AdminRoom) : HostMsg
}
