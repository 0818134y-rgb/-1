package com.zen.partygame.game

import kotlin.random.Random

/**
 * やおよろずバトル。八百万の神さまのご加護で、身近な物が神器になって戦うカードバトル。
 * HP・霊力・おさいせんを持ち、配られたカードだけで戦う。HP が 0 になったら「成仏」、最後に残った人の勝ち。
 * 手番の人が1回だけカードを使う（武器は上乗せカードと一緒に出せる）。攻撃された人は防具で守る。
 *
 * 画面に出す言葉（記録）は日本語の型で送り、受け取った端末がその言語に訳す。
 */
class YaoGame(
    ids: List<String>,
    private val nameOf: (String) -> String,
    private val rng: Random,
    private val now: () -> Long,
    private val turnSec: Int = 60,
) {
    enum class Step { TURN, DEFEND, OVER }

    private class Seat(val id: String) {
        var hp = START_HP
        var mp = START_MP
        var coin = START_COIN
        val hand = mutableListOf<YaoCard>()
        var curse = 0
        val alive get() = hp > 0
    }

    private class Attack(val by: String, val targets: List<String>, val atk: Int, val cards: List<String>, val halfArmor: Boolean) {
        val defended = linkedMapOf<String, List<YaoCard>>()
    }

    private val order = ids.toList()
    private val seats = linkedMapOf<String, Seat>().apply { ids.forEach { put(it, Seat(it)) } }
    private val deck = ArrayDeque<YaoCard>()
    private var uid = 0
    var turn = order.first()
        private set
    var step = Step.TURN
        private set
    var deadline: Long? = null
        private set
    private var attack: Attack? = null
    private var turns = 0
    var winners = listOf<String>()
        private set
    private val logLines = ArrayDeque<String>()
    val log: List<String> get() = logLines.toList()

    init {
        refill()
        repeat(START_HAND) { seats.values.forEach { draw(it) } }
        log("やおよろずバトル開始！ 八百万の神さまが見ています")
        beginTurn(order[rng.nextInt(order.size)], first = true)
    }

    // ================= 共通 =================

    private fun log(tpl: String, vararg args: Any?) {
        logLines.addLast(LogFmt.entry(tpl, args.toList()))
        while (logLines.size > 14) logLines.removeFirst()
    }
    private fun tx(tpl: String, vararg args: Any?) = LogFmt.entry(tpl, args.toList())
    private fun cardName(id: String) = tx(YaoCards.def(id).name)
    private fun n(id: String) = nameOf(id)
    private fun alive() = order.filter { seats[it]?.alive == true }

    private fun refill() {
        val cards = YaoCards.all.flatMap { d -> List(d.weight) { YaoCard(uid++, d.id) } }.shuffled(rng)
        deck.addAll(cards)
    }
    private fun draw(s: Seat, count: Int = 1) {
        repeat(count) {
            if (s.hand.size >= MAX_HAND) return
            if (deck.isEmpty()) refill()
            s.hand += deck.removeFirst()
        }
    }

    private fun beginTurn(id: String, first: Boolean = false) {
        turn = id; step = Step.TURN; attack = null
        val s = seats.getValue(id)
        if (!first) draw(s)
        turns++
        // 長引いたら、神さまが早く決着をつけさせる
        if (turns > SUDDEN_TURNS) {
            if (turns == SUDDEN_TURNS + 1) log("神さまがあくびをした…ここからは手番のはじめに{0}ダメージ", SUDDEN_DAMAGE)
            hurt(s, SUDDEN_DAMAGE, null)
            if (checkEnd()) return
            if (!s.alive) { nextTurn(); return }
        }
        if (s.curse > 0) {
            s.curse--
            hurt(s, CURSE_DAMAGE, null)
            log("{0}はたたられている…{1}ダメージ（あと{2}回）", n(id), CURSE_DAMAGE, s.curse)
            if (checkEnd()) return
            if (!s.alive) { nextTurn(); return }
        }
        deadline = now() + turnSec * 1000L
    }

    private fun nextTurn() {
        if (checkEnd()) return
        val i = order.indexOf(turn)
        for (k in 1..order.size) {
            val id = order[(i + k) % order.size]
            if (seats[id]?.alive == true) { beginTurn(id); return }
        }
    }

    /** ダメージ。by = 与えた人（成仏させたら香典を受け取る） */
    private fun hurt(s: Seat, dmg: Int, by: String?) {
        if (dmg <= 0 || !s.alive) return
        s.hp -= dmg
        if (s.hp <= 0) {
            s.hp = 0
            s.hand.clear(); s.curse = 0
            log("{0}は成仏した…南無", n(s.id))
            val killer = by?.let { seats[it] }
            if (killer != null && killer.alive && killer.id != s.id && s.coin > 0) {
                killer.coin += s.coin
                log("{0}は{1}の香典 {2}おさいせんを受け取った", n(killer.id), n(s.id), s.coin)
                s.coin = 0
            }
        }
    }

    private fun checkEnd(): Boolean {
        if (step == Step.OVER) return true
        val a = alive()
        if (a.size <= 1) {
            winners = a; step = Step.OVER; deadline = null; attack = null
            if (a.isEmpty()) log("全員成仏…八百万の神さまもびっくり") else log("{0}の勝ち！ 神さまに選ばれし者", n(a[0]))
            return true
        }
        return false
    }

    // ================= 操作 =================

    /**
     * 手番の人の操作。act = play（cards = 出すカード、target = 相手、opt = 両替の向き）/ pray（お祈り：1枚引いて終わる）。
     * 攻撃された人は act = defend（cards = 防具。空ならそのまま受ける）。
     * @return 状態が変わったら true
     */
    fun act(from: String, act: String, cards: List<Int>, target: String?, opt: String): Boolean {
        val s = seats[from] ?: return false
        if (!s.alive || step == Step.OVER) return false
        return when (act) {
            "pray" -> if (step == Step.TURN && from == turn) { pray(s, cards); true } else false
            "play" -> step == Step.TURN && from == turn && play(s, cards, target, opt)
            "defend" -> step == Step.DEFEND && defend(s, cards)
            else -> false
        }
    }

    /** お祈り：いらないカードを捨てて、捨てた枚数＋1枚引く */
    private fun pray(s: Seat, discard: List<Int> = emptyList()) {
        val gone = discard.distinct().mapNotNull { u -> s.hand.firstOrNull { it.uid == u } }
        s.hand.removeAll(gone)
        val before = s.hand.size
        draw(s, gone.size + 1)
        val got = s.hand.size - before
        if (gone.isEmpty()) log("{0}はお祈りした（{1}枚引いた）", n(s.id), got)
        else log("{0}は{1}枚捨ててお祈りした（{2}枚引いた）", n(s.id), gone.size, got)
        nextTurn()
    }

    private fun takeCards(s: Seat, uids: List<Int>): List<YaoCard>? {
        val picked = uids.distinct().map { u -> s.hand.firstOrNull { it.uid == u } ?: return null }
        return picked
    }

    private fun play(s: Seat, uids: List<Int>, target: String?, opt: String): Boolean {
        val cards = takeCards(s, uids) ?: return false
        if (cards.isEmpty()) return false
        val defs = cards.map { YaoCards.def(it.id) }
        val weapons = defs.filter { it.kind == YaoKind.WEAPON }
        val pluses = defs.filter { it.kind == YaoKind.PLUS }
        val t = target?.let { seats[it] }?.takeIf { it.alive && it.id != s.id }
        // 武器（＋上乗せ）
        if (weapons.size == 1 && weapons.size + pluses.size == defs.size) {
            val w = weapons[0]
            val targets = if (w.allOthers) alive().filter { it != s.id } else listOfNotNull(t?.id)
            if (targets.isEmpty()) return false
            s.hand.removeAll(cards)
            val atk = w.value + pluses.sumOf { it.value }
            val names = cards.map { it.id }
            val a = Attack(s.id, targets, atk, names, halfArmor = w.id == "harisen")
            attack = a
            val used = if (pluses.isEmpty()) cardName(w.id) else (listOf(cardName(w.id)) + pluses.map { cardName(it.id) }).joinToString("＋")
            if (w.allOthers) log("{0}の「{1}」！ ほかの全員に攻{2}", n(s.id), used, atk)
            else log("{0}が{1}に「{2}」！ 攻{3}", n(s.id), n(targets[0]), used, atk)
            step = Step.DEFEND; deadline = now() + DEFEND_SEC * 1000L
            return true
        }
        if (defs.size != 1) return false
        val d = defs[0]
        val c = cards[0]
        when (d.kind) {
            YaoKind.MIRACLE -> {
                if (s.mp < d.mp) return false
                if (d.needsTarget && t == null) return false
                s.mp -= d.mp
                s.hand.remove(c)
                miracle(s, d, t)
            }
            YaoKind.ITEM -> {
                if (d.needsTarget && t == null) return false
                if (!item(s, d, c, t, opt)) return false
            }
            else -> return false
        }
        if (!checkEnd()) nextTurn()
        return true
    }

    private fun miracle(s: Seat, d: YaoDef, t: Seat?) {
        when (d.id) {
            "kitsunebi" -> { log("{0}の「{1}」！ {2}に{3}ダメージ（防具は効かない）", n(s.id), cardName(d.id), n(t!!.id), d.value); hurt(t, d.value, s.id) }
            "onsen" -> { s.hp += d.value; log("{0}は「{1}」でHP+{2}。ごくらく", n(s.id), cardName(d.id), d.value) }
            "tatari" -> { t!!.curse = 3; log("{0}が{1}を「たたり」で呪った！ 3回の手番で{2}ダメージずつ", n(s.id), n(t.id), CURSE_DAMAGE) }
            "kamikakushi" -> {
                val gone = t!!.hand.shuffled(rng).take(2)
                t.hand.removeAll(gone)
                log("{0}の「{1}」！ {2}の手札が{3}枚消えた", n(s.id), cardName(d.id), n(t.id), gone.size)
            }
            "tenpen" -> {
                log("{0}の「{1}」！ ほかの全員に{2}ダメージ", n(s.id), cardName(d.id), d.value)
                alive().filter { it != s.id }.forEach { hurt(seats.getValue(it), d.value, s.id) }
            }
            "goraiko" -> { s.curse = 0; s.hp += d.value; log("{0}は「{1}」を拝んだ。たたりが消えてHP+{2}", n(s.id), cardName(d.id), d.value) }
            "kotodama" -> { draw(s, d.value); log("{0}の「{1}」。カードを{2}枚引いた", n(s.id), cardName(d.id), d.value) }
        }
    }

    private fun item(s: Seat, d: YaoDef, c: YaoCard, t: Seat?, opt: String): Boolean {
        when (d.id) {
            "onigiri" -> { s.hand.remove(c); s.hp += d.value; log("{0}は「{1}」を食べた。HP+{2}", n(s.id), cardName(d.id), d.value) }
            "drink" -> { s.hand.remove(c); s.mp += d.value; log("{0}は「{1}」を飲んだ。霊力+{2}", n(s.id), cardName(d.id), d.value) }
            "omikuji" -> {
                s.hand.remove(c); draw(s, 2)
                val luck = listOf("大吉", "中吉", "小吉", "末吉", "凶")[rng.nextInt(5)]
                log("{0}の「おみくじ」は{1}！ カードを2枚引いた", n(s.id), tx(luck))
            }
            "ryogae" -> {
                val (from, to) = opt.split(">").takeIf { it.size == 2 } ?: return false
                val have = when (from) { "hp" -> s.hp - 1; "mp" -> s.mp; "coin" -> s.coin; else -> return false }
                if (to !in setOf("hp", "mp", "coin") || to == from) return false
                val amt = minOf(d.value, have)
                if (amt <= 0) return false
                s.hand.remove(c)
                when (from) { "hp" -> s.hp -= amt; "mp" -> s.mp -= amt; "coin" -> s.coin -= amt }
                when (to) { "hp" -> s.hp += amt; "mp" -> s.mp += amt; "coin" -> s.coin += amt }
                log("{0}は「両替」で{1}を{2}に{3}移した", n(s.id), tx(STAT[from]!!), tx(STAT[to]!!), amt)
            }
            "dorobo" -> {
                s.hand.remove(c)
                val amt = minOf(d.value, t!!.coin)
                t.coin -= amt; s.coin += amt
                log("{0}は{1}のおさいせんを{2}くすねた。バチが当たるぞ", n(s.id), n(t.id), amt)
            }
            "saisen" -> {
                if (s.coin < d.cost) return false
                s.hand.remove(c); s.coin -= d.cost; s.hp += d.value
                log("{0}がおさいせん{1}を投げた。ご利益でHP+{2}", n(s.id), d.cost, d.value)
            }
            "kaitori" -> {
                if (s.coin < d.cost || t!!.hand.isEmpty()) return false
                s.hand.remove(c)
                val got = t.hand.random(rng)
                t.hand.remove(got); s.hand += got
                s.coin -= d.cost; t.coin += d.cost
                log("{0}は{1}から「{2}」をおさいせん{3}で買い取った", n(s.id), n(t.id), cardName(got.id), d.cost)
            }
            "oshiuri" -> {
                s.hand.remove(c)
                val rest = s.hand.toList()
                if (rest.isEmpty()) { log("{0}は押し売りしようとしたが、売る物がなかった", n(s.id)); return true }
                val sold = rest.random(rng)
                s.hand.remove(sold); t!!.hand += sold
                val amt = minOf(d.cost, t.coin)
                t.coin -= amt; s.coin += amt
                log("{0}が{1}に「{2}」を押し売り！ おさいせん{3}をせしめた", n(s.id), n(t.id), cardName(sold.id), amt)
            }
            else -> return false
        }
        return true
    }

    private fun defend(s: Seat, uids: List<Int>): Boolean {
        val a = attack ?: return false
        if (s.id !in a.targets || s.id in a.defended) return false
        val cards = takeCards(s, uids) ?: return false
        if (cards.any { YaoCards.def(it.id).kind != YaoKind.ARMOR }) return false
        a.defended[s.id] = cards
        s.hand.removeAll(cards)
        if (a.targets.all { it in a.defended || seats[it]?.alive != true }) resolveAttack()
        return true
    }

    private fun resolveAttack() {
        val a = attack ?: return
        val attacker = seats.getValue(a.by)
        for (tid in a.targets) {
            val t = seats[tid] ?: continue
            if (!t.alive) continue
            val armor = a.defended[tid].orEmpty()
            if (armor.any { it.id == "kagami" }) {
                log("{0}の「鏡もち」！ 攻{1}がそのまま{2}に跳ね返った", n(tid), a.atk, n(a.by))
                hurt(attacker, a.atk, tid)
                continue
            }
            var def = armor.sumOf { YaoCards.def(it.id).value }
            if (a.halfArmor) def /= 2
            val dmg = (a.atk - def).coerceAtLeast(0)
            if (armor.isEmpty()) log("{0}は{1}ダメージ", n(tid), dmg)
            else if (dmg == 0) log("{0}は{1}で完全に防いだ！", n(tid), LogFmt.entry(LogFmt.LIST, listOf("") + armor.map { cardName(it.id) }))
            else log("{0}は{1}で守ったが{2}ダメージ", n(tid), LogFmt.entry(LogFmt.LIST, listOf("") + armor.map { cardName(it.id) }), dmg)
            hurt(t, dmg, a.by)
        }
        attack = null
        if (!checkEnd()) nextTurn()
    }

    /** 抜けた人 */
    fun forfeit(id: String) {
        val s = seats[id] ?: return
        if (!s.alive) return
        s.hp = 0; s.hand.clear()
        log("{0}は部屋を出た", n(id))
        if (checkEnd()) return
        val a = attack
        when {
            a != null && a.by == id -> { attack = null; nextTurn() }
            a != null && a.targets.all { it in a.defended || seats[it]?.alive != true } -> resolveAttack()
            turn == id && step == Step.TURN -> nextTurn()
        }
    }

    /** 時間切れ。手番ならお祈り、守りなら防具なしで受ける。@return 状態が変わったら true */
    fun tick(): Boolean {
        val d = deadline ?: return false
        if (now() < d) return false
        when (step) {
            Step.TURN -> { seats[turn]?.let { log("{0}は時間切れ", n(it.id)); pray(it) } }
            Step.DEFEND -> {
                val a = attack ?: return false
                a.targets.filter { it !in a.defended }.forEach { a.defended[it] = emptyList() }
                resolveAttack()
            }
            Step.OVER -> { deadline = null }
        }
        return true
    }

    /** 管理者の「次へ」 */
    fun expire(): Boolean { if (step == Step.OVER) return false; deadline = now(); return tick() }

    // ================= 画面 =================

    fun view(id: String): Screen.Yao {
        val me = seats[id]
        val a = attack
        return Screen.Yao(
            seats = order.mapNotNull { seats[it] }.map { YaoSeat(it.id, it.hp, it.mp, it.coin, it.hand.size, it.alive, it.curse) },
            turn = turn,
            hand = me?.hand?.toList() ?: emptyList(),
            attack = a?.let { YaoAttackView(it.by, it.targets, it.atk, it.cards, it.targets.filter { t -> t !in it.defended && seats[t]?.alive == true }) },
            log = log,
            over = step == Step.OVER,
            winners = winners,
            deck = deck.size,
        )
    }

    companion object {
        const val START_HP = 40
        const val START_MP = 10
        const val START_COIN = 20
        const val START_HAND = 7
        const val MAX_HAND = 10
        const val CURSE_DAMAGE = 3
        const val DEFEND_SEC = 25
        const val SUDDEN_TURNS = 36
        const val SUDDEN_DAMAGE = 3
        val STAT = mapOf("hp" to "HP", "mp" to "霊力", "coin" to "おさいせん")
    }
}

