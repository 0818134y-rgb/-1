package com.zen.partygame.ui

import com.zen.partygame.game.afterFirstSentence
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
        Text(tr(if (hidden) def.coverName ?: def.name else def.name), color = Night, fontWeight = FontWeight.Black,
            fontSize = if (small) 12.sp else 15.sp, textAlign = TextAlign.Center, maxLines = 1)
        if (!small) {
            Spacer(Modifier.height(4.dp))
            Text(tr(if (hidden) def.cover ?: def.text else def.text), color = Color(0xFF3B3560), fontSize = 11.sp, lineHeight = 14.sp,
                textAlign = TextAlign.Center, minLines = 4, maxLines = 7)
        }
    }
}

/** 席の前に置かれたカードを小さな絵で並べる */
@Composable
private fun FrontIcons(front: List<ArenaCard>) {
    if (front.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        front.forEach { Image(painterResource(cardArt(it.id)), tr(ArenaCards.def(it.id).name), Modifier.size(22.dp)) }
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
        Text(tr("なんでもアリーナ"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        if (s.mini == null && v.remainingMs != null) Text(formatMs(remaining), style = MaterialTheme.typography.titleLarge,
            color = if (remaining < 10_000) Berry else Lantern)
    }
    s.chaos?.let { ch ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2858)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌙", fontSize = 18.sp); Spacer(Modifier.size(8.dp))
            Column { Text(tr("今夜のルール：{0}", tr(ch.title)), fontWeight = FontWeight.Bold, color = Lantern); Caption(tr(ch.text)) }
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
                Text(v.nameOf(st.id) + if (st.id == v.me) tr("（あなた）") else "", fontWeight = FontWeight.Bold,
                    color = if (st.alive) Soft else Muted, fontSize = 14.sp)
                Text(
                    if (!st.alive) tr("負け") else tr("手札{0}枚", st.hand) + (if (st.points > 0) "・${st.points}pt" else ""),
                    fontSize = 12.sp, color = if (st.alive) Muted else Berry,
                )
                FrontIcons(st.front)
                if (canReport && st.alive && st.id != v.me) {
                    Text(tr("違反！"), color = Berry, fontWeight = FontWeight.Black, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp).clickable { reportAsk = st.id })
                }
            }
        }
    }

    // 場
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.card_back), null, Modifier.size(width = 64.dp, height = 88.dp))
            Caption(if (s.noDraw) tr("引けない！") else tr("山札 {0}枚", s.deck))
        }
        s.discardTop?.let { Column(horizontalAlignment = Alignment.CenterHorizontally) { ArenaCardView(it, small = true, hidden = s.topHidden); Caption(tr("さっき出た")) } }
        if (s.rules.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(s.rules, key = { it.uid }) { Column(horizontalAlignment = Alignment.CenterHorizontally) { ArenaCardView(it, small = true); Caption(tr("ルール")) } }
        }
    }
    if (s.rules.isNotEmpty()) Caption(tr("ルール：") + s.rules.joinToString("　") { tr("「{0}」{1}", tr(ArenaCards.def(it.id).name), afterFirstSentence(tr(ArenaCards.def(it.id).text))) })
    if (s.endOwner != null) Text(tr("🔔 {0}さんの次の手番が終わると、全員の負け！", v.nameOf(s.endOwner)), color = Berry, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    if (s.pointGoal != 15) Caption(tr("大金庫が出たので、勝つには{0}ポイント必要", s.pointGoal))

    // 同時勝負
    s.mini?.let { MiniPanel(vm, v, s, it, receivedAt, remaining) }

    // 自分の手札
    when {
        me != null && !me.alive -> Panel { Text(tr("あなたの負けです"), color = Berry, fontWeight = FontWeight.Bold); Caption(tr("最後まで見守りましょう。違反の報告はできます")) }
        myTurn -> Text(tr("あなたの番！ カードを1枚選んで出そう"), color = Lantern, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        s.mini == null -> Caption(tr("{0}さんの番です", v.nameOf(s.turn)), Modifier.fillMaxWidth())
    }
    if (s.hand.isNotEmpty()) {
        Text(tr("あなたの手札（{0}枚）", s.hand.size), style = MaterialTheme.typography.titleMedium)
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
            Text(tr("だれに？"), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                targets.forEach { t -> FilterChip(target == t, { target = t }, { Text(v.nameOf(t)) }) }
            }
        } else if (def.target != TargetKind.NONE) Caption(tr("指名できる人がいないので、出しても何も起きません"))
        BigButton(tr("「{0}」を出す", tr(def.name)), enabled = targets.isEmpty() || target != null) {
            vm.send(Intent.ArenaPlay(sel.uid, target)); selected = null
        }
    }

    // できごと
    Panel {
        Text(tr("できごと"), style = MaterialTheme.typography.titleMedium)
        s.log.takeLast(6).reversed().map { formatLog(it) }.forEachIndexed { i, line ->
            Text(line, fontSize = 13.sp, color = if (i == 0) Soft else Muted, modifier = Modifier.padding(vertical = 2.dp))
        }
    }

    reportAsk?.let { id ->
        val names = socialRules.map { tr("「{0}」", tr(ArenaCards.def(it.id).name)) } + listOfNotNull(s.chaos?.takeIf { it.social }?.let { tr("今夜のルール「{0}」", tr(it.title)) })
        AlertDialog(
            onDismissRequest = { reportAsk = null },
            title = { Text(tr("{0}さんがルール違反？", v.nameOf(id))) },
            text = { Text(tr("{0}を破りましたか？ 報告するとその人は負けになります（お守りがあれば守られます）", names.joinToString("・"))) },
            confirmButton = { TextButton(onClick = { vm.send(Intent.ArenaReport(id)); reportAsk = null }) { Text(tr("違反！")) } },
            dismissButton = { TextButton(onClick = { reportAsk = null }) { Text(tr("やめる")) } },
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
            "cancel" -> tr("「{0}」が出された！", tr((if (s.topHidden) def?.coverName ?: def?.name else def?.name) ?: ""))
            else -> tr(if (isOwner || def?.cover == null) def?.text ?: "" else def.cover ?: "")
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = Lantern, textAlign = TextAlign.Center)
        when (m.kind) {
            "cancel" -> if (inIt && !doneMe) {
                Caption(tr("あなたはネコを持っています。横やりで打ち消しますか？"))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { send("use") }, Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Berry)) { Text(tr("横やり！")) }
                    Button(onClick = { send("pass") }, Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = NightCard, contentColor = Soft)) { Text(tr("見送る")) }
                }
            } else Caption(tr("…ちょっと待って。横やりが入るかも？"))
            "yesno" -> if (isOwner && !doneMe) {
                Text(tr("今月はあなたの誕生月？"), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { send("yes") }, Modifier.weight(1f).height(56.dp)) { Text(tr("はい")) }
                    Button(onClick = { send("no") }, Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = NightCard, contentColor = Soft)) { Text(tr("いいえ")) }
                }
            } else Caption(tr("{0}さんが答えています…", v.nameOf(m.owner)))
            "rps" -> if (inIt && !doneMe) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Triple("g", "✊", tr("グー")), Triple("c", "✌", tr("チョキ")), Triple("p", "✋", tr("パー"))).forEach { (k, e, l) ->
                    Button(onClick = { send(k) }, Modifier.weight(1f).height(76.dp), contentPadding = PaddingValues(4.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(e, fontSize = 24.sp); Text(l, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            } else Caption(if (inIt) tr("出しました。みんなを待っています…") else tr("じゃんけん中…"), Modifier.fillMaxWidth())
            "fingers" -> if (inIt && !doneMe) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { n ->
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(NightCard).clickable { send(n.toString()) },
                        contentAlignment = Alignment.Center,
                    ) { Text("${n}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Soft) }
                }
            } else Caption(if (inIt) tr("{0}本を出しました。みんなを待っています…", m.myInput) else tr("みんなが選んでいます…"))
            "vote" -> if (inIt && !doneMe) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.seats.filter { it.alive }.forEach { st ->
                    FilterChip(false, { send(st.id) }, { Text(v.nameOf(st.id) + if (st.id == v.me) tr("（自分）") else "") })
                }
            } else Caption(if (inIt) tr("{0}さんに投票しました", v.nameOf(m.myInput)) else tr("投票中…"))
            "present" -> if (inIt && !doneMe) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { send("want") }, Modifier.weight(1f).height(56.dp)) { Text(tr("ほしい！")) }
                Button(onClick = { send("no") }, Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = NightCard, contentColor = Soft)) { Text(tr("いらない")) }
            } else Caption(if (inIt) tr("「{0}」と答えました", if (m.myInput == "want") tr("ほしい") else tr("いらない")) else tr("みんなの答えを待っています…"))
            "nose" -> NosePanel(v, m, receivedAt, isOwner, ::send)
        }
        if (m.kind != "nose" && v.remainingMs != null) Caption(tr("残り {0}　答えた人 {1} / {2}人", formatMs(remaining), m.done.size, m.involved.size))
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
            Caption(tr("好きなタイミングで合図しよう。合図は全員の画面に出ます"))
            Box(
                Modifier.size(150.dp).clip(CircleShape).background(Berry).border(BorderStroke(6.dp, Lantern), CircleShape)
                    .clickable { send("go") },
                contentAlignment = Alignment.Center,
            ) { Text(tr("合図！"), fontSize = 30.sp, fontWeight = FontWeight.Black, color = Soft) }
        }
        isOwner -> Caption(tr("合図しました！ みんなの反応を待っています…（{0} / {1}人）", m.done.size - 1, m.involved.size - 1))
        !go -> Text(tr("{0}さんの合図を待て…", v.nameOf(m.owner)), fontSize = 22.sp, fontWeight = FontWeight.Black, color = Muted)
        else -> {
            val left = (ArenaGame.NOSE_WINDOW_MS - (now - goSeenAt)).coerceAtLeast(0)
            Text(if (pressed) tr("押した！") else tr("押せ！！"), fontSize = 32.sp, fontWeight = FontWeight.Black, color = Berry)
            Box(
                Modifier.size(150.dp).clip(CircleShape)
                    .background(if (pressed) NightCard else Berry)
                    .border(BorderStroke(6.dp, Lantern), CircleShape)
                    .let { if (!pressed && v.me in m.involved) it.clickable { send("press:${SystemClock.elapsedRealtime() - goSeenAt}") } else it },
                contentAlignment = Alignment.Center,
            ) { Text(if (pressed) "✓" else "PUSH", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Soft) }
            if (!pressed) Caption(tr("あと {0} 秒", "%.1f".format(left / 1000.0)))
        }
    }
}

// ================= 結果 =================

@Composable
fun ArenaResult(vm: AppViewModel, v: PlayerView, s: Screen.ArenaResult, next: @Composable () -> Unit) {
    Image(painterResource(R.drawable.arena_hero), null, Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
    BigWord(if (s.winners.isEmpty()) tr("勝者なし！") else s.winners.joinToString(tr("、")) { v.nameOf(it) } + tr("の勝ち！"), Lantern)
    Panel {
        Text(tr("最終結果"), style = MaterialTheme.typography.titleMedium)
        s.seats.sortedByDescending { it.id in s.winners }.forEach { st ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(v.nameOf(st.id), modifier = Modifier.weight(1f))
                Text(if (st.id in s.winners) tr("👑 優勝") else if (st.alive) tr("生き残り") else tr("負け"),
                    color = if (st.id in s.winners) Lantern else if (st.alive) Mint else Muted, fontWeight = FontWeight.Bold)
            }
        }
    }
    Panel {
        Text(tr("最後のできごと"), style = MaterialTheme.typography.titleMedium)
        s.log.takeLast(6).forEach { Text(formatLog(it), fontSize = 13.sp, color = Muted) }
    }
    next()
}
