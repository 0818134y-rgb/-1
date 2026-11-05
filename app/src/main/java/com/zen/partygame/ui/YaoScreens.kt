package com.zen.partygame.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zen.partygame.R
import com.zen.partygame.game.Intent
import com.zen.partygame.game.PlayerView
import com.zen.partygame.game.Screen
import com.zen.partygame.game.YaoCard
import com.zen.partygame.game.YaoCards
import com.zen.partygame.game.YaoDef
import com.zen.partygame.game.YaoKind

// ================= やおよろずバトル =================

private val Lav = Color(0xFFB9A8F0)
private val Orange = Color(0xFFFF9F43)

private fun kindColor(k: YaoKind) = when (k) {
    YaoKind.WEAPON -> Berry
    YaoKind.PLUS -> Orange
    YaoKind.ARMOR -> Mint
    YaoKind.MIRACLE -> Lav
    YaoKind.ITEM -> Lantern
}

/** カードの右上の数（攻5・防3・霊力3 など） */
private fun badge(d: YaoDef): String = when (d.kind) {
    YaoKind.WEAPON -> tr("攻{0}", d.value) + if (d.allOthers) tr("・全体") else ""
    YaoKind.PLUS -> "+" + d.value
    YaoKind.ARMOR -> if (d.id == "kagami") tr("反射") else tr("防{0}", d.value)
    YaoKind.MIRACLE -> tr("霊力{0}", d.mp)
    YaoKind.ITEM -> if (d.cost > 0) "💰${d.cost}" else tr("道具")
}