/** カードの種類 */
enum class YaoKind { WEAPON, PLUS, ARMOR, MIRACLE, ITEM }

/**
 * やおよろずバトルのカード。value = 攻撃・守り・回復などの数、mp = 使う霊力、cost = 使うおさいせん、weight = 山札に入る枚数。
 * text の1文目が効果、2文目からはひとこと。
 */
data class YaoDef(
    val id: String,
    val name: String,
    val kind: YaoKind,
    val emoji: String,
    val value: Int,
    val text: String,
    val weight: Int,
    val mp: Int = 0,
    val cost: Int = 0,
    val needsTarget: Boolean = false,
    val allOthers: Boolean = false,
)

object YaoCards {
    val all = listOf(
        // ---------- 武器（相手1人を攻撃。上乗せカードと一緒に出せる） ----------
        YaoDef("negi", "長ねぎ", YaoKind.WEAPON, "🥬", 5, "攻5。ふり回すと、いい香りがする。", 8),
        YaoDef("surikogi", "すりこぎ棒", YaoKind.WEAPON, "🪵", 4, "攻4。台所の神さまの愛用品。", 8),
        YaoDef("monohoshi", "ものほし竿", YaoKind.WEAPON, "🎋", 6, "攻6。リーチの長さは正義。", 6),
        YaoDef("harisen", "ハリセン", YaoKind.WEAPON, "🪭", 4, "攻4。相手の防具を半分しか信じない。スパーン！", 4),
        YaoDef("bat", "ご神木バット", YaoKind.WEAPON, "🏏", 8, "攻8。ご神木から削り出した…らしい。", 4),
        YaoDef("otama", "伝説のおたま", YaoKind.WEAPON, "🥄", 11, "攻11。味見のしすぎで神器になった。", 1),
        YaoDef("taiko", "かみなり太鼓", YaoKind.WEAPON, "🥁", 4, "ほかの全員に攻4。ゴロゴロドーン。", 2, allOthers = true),
        YaoDef("makura", "まくら投げ", YaoKind.WEAPON, "🛏️", 2, "ほかの全員に攻2。修学旅行の夜を思い出せ。", 3, allOthers = true),
        // ---------- 上乗せ（武器と一緒に出す） ----------
        YaoDef("kiai", "気合い", YaoKind.PLUS, "💢", 2, "武器と一緒に出すと攻撃+2。叫ぶと強くなる気がする。", 6),
        YaoDef("oikaze", "追い風", YaoKind.PLUS, "🌬️", 3, "武器と一緒に出すと攻撃+3。風の神さまのきまぐれ。", 4),
        YaoDef("karashi", "からし", YaoKind.PLUS, "🌶️", 4, "武器と一緒に出すと攻撃+4。鼻にツーンとくる。", 3),
        // ---------- 防具（攻撃されたときに出す。何枚でも重ねられる） ----------
        YaoDef("danbo", "段ボール", YaoKind.ARMOR, "📦", 2, "防2。中に入ると落ち着く。", 7),
        YaoDef("nabebuta", "なべのふた", YaoKind.ARMOR, "🍳", 3, "防3。台所で最強の盾。", 8),
        YaoDef("futon", "ふとん", YaoKind.ARMOR, "🛌", 5, "防5。出たくなくなるのが難点。", 5),
        YaoDef("kotatsu", "こたつ", YaoKind.ARMOR, "🍊", 7, "防7。みかん付き。", 3),
        YaoDef("kane", "お寺の鐘", YaoKind.ARMOR, "🔔", 12, "防12。ゴーン。", 1),
        YaoDef("kagami", "鏡もち", YaoKind.ARMOR, "🪞", 0, "攻撃をまるごと相手に跳ね返す。お正月パワー。", 2),
        // ---------- 奇跡（霊力を使う。防具は効かない） ----------
        YaoDef("kitsunebi", "きつね火", YaoKind.MIRACLE, "🔥", 6, "霊力3：相手1人に6ダメージ（防具は効かない）。コンコン。", 5, mp = 3, needsTarget = true),
        YaoDef("onsen", "いやしの温泉", YaoKind.MIRACLE, "♨️", 9, "霊力2：自分のHP+9。ごくらく、ごくらく。", 5, mp = 2),
        YaoDef("tatari", "たたり", YaoKind.MIRACLE, "👻", 3, "霊力4：相手1人をたたる。その人の手番のはじめに3ダメージ×3回。うらめしや。", 3, mp = 4, needsTarget = true),
        YaoDef("kamikakushi", "神かくし", YaoKind.MIRACLE, "🌫️", 2, "霊力5：相手1人の手札をランダムに2枚消す。あれ、どこいった？", 2, mp = 5, needsTarget = true),
        YaoDef("tenpen", "天変地異", YaoKind.MIRACLE, "🌋", 8, "霊力9：ほかの全員に8ダメージ（防具は効かない）。神さま、本気出す。", 1, mp = 9),
        YaoDef("goraiko", "ご来光", YaoKind.MIRACLE, "🌅", 5, "霊力3：自分のたたりを消してHP+5。ありがたや。", 2, mp = 3),
        YaoDef("kotodama", "言霊", YaoKind.MIRACLE, "🗣️", 3, "霊力2：カードを3枚引く。言ったもん勝ち。", 3, mp = 2),
        // ---------- 道具 ----------
        YaoDef("onigiri", "おにぎり", YaoKind.ITEM, "🍙", 6, "HP+6。具は梅。", 6),
        YaoDef("drink", "栄養ドリンク", YaoKind.ITEM, "🧃", 5, "霊力+5。ファイトー！", 5),
        YaoDef("omikuji", "おみくじ", YaoKind.ITEM, "🎴", 2, "カードを2枚引く。運勢も占える。", 5),
        YaoDef("ryogae", "両替", YaoKind.ITEM, "💱", 8, "HP・霊力・おさいせんのどれかから、ほかのものへ最大8移す。", 4),
        YaoDef("dorobo", "さいせん泥棒", YaoKind.ITEM, "🦝", 8, "相手1人のおさいせんを最大8くすねる。バチ当たり。", 3, needsTarget = true),
        YaoDef("saisen", "おさいせん", YaoKind.ITEM, "⛩️", 15, "おさいせん10を払ってHP+15。ご利益はお金で買える。", 3, cost = 10),
        YaoDef("kaitori", "買い取り", YaoKind.ITEM, "🛒", 0, "おさいせん5を払い、相手1人の手札からランダムに1枚もらう。", 3, cost = 5, needsTarget = true),
        YaoDef("oshiuri", "押し売り", YaoKind.ITEM, "📣", 0, "自分の手札からランダムな1枚を相手1人に押しつけ、おさいせんを最大5もらう。", 2, cost = 5, needsTarget = true),
    )
    private val byId = all.associateBy { it.id }
    fun def(id: String): YaoDef = byId.getValue(id)
}
