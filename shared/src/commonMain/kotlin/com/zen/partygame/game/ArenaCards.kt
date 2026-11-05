package com.zen.partygame.game

import kotlinx.serialization.Serializable

/** カードのマーク（枠の色に使うだけ） */
@Serializable
enum class Suit(val label: String) { SUN("太陽"), MOON("月"), FLOWER("花"), LEAF("葉") }

/** カードの効果の種類 */
enum class Fx {
    /** 指名した人の前に置く。その人の手番の終わりに残っていたら負け */
    TRAP,
    /** 次の自分の手番の終わりに全員負け */
    THE_END,
    /** 自分の前に置いてポイント */
    POINTS,
    /** 全員じゃんけん（rule = 負ける手 g/c/p、または diff = 出した人と違う手） */
    RPS_ALL,
    /** 全員が指を1〜5本（rule = odd / even / dup / prime） */
    FINGERS,
    /** ルールとして残る（言葉・しぐさの禁止など。違反は報告） */
    RULE_SOCIAL,
    /** ルールとして残る：もう山札から引けない */
    RULE_NO_DRAW,
    /** 手札に持っておくと、だれかが勝ったときにその人と一緒に負ける */
    SPITE,
    /** 自分の前に置く。ほかの人が負けるたびに1枚引く */
    LAUGH,
    /** ほかの全員が「ほしい／いらない」を選ぶ（いらないは負け） */
    PRESENT,
    /** 今月が誕生月なら勝ち（自己申告） */
    BIRTHDAY,
    VOTE,
    SWAP,
    /** 手札に持っておくと、負けを1回だけ防ぐ */
    SHIELD,
    /** 自分の前に置く。4つそろったら全員負け */
    BOMB,
    /** 自分の前に置く。だれもあなたの前にカードを置けなくなる */
    DINO,
    /** 手札に持っておくと、だれかが出したカードを打ち消せる。手番に出すと2枚引く */
    CANCEL,
    /** 出した人の合図で全員が押す（rule = must：押さないと負け / mustNot：押したら負け） */
    NOSE,
}

/** だれを指名するか */
enum class TargetKind { NONE, ALIVE_OTHER }

/** 今夜のルール（ゲーム開始時に1つ追加される） */
@Serializable
enum class Chaos(val title: String, val text: String, val social: Boolean) {
    SUDDEN("一蓮托生の夜", "だれかが負けたら、その次の席の人も巻きぞえで負ける。席替えは受け付けません", false),
    YUMMY("エア食レポの夜", "手番が来たら食べ物の名前と「おいしい！」を言う。ほかの人は食べてなくても、すぐ「ほんとだ！」と返す。できなかった人は負け（違反を報告）", true),
    TABOO_WIN("勝負師お断りの夜", "「勝つ」「負ける」を含む言葉を言った人は負け。「負けないぞ！」も当然アウト（違反を報告）", true),
    TABOO_NAME("あれ・それの夜", "カードの名前を口にしてはいけない。「あれ」「例のやつ」で乗り切ろう。言った人は負け（違反を報告）", true),
    FAST("せっかちの夜", "手番は20秒。迷っていると、手札が勝手に飛び出していく", false),
    RICH("お年玉の夜", "最初の手札が3枚。太っ腹！", false),
}

data class CardDef(
    val id: String,
    val name: String,
    val fx: Fx,
    val text: String,
    val target: TargetKind = TargetKind.NONE,
    val star: Boolean = false,
    val copies: Int,
    /** 絵（res/drawable-nodpi/card_<art>.webp） */
    val art: String = id,
    /** 細かい効果（じゃんけんの負ける手など） */
    val rule: String = "",
    /** 結果が出るまで、ほかの人に見せる文（null なら隠さない） */
    val cover: String? = null,
    /** 結果が出るまで、ほかの人に見せる名前 */
    val coverName: String? = null,
) {
    val placesFront get() = fx in setOf(Fx.TRAP, Fx.POINTS, Fx.LAUGH, Fx.BOMB, Fx.DINO)
    val isRule get() = fx == Fx.RULE_SOCIAL || fx == Fx.RULE_NO_DRAW
}

