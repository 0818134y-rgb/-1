package com.zen.partygame.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zen.partygame.R
import com.zen.partygame.game.Intent
import com.zen.partygame.game.MAX_SCALE_HINT_LEN
import com.zen.partygame.game.PlayerView
import com.zen.partygame.game.ScaleCard
import com.zen.partygame.game.ScaleState
import com.zen.partygame.game.ScaleTopic
import com.zen.partygame.game.Screen

// ================= みんなのものさし =================

@Composable
private fun Lives(lives: Int, max: Int) {
    Text("❤️".repeat(lives.coerceAtLeast(0)) + "🖤".repeat((max - lives).coerceAtLeast(0)), fontSize = 22.sp)
}

/** お題と、1〜100 のものさし */
@Composable
private fun TopicRuler(t: ScaleTopic) {
    Panel {
        Caption(tr("今回のお題"))
        BigWord(t.t)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))
                .background(Brush.horizontalGradient(listOf(Mint, Lantern, Berry))),
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("1", fontWeight = FontWeight.Black, color = Mint)
                Caption(t.lo)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("100", fontWeight = FontWeight.Black, color = Berry)
                Text(t.hi, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
        }
    }
}

/** 数字の札（数字がまだ見えないときは「？」） */
@Composable
private fun NumberTag(value: Int?, state: ScaleState, big: Boolean = false) {
    val color = when (state) {
        ScaleState.MISSED -> Berry
        ScaleState.PLAYED -> Mint
        ScaleState.HELD -> Lantern
    }
    Box(
        Modifier.size(if (big) 64.dp else 46.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.18f))
            .border(2.dp, color, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(value?.toString() ?: "?", fontWeight = FontWeight.Black, fontSize = if (big) 28.sp else 20.sp, color = color)
    }
}

@Composable
private fun CardRow(v: PlayerView, c: ScaleCard) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        NumberTag(c.value, c.state)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (c.hint.isEmpty()) tr("（考え中…）") else tr("「{0}」", c.hint), style = MaterialTheme.typography.titleMedium,
                color = if (c.hint.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Caption(c.ownerName + (if (c.owner == v.me) tr("（あなた）") else "") + when (c.state) {
                ScaleState.MISSED -> tr("・ミスで捨てられた")
                else -> ""
            })
        }
    }
}

/** 自分のカード1枚：数字とヒントの入力 */
@Composable
private fun MyCard(vm: AppViewModel, c: ScaleCard) {
    var text by rememberSaveable(c.uid) { mutableStateOf(c.hint) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberTag(c.value, c.state, big = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                when (c.state) {
                    ScaleState.PLAYED -> { Text(tr("出しました"), color = Mint, fontWeight = FontWeight.Bold); Caption(tr("「{0}」", c.hint)) }
                    ScaleState.MISSED -> { Text(tr("ミスで捨てられました"), color = Berry, fontWeight = FontWeight.Bold); Caption(tr("「{0}」", c.hint)) }
                    ScaleState.HELD -> {
                        OutlinedTextField(
                            value = text, onValueChange = { text = it.take(MAX_SCALE_HINT_LEN) }, singleLine = true,
                            label = { Text(tr("この数字をお題で表すと？")) }, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        if (c.state == ScaleState.HELD) {
            Spacer(Modifier.height(8.dp))
            BigButton(if (c.hint.isEmpty()) tr("このヒントで決める") else tr("ヒントを変える"), enabled = text.isNotBlank() && text.trim() != c.hint) {
                vm.send(Intent.ScaleHint(c.uid, text))
            }
        }
    }
}

@Composable
fun ScalePlay(vm: AppViewModel, v: PlayerView, s: Screen.Scale) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(R.drawable.icon_scale, 44.dp); Spacer(Modifier.size(10.dp))
        Text(tr("みんなのものさし"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        Lives(s.lives, s.maxLives)
    }
    if (s.streak > 0) Caption(tr("連続クリア {0}回中", s.streak))
    TopicRuler(s.topic)
    if (s.lastMiss.isNotEmpty()) {
        val missed = s.cards.filter { it.uid in s.lastMiss }.sortedBy { it.value }
        Panel {
            Text(tr("ミス！ もっと小さい数字がありました"), color = Berry, fontWeight = FontWeight.Black)
            Caption(missed.joinToString(tr("、")) { tr("{0}（{1}さん）", it.value ?: "?", it.ownerName) })
        }
    }

    val mine = s.cards.filter { it.owner == v.me }.sortedBy { it.value }
    val myNext = mine.firstOrNull { it.state == ScaleState.HELD }
    if (!s.playing) {
        Caption(tr("自分の数字を、お題に合わせた「ことば」で表しましょう。数字そのものは言わないで！"))
    } else if (myNext != null) {
        BigButton(tr("{0} を出す", myNext.value ?: "?"), icon = R.drawable.icon_scale) { vm.send(Intent.ScalePlay) }
        Caption(tr("自分の数字がいちばん小さいと思ったら出しましょう。話し合ってOK（数字は言わない）"))
    } else {
        Caption(tr("あなたのカードは全部出ました。みんなを応援しよう"))
    }
    mine.forEach { MyCard(vm, it) }

    val played = s.cards.filter { it.state != ScaleState.HELD }.sortedWith(compareBy<ScaleCard> { if (it.order == 0) Int.MAX_VALUE else it.order }.thenBy { it.value })
    if (played.isNotEmpty()) Panel {
        Text(tr("場に出たカード"), style = MaterialTheme.typography.titleMedium)
        played.forEach { CardRow(v, it) }
    }
    val others = s.cards.filter { it.owner != v.me && it.state == ScaleState.HELD }
    if (others.isNotEmpty()) Panel {
        Text(tr("みんなのヒント（まだ出ていない）"), style = MaterialTheme.typography.titleMedium)
        others.forEach { CardRow(v, it) }
    }
    if (!s.playing) {
        val ready = s.cards.count { it.hint.isNotEmpty() }
        Caption(tr("ヒント {0} / {1}枚。全員そろうと並べ始めます", ready, s.cards.size), Modifier.fillMaxWidth())
        if (v.isHost) BigButton(tr("そろわなくても並べ始める"), secondary = true) { vm.send(Intent.ScaleBegin) }
    }
}

@Composable
fun ScaleResult(vm: AppViewModel, v: PlayerView, s: Screen.ScaleResult, next: @Composable () -> Unit) {
    ArtTitle(R.drawable.icon_scale, if (s.cleared) tr("クリア！") else tr("しっぱい…"), if (s.cleared) Mint else Berry)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Lives(s.lives, s.maxLives); Spacer(Modifier.width(10.dp))
        Caption(if (s.cleared) tr("連続クリア {0}回", s.streak) else tr("ライフがなくなりました"))
    }
    Panel {
        Caption(tr("今回のお題"))
        Text(s.topic.t, style = MaterialTheme.typography.titleLarge)
        Caption(tr("1＝{0} ／ 100＝{1}", s.topic.lo, s.topic.hi))
    }
    Panel {
        Text(tr("答え（小さい順）"), style = MaterialTheme.typography.titleMedium)
        s.cards.forEach { CardRow(v, it) }
    }
    next()
}
