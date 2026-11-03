package com.zen.partygame.ui

import android.os.SystemClock
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zen.partygame.R
import com.zen.partygame.game.ArenaCard
import com.zen.partygame.game.ArenaCards
import com.zen.partygame.game.ArenaGame
import com.zen.partygame.game.ArenaMini
import com.zen.partygame.game.Fx
import com.zen.partygame.game.Intent
import com.zen.partygame.game.PlayerView
import com.zen.partygame.game.Screen
import com.zen.partygame.game.Suit
import com.zen.partygame.game.TargetKind
import kotlinx.coroutines.delay

// ================= 絵の対応表（R8で消されないように静的に持つ） =================

@DrawableRes
fun cardArt(id: String): Int = when (ArenaCards.all.firstOrNull { it.id == id }?.art ?: id) {
    "pitfall" -> R.drawable.card_pitfall; "bomb" -> R.drawable.card_bomb; "charm" -> R.drawable.card_charm
    "coin" -> R.drawable.card_coin; "rps" -> R.drawable.card_rps; "press" -> R.drawable.card_press
    "vote" -> R.drawable.card_vote; "number" -> R.drawable.card_number; "swap" -> R.drawable.card_swap
    "limit" -> R.drawable.card_limit; "silence" -> R.drawable.card_silence; "laugh" -> R.drawable.card_laugh
    "rich" -> R.drawable.card_rich; "doom" -> R.drawable.card_doom; "again" -> R.drawable.card_again
    "lottery" -> R.drawable.card_lottery; "champion" -> R.drawable.card_champion; "peek" -> R.drawable.card_peek
    "megaphone" -> R.drawable.card_megaphone; "dino" -> R.drawable.card_dino; "cat" -> R.drawable.card_cat
    "zombie" -> R.drawable.card_zombie
    else -> R.drawable.card_back
}

@DrawableRes
fun suitArt(s: Suit): Int = when (s) {
    Suit.SUN -> R.drawable.suit_sun; Suit.MOON -> R.drawable.suit_moon
    Suit.FLOWER -> R.drawable.suit_flower; Suit.LEAF -> R.drawable.suit_leaf
}

fun suitColor(s: Suit): Color = when (s) {
    Suit.SUN -> Lantern; Suit.MOON -> Color(0xFF8E86B8); Suit.FLOWER -> Berry; Suit.LEAF -> Mint
}

// ================= カード =================

/** @param hidden 結果が出るまで中身を伏せる（ほかの人が出した「全員じゃんけん」など） */
@Composable
fun ArenaCardView(card: ArenaCard, selected: Boolean = false, small: Boolean = false, hidden: Boolean = false, onClick: (() -> Unit)? = null) {
    val def = ArenaCards.def(card.id)
    val c = suitColor(card.suit)
    val w = if (small) 104.dp else 150.dp
    Column(
        Modifier
            .width(w)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF6EBD9))
            .border(BorderStroke(if (selected) 4.dp else 3.dp, if (selected) Berry else c), RoundedCornerShape(14.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(suitArt(card.suit)), null, Modifier.size(if (small) 16.dp else 20.dp))
            Spacer(Modifier.weight(1f))
            if (def.star) Text("★", color = Berry, fontWeight = FontWeight.Black, fontSize = if (small) 13.sp else 16.sp)
        }
        Image(painterResource(cardArt(card.id)), null, Modifier.size(if (small) 56.dp else 84.dp), contentScale = ContentScale.Fit)
        Text(if (hidden) def.coverName ?: def.name else def.name, color = Night, fontWeight = FontWeight.Black,
            fontSize = if (small) 12.sp else 15.sp, textAlign = TextAlign.Center, maxLines = 1)
        if (!small) {
            Spacer(Modifier.height(4.dp))
            Text(if (hidden) def.cover ?: def.text else def.text, color = Color(0xFF3B3560), fontSize = 11.sp, lineHeight = 14.sp,
                textAlign = TextAlign.Center, minLines = 4, maxLines = 7)
        }
    }
}

/** 席の前に置かれたカードを小さな絵で並べる */
@Composable
private fun FrontIcons(front: List<ArenaCard>) {
    if (front.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        front.forEach { Image(painterResource(cardArt(it.id)), ArenaCards.def(it.id).name, Modifier.size(22.dp)) }
    }
}