/**
 * なんでもアリーナのカード。効果は昔ながらの理不尽カードゲームの定番に合わせ、名前・文章・絵はオリジナル。
 */
object ArenaCards {
    private const val FINGER_COVER = "全員せーので、指を1〜5本出す。何が起きるかは、出してからのお楽しみ…"
    private const val RPS_COVER = "全員でいっせいにじゃんけん！ 何が起きるかは、出してからのお楽しみ…"
    private const val NOSE_COVER = "出した人が合図したら、全員すぐに押す…？"
    private const val PRESENT_COVER = "ほかの全員に聞きます。「プレゼント、ほしい？ いらない？」"

    val all = listOf(
        // ---------- 通常カード ----------
        CardDef("pitfall", "落とし穴", Fx.TRAP, "指名した人の前に置く。その人の手番の終わりにまだあったら、穴にすっぽり落ちて負け。足もとに注意。", TargetKind.ALIVE_OTHER, copies = 4),
        CardDef("theend", "閉店ガラガラ", Fx.THE_END, "次のあなたの手番が終わったとき、まだ残っている全員の負け。営業時間は守りましょう。", copies = 2, art = "peek"),
        CardDef("coin", "金貨", Fx.POINTS, "自分の前に置く。5ポイント。15ポイント以上ためた人の勝ち。お金で買える勝利もある。", copies = 4, rule = "5"),
        CardDef("finger_odd", "せーのでゆび", Fx.FINGERS, "全員せーので、指を1〜5本出す。奇数を出した人は負け。割り切れない思いを胸に去れ。", copies = 1, art = "number", rule = "odd", cover = FINGER_COVER),
        CardDef("finger_even", "せーのでゆび", Fx.FINGERS, "全員せーので、指を1〜5本出す。偶数を出した人は負け。きれいに割り切れたのが運の尽き。", copies = 1, art = "number", rule = "even", cover = FINGER_COVER),
        CardDef("finger_dup", "せーのでゆび", Fx.FINGERS, "全員せーので、指を1〜5本出す。ほかの人とかぶった人は負け。人と同じじゃつまらない。", copies = 1, art = "number", rule = "dup", cover = FINGER_COVER),
        CardDef("rps_g", "全員じゃんけん", Fx.RPS_ALL, "全員でいっせいにじゃんけん。グーを出した人は負け。グーの音も出ない。", copies = 1, art = "rps", rule = "g", cover = RPS_COVER),
        CardDef("rps_c", "全員じゃんけん", Fx.RPS_ALL, "全員でいっせいにじゃんけん。チョキを出した人は負け。ピースしてる場合じゃない。", copies = 1, art = "rps", rule = "c", cover = RPS_COVER),
        CardDef("rps_p", "全員じゃんけん", Fx.RPS_ALL, "全員でいっせいにじゃんけん。パーを出した人は負け。手のひらを返される。", copies = 1, art = "rps", rule = "p", cover = RPS_COVER),
        CardDef("rps_diff", "全員じゃんけん", Fx.RPS_ALL, "全員でいっせいにじゃんけん。あなたと違う手を出した人は負け。空気を読め。", copies = 1, art = "rps", rule = "diff", cover = RPS_COVER),
        CardDef("password", "合言葉", Fx.RULE_SOCIAL, "ルールとして残る。カードを出す前に「ちくわの穴から未来が見える！」と言わなかった人は負け。", copies = 2, art = "megaphone"),
        CardDef("silence", "一人称禁止", Fx.RULE_SOCIAL, "ルールとして残る。「わたし」「ぼく」「おれ」など、自分のことを指す言葉を言った人は負け。今日から全員、名もなき存在。", copies = 2),
        CardDef("doom", "逆恨み", Fx.SPITE, "手札に持っておく。だれかが勝ったとき自動で発動し、その人とあなたがそろって負け。「おめでとう」とは言ってない。出しても何も起きない。", copies = 2),
        CardDef("laugh", "高笑い", Fx.LAUGH, "自分の前に置く。ほかの人が負けるたびに、あなたは1枚引く。人の不幸で今日もごはんがおいしい。", copies = 2),
        CardDef("present", "プレゼント", Fx.PRESENT, "ほかの全員に聞く。「プレゼント、ほしい？ いらない？」……「いらない」を選んだ人は負け。人の好意は受け取っておこう。", copies = 2, art = "lottery", cover = PRESENT_COVER),
        CardDef("birthday", "誕生月", Fx.BIRTHDAY, "今月があなたの誕生月なら、あなたの勝ち！ 主役は遅れてやってくる。（正直に答えよう）", copies = 2, art = "champion"),
        CardDef("vote", "多数決", Fx.VOTE, "全員で投票。いちばん票を集めた人は負け（同数ならだれも負けない）。人気者はつらい。", copies = 2),
        CardDef("swap", "ちょっと交換しよ？", Fx.SWAP, "指名した人と手札をすべて交換する。拒否権はない。", TargetKind.ALIVE_OTHER, copies = 2),
        CardDef("charm", "お守り", Fx.SHIELD, "手札に持っておく。あなたが負けるとき、1回だけ身代わりになって砕け散る（自動）。出しても何も起きない。ご利益は1回まで。", copies = 3),
        // ---------- スターカード（強力） ----------
        CardDef("bomb", "ばくだん", Fx.BOMB, "自分の前に置く。場のばくだんが4つになったら、全員まとめて負け。仲よく爆発オチ。", star = true, copies = 4),
        CardDef("vault", "宝くじ1等", Fx.POINTS, "自分の前に置く。90ポイント。ただしこれ以降、勝つには100ポイント必要になる。インフレには勝てない。", star = true, copies = 1, art = "rich", rule = "90"),
        CardDef("dino", "恐竜", Fx.DINO, "自分の前に置く。恐竜は大きいので、もうだれもあなたの前にカードを置けない。でかいは正義。", star = true, copies = 1),
        CardDef("finger_prime", "せーのでゆび", Fx.FINGERS, "全員せーので、指を1〜5本出す。全員の合計が素数なら、あなたの勝ち！ 数学の神は気まぐれ。", star = true, copies = 1, art = "number", rule = "prime", cover = FINGER_COVER),
        CardDef("cat", "ネコの横やり", Fx.CANCEL, "手札に持っておけば、だれかがカードを出した直後に「横やり！」で効果を打ち消せる（そのあと1枚引く）。自分の手番に出すと2枚引く。ネコは空気を読まない。", star = true, copies = 2),
        CardDef("zombie", "ゾンビ注意報", Fx.RULE_SOCIAL, "ルールとして残る。自分の手番が来たら「ゾンビだ！」と叫ぶ。叫ばなかった人は負け。ご近所への配慮は各自で。", star = true, copies = 1),
        CardDef("pointing", "指さし禁止", Fx.RULE_SOCIAL, "ルールとして残る。人や物を指さした人は負け。「あれ取って」はあごで。", star = true, copies = 1, art = "again"),
        CardDef("nose", "鼻タッチ", Fx.NOSE, "あなたが合図したら、全員すぐに押す。3秒以内に押さなかった人は負け。反射神経の見せどころ。", star = true, copies = 1, art = "press", rule = "must", cover = NOSE_COVER, coverName = "鼻タッチ"),
        CardDef("nonose", "鼻タッチ…？", Fx.NOSE, "あなたが合図したら……押してしまった人は負け！ 素直な人から消えていく。", star = true, copies = 1, art = "press", rule = "mustNot", cover = NOSE_COVER, coverName = "鼻タッチ"),
        CardDef("norefill", "品切れ御免", Fx.RULE_NO_DRAW, "ルールとして残る。もうだれも山札から引けない。手番のはじめに手札がない人は負け。入荷の予定はありません。", star = true, copies = 1, art = "limit"),
    )
    private val byId = all.associateBy { it.id }
    fun def(id: String): CardDef = byId.getValue(id)

    /** 山札を作る（同じカードはマークを順番に振る） */
    fun buildDeck(): List<ArenaCard> {
        var uid = 0
        return all.flatMap { d -> (0 until d.copies).map { i -> ArenaCard(uid++, d.id, Suit.entries[(i + d.id.length) % Suit.entries.size]) } }
    }
}
