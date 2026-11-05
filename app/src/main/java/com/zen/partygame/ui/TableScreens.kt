package com.zen.partygame.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zen.partygame.R
import com.zen.partygame.game.GameType
import com.zen.partygame.game.Intent
import com.zen.partygame.game.PCard
import com.zen.partygame.game.PlayerView
import com.zen.partygame.game.Screen
import com.zen.partygame.game.TableSeat

// ================= トランプのゲーム（ブラックジャック・ハイロー・ポーカー・インディアンポーカー） =================

private val SUITS = listOf("♠", "♥", "♦", "♣")
private fun rankText(r: Int) = when (r) { 11 -> "J"; 12 -> "Q"; 13 -> "K"; 14 -> "A"; else -> r.toString() }
private val CardRed = Color(0xFFD6336C)
private val CardInk = Color(0xFF2B2547)

/** トランプ1枚（null は裏向き） */
@Composable
fun PlayingCard(c: PCard?, w: Dp = 46.dp, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val h = w * 1.4f
    val mod = Modifier.size(w, h).clip(RoundedCornerShape(w / 7))
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    if (c == null) {
        Image(painterResource(R.drawable.card_back), null, mod)
        return
    }
    val red = c.s == 1 || c.s == 2
    Box(
        mod.background(Color(0xFFFFFBF2))
            .border(BorderStroke(if (selected) 3.dp else 1.5.dp, if (selected) Berry else Color(0xFFCFC6B4)), RoundedCornerShape(w / 7)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(rankText(c.r), color = if (red) CardRed else CardInk, fontWeight = FontWeight.Black, fontSize = (w.value * 0.42f).sp, lineHeight = (w.value * 0.46f).sp)
            Text(SUITS[c.s], color = if (red) CardRed else CardInk, fontSize = (w.value * 0.36f).sp, lineHeight = (w.value * 0.4f).sp)
        }
    }
}

@Composable
private fun CardFan(cards: List<PCard?>, w: Dp = 38.dp, overlap: Boolean = true) {
    // 少しずつ重ねて並べる
    Box {
        cards.forEachIndexed { i, c -> Box(Modifier.padding(start = if (overlap) w * 0.62f * i else (w + 6.dp) * i)) { PlayingCard(c, w) } }
    }
}

private fun tableIcon(g: GameType) = when (g) {
    GameType.HILO -> R.drawable.icon_highlow
    GameType.POKER -> R.drawable.icon_poker
    GameType.INDIAN -> R.drawable.icon_indian
    else -> R.drawable.icon_blackjack
}

private fun statusText(s: TableSeat) = if (s.status.isEmpty()) "" else tr(s.status, s.statusArg)

private fun actionLabel(a: String): String = when {
    a.startsWith("bet") -> tr("{0}枚賭ける", a.removePrefix("bet"))
    a == "hit" -> tr("ヒット（もう1枚）")
    a == "stand" -> tr("スタンド（これで勝負）")
    a == "double" -> tr("ダブルダウン（賭け2倍で1枚だけ）")
    a == "high" -> tr("⬆ ハイ（大きい）")
    a == "low" -> tr("⬇ ロー（小さい）")
    a == "same" -> tr("＝ 同じ（+5点）")
    a == "call" -> tr("勝負する（+10枚）")
    a == "fold" -> tr("降りる")
    else -> a
}