// ================= 対戦画面 =================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArenaPlay(vm: AppViewModel, v: PlayerView, s: Screen.Arena, receivedAt: Long) {
    val remaining = rememberRemainingMs(v, receivedAt)
    val me = s.seats.firstOrNull { it.id == v.me }
    val myTurn = s.turn == v.me && s.mini == null
    var selected by remember(s.hand.map { it.uid }, s.turn) { mutableStateOf<Int?>(null) }
    var target by remember(selected) { mutableStateOf<String?>(null) }
    var reportAsk by remember { mutableStateOf<String?>(null) }
    val socialRules = s.rules.filter { ArenaCards.def(it.id).fx == Fx.RULE_SOCIAL }
    val canReport = socialRules.isNotEmpty() || s.chaos?.social == true

    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(R.drawable.icon_arena, 44.dp); Spacer(Modifier.size(10.dp))
        Text("なんでもアリーナ", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        if (s.mini == null && v.remainingMs != null) Text(formatMs(remaining), style = MaterialTheme.typography.titleLarge,
            color = if (remaining < 10_000) Berry else Lantern)
    }
    s.chaos?.let { ch ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2858)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌙", fontSize = 18.sp); Spacer(Modifier.size(8.dp))
            Column { Text("今夜のルール：${ch.title}", fontWeight = FontWeight.Bold, color = Lantern); Caption(ch.text) }
        }
    }

    // 席（全員の様子）
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        s.seats.forEach { st ->
            val isTurn = st.id == s.turn && st.alive
            Column(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(if (isTurn) Color(0xFF3A2E14) else NightCard)
                    .border(BorderStroke(2.dp, if (isTurn) Lantern else Color.Transparent), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(v.nameOf(st.id) + if (st.id == v.me) "（あなた）" else "", fontWeight = FontWeight.Bold,
                    color = if (st.alive) Soft else Muted, fontSize = 14.sp)
                Text(
                    if (!st.alive) "負け" else "手札${st.hand}枚" + (if (st.points > 0) "・${st.points}pt" else ""),
                    fontSize = 12.sp, color = if (st.alive) Muted else Berry,
                )
                FrontIcons(st.front)
                if (canReport && st.alive && st.id != v.me) {
                    Text("違反！", color = Berry, fontWeight = FontWeight.Black, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp).clickable { reportAsk = st.id })
                }
            }
        }
    }

    // 場
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.card_back), null, Modifier.size(width = 64.dp, height = 88.dp))
            Caption(if (s.noDraw) "引けない！" else "山札 ${s.deck}枚")
        }
        s.discardTop?.let { Column(horizontalAlignment = Alignment.CenterHorizontally) { ArenaCardView(it, small = true, hidden = s.topHidden); Caption("さっき出た") } }
        if (s.rules.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(s.rules, key = { it.uid }) { Column(horizontalAlignment = Alignment.CenterHorizontally) { ArenaCardView(it, small = true); Caption("ルール") } }
        }
    }
    if (s.rules.isNotEmpty()) Caption("ルール：" + s.rules.joinToString("　") { "「${ArenaCards.def(it.id).name}」${ArenaCards.def(it.id).text.substringAfter("。")}" })
    if (s.endOwner != null) Text("🔔 ${v.nameOf(s.endOwner)}さんの次の手番が終わると、全員の負け！", color = Berry, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    if (s.pointGoal != 15) Caption("大金庫が出たので、勝つには${s.pointGoal}ポイント必要")

    // 同時勝負
    s.mini?.let { MiniPanel(vm, v, s, it, receivedAt, remaining) }

    // 自分の手札
    when {
        me != null && !me.alive -> Panel { Text("あなたの負けです", color = Berry, fontWeight = FontWeight.Bold); Caption("最後まで見守りましょう。違反の報告はできます") }
        myTurn -> Text("あなたの番！ カードを1枚選んで出そう", color = Lantern, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        s.mini == null -> Caption("${v.nameOf(s.turn)}さんの番です", Modifier.fillMaxWidth())
    }
    if (s.hand.isNotEmpty()) {
        Text("あなたの手札（${s.hand.size}枚）", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(s.hand, key = { it.uid }) { c -> ArenaCardView(c, selected = selected == c.uid) { selected = if (selected == c.uid) null else c.uid } }
        }
    }
    val sel = s.hand.firstOrNull { it.uid == selected }
    if (myTurn && sel != null) {
        val def = ArenaCards.def(sel.id)
        val targets = when (def.target) {
            TargetKind.NONE -> emptyList()
            TargetKind.ALIVE_OTHER -> s.seats.filter { st ->
                st.alive && st.id != v.me && !(def.placesFront && st.front.any { ArenaCards.def(it.id).fx == Fx.DINO })
            }.map { it.id }
        }
        if (targets.isNotEmpty()) {
            Text("だれに？", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                targets.forEach { t -> FilterChip(target == t, { target = t }, { Text(v.nameOf(t)) }) }
            }
        } else if (def.target != TargetKind.NONE) Caption("指名できる人がいないので、出しても何も起きません")
        BigButton("「${def.name}」を出す", enabled = targets.isEmpty() || target != null) {
            vm.send(Intent.ArenaPlay(sel.uid, target)); selected = null
        }
    }

    // できごと
    Panel {
        Text("できごと", style = MaterialTheme.typography.titleMedium)
        s.log.takeLast(6).reversed().forEachIndexed { i, line ->
            Text(line, fontSize = 13.sp, color = if (i == 0) Soft else Muted, modifier = Modifier.padding(vertical = 2.dp))
        }
    }

    reportAsk?.let { id ->
        val names = socialRules.map { "「${ArenaCards.def(it.id).name}」" } + listOfNotNull(s.chaos?.takeIf { it.social }?.let { "今夜のルール「${it.title}」" })
        AlertDialog(
            onDismissRequest = { reportAsk = null },
            title = { Text("${v.nameOf(id)}さんがルール違反？") },
            text = { Text("${names.joinToString("・")}を破りましたか？ 報告するとその人は負けになります（お守りがあれば守られます）") },
            confirmButton = { TextButton(onClick = { vm.send(Intent.ArenaReport(id)); reportAsk = null }) { Text("違反！") } },
            dismissButton = { TextButton(onClick = { reportAsk = null }) { Text("やめる") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MiniPanel(vm: AppViewModel, v: PlayerView, s: Screen.Arena, m: ArenaMini, receivedAt: Long, remaining: Long) {
    val inIt = v.me in m.involved
    val doneMe = m.myInput != null
    val isOwner = v.me == m.owner
    val def = m.card?.let { ArenaCards.def(it) }
    fun send(x: String) = vm.send(Intent.ArenaInput(x))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF2C2858))
            .border(BorderStroke(3.dp, Lantern), RoundedCornerShape(20.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (def != null) Art(cardArt(def.id), 64.dp)
        // 出した人には本当の文、ほかの人には伏せた文
        val title = when (m.kind) {
            "cancel" -> "「${(if (s.topHidden) def?.coverName ?: def?.name else def?.name) ?: ""}」が出された！"
            else -> if (isOwner || def?.cover == null) def?.text ?: "" else def.cover ?: ""
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = Lantern, textAlign = TextAlign.Center)
        when (m.kind) {
            "cancel" -> if (inIt && !doneMe) {
                Caption("あなたはネコを持っています。横やりで打ち消しますか？")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { send("use") }, Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Berry)) { Text("横やり！") }
                    Button(onClick = { send("pass") }, Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = NightCard, contentColor = Soft)) { Text("見送る") }
                }
            } else Caption("…ちょっと待って。横やりが入るかも？")
            "yesno" -> if (isOwner && !doneMe) {
                Text("今月はあなたの誕生月？", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { send("yes") }, Modifier.weight(1f).height(56.dp)) { Text("はい") }
                    Button(onClick = { send("no") }, Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = NightCard, contentColor = Soft)) { Text("いいえ") }
                }
            } else Caption("${v.nameOf(m.owner)}さんが答えています…")
            "rps" -> if (inIt && !doneMe) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Triple("g", "✊", "グー"), Triple("c", "✌", "チョキ"), Triple("p", "✋", "パー")).forEach { (k, e, l) ->
                    Button(onClick = { send(k) }, Modifier.weight(1f).height(76.dp), contentPadding = PaddingValues(4.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(e, fontSize = 24.sp); Text(l, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            } else Caption(if (inIt) "出しました。みんなを待っています…" else "じゃんけん中…", Modifier.fillMaxWidth())
            "fingers" -> if (inIt && !doneMe) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { n ->
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(NightCard).clickable { send(n.toString()) },
                        contentAlignment = Alignment.Center,
                    ) { Text("$n", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Soft) }
                }
            } else Caption(if (inIt) "${m.myInput}本を出しました。みんなを待っています…" else "みんなが選んでいます…")
            "vote" -> if (inIt && !doneMe) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.seats.filter { it.alive }.forEach { st ->
                    FilterChip(false, { send(st.id) }, { Text(v.nameOf(st.id) + if (st.id == v.me) "（自分）" else "") })
                }
            } else Caption(if (inIt) "${v.nameOf(m.myInput)}さんに投票しました" else "投票中…")
            "present" -> if (inIt && !doneMe) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { send("want") }, Modifier.weight(1f).height(56.dp)) { Text("ほしい！") }
                Button(onClick = { send("no") }, Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = NightCard, contentColor = Soft)) { Text("いらない") }
            } else Caption(if (inIt) "「${if (m.myInput == "want") "ほしい" else "いらない"}」と答えました" else "みんなの答えを待っています…")
            "nose" -> NosePanel(v, m, receivedAt, isOwner, ::send)
        }
        if (m.kind != "nose" && v.remainingMs != null) Caption("残り ${formatMs(remaining)}　答えた人 ${m.done.size} / ${m.involved.size}人")
    }
}