@Composable
private fun YaoCardView(c: YaoCard, selected: Boolean, onClick: (() -> Unit)?) {
    val d = YaoCards.def(c.id)
    val col = kindColor(d.kind)
    Column(
        Modifier.width(118.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF6EBD9))
            .border(BorderStroke(if (selected) 4.dp else 3.dp, if (selected) Berry else col), RoundedCornerShape(14.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(d.emoji, fontSize = 28.sp)
            Spacer(Modifier.weight(1f))
            Text(badge(d), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(col).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        val name = tr(d.name)
        Text(name, color = Night, fontWeight = FontWeight.Black, fontSize = if (name.length > 6) 12.sp else 14.sp, maxLines = 1, textAlign = TextAlign.Center)
        Text(tr(d.text), color = Color(0xFF3B3560), fontSize = 10.sp, lineHeight = 13.sp, textAlign = TextAlign.Center, minLines = 3, maxLines = 6)
        if (selected) Text(tr("✓ 選択中"), color = Berry, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YaoPlay(vm: AppViewModel, v: PlayerView, s: Screen.Yao, receivedAt: Long, next: @Composable () -> Unit) {
    val remaining = rememberRemainingMs(v, receivedAt)
    val me = s.seats.firstOrNull { it.id == v.me }
    val att = s.attack
    val myTurn = !s.over && att == null && s.turn == v.me && me?.alive == true
    val defending = att != null && v.me in att.waiting
    var picked by remember(s.hand.map { it.uid }, s.turn, att?.by) { mutableStateOf(listOf<Int>()) }
    var target by remember(picked) { mutableStateOf<String?>(null) }
    var opt by remember(picked) { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(R.drawable.icon_yao, 44.dp); Spacer(Modifier.size(10.dp))
        Text(tr("やおよろずバトル"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (!s.over && v.remainingMs != null) Text(formatMs(remaining), style = MaterialTheme.typography.titleLarge, color = if (remaining < 10_000) Berry else Lantern)
    }

    if (s.over) {
        Panel {
            Text(if (s.winners.isEmpty()) tr("全員成仏…勝者なし") else tr("{0}の勝ち！", s.winners.joinToString("・") { v.nameOf(it) }),
                style = MaterialTheme.typography.headlineSmall, color = if (v.me in s.winners) Mint else Lantern, fontWeight = FontWeight.Black)
            Caption(tr("神さまに選ばれし者"))
        }
    }

    // 席
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        s.seats.forEach { st ->
            val isTurn = st.id == s.turn && st.alive && !s.over
            val targeted = att != null && st.id in att.targets
            Column(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(if (isTurn) Color(0xFF3A2E14) else NightCard)
                    .border(BorderStroke(2.dp, when { targeted -> Berry; isTurn -> Lantern; else -> Color.Transparent }), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(v.nameOf(st.id) + if (st.id == v.me) tr("（あなた）") else "", fontWeight = FontWeight.Bold, color = if (st.alive) Soft else Muted, fontSize = 14.sp)
                if (!st.alive) Text(tr("成仏"), color = Muted, fontSize = 13.sp)
                else {
                    Text("❤️${st.hp}  💫${st.mp}  💰${st.coin}", fontSize = 13.sp, color = Soft)
                    Text(tr("手札{0}枚", st.hand) + (if (st.curse > 0) "  👻×${st.curse}" else ""), fontSize = 12.sp, color = Muted)
                }
            }
        }
    }

    // 攻撃が来ている
    if (att != null) Panel {
        val cards = att.cards.joinToString(" ＋ ") { YaoCards.def(it).emoji + tr(YaoCards.def(it).name) }
        Text(tr("{0}の攻撃！ 攻{1}", v.nameOf(att.by), att.atk), color = Berry, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Caption(cards + "  →  " + att.targets.joinToString("・") { v.nameOf(it) })
        if (!defending) Caption(tr("{0}の守りを待っています…", att.waiting.joinToString("・") { v.nameOf(it) }))
    }

    // 自分の手札
    if (!s.over && me?.alive == true && s.hand.isNotEmpty()) {
        Text(tr("あなたの手札（{0}枚）", s.hand.size), style = MaterialTheme.typography.titleMedium)
        val canPick = myTurn || defending
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.hand, key = { it.uid }) { c ->
                val d = YaoCards.def(c.id)
                val pickable = canPick && (myTurn || d.kind == YaoKind.ARMOR)
                YaoCardView(c, c.uid in picked, if (pickable) ({ picked = if (c.uid in picked) picked - c.uid else picked + c.uid }) else null)
            }
        }
    }
    if (me != null && !me.alive && !s.over) Panel { Text(tr("あなたは成仏しました"), color = Muted, fontWeight = FontWeight.Bold); Caption(tr("最後まで見守りましょう")) }

    // 守る
    if (defending) {
        val armor = s.hand.filter { it.uid in picked && YaoCards.def(it.id).kind == YaoKind.ARMOR }
        val mirror = armor.any { it.id == "kagami" }
        val def = armor.sumOf { YaoCards.def(it.id).value }
        Text(tr("防具を選んで守ろう（何枚でも重ねられる）"), color = Lantern, fontWeight = FontWeight.Bold)
        BigButton(when {
            armor.isEmpty() -> tr("そのまま受ける（{0}ダメージ）", att!!.atk)
            mirror -> tr("鏡もちで跳ね返す！")
            else -> tr("守る（防{0} → {1}ダメージ）", def, (att!!.atk - def).coerceAtLeast(0))
        }) { vm.send(Intent.YaoAct("defend", armor.map { it.uid })); picked = emptyList() }
    }

    // 自分の番
    if (myTurn) {
        val sel = s.hand.filter { it.uid in picked }
        val defs = sel.map { YaoCards.def(it.id) }
        val weapons = defs.filter { it.kind == YaoKind.WEAPON }
        val pluses = defs.filter { it.kind == YaoKind.PLUS }
        val others = s.seats.filter { it.alive && it.id != v.me }.map { it.id }
        val single = defs.singleOrNull()
        val isAttack = weapons.size == 1 && weapons.size + pluses.size == defs.size
        val needsTarget = (isAttack && !weapons[0].allOthers) || (single != null && single.kind in setOf(YaoKind.MIRACLE, YaoKind.ITEM) && single.needsTarget)
        val usable = isAttack || (single != null && when (single.kind) {
            YaoKind.MIRACLE -> me!!.mp >= single.mp
            YaoKind.ITEM -> me!!.coin >= single.cost || single.cost == 0 || single.id == "oshiuri"
            else -> false
        })
        Text(when {
            sel.isEmpty() && s.hand.size >= com.zen.partygame.game.YaoGame.MAX_HAND -> tr("手札がいっぱい！ いらないカードを選んで捨ててからお祈りしよう")
            sel.isEmpty() -> tr("あなたの番！ カードを選ぶか、お祈りしよう")
            isAttack -> tr("攻{0}で攻撃！", weapons[0].value + pluses.sumOf { it.value })
            single?.kind == YaoKind.MIRACLE && !usable -> tr("霊力が足りません")
            single?.kind == YaoKind.ITEM && !usable -> tr("おさいせんが足りません")
            single?.kind == YaoKind.ARMOR -> tr("防具は攻撃されたときに使います")
            single?.kind == YaoKind.PLUS || (pluses.isNotEmpty() && weapons.isEmpty()) -> tr("上乗せカードは武器と一緒に選びます")
            !usable -> tr("この組み合わせでは使えません")
            else -> ""
        }, color = Lantern, fontWeight = FontWeight.Bold)
        if (usable && needsTarget) {
            Text(tr("だれに？"), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { others.forEach { t -> FilterChip(target == t, { target = t }, { Text(v.nameOf(t)) }) } }
        }
        if (usable && single?.id == "ryogae") {
            Text(tr("何を何に両替する？（最大8）"), style = MaterialTheme.typography.titleMedium)
            val names = mapOf("hp" to "❤️HP", "mp" to tr("💫霊力"), "coin" to tr("💰おさいせん"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("coin>hp", "mp>hp", "hp>mp", "coin>mp", "hp>coin", "mp>coin").forEach { o ->
                    val (a, b) = o.split(">")
                    FilterChip(opt == o, { opt = o }, { Text(names[a] + " → " + names[b]) })
                }
            }
        }
        if (usable) BigButton(if (isAttack) tr("攻撃！") else tr("「{0}」を使う", tr(single!!.name)),
            enabled = (!needsTarget || target != null) && (single?.id != "ryogae" || opt.isNotEmpty())) {
            vm.send(Intent.YaoAct("play", picked, target, opt)); picked = emptyList()
        }
        BigButton(if (sel.isEmpty()) tr("お祈り（1枚引いて終わる）") else tr("選んだ{0}枚を捨ててお祈り（{1}枚引く）", sel.size, sel.size + 1), secondary = true) {
            vm.send(Intent.YaoAct("pray", picked)); picked = emptyList()
        }
    } else if (!s.over && att == null && me?.alive == true) Caption(tr("{0}さんの番です", v.nameOf(s.turn)), Modifier.fillMaxWidth())

    if (s.over) next()

    Panel {
        Text(tr("できごと"), style = MaterialTheme.typography.titleMedium)
        s.log.takeLast(7).reversed().map { formatLog(it) }.forEachIndexed { i, line ->
            Text(line, fontSize = 13.sp, color = if (i == 0) Soft else Muted, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
    Spacer(Modifier.height(4.dp))
}