/** その段階で何をすればいいか */
private fun stageHint(s: Screen.Table, me: TableSeat?): String = when {
    s.stage == "result" -> ""
    s.actions.isEmpty() -> tr("ほかの人を待っています…")
    s.stage == "bet" -> tr("何枚賭ける？")
    s.stage == "play" -> tr("21に近づけよう。超えたらバースト！")
    s.stage == "guess" -> tr("次のカードは、今のカードより大きい？ 小さい？")
    s.stage == "swap" -> tr("交換したいカードをタップ（何枚でも）")
    s.stage == "decide" && s.game == GameType.INDIAN -> tr("自分のカードは見えません。みんなの反応で読もう")
    s.stage == "decide" -> tr("この手で勝負する？")
    else -> ""
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TablePlay(vm: AppViewModel, v: PlayerView, s: Screen.Table, receivedAt: Long, next: @Composable () -> Unit) {
    val remaining = rememberRemainingMs(v, receivedAt)
    val me = s.seats.firstOrNull { it.id == v.me }
    val result = s.stage == "result"
    var picked by remember(s.round, s.stage) { mutableStateOf(setOf<Int>()) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(tableIcon(s.game), 44.dp); Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tr(s.game.label), style = MaterialTheme.typography.headlineSmall)
            Caption(if (s.rounds > 0) tr("{0}枚目 / {1}枚", s.round, s.rounds) else tr("第{0}ラウンド", s.round))
        }
        if (!result && v.remainingMs != null) Text(formatMs(remaining), style = MaterialTheme.typography.titleLarge,
            color = if (remaining < 10_000) Berry else Lantern)
    }

    // 場（ディーラー・ハイローのカード・ポット）
    when (s.game) {
        GameType.BLACKJACK -> if (s.center.isNotEmpty()) Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("ディーラー"), fontWeight = FontWeight.Bold)
                    if (s.centerLabel.isNotEmpty()) Text(tr(s.centerLabel), color = Lantern, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                CardFan(s.center, 46.dp)
            }
        }
        GameType.HILO -> Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                s.center.getOrNull(0)?.let { PlayingCard(it, 72.dp) }
                if (s.center.size > 1) { Text("  →  ", fontSize = 24.sp, color = Muted); PlayingCard(s.center[1], 72.dp) }
                else if (!result) { Text("  →  ", fontSize = 24.sp, color = Muted); PlayingCard(null, 72.dp) }
            }
        }
        else -> if (s.pot > 0 || !result) Row(verticalAlignment = Alignment.CenterVertically) {
            Art(R.drawable.icon_chip, 32.dp); Spacer(Modifier.size(8.dp))
            Text(if (result) tr("持ち越し {0}枚", s.pot) else tr("ポット {0}枚", s.pot), fontWeight = FontWeight.Black, color = Lantern, fontSize = 20.sp)
        }
    }

    // 席
    val seats = if (result) s.seats.sortedByDescending { it.chips } else s.seats
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        seats.forEachIndexed { i, st ->
            val mine = st.id == v.me
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (mine) Color(0xFF2C2858) else NightCard)
                    .border(BorderStroke(2.dp, if (mine) Lantern.copy(alpha = 0.6f) else Color.Transparent), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text((if (result) "${i + 1}. " else "") + v.nameOf(st.id) + (if (mine) tr("（あなた）") else ""), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val money = if (s.game == GameType.HILO) tr("{0}点", st.chips) else tr("{0}枚", st.chips) + (if (st.bet > 0 && !result) tr("（賭け {0}）", st.bet) else "")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (s.game != GameType.HILO) { Art(R.drawable.icon_chip, 16.dp); Spacer(Modifier.size(4.dp)) }
                        Text(money, fontSize = 13.sp, color = Muted)
                        if (st.delta != 0 && (result || s.stage == "reveal")) Text("  " + (if (st.delta > 0) "+" else "") + st.delta,
                            fontSize = 14.sp, fontWeight = FontWeight.Black, color = if (st.delta > 0) Mint else Berry)
                    }
                    val line = listOf(statusText(st), if (st.label.isNotEmpty()) tr(st.label) else "")
                        .filter { it.isNotEmpty() }.distinct().joinToString(" ・ ")
                    if (line.isNotEmpty()) Text(line, fontSize = 13.sp, color = if (st.delta > 0) Mint else Lantern, fontWeight = FontWeight.Bold)
                    else if (!result && st.done) Text(tr("OK"), fontSize = 12.sp, color = Mint)
                }
                if (st.cards.isNotEmpty() && !(mine && (s.game == GameType.POKER && !result))) CardFan(st.cards, if (s.game == GameType.INDIAN) 48.dp else 34.dp)
            }
        }
    }

    // 自分の手（ポーカーは大きく、交換するカードを選べる）
    if (me != null && s.game == GameType.POKER && !result) {
        Text(tr("あなたの手") + if (me.label.isNotEmpty()) "：" + tr(me.label) else "", style = MaterialTheme.typography.titleMedium, color = Lantern)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            me.cards.forEachIndexed { i, c ->
                val canPick = "swap" in s.actions
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PlayingCard(c, 56.dp, selected = i in picked, onClick = if (canPick) ({ picked = if (i in picked) picked - i else picked + i }) else null)
                    if (i in picked) Text(tr("交換"), fontSize = 11.sp, color = Berry, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (me != null && s.game == GameType.INDIAN && !result) Caption(tr("あなたのおでこのカードは、ほかの人にだけ見えています"))

    // 操作
    if (!result) {
        val hint = stageHint(s, me)
        if (hint.isNotEmpty()) Text(hint, color = if (s.actions.isNotEmpty()) Lantern else Muted, fontWeight = FontWeight.Bold)
        if ("swap" in s.actions) {
            BigButton(if (picked.isEmpty()) tr("このままでいい（交換しない）") else tr("{0}枚交換する", picked.size)) {
                vm.send(Intent.TableAct("swap", picked.toList())); picked = emptySet()
            }
        } else if (s.actions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.actions.forEach { a ->
                    val danger = a == "fold" || a == "stand"
                    Button(
                        onClick = { vm.send(Intent.TableAct(a)) },
                        modifier = Modifier.height(54.dp),
                        colors = if (danger) ButtonDefaults.buttonColors(containerColor = NightCard, contentColor = Soft) else ButtonDefaults.buttonColors(),
                    ) { Text(actionLabel(a), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    if (result) next()

    // できごと
    if (s.log.isNotEmpty()) Panel {
        Text(tr("できごと"), style = MaterialTheme.typography.titleMedium)
        s.log.takeLast(6).reversed().map { formatLog(it) }.forEachIndexed { i, line ->
            Text(line, fontSize = 13.sp, color = if (i == 0) Soft else Muted, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}
