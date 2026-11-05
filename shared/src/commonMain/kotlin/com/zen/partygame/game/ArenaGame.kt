package com.zen.partygame.game

import kotlin.random.Random

/**
 * なんでもアリーナ（理不尽カードバトル）の進行。Engine から使う。純Kotlin。
 *
 * 手番：山札から1枚引き、手札から1枚出して効果に従う。負けた人は抜ける。
 * 最後の1人になるか、カードの勝利条件を満たした人の勝ち。全員が負けることもある。
 * 同時に答える勝負（じゃんけん・指・投票など）は全員の端末で入力する。
 */
class ArenaGame(
    order: List<String>,
    private val nameOf: (String) -> String,
    private val rng: Random,
    private val now: () -> Long,
    private val turnSec: Int,
    useChaos: Boolean,
    /** テスト用：山札の並びを固定する */
    presetDeck: List<ArenaCard>? = null,
    presetChaos: Chaos? = null,
    /** テスト用：最初の手番の人 */
    presetFirst: String? = null,
) {
    enum class Step { TURN, MINI, OVER }

    class Seat(val id: String) {
        var alive = true
        val hand = mutableListOf<ArenaCard>()
        /** 自分の前に置かれたカード */
        val front = mutableListOf<ArenaCard>()
    }

    private class Mini(
        val kind: String,
        val owner: String,
        val involved: List<String>,
        /** この勝負を起こしたカード */
        val card: ArenaCard? = null,
    ) {
        val inputs = linkedMapOf<String, String>()
        /** 鼻タッチ：合図した時刻（-1 = まだ） */
        var goAt = -1L
        /** 横やり待ち：打ち消されるかもしれないカード */
        var pending: Triple<String, ArenaCard, String?>? = null
    }

    val seats = linkedMapOf<String, Seat>().apply { order.forEach { put(it, Seat(it)) } }
    private val deck = ArrayDeque<ArenaCard>()
    private val discard = mutableListOf<ArenaCard>()
    var turn: String = order.first()
        private set
    var step = Step.TURN
        private set
    /** 場に残っているルール */
    val rules = mutableListOf<ArenaCard>()
    val chaos: Chaos? = presetChaos ?: if (useChaos) Chaos.entries[rng.nextInt(Chaos.entries.size)] else null
    private var mini: Mini? = null
    /** 閉幕のベル：鳴らす人と、その人の手番があと何回終わったら鳴るか */
    var endOwner: String? = null
        private set
    private var endArmed = false
    /** だれもカードを出せずに過ぎた手番の数（全員の手札が尽きたら引き分け） */
    private var idleTurns = 0
    private val logLines = ArrayDeque<String>()
    var winners = listOf<String>()
        private set
    /** 今の手番や勝負の締め切り */
    var deadline: Long? = null
        private set

    init {
        deck.addAll(presetDeck ?: ArenaCards.buildDeck().shuffled(rng))
        val dealt = if (chaos == Chaos.RICH) 3 else 2
        repeat(dealt) { seats.values.forEach { s -> drawTo(s) } }
        chaos?.let { log("今夜のルール：{0}（{1}）", tx(it.title), tx(it.text)) }
        beginTurn(presetFirst ?: order[rng.nextInt(order.size)])
    }

    val log: List<String> get() = logLines.toList()
    /** 記録を1行足す（型と値で送り、表示する端末の言語に訳す） */
    private fun log(tpl: String, vararg args: Any?) {
        logLines.addLast(if (args.isEmpty() && LogFmt.isEntry(tpl)) tpl else tx(tpl, *args))
        while (logLines.size > 12) logLines.removeFirst()
    }
    /** 訳す言葉（ログの値に入れる） */
    private fun tx(tpl: String, vararg args: Any?) = LogFmt.entry(tpl, args.toList())
    private fun txList(head: String, items: List<String>) = LogFmt.entry(LogFmt.LIST, listOf(head) + items)
    private fun cardRule(d: CardDef) = LogFmt.entry(LogFmt.CARD_RULE + d.id, emptyList())
    private fun n(id: String?) = if (id == null) "?" else nameOf(id)
    private fun alive() = seats.values.filter { it.alive }.map { it.id }
    private fun seat(id: String) = seats.getValue(id)
    private fun def(c: ArenaCard) = ArenaCards.def(c.id)
    fun deckCount() = deck.size
    fun discardTop() = discard.lastOrNull()
    fun hand(id: String): List<ArenaCard> = seats[id]?.hand?.toList() ?: emptyList()
    val noDraw get() = rules.any { def(it).fx == Fx.RULE_NO_DRAW }
    /** 社会的なルール（違反を報告できる）があるか */
    val hasSocialRule get() = rules.any { def(it).fx == Fx.RULE_SOCIAL } || chaos?.social == true
    /** 勝つのに必要なポイント */
    val pointGoal get() = if (seats.values.any { s -> s.front.any { it.id == "vault" } }) 100 else 15
    fun points(id: String) = seat(id).front.sumOf { c -> def(c).rule.toIntOrNull()?.takeIf { def(c).fx == Fx.POINTS } ?: 0 }

    private fun drawTo(s: Seat, force: Boolean = false): Boolean {
        if (noDraw && !force) return false
        if (deck.isEmpty()) {
            if (discard.isEmpty()) return false
            deck.addAll(discard.shuffled(rng)); discard.clear()
            log("山札を切り直した")
        }
        s.hand += deck.removeFirst(); return true
    }

    // ================= 手番 =================

    private fun beginTurn(id: String) {
        turn = id
        val s = seat(id)
        step = Step.TURN
        if (noDraw) {
            if (s.hand.isEmpty()) {
                log("{0}は手札がない…", n(id))
                lose(id, tx("品切れ"))
                if (!checkEnd()) beginTurn(nextAlive(id))
                return
            }
        } else drawTo(s)
        val sec = if (chaos == Chaos.FAST) 20 else turnSec
        deadline = if (sec > 0) now() + sec * 1000L else null
    }

    private fun nextAlive(from: String): String {
        val ids = seats.keys.toList()
        var i = ids.indexOf(from)
        repeat(ids.size) {
            i = (i + 1) % ids.size
            if (seat(ids[i]).alive) return ids[i]
        }
        return from
    }

    /** 手番の終わり：前に置かれた落とし穴と、閉幕のベルを確かめてから次の人へ */
    private fun endTurn() {
        if (step == Step.OVER) return
        val s = seat(turn)
        if (s.alive) {
            val trap = s.front.firstOrNull { def(it).fx == Fx.TRAP }
            if (trap != null) {
                s.front.remove(trap); discard += trap
                log("{0}は落とし穴にすっぽり落ちた！", n(turn))
                lose(turn, tx("落とし穴"))
                if (checkEnd()) return
            }
        }
        if (endOwner == turn) {
            if (endArmed) {
                log("閉店ガラガラ！ 本日の営業は終了しました")
                loseAll(); return
            }
            endArmed = true
        }
        if (checkEnd()) return
        beginTurn(nextAlive(turn))
    }

    /** 負ける。お守りがあれば身代わり。@return 本当に負けたら true */
    private fun lose(id: String, why: String, chain: Boolean = true): Boolean {
        val s = seats[id] ?: return false
        if (!s.alive) return false
        val charm = s.hand.firstOrNull { def(it).fx == Fx.SHIELD }
        if (charm != null) {
            s.hand.remove(charm); discard += charm
            log("{0}のお守りが身代わりに砕け散った！（{1}）", n(id), why)
            return false
        }
        val next = nextAlive(id)
        out(id, tx("負け（{0}）", why))
        seats.values.filter { it.alive && it.front.any { c -> def(c).fx == Fx.LAUGH } }.forEach {
            if (drawTo(it)) log("{0}は高笑いしながら1枚引いた。性格が悪い", n(it.id))
        }
        if (chain && chaos == Chaos.SUDDEN && next != id && seat(next).alive) {
            log("一蓮托生…となりの{0}も巻きこまれる！", n(next))
            lose(next, tx("巻きぞえ"), chain = false)
        }
        return true
    }

    private fun out(id: String, why: String) {
        val s = seat(id)
        s.alive = false
        discard += s.hand; s.hand.clear()
        discard += s.front; s.front.clear()
        if (endOwner == id) { endOwner = null; endArmed = false }
        log("{0}は{1}", n(id), why)
    }

    /** 全員まとめて負け（お守りも効かない） */
    private fun loseAll() {
        alive().forEach { out(it, tx("負け")) }
        winners = emptyList(); step = Step.OVER; deadline = null; mini = null
        log("全員負け…勝者なし！")
    }

    /** 勝ち。道連れカードを持っている人がいれば、その人と一緒に負ける */
    private fun win(id: String) {
        val spite = alive().filter { it != id }.firstOrNull { o -> seat(o).hand.any { def(it).fx == Fx.SPITE } }
        if (spite != null) {
            val c = seat(spite).hand.first { def(it).fx == Fx.SPITE }
            seat(spite).hand.remove(c); discard += c
            log("{0}が勝ちかけた…が、{1}の逆恨みで台無し！", n(id), n(spite))
            out(id, tx("逆恨みで負け")); out(spite, tx("逆恨みで負け"))
            return   // 手番を進めるのは呼び出し側
        }
        winners = listOf(id); step = Step.OVER; deadline = null; mini = null
        log("{0}の勝ち！", n(id))
    }

    private fun checkEnd(): Boolean {
        if (step == Step.OVER) return true
        val a = alive()
        if (a.size <= 1) {
            winners = a; step = Step.OVER; deadline = null; mini = null
            log(if (a.isEmpty()) tx("全員負け…勝者なし！") else tx("{0}が最後の1人！", n(a[0])))
            return true
        }
        val goal = pointGoal
        a.sortedBy { if (it == turn) 0 else 1 }.firstOrNull { points(it) >= goal }?.let {
            log("{0}が{1}ポイント！", n(it), points(it)); win(it); return checkEnd()
        }
        if (seats.values.sumOf { s -> s.front.count { def(it).fx == Fx.BOMB } } >= 4) {
            log("ばくだんが4つそろった…ドカーン！ 全員アフロに")
            loseAll(); return true
        }
        return false
    }

    // ================= 操作 =================

    fun validTargets(from: String, d: CardDef): List<String> = when (d.target) {
        TargetKind.NONE -> emptyList()
        TargetKind.ALIVE_OTHER -> alive().filter { it != from }.filter { o ->
            // 恐竜の前にはカードを置けない
            !(d.placesFront && seat(o).front.any { def(it).fx == Fx.DINO })
        }
    }

    fun play(from: String, uid: Int, target: String?): Boolean {
        if (step != Step.TURN || from != turn) return false
        val s = seat(from)
        val card = s.hand.firstOrNull { it.uid == uid } ?: return false
        val d = def(card)
        val targets = validTargets(from, d)
        val t = if (d.target == TargetKind.NONE) null else target?.takeIf { it in targets }
        if (d.target != TargetKind.NONE && targets.isNotEmpty() && t == null) return false
        s.hand.remove(card)
        idleTurns = 0
        log("{0}が「{1}」を出した{2}", n(from), tx(d.name), t?.let { " → ${n(it)}" } ?: "")
        // 横やり：ほかにネコを持っている人がいれば、少しだけ待つ
        if (d.fx != Fx.CANCEL) {
            val holders = alive().filter { it != from && seat(it).hand.any { c -> def(c).fx == Fx.CANCEL } }
            if (holders.isNotEmpty()) {
                val m = Mini("cancel", from, holders, card)
                m.pending = Triple(from, card, t)
                startMini(m, 6_000); return true
            }
        }
        resolve(from, card, d, t)
        return true
    }

    /** 時間切れ：手札からランダムに出す */
    private fun autoPlay() {
        val s = seat(turn)
        if (s.hand.isEmpty()) {
            log("{0}は出せるカードがない", n(turn))
            idleTurns++
            if (idleTurns >= alive().size * 2 && seats.values.none { it.alive && it.hand.isNotEmpty() }) {
                log("だれもカードを出せない…引き分け！")
                winners = emptyList(); step = Step.OVER; deadline = null; return
            }
            endTurn(); return
        }
        val card = s.hand[rng.nextInt(s.hand.size)]
        val targets = validTargets(turn, def(card))
        log("{0}は時間切れ！ カードが勝手に飛び出した", n(turn))
        play(turn, card.uid, targets.randomOrNull(rng))
    }

    private fun place(card: ArenaCard, on: String) { seat(on).front += card }

    private fun resolve(from: String, card: ArenaCard, d: CardDef, t: String?) {
        val me = seat(from)
        var keep = false   // 場に残す（捨て札にしない）
        when (d.fx) {
            Fx.TRAP -> if (t != null) { place(card, t); keep = true; log("{0}の前に落とし穴！ 手番の終わりまでに何とかしないと…", n(t)) }
            Fx.THE_END -> { endOwner = from; endArmed = false; log("次の{0}の手番の終わりに、シャッターが下りる…", n(from)) }
            Fx.POINTS, Fx.LAUGH, Fx.DINO, Fx.BOMB -> { place(card, from); keep = true }
            Fx.RULE_SOCIAL, Fx.RULE_NO_DRAW -> { rules += card; keep = true; log("ルール追加：「{0}」", tx(d.name)) }
            Fx.SPITE, Fx.SHIELD -> log("…何も起きない")
            Fx.SWAP -> t?.let {
                val other = seat(it); val mine = me.hand.toList()
                me.hand.clear(); me.hand += other.hand; other.hand.clear(); other.hand += mine
                log("{0}と{1}の手札が入れ替わった", n(from), n(it))
            }
            Fx.CANCEL -> { repeat(2) { drawTo(me) }; log("{0}は2枚引いた", n(from)) }
            Fx.BIRTHDAY -> { discard += card; startMini(Mini("yesno", from, listOf(from), card), 20_000); return }
            Fx.RPS_ALL -> { discard += card; startMini(Mini("rps", from, alive(), card), 15_000); return }
            Fx.FINGERS -> { discard += card; startMini(Mini("fingers", from, alive(), card), 15_000); return }
            Fx.VOTE -> { discard += card; startMini(Mini("vote", from, alive(), card), 25_000); return }
            Fx.PRESENT -> {
                discard += card
                val others = alive().filter { it != from }
                startMini(Mini("present", from, others, card), 15_000); return
            }
            Fx.NOSE -> { discard += card; startMini(Mini("nose", from, alive(), card), 12_000); return }
        }
        if (!keep) discard += card
        if (checkEnd()) return
        endTurn()
    }

    private fun startMini(m: Mini, waitMs: Long) {
        mini = m; step = Step.MINI
        deadline = now() + waitMs
        log(when (m.kind) {
            "cancel" -> tx("…横やりが入るかも？")
            "yesno" -> tx("{0}は今月が誕生月？", n(m.owner))
            "rps" -> tx("全員じゃんけん！")
            "fingers" -> tx("全員、指を1〜5本！")
            "vote" -> tx("多数決！ 負けさせたい人に投票")
            "present" -> tx("プレゼント、ほしい？ いらない？")
            else -> tx("{0}の合図を待て…！", n(m.owner))
        })
    }

    /**
     * 勝負の入力。
     * cancel: use / pass、yesno: yes / no、rps: g / c / p、fingers: 1〜5、vote: プレイヤーID、
     * present: want / no、nose: 出した人は go、ほかは press:<合図からのミリ秒>
     */
    fun input(from: String, value: String): Boolean {
        val m = mini ?: return false
        if (step != Step.MINI || from !in m.involved || from in m.inputs) return false
        when (m.kind) {
            "cancel" -> if (value !in setOf("use", "pass")) return false
            "yesno" -> if (value !in setOf("yes", "no")) return false
            "rps" -> if (value !in setOf("g", "c", "p")) return false
            "fingers" -> if (value.toIntOrNull() !in 1..5) return false
            "vote" -> if (value !in alive()) return false
            "present" -> if (value !in setOf("want", "no")) return false
            "nose" -> {
                if (from == m.owner) {
                    if (value != "go" || m.goAt >= 0) return false
                    m.goAt = now(); m.inputs[from] = "go"
                    deadline = now() + NOSE_WINDOW_MS + NOSE_GRACE_MS
                    log("{0}が鼻にタッチ！", n(from))
                    return true
                }
                if (m.goAt < 0 || !value.startsWith("press")) return false
                val ms = value.substringAfter(":", "").toLongOrNull() ?: (now() - m.goAt)
                m.inputs[from] = ms.toString()
                if (m.inputs.size >= m.involved.size) finishMini()
                return true
            }
        }
        m.inputs[from] = value
        if (m.kind == "cancel" && value == "use") { finishMini(); return true }
        if (m.inputs.size >= m.involved.size) finishMini()
        return true
    }

    private fun finishMini() {
        val m = mini ?: return
        mini = null; step = Step.TURN; deadline = null
        val living = alive().toSet()
        val d = m.card?.let { def(it) }
        when (m.kind) {
            "cancel" -> {
                val (from, card, t) = m.pending!!
                val user = m.involved.firstOrNull { m.inputs[it] == "use" && seat(it).alive }
                val cat = user?.let { u -> seat(u).hand.firstOrNull { def(it).fx == Fx.CANCEL } }
                if (user != null && cat != null) {
                    seat(user).hand.remove(cat); discard += cat; discard += card
                    log("{0}のネコが横やり！「{1}」はネコパンチで消し飛んだ", n(user), tx(def(card).name))
                    drawTo(seat(user))
                    if (checkEnd()) return
                    endTurn(); return
                }
                // だれも使わなければそのまま効果が出る
                turn = from
                resolve(from, card, def(card), t.takeIf { it == null || seats[it]?.alive == true }); return
            }
            "yesno" -> {
                if (m.inputs[m.owner] == "yes") { log("今月は{0}の誕生月！ ハッピーバースデー！", n(m.owner)); win(m.owner) }
                else log("誕生月ではなかった…ケーキはおあずけ")
            }
            "rps" -> {
                val name = mapOf("g" to tx("グー"), "c" to tx("チョキ"), "p" to tx("パー"))
                val hands = m.involved.filter { it in living }.associateWith { m.inputs[it] ?: listOf("g", "c", "p").random(rng) }
                log(txList("", hands.entries.map { tx("{0}：{1}", n(it.key), name[it.value]) }))
                val rule = d!!.rule
                val mine = hands[m.owner]
                val losers = hands.filter { (_, h) -> if (rule == "diff") h != mine else h == rule }.keys
                log("ルールは…「{0}」", cardRule(d))
                if (losers.isEmpty()) log("だれも負けなかった。平和だ…")
                losers.forEach { lose(it, tx("じゃんけん")) }
            }
            "fingers" -> {
                val picks = m.involved.filter { it in living }.associateWith { m.inputs[it]?.toInt() ?: (1 + rng.nextInt(5)) }
                log(txList(tx("指："), picks.entries.map { tx("{0} {1}本", n(it.key), it.value) }))
                log("ルールは…「{0}」", cardRule(d!!))
                when (d.rule) {
                    "odd" -> picks.filter { it.value % 2 == 1 }.keys.forEach { lose(it, tx("奇数")) }
                    "even" -> picks.filter { it.value % 2 == 0 }.keys.forEach { lose(it, tx("偶数")) }
                    "dup" -> picks.filter { (id, v) -> picks.any { (o, w) -> o != id && w == v } }.keys.forEach { lose(it, tx("かぶり")) }
                    "prime" -> {
                        val sum = picks.values.sum()
                        if (isPrime(sum)) { log("合計{0} は素数！ 数学の神がほほえんだ", sum); win(m.owner) } else log("合計{0} は素数じゃなかった。数学に見放された", sum)
                    }
                }
            }
            "vote" -> {
                val tally = m.inputs.values.filter { it in living }.groupingBy { it }.eachCount()
                val max = tally.values.maxOrNull() ?: 0
                val tops = tally.filter { it.value == max }.keys
                log(txList(tx("投票："), tally.entries.sortedByDescending { it.value }.map { tx("{0} {1}票", n(it.key), it.value) }.ifEmpty { listOf(tx("なし")) }))
                if (tops.size == 1 && max > 0) lose(tops.first(), tx("多数決")) else log("同数なのでだれも負けない")
            }
            "present" -> {
                log("ルールは…「「いらない」を選んだ人は負け」")
                val no = m.involved.filter { it in living && m.inputs[it] != "want" }
                if (no.isEmpty()) log("全員ほしがった！ 物欲の勝利") else no.forEach { lose(it, if (m.inputs[it] == null) tx("答えなかった") else tx("いらない")) }
            }
            "nose" -> {
                if (m.goAt < 0) log("{0}は合図をしなかった", n(m.owner))
                else {
                    val must = d!!.rule == "must"
                    val others = m.involved.filter { it != m.owner && it in living }
                    val pressed = others.filter { id -> m.inputs[id]?.toLongOrNull()?.let { it in 0..NOSE_WINDOW_MS } == true }
                    if (must) {
                        log("ルールは…「3秒以内に押さなかった人は負け」")
                        (others - pressed.toSet()).forEach { lose(it, tx("鼻タッチに遅れた")) }
                    } else {
                        log("ルールは…「押してしまった人は負け」")
                        others.filter { it in m.inputs }.forEach { lose(it, tx("押してしまった")) }
                    }
                }
            }
        }
        if (step == Step.OVER) return
        if (checkEnd()) return
        if (!seat(turn).alive) { beginTurn(nextAlive(turn)); return }
        endTurn()
    }

    /** 途中で抜けた人 */
    fun forfeit(id: String) {
        val s = seats[id] ?: return
        if (s.alive) out(id, tx("抜けた"))
        if (checkEnd()) return
        val m = mini
        if (m != null && step == Step.MINI) {
            if (m.owner == id && m.kind in setOf("yesno", "nose") && m.goAt < 0) { mini = null; step = Step.TURN; deadline = null; beginTurn(nextAlive(turn)); return }
            val left = m.involved.filter { it != id && seat(it).alive }
            if (left.all { it in m.inputs }) finishMini()
            return
        }
        if (turn == id && step == Step.TURN) beginTurn(nextAlive(id))
    }

    /** ルール違反を報告 */
    fun report(from: String, target: String): Boolean {
        if (!hasSocialRule || step == Step.OVER) return false
        val t = seats[target] ?: return false
        if (!t.alive || from !in seats) return false
        log("{0}が{1}の違反を報告！", n(from), n(target))
        lose(target, tx("ルール違反"))
        if (checkEnd()) return true
        if (!seat(turn).alive && step == Step.TURN) beginTurn(nextAlive(turn))
        return true
    }

    /** 時間切れの処理。@return 何か変わったら true */
    fun tick(): Boolean {
        val d = deadline
        if (d == null || now() < d) return false
        if (step == Step.MINI) finishMini() else if (step == Step.TURN) autoPlay()
        return true
    }

    // ================= 表示 =================

    fun seatsView() = seats.values.map { ArenaSeat(it.id, it.alive, it.hand.size, it.front.toList(), points(it.id)) }

    fun view(id: String): Screen = if (step == Step.OVER) {
        Screen.ArenaResult(winners, seatsView(), log, chaos)
    } else {
        val m = mini
        // 結果が出るまで、出した人以外にはカードの中身を隠す
        val hidden = m != null && m.kind != "cancel" && m.card?.let { def(it).cover } != null && id != m.owner
        val pendingHidden = m != null && m.kind == "cancel" && def(m.pending!!.second).cover != null && id != m.owner
        Screen.Arena(
            seats = seatsView(), turn = turn, deck = deck.size,
            discardTop = if (m?.kind == "cancel") m.pending!!.second else discardTop(),
            topHidden = hidden || pendingHidden,
            rules = rules.toList(), chaos = chaos,
            hand = hand(id),
            mini = m?.let {
                ArenaMini(
                    kind = it.kind, owner = it.owner, involved = it.involved,
                    done = it.inputs.keys.toList(),
                    myInput = it.inputs[id],
                    card = (if (it.kind == "cancel") it.pending!!.second else it.card)?.id,
                    goInMs = if (it.kind == "nose") (if (it.goAt < 0) null else 0L) else null,
                )
            },
            log = log,
            pointGoal = pointGoal,
            endOwner = endOwner,
            noDraw = noDraw,
        )
    }

    companion object {
        const val NOSE_WINDOW_MS = 3_000L
        /** 通信の遅れを見込んだ待ち時間 */
        const val NOSE_GRACE_MS = 2_500L

        fun isPrime(n: Int): Boolean {
            if (n < 2) return false
            var i = 2
            while (i * i <= n) { if (n % i == 0) return false; i++ }
            return true
        }
    }
}
