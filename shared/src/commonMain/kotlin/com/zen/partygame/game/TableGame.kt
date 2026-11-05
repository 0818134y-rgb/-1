package com.zen.partygame.game

import kotlin.random.Random

/**
 * トランプのゲーム（ブラックジャック・ハイロー・ポーカー・インディアンポーカー）の進行。ホストの端末だけで動く。
 * 1つの部屋で続けて遊ぶあいだ、チップ（ハイローは点数）は持ち越す。ロビーから始め直すとリセット。
 * 全員が同時に操作する形にして、待ち時間を短くしている。
 *
 * 画面に出す言葉（status・label・記録）は日本語の型で送り、受け取った端末がその言語に訳す。
 */
class TableGame(
    val game: GameType,
    ids: List<String>,
    private val nameOf: (String) -> String,
    private val rng: Random,
    private val now: () -> Long,
) {
    enum class Stage { BET, PLAY, SWAP, DECIDE, GUESS, REVEAL, RESULT }

    private class Seat(val id: String, var chips: Int) {
        var cards = mutableListOf<PCard>()
        var bet = 0
        var done = false
        var status = ""
        var delta = 0
        /** 今のラウンドに参加しているか（途中で抜けたら false） */
        var inRound = true
        /** ハイローの予想・ポーカーの勝負/降りる */
        var choice = ""
        var doubled = false
    }

    private val order = ids.toMutableList()
    private val seats = linkedMapOf<String, Seat>().apply { ids.forEach { put(it, Seat(it, startChips(game))) } }
    private var deck = ArrayDeque<PCard>()
    private val dealer = mutableListOf<PCard>()
    private var dealerOpen = false
    private var pot = 0
    /** だれも勝負しなかったときに次へ持ち越すチップ */
    private var carry = 0
    var stage = Stage.BET
        private set
    var deadline: Long? = null
        private set
    /** 何ラウンド目か（ハイローは何枚目か） */
    var round = 0
        private set
    private var hiloCurrent: PCard? = null
    private var hiloNext: PCard? = null
    private val logLines = ArrayDeque<String>()
    val log: List<String> get() = logLines.toList()

    init { newRound() }

    // ================= 共通 =================

    private fun log(tpl: String, vararg args: Any?) {
        logLines.addLast(LogFmt.entry(tpl, args.toList()))
        while (logLines.size > 12) logLines.removeFirst()
    }
    private fun n(id: String) = nameOf(id)
    private fun active() = seats.values.filter { it.inRound }
    private fun freshDeck(decks: Int) = ArrayDeque((0 until decks).flatMap { (0..3).flatMap { s -> (2..14).map { r -> PCard(r, s) } } }.shuffled(rng))
    private fun draw(): PCard {
        if (deck.isEmpty()) deck = freshDeck(if (game == GameType.BLACKJACK) 2 else 1)
        return deck.removeFirst()
    }
    private fun setTimer(sec: Int) { deadline = now() + sec * 1000L }

    /** 次のラウンドを配る（結果画面の「もう一回」） */
    fun newRound() {
        round++
        dealer.clear(); dealerOpen = false; pot = carry; carry = 0
        for (s in seats.values) {
            s.cards.clear(); s.bet = 0; s.done = false; s.status = ""; s.delta = 0; s.choice = ""; s.doubled = false; s.inRound = true
            if (game != GameType.HILO && s.chips <= 0) {
                s.chips = PITY_CHIPS
                log("{0}におなさけチップ{1}枚。次は大事に使おう", n(s.id), PITY_CHIPS)
            }
        }
        when (game) {
            GameType.BLACKJACK -> {
                if (deck.size < 15 + seats.size * 5) deck = freshDeck(2)
                stage = Stage.BET; setTimer(BET_SEC)
            }
            GameType.HILO -> {
                deck = freshDeck(1)
                hiloCurrent = draw(); hiloNext = null; round = 1
                stage = Stage.GUESS; setTimer(GUESS_SEC)
                log("ハイロー開始！ 最初のカードは{0}", cardText(hiloCurrent!!))
            }
            GameType.POKER, GameType.INDIAN -> {
                deck = freshDeck(1)
                for (s in seats.values) { val a = minOf(ANTE, s.chips); s.chips -= a; s.bet = a; pot += a }
                if (game == GameType.POKER) {
                    repeat(5) { seats.values.forEach { s -> s.cards += draw() } }
                    seats.values.forEach { sortHand(it.cards) }
                    stage = Stage.SWAP; setTimer(SWAP_SEC)
                } else {
                    seats.values.forEach { s -> s.cards += draw() }
                    stage = Stage.DECIDE; setTimer(INDIAN_SEC)
                    log("カードをおでこに！ 自分のだけ見えません")
                }
                if (pot > ANTE * seats.size) log("持ち越しのチップ入り！ ポットは{0}枚", pot)
            }
            else -> error("not a table game: $game")
        }
    }

    private fun sortHand(c: MutableList<PCard>) = c.sortWith(compareBy<PCard> { it.r }.thenBy { it.s })

    /** 途中で抜けた人 */
    fun leave(id: String) {
        val s = seats.remove(id) ?: return
        order.remove(id)   // 払った参加料はポットに残る
        s.inRound = false
        checkStage()
    }

    /** @return 状態が変わったら true */
    fun act(from: String, act: String, cards: List<Int>): Boolean {
        val s = seats[from] ?: return false
        if (!s.inRound) return false
        val ok = when (stage) {
            Stage.BET -> bet(s, act)
            Stage.PLAY -> playBj(s, act)
            Stage.GUESS -> guess(s, act)
            Stage.SWAP -> swap(s, act, cards)
            Stage.DECIDE -> decide(s, act)
            Stage.REVEAL, Stage.RESULT -> false
        }
        if (ok) checkStage()
        return ok
    }

    /** 管理者の「次へ」：今の段階を時間切れにする */
    fun expire(): Boolean { if (stage == Stage.RESULT) return false; deadline = now(); return tick() }

    /** 時間切れ。@return 状態が変わったら true */
    fun tick(): Boolean {
        val d = deadline ?: return false
        if (now() < d) return false
        when (stage) {
            Stage.BET -> active().filter { !it.done }.forEach { bet(it, "bet${betOptions(it).first()}"); it.status = "おまかせで賭けた" }
            Stage.PLAY -> active().filter { !it.done }.forEach { it.done = true; it.status = "時間切れでスタンド" }
            Stage.GUESS -> active().filter { !it.done }.forEach { it.done = true; it.choice = ""; it.status = "考えすぎた" }
            Stage.SWAP -> active().filter { !it.done }.forEach { it.done = true; it.status = "交換なし" }
            Stage.DECIDE -> active().filter { !it.done }.forEach { it.done = true; it.choice = "fold"; it.status = "時間切れで降りた" }
            Stage.REVEAL -> { nextHilo(); return true }
            Stage.RESULT -> { deadline = null; return true }
        }
        checkStage()
        return true
    }

    /** 全員の操作がそろったら次の段階へ */
    private fun checkStage() {
        val act = active()
        if (act.isEmpty()) { stage = Stage.RESULT; deadline = null; return }
        if (act.any { !it.done }) return
        when (stage) {
            Stage.BET -> dealBj()
            Stage.PLAY -> finishBj()
            Stage.GUESS -> revealHilo()
            Stage.SWAP -> {
                act.forEach { it.done = false; it.status = "" }
                stage = Stage.DECIDE; setTimer(DECIDE_SEC)
            }
            Stage.DECIDE -> showdown()
            else -> Unit
        }
    }

    // ================= ブラックジャック =================

    private fun betOptions(s: Seat) = listOf(10, 20, 50).filter { it <= s.chips }.ifEmpty { listOf(s.chips) }

    private fun bet(s: Seat, act: String): Boolean {
        if (s.done || !act.startsWith("bet")) return false
        val amount = act.removePrefix("bet").toIntOrNull() ?: return false
        if (amount !in betOptions(s)) return false
        s.bet = amount; s.done = true; s.status = "賭けた"
        return true
    }

    private fun dealBj() {
        repeat(2) { active().forEach { it.cards += draw() }; dealer += draw() }
        for (s in active()) {
            s.done = false; s.status = ""
            if (bjTotal(s.cards) == 21) { s.done = true; s.status = "ブラックジャック！" }
        }
        if (bjTotal(dealer) == 21) {
            log("ディーラーがいきなりブラックジャック…無慈悲")
            finishBj(); return
        }
        stage = Stage.PLAY; setTimer(PLAY_SEC)
        if (active().all { it.done }) finishBj()
    }

    private fun playBj(s: Seat, act: String): Boolean {
        if (s.done) return false
        when (act) {
            "hit" -> {
                s.cards += draw()
                val t = bjTotal(s.cards)
                if (t > 21) { s.done = true; s.status = "バースト"; log("{0}がバースト！ 欲張りすぎた", n(s.id)) }
                else if (t == 21) { s.done = true; s.status = "21" }
                else s.status = "ヒット"
            }
            "stand" -> { s.done = true; s.status = "スタンド" }
            "double" -> {
                if (s.cards.size != 2 || s.chips < s.bet * 2) return false
                s.bet *= 2; s.doubled = true; s.cards += draw(); s.done = true
                s.status = if (bjTotal(s.cards) > 21) "バースト" else "ダブルダウン"
                log("{0}がダブルダウン！ 強気だ", n(s.id))
            }
            else -> return false
        }
        return true
    }

    private fun finishBj() {
        dealerOpen = true
        val dealerBj = dealer.size == 2 && bjTotal(dealer) == 21
        val anyAlive = active().any { bjTotal(it.cards) <= 21 && !isBlackjack(it.cards) }
        if (!dealerBj && anyAlive) while (bjTotal(dealer) < 17) dealer += draw()
        val dt = bjTotal(dealer)
        if (dt > 21) log("ディーラーがバースト！ 今夜は大盤振る舞い")
        else log("ディーラーは{0}", dt)
        for (s in active()) {
            val t = bjTotal(s.cards)
            val bj = isBlackjack(s.cards)
            s.delta = when {
                t > 21 -> -s.bet
                bj && dealerBj -> 0
                bj -> s.bet * 3 / 2
                dealerBj -> -s.bet
                dt > 21 || t > dt -> s.bet
                t == dt -> 0
                else -> -s.bet
            }
            s.status = when {
                t > 21 -> "バースト"
                bj && s.delta > 0 -> "ブラックジャック！"
                s.delta > 0 -> "勝ち"
                s.delta == 0 -> "引き分け"
                else -> "負けた"
            }
            s.chips += s.delta
        }
        stage = Stage.RESULT; deadline = null
    }

    // ================= ハイロー =================

    private fun guess(s: Seat, act: String): Boolean {
        if (s.done || act !in setOf("high", "low", "same")) return false
        s.choice = act; s.done = true; s.status = "予想した"
        return true
    }

    private fun revealHilo() {
        val cur = hiloCurrent!!
        val next = draw(); hiloNext = next
        val right = when {
            next.r > cur.r -> "high"
            next.r < cur.r -> "low"
            else -> "same"
        }
        var hits = 0
        for (s in active()) {
            val gain = when {
                s.choice == "" -> 0
                s.choice == right -> if (right == "same") SAME_POINTS else 1
                else -> 0
            }
            s.delta = gain; s.chips += gain
            if (gain > 0) hits++
            s.status = when (s.choice) { "high" -> "ハイ"; "low" -> "ロー"; "same" -> "同じ"; else -> "考えすぎた" }
        }
        when {
            right == "same" && hits > 0 -> log("まさかの同じ数字{0}！ 当てた人は+{1}点", cardText(next), SAME_POINTS)
            right == "same" -> log("まさかの同じ数字{0}！ 全員ハズレ", cardText(next))
            hits == 0 -> log("{0}が出た。全員ハズレ…", cardText(next))
            hits == active().size -> log("{0}が出た。全員正解！ 仲よしか", cardText(next))
            else -> log("{0}が出た", cardText(next))
        }
        stage = Stage.REVEAL; setTimer(REVEAL_SEC)
    }

    private fun nextHilo() {
        if (round >= HILO_CARDS) {
            stage = Stage.RESULT; deadline = null
            val top = active().maxOfOrNull { it.chips } ?: 0
            log("ハイロー終了！ トップは{0}点", top)
            return
        }
        round++
        hiloCurrent = hiloNext; hiloNext = null
        for (s in active()) { s.done = false; s.choice = ""; s.status = ""; s.delta = 0 }
        stage = Stage.GUESS; setTimer(GUESS_SEC)
    }

    // ================= ポーカー・インディアンポーカー =================

    private fun swap(s: Seat, act: String, idx: List<Int>): Boolean {
        if (s.done || act != "swap") return false
        val pick = idx.distinct().filter { it in s.cards.indices }
        if (pick.size > 5) return false
        pick.sortedDescending().forEach { s.cards.removeAt(it) }
        repeat(pick.size) { s.cards += draw() }
        sortHand(s.cards)
        s.done = true; s.status = if (pick.isEmpty()) "交換なし" else "{0}枚交換"
        s.choice = pick.size.toString()
        return true
    }

    private fun decide(s: Seat, act: String): Boolean {
        if (s.done || act !in setOf("call", "fold")) return false
        s.choice = act; s.done = true
        if (act == "call") { val add = minOf(CALL, s.chips); s.chips -= add; s.bet += add; pot += add; s.status = "勝負！" }
        else s.status = "降りた"
        return true
    }

    private fun showdown() {
        dealerOpen = true
        val callers = active().filter { it.choice == "call" }
        for (s in active()) s.delta = -s.bet
        when {
            callers.isEmpty() -> {
                carry = pot
                log("全員降りた…ポット{0}枚は次のラウンドへ持ち越し", pot)
            }
            callers.size == 1 -> {
                val w = callers[0]
                w.chips += pot; w.delta += pot; w.status = "不戦勝"
                log("勝負したのは{0}だけ。ポット{1}枚をいただき！", n(w.id), pot)
            }
            else -> {
                val best = callers.maxOf { strength(it.cards) }
                val winners = callers.filter { strength(it.cards) == best }
                val share = pot / winners.size
                val extra = pot - share * winners.size
                winners.forEachIndexed { i, w -> val g = share + if (i == 0) extra else 0; w.chips += g; w.delta += g; w.status = "勝ち" }
                callers.filter { it !in winners }.forEach { it.status = "負けた" }
                val what = if (game == GameType.POKER) LogFmt.entry(handName(winners[0].cards), emptyList()) else cardText(winners[0].cards[0])
                if (winners.size == 1) log("{0}が{1}で勝ち！ ポット{2}枚", n(winners[0].id), what, pot)
                else log("{0}で引き分け。ポットを山分け", what)
            }
        }
        // インディアンポーカー：いちばん強いカードで降りた人をいじる
        if (game == GameType.INDIAN) {
            val top = active().maxOfOrNull { it.cards[0].r } ?: 0
            active().filter { it.choice == "fold" && it.cards[0].r == top }.forEach {
                it.status = "最強だったのに…"
                log("{0}は最強の{1}を持ったまま降りていた…", n(it.id), cardText(it.cards[0]))
            }
        }
        pot = carry
        stage = Stage.RESULT; deadline = null
    }

    /** 手の強さ（大きいほど強い）。ポーカーは役、インディアンは数字だけ */
    private fun strength(c: List<PCard>): Long = if (game == GameType.INDIAN) c[0].r.toLong() else pokerScore(c)

    // ================= 画面 =================

    fun view(id: String): Screen.Table {
        val me = seats[id]
        val reveal = stage == Stage.RESULT
        val seatViews = order.mapNotNull { seats[it] }.map { s ->
            val showCards: List<PCard?> = when (game) {
                GameType.BLACKJACK, GameType.HILO -> s.cards
                GameType.POKER -> if (s.id == id || (reveal && s.choice == "call")) s.cards else s.cards.map { null }
                // インディアン：自分のだけ見えない（結果では全員見える）
                else -> if (s.id == id && !reveal) s.cards.map { null } else s.cards
            }
            TableSeat(
                id = s.id, cards = showCards, chips = s.chips, bet = s.bet,
                status = s.status, done = s.done,
                label = labelFor(s, s.id == id || reveal),
                delta = s.delta,
                statusArg = if (s.status == "{0}枚交換") s.choice else "",
            )
        }
        val center: List<PCard?> = when (game) {
            GameType.BLACKJACK -> if (dealerOpen) dealer else dealer.mapIndexed { i, c -> if (i == 0) c else null }
            GameType.HILO -> listOfNotNull(hiloCurrent) + (if (stage == Stage.REVEAL) listOfNotNull(hiloNext) else emptyList())
            else -> emptyList()
        }
        val centerLabel = when (game) {
            GameType.BLACKJACK -> when {
                dealer.isEmpty() -> ""
                dealerOpen -> bjLabel(dealer)
                else -> bjLabel(dealer.take(1))
            }
            else -> ""
        }
        return Screen.Table(
            game = game, stage = stage.name.lowercase(), round = round,
            rounds = if (game == GameType.HILO) HILO_CARDS else 0,
            seats = seatViews, center = center, centerLabel = centerLabel,
            pot = if (game == GameType.POKER || game == GameType.INDIAN) (if (reveal) carry else pot) else 0,
            actions = if (me != null && me.inRound && !me.done) actionsFor(me) else emptyList(),
            log = log,
        )
    }

    private fun actionsFor(s: Seat): List<String> = when (stage) {
        Stage.BET -> betOptions(s).map { "bet$it" }
        Stage.PLAY -> listOf("hit", "stand") + (if (s.cards.size == 2 && s.chips >= s.bet * 2) listOf("double") else emptyList())
        Stage.GUESS -> listOf("high", "low", "same")
        Stage.SWAP -> listOf("swap")
        Stage.DECIDE -> listOf("call", "fold")
        else -> emptyList()
    }

    private fun labelFor(s: Seat, visible: Boolean): String = if (!visible || s.cards.isEmpty()) "" else when (game) {
        GameType.BLACKJACK -> bjLabel(s.cards)
        GameType.POKER -> handName(s.cards)
        else -> ""
    }

    private fun bjLabel(c: List<PCard>): String {
        if (isBlackjack(c)) return "ブラックジャック！"
        val t = bjTotal(c)
        val soft = bjSoft(c)
        return if (soft && t < 21) "${t - 10} / $t" else t.toString()
    }

    companion object {
        const val PITY_CHIPS = 30
        const val ANTE = 10
        const val CALL = 10
        const val SAME_POINTS = 5
        const val HILO_CARDS = 10
        const val BET_SEC = 30
        const val PLAY_SEC = 45
        const val GUESS_SEC = 20
        const val REVEAL_SEC = 3
        const val SWAP_SEC = 40
        const val DECIDE_SEC = 40
        const val INDIAN_SEC = 90

        fun startChips(g: GameType) = if (g == GameType.HILO) 0 else 100
        val GAMES = setOf(GameType.BLACKJACK, GameType.HILO, GameType.POKER, GameType.INDIAN)

        private val RANKS = mapOf(11 to "J", 12 to "Q", 13 to "K", 14 to "A")
        private val SUITS = listOf("♠", "♥", "♦", "♣")
        fun rankText(r: Int) = RANKS[r] ?: r.toString()
        fun cardText(c: PCard) = SUITS[c.s] + rankText(c.r)

        /** ブラックジャックの点数（A は 11、超えたら 1） */
        fun bjTotal(c: List<PCard>): Int {
            var t = 0; var aces = 0
            for (x in c) { t += when { x.r == 14 -> { aces++; 11 }; x.r >= 10 -> 10; else -> x.r } }
            while (t > 21 && aces > 0) { t -= 10; aces-- }
            return t
        }
        /** A を 11 として数えている（ソフト）か */
        fun bjSoft(c: List<PCard>): Boolean {
            var t = 0; var aces = 0
            for (x in c) { t += when { x.r == 14 -> { aces++; 11 }; x.r >= 10 -> 10; else -> x.r } }
            while (t > 21 && aces > 0) { t -= 10; aces-- }
            return aces > 0
        }
        fun isBlackjack(c: List<PCard>) = c.size == 2 && bjTotal(c) == 21

        /** ポーカーの役の順位（0 = ハイカード … 9 = ロイヤルストレートフラッシュ） */
        fun pokerRank(c: List<PCard>): Int = (pokerScore(c) shr 40).toInt()

        /** 役と、同じ役どうしの比べ方をまとめた数（大きいほど強い） */
        fun pokerScore(c: List<PCard>): Long {
            val ranks = c.map { it.r }.sortedDescending()
            val groups = ranks.groupBy { it }.map { it.key to it.value.size }
                .sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenByDescending { it.first })
            val flush = c.size == 5 && c.map { it.s }.toSet().size == 1
            val uniq = ranks.distinct()
            val wheel = uniq == listOf(14, 5, 4, 3, 2)
            val straight = c.size == 5 && uniq.size == 5 && (uniq.first() - uniq.last() == 4 || wheel)
            val high = if (wheel) 5 else uniq.firstOrNull() ?: 0
            val counts = groups.map { it.second }
            val cat = when {
                straight && flush && high == 14 -> 9
                straight && flush -> 8
                counts.first() == 4 -> 7
                counts.first() == 3 && counts.getOrNull(1) == 2 -> 6
                flush -> 5
                straight -> 4
                counts.first() == 3 -> 3
                counts.first() == 2 && counts.getOrNull(1) == 2 -> 2
                counts.first() == 2 -> 1
                else -> 0
            }
            val kick = if (straight) listOf(high) else groups.map { it.first }
            var v = cat.toLong() shl 40
            kick.take(5).forEachIndexed { i, r -> v = v or (r.toLong() shl (32 - i * 8)) }
            return v
        }

        val HAND_NAMES = listOf("ハイカード", "ワンペア", "ツーペア", "スリーカード", "ストレート", "フラッシュ", "フルハウス", "フォーカード", "ストレートフラッシュ", "ロイヤルストレートフラッシュ")
        fun handName(c: List<PCard>) = HAND_NAMES[pokerRank(c)]
    }
}