/** 鼻タッチ：出した人が合図 → ほかの人は自分の画面に合図が出てから何ミリ秒で押したかを送る */
@Composable
private fun NosePanel(v: PlayerView, m: ArenaMini, receivedAt: Long, isOwner: Boolean, send: (String) -> Unit) {
    val go = m.goInMs != null
    // 合図が届いた時刻（この画面で最初に合図を見たとき）
    val goSeenAt = remember(m.owner, go) { if (go) receivedAt else 0L }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(go) { while (go) { now = SystemClock.elapsedRealtime(); delay(50) } }
    val pressed = m.myInput != null
    when {
        isOwner && !go -> {
            Caption("好きなタイミングで合図しよう。合図は全員の画面に出ます")
            Box(
                Modifier.size(150.dp).clip(CircleShape).background(Berry).border(BorderStroke(6.dp, Lantern), CircleShape)
                    .clickable { send("go") },
                contentAlignment = Alignment.Center,
            ) { Text("合図！", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Soft) }
        }
        isOwner -> Caption("合図しました！ みんなの反応を待っています…（${m.done.size - 1} / ${m.involved.size - 1}人）")
        !go -> Text("${v.nameOf(m.owner)}さんの合図を待て…", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Muted)
        else -> {
            val left = (ArenaGame.NOSE_WINDOW_MS - (now - goSeenAt)).coerceAtLeast(0)
            Text(if (pressed) "押した！" else "押せ！！", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Berry)
            Box(
                Modifier.size(150.dp).clip(CircleShape)
                    .background(if (pressed) NightCard else Berry)
                    .border(BorderStroke(6.dp, Lantern), CircleShape)
                    .let { if (!pressed && v.me in m.involved) it.clickable { send("press:${SystemClock.elapsedRealtime() - goSeenAt}") } else it },
                contentAlignment = Alignment.Center,
            ) { Text(if (pressed) "✓" else "PUSH", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Soft) }
            if (!pressed) Caption("あと ${"%.1f".format(left / 1000.0)} 秒")
        }
    }
}

// ================= 結果 =================

@Composable
fun ArenaResult(vm: AppViewModel, v: PlayerView, s: Screen.ArenaResult, next: @Composable () -> Unit) {
    Image(painterResource(R.drawable.arena_hero), null, Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
    BigWord(if (s.winners.isEmpty()) "勝者なし！" else s.winners.joinToString("、") { v.nameOf(it) } + "の勝ち！", Lantern)
    Panel {
        Text("最終結果", style = MaterialTheme.typography.titleMedium)
        s.seats.sortedByDescending { it.id in s.winners }.forEach { st ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(v.nameOf(st.id), modifier = Modifier.weight(1f))
                Text(if (st.id in s.winners) "👑 優勝" else if (st.alive) "生き残り" else "負け",
                    color = if (st.id in s.winners) Lantern else if (st.alive) Mint else Muted, fontWeight = FontWeight.Bold)
            }
        }
    }
    Panel {
        Text("最後のできごと", style = MaterialTheme.typography.titleMedium)
        s.log.takeLast(6).forEach { Text(it, fontSize = 13.sp, color = Muted) }
    }
    next()
}
