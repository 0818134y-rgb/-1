package com.zen.partygame.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.zen.partygame.game.MAX_ANSWER_LEN
import com.zen.partygame.game.VoteCount
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.font.FontWeight
import com.zen.partygame.R
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.annotation.DrawableRes
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.zen.partygame.game.GameType
import com.zen.partygame.game.Intent
import com.zen.partygame.game.MIN_PLAYERS
import com.zen.partygame.game.NgLevel
import com.zen.partygame.game.PlayerView
import com.zen.partygame.game.ResultReason
import com.zen.partygame.game.Screen
import com.zen.partygame.game.Settings
import com.zen.partygame.game.Sfx
import com.zen.partygame.game.Team
import kotlinx.coroutines.delay

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** ホストが送った「残りミリ秒」から、この端末の時計で残り時間を数える */
@Composable
fun rememberRemainingMs(view: PlayerView, receivedAt: Long): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(receivedAt) { while (true) { now = SystemClock.elapsedRealtime(); delay(200) } }
    val rem = view.remainingMs ?: return 0
    return (rem - (now - receivedAt)).coerceAtLeast(0)
}

fun formatMs(ms: Long): String {
    val s = (ms + 999) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun RoomScreen(vm: AppViewModel, state: Ui.Room) {
    val v = state.view
    var confirmLeave by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }
    val lang by vm.lang.collectAsState()
    val ctx = LocalContext.current
    DisposableEffect(Unit) {  // 遊んでいる間は画面を消さない
        val w = ctx.findActivity()?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler { confirmLeave = true }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text(if (v.isHost && !v.localMode) tr("部屋を閉じますか？") else tr("部屋を出ますか？")) },
        text = { if (v.isHost && !v.localMode) Text(tr("全員の接続が切れます")) },
        confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leave() }) { Text(tr("出る")) } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(tr("やめる")) } },
    )

    if (showLang) LanguageDialog(lang, onPick = { vm.setLang(it); showLang = false }, onClose = { showLang = false })
    val withBanner = when (v.screen) {
        Screen.Lobby, is Screen.ArenaResult, is Screen.WwResult, is Screen.NgResult, is Screen.SyncReveal, is Screen.GuessResult, is Screen.QReveal,
        is Screen.ScaleResult -> true
        else -> false
    }
    val scroll = rememberScrollState()
    // 残り10秒からカウント音、0秒でブザー
    val countdown = Sfx.countsDown(v)
    val secLeft = if (countdown) ((rememberRemainingMs(v, state.receivedAt) + 999) / 1000).toInt() else -1
    LaunchedEffect(secLeft) {
        when {
            secLeft in 4..10 -> Sound.play(Sfx.TICK)
            secLeft in 1..3 -> Sound.play(Sfx.TICK_LAST)
            secLeft == 0 -> Sound.play(Sfx.BUZZER)
        }
    }
    // お絵かき伝言の発表：新しいコマがめくられたら下までスクロール
    val revealKey = (v.screen as? Screen.DrawReveal)?.let { it.book * 100 + it.entries.size }
    LaunchedEffect(revealKey) { if (revealKey != null) { delay(120); scroll.animateScrollTo(scroll.maxValue) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (v.localMode) tr("1台モード") else (if (state.online) "🌐 " else "📶 ") + tr("部屋 {0}", v.roomCode), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            val soundOn by vm.sound.collectAsState()
            val musicOn by vm.music.collectAsState()
            TextButton(onClick = { vm.setMusic(!musicOn) }) { MusicIcon(musicOn) }
            TextButton(onClick = { vm.setSound(!soundOn) }) { Text(if (soundOn) "🔊" else "🔇") }
            TextButton(onClick = { showLang = true }) { Text("🌐 " + lang.uppercase()) }
            TextButton(onClick = { confirmLeave = true }) { Text(tr("退出")) }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val s = v.screen) {
                Screen.Lobby -> Lobby(vm, v, state.online)
                is Screen.WwReveal -> WwReveal(vm, v, s)
                is Screen.WwRevealLocal -> WwRevealLocal(vm, v, s)
                is Screen.WwTalk -> WwTalk(vm, v, s, rememberRemainingMs(v, state.receivedAt))
                is Screen.WwVote -> WwVote(vm, v, s)
                is Screen.WwAccuse -> WwAccuse(vm, v, s)
                is Screen.WwGuess -> WwGuess(vm, v, s)
                is Screen.WwResult -> WwResult(vm, v, s)
                is Screen.NgPlay -> NgPlay(vm, v, s, rememberRemainingMs(v, state.receivedAt))
                is Screen.NgResult -> NgResult(vm, v, s)
                is Screen.SyncAnswer -> SyncAnswer(vm, v, s, rememberRemainingMs(v, state.receivedAt))
                is Screen.SyncReveal -> SyncReveal(vm, v, s)
                is Screen.GuessPlay -> GuessPlay(vm, v, s, rememberRemainingMs(v, state.receivedAt))
                is Screen.GuessResult -> GuessResult(vm, v, s)
                is Screen.QVote -> QVote(vm, v, s)
                is Screen.QReveal -> QReveal(vm, v, s)
                is Screen.Arena -> ArenaPlay(vm, v, s, state.receivedAt)
                is Screen.ArenaResult -> ArenaResult(vm, v, s) { NextButtons(vm, v, tr("もう一戦")) }
                is Screen.AdBreak -> AdBreak(vm, s)
                is Screen.DrawTask -> DrawTask(vm, v, s, rememberRemainingMs(v, state.receivedAt))
                is Screen.DrawReveal -> DrawReveal(vm, v, s) { NextButtons(vm, v, tr("もう一回")) }
                is Screen.Scale -> ScalePlay(vm, v, s)
                is Screen.ScaleResult -> ScaleResult(vm, v, s) { NextButtons(vm, v, tr("次のお題へ")) }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (withBanner) AdBanner()
    }
}

@Composable
private fun HostOnly(v: PlayerView, waitingText: String, content: @Composable () -> Unit) {
    if (v.isHost) content() else Caption(waitingText, Modifier.fillMaxWidth().padding(vertical = 8.dp))
}

// ================= ロビー =================

@DrawableRes
fun gameIcon(g: GameType): Int = when (g) {
    GameType.ARENA -> R.drawable.icon_arena
    GameType.WORD_WOLF -> R.drawable.icon_wordwolf
    GameType.NG_WORD -> R.drawable.icon_ngword
    GameType.SPY -> R.drawable.icon_spy
    GameType.SYNC -> R.drawable.icon_sync
    GameType.GUESS -> R.drawable.icon_guess
    GameType.QUESTION -> R.drawable.icon_question
    GameType.DRAW -> R.drawable.icon_draw
    GameType.SCALE -> R.drawable.icon_scale
}

private fun gameSummary(st: Settings): String = when (st.game) {
    GameType.ARENA -> (if (st.arenaTurnSec == 0) tr("手番の制限なし") else tr("手番 {0}秒", st.arenaTurnSec)) + (if (st.arenaChaos) tr("・今夜のルールあり") else "")
    GameType.WORD_WOLF -> tr("話し合い {0}分", st.talkSec / 60)
    GameType.SPY -> tr("質問タイム {0}分", st.talkSec / 60)
    GameType.NG_WORD -> tr("制限時間 {0}分", st.ngSec / 60)
    GameType.SYNC -> if (st.syncSec == 0) tr("回答時間 制限なし") else tr("回答時間 {0}秒", st.syncSec)
    GameType.GUESS -> tr("制限時間 {0}分", st.guessSec / 60)
    GameType.QUESTION -> tr("全員で匿名投票")
    GameType.DRAW -> tr("描く {0}秒・書く {1}秒", st.drawSec, st.writeSec)
    GameType.SCALE -> if (st.scaleCards == 0) tr("全員で協力・ライフ{0}", com.zen.partygame.game.SCALE_LIVES) else tr("1人{0}枚・ライフ{1}", st.scaleCards, com.zen.partygame.game.SCALE_LIVES)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Lobby(vm: AppViewModel, v: PlayerView, online: Boolean) {
    val st = v.settings
    fun set(s: Settings) = vm.send(Intent.UpdateSettings(s))
    if (!v.localMode) Panel {
        Caption(if (online) tr("オンラインの部屋コード。参加する人は「オンライン」→「部屋に参加する」でこの数字を入れます（PC版からもOK）")
            else tr("近くの人は「部屋に参加する」からこの番号を選んでください"))
        Text(v.roomCode, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (online && v.isHost) BigButton(tr("招待を送る（LINEなど）"), secondary = true) { vm.shareInvite(v.roomCode) }
    }
    Panel {
        Text(tr("参加者 {0}人", v.players.size), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        v.players.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((if (p.isHost) "👑 " else "・ ") + p.name + (if (p.id == v.me) tr("（あなた）") else ""), modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                if (v.isHost && !v.localMode && !p.isHost) TextButton(onClick = { vm.send(Intent.Kick(p.id)) }) { Text(tr("退出させる")) }
            }
        }
    }
    if (v.isHost) {
        Panel {
            Text(tr("ゲーム"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            if (!v.localMode) {
                FeaturedGame(st.game == GameType.ARENA) { set(st.copy(game = GameType.ARENA)) }
                Spacer(Modifier.height(10.dp))
            }
            val games = GameType.entries.filter { (!v.localMode || it.localOk) && it != GameType.ARENA }
            games.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    row.forEach { g -> GameCard(gameIcon(g), tr(g.short), st.game == g, Modifier.weight(1f)) { set(st.copy(game = g)) } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(4.dp))
            when (st.game) {
                GameType.ARENA -> {
                    ChipRow(tr("1手番の制限時間（過ぎると勝手にカードが出る）")) {
                        listOf(30 to tr("30秒"), 60 to tr("60秒"), 90 to tr("90秒"), 0 to tr("なし")).forEach { (sec, label) ->
                            FilterChip(st.arenaTurnSec == sec, { set(st.copy(arenaTurnSec = sec)) }, { Text(label) })
                        }
                    }
                    ChipRow(tr("今夜のルール（開始時にランダムで1つ追加）")) {
                        FilterChip(st.arenaChaos, { set(st.copy(arenaChaos = true)) }, { Text(tr("あり")) })
                        FilterChip(!st.arenaChaos, { set(st.copy(arenaChaos = false)) }, { Text(tr("なし（初めての人向け）")) })
                    }
                }
                GameType.WORD_WOLF, GameType.SPY -> {
                    val role = if (st.game == GameType.SPY) tr("スパイ") else tr("ウルフ")
                    ChipRow(if (st.game == GameType.SPY) tr("質問タイム") else tr("話し合いの時間")) {
                        listOf(120, 180, 240, 300, 420).forEach { sec -> FilterChip(st.talkSec == sec, { set(st.copy(talkSec = sec)) }, { Text(tr("{0}分", sec / 60)) }) }
                    }
                    ChipRow(tr("{0}の人数", role)) {
                        listOf(0 to tr("おまかせ"), 1 to tr("1人"), 2 to tr("2人")).forEach { (n, label) -> FilterChip(st.wolves == n, { set(st.copy(wolves = n)) }, { Text(label) }) }
                    }
                    if (st.game == GameType.WORD_WOLF) ChipRow(tr("お題のジャンル（選ばなければ全部）")) {
                        v.categories.forEach { c ->
                            val on = c in st.categories
                            FilterChip(on, { set(st.copy(categories = if (on) st.categories - c else st.categories + c)) }, { Text(c) })
                        }
                    }
                }
                GameType.NG_WORD -> {
                    ChipRow(tr("制限時間")) {
                        listOf(180, 300, 600).forEach { sec -> FilterChip(st.ngSec == sec, { set(st.copy(ngSec = sec)) }, { Text(tr("{0}分", sec / 60)) }) }
                    }
                    ChipRow(tr("ワードの難しさ")) {
                        listOf(NgLevel.NORMAL to tr("ふつう"), NgLevel.HARD to tr("ハード（口ぐせ）"), NgLevel.MIX to tr("ミックス")).forEach { (lv, label) ->
                            FilterChip(st.ngLevel == lv, { set(st.copy(ngLevel = lv)) }, { Text(label) })
                        }
                    }
                }
                GameType.SYNC -> ChipRow(tr("回答時間")) {
                    listOf(30 to tr("30秒"), 60 to tr("60秒"), 90 to tr("90秒"), 0 to tr("制限なし")).forEach { (sec, label) ->
                        FilterChip(st.syncSec == sec, { set(st.copy(syncSec = sec)) }, { Text(label) })
                    }
                }
                GameType.GUESS -> ChipRow(tr("制限時間")) {
                    listOf(120, 180, 300).forEach { sec -> FilterChip(st.guessSec == sec, { set(st.copy(guessSec = sec)) }, { Text(tr("{0}分", sec / 60)) }) }
                }
                GameType.QUESTION -> Caption(tr("設定はありません。だれが誰に入れたかは表示されません。"))
                GameType.SCALE -> {
                    Caption(tr("全員にひみつの数字（1〜100）を配ります。お題に合わせて数字を「ことば」で表し、話し合いながら小さい順に出していく協力ゲーム。もっと小さい数字が残っていたらミスでライフ -1（2〜8人）。"))
                    ChipRow(tr("1人に配る数字の枚数")) {
                        listOf(0 to tr("おまかせ"), 1 to tr("1枚"), 2 to tr("2枚"), 3 to tr("3枚")).forEach { (n, label) ->
                            FilterChip(st.scaleCards == n, { set(st.copy(scaleCards = n)) }, { Text(label) })
                        }
                    }
                }
                GameType.DRAW -> {
                    Caption(tr("お題を書く → 次の人が絵にする → 次の人がその絵を見て答える…を全員分くり返し、最後にどう伝わったかを発表します（3〜8人）。"))
                    ChipRow(tr("絵を描く時間")) {
                        listOf(60, 90, 120, 180).forEach { sec -> FilterChip(st.drawSec == sec, { set(st.copy(drawSec = sec)) }, { Text(tr("{0}秒", sec)) }) }
                    }
                    ChipRow(tr("お題を書く・答える時間")) {
                        listOf(30, 45, 60, 90).forEach { sec -> FilterChip(st.writeSec == sec, { set(st.copy(writeSec = sec)) }, { Text(tr("{0}秒", sec)) }) }
                    }
                }
            }
        }
        val need = st.game.minPlayers
        BigButton(if (v.players.size >= need) tr("はじめる") else tr("{0}人からはじめられます", need), enabled = v.players.size >= need, icon = gameIcon(st.game)) { vm.send(Intent.Start) }
    } else {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Art(gameIcon(st.game), 56.dp)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(tr(st.game.label), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Caption(gameSummary(st))
                }
            }
        }
        Caption(tr("ホストがはじめるのを待っています…"), Modifier.fillMaxWidth())
    }
}

/** 一押しゲームの大きなボタン（ロビーの一番上） */
@Composable
private fun FeaturedGame(selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(22.dp))
            .border(BorderStroke(if (selected) 4.dp else 2.dp, if (selected) Lantern else Berry), RoundedCornerShape(22.dp))
            .clickable { Sound.play(com.zen.partygame.game.Sfx.TAP); onClick() },
    ) {
        Image(painterResource(R.drawable.arena_hero), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Row(
            Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Berry).padding(horizontal = 10.dp, vertical = 4.dp),
        ) { Text(tr("一押し！ NEW"), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Black) }
        if (selected) Row(
            Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Lantern).padding(horizontal = 10.dp, vertical = 4.dp),
        ) { Text(tr("✓ 選択中"), color = Night, fontWeight = FontWeight.Black) }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xE015102D)).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Art(R.drawable.icon_arena, 36.dp); Spacer(Modifier.size(8.dp))
                Text(tr("なんでもアリーナ"), style = MaterialTheme.typography.headlineMedium, color = Lantern)
            }
            Text(tr("何が起きるかわからない理不尽カードバトル（2〜8人）"), style = MaterialTheme.typography.bodyMedium, color = Soft)
        }
    }
}

@Composable
private fun CenterArt(@DrawableRes id: Int, size: androidx.compose.ui.unit.Dp) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Art(id, size) }
}

@Composable
private fun GameCard(@DrawableRes icon: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) cs.surfaceContainerHigh else cs.background)
            .border(BorderStroke(2.dp, if (selected) cs.primary else cs.surfaceContainerHigh), RoundedCornerShape(16.dp))
            .clickable { Sound.play(com.zen.partygame.game.Sfx.TAP); onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Art(icon, 52.dp)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            color = if (selected) cs.primary else cs.onSurfaceVariant, maxLines = 1)
    }
}

/** スパイ／ウルフの呼び名 */
private fun PlayerView.roleName() = if (settings.game == GameType.SPY) tr("スパイ") else tr("ウルフ")
private fun PlayerView.isSpyGame() = settings.game == GameType.SPY

// ================= ワードウルフ／スパイ =================

@Composable
private fun SpyCard() {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Art(R.drawable.icon_spy, 64.dp); Spacer(Modifier.size(12.dp))
            Column {
                Text(tr("あなたはスパイ"), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
                Caption(tr("みんなの質問や答えから場所を推理しよう。バレないように！"))
            }
        }
    }
}

@Composable
private fun WwReveal(vm: AppViewModel, v: PlayerView, s: Screen.WwReveal) {
    val done = v.me in s.confirmed
    val spyGame = v.isSpyGame()
    ArtTitle(gameIcon(v.settings.game), if (spyGame) tr("場所") else tr("お題"))
    if (s.spy) SpyCard() else {
        Caption(if (spyGame) tr("あなたがいる場所（ほかの人に見せないで）") else tr("あなたのお題（ほかの人に見せないで）"))
        Panel { BigWord(s.word) }
        Caption(
            if (spyGame) tr("全員同じ場所にいますが、1人だけ場所を知らないスパイがまぎれています。場所がバレないように質問し合いましょう。")
            else tr("多数派（市民）か少数派（ウルフ）かは、まだ誰にも分かりません。話しながら探りましょう。"),
        )
    }
    BigButton(if (done) tr("確認しました") else tr("確認した"), enabled = !done) { vm.send(Intent.ConfirmWord()) }
    Caption(tr("確認済み {0} / {1}人", s.confirmed.size, v.players.size), Modifier.fillMaxWidth())
}

@Composable
private fun WwRevealLocal(vm: AppViewModel, v: PlayerView, s: Screen.WwRevealLocal) {
    val next = s.entries.firstOrNull { it.id !in s.confirmed } ?: return
    var showing by rememberSaveable(next.id) { mutableStateOf(false) }
    val name = v.nameOf(next.id)
    ArtTitle(R.drawable.icon_pass, if (v.isSpyGame()) tr("順番に場所を見る") else tr("順番にお題を見る"))
    Caption(tr("{0} / {1}人目", s.confirmed.size + 1, s.entries.size))
    Panel {
        Text(tr("{0}さんの番", name), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        when {
            !showing -> Caption(tr("{0}さん以外は画面を見ないでください", name), Modifier.fillMaxWidth())
            next.spy -> BigWord(tr("あなたはスパイ"), MaterialTheme.colorScheme.secondary)
            else -> BigWord(next.word)
        }
    }
    if (!showing) BigButton(if (v.isSpyGame()) tr("場所を見る") else tr("お題を見る")) { showing = true }
    else BigButton(tr("覚えた（隠して次の人へ）")) { vm.send(Intent.ConfirmWord(forPlayer = next.id)) }
}

@Composable
private fun WwTalk(vm: AppViewModel, v: PlayerView, s: Screen.WwTalk, remaining: Long) {
    var peek by remember { mutableStateOf(false) }
    val spyGame = v.isSpyGame()
    Text(if (spyGame) tr("質問タイム") else tr("話し合い"), style = MaterialTheme.typography.headlineMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Art(R.drawable.icon_timer, 64.dp)
        Spacer(Modifier.size(12.dp))
        Text(formatMs(remaining), style = MaterialTheme.typography.displayLarge, color = if (remaining < 30_000) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
    }
    when {
        s.spy -> SpyCard()
        s.word != null -> Panel(Modifier.clickable { peek = !peek }) {
            Caption(if (spyGame) tr("あなたがいる場所（タップで表示／非表示）") else tr("あなたのお題（タップで表示／非表示）"))
            if (peek) BigWord(s.word ?: "") else Text("●●●", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        else -> Caption(if (spyGame) tr("順番に質問し合って、場所を知らないスパイを探しましょう") else tr("お題について話し合って、ウルフ（違うお題の人）を探しましょう"))
    }
    if (spyGame) Caption(tr("例：「ここには何をしに来ますか？」「今どんな服を着ていますか？」"))
    HostOnly(v, tr("時間になると投票に進みます")) {
        BigButton(tr("切り上げて投票へ"), secondary = true) { vm.send(Intent.EndTalk) }
    }
}

@Composable
private fun WwVote(vm: AppViewModel, v: PlayerView, s: Screen.WwVote) {
    val role = v.roleName()
    ArtTitle(R.drawable.icon_vote, if (s.runoff) tr("決選投票") else tr("投票"))
    Caption(if (s.runoff) tr("同票だった人の中から、{0}だと思う人を選んでください", role) else tr("{0}だと思う人を選んでください", role))
    s.candidates.filter { it != v.me }.forEach { id -> ChoiceButton(v.nameOf(id), s.myVote == id) { vm.send(Intent.Vote(id)) } }
    Caption(tr("投票済み {0} / {1}人", s.voted.size, v.players.size) + if (s.myVote != null) tr("（全員そろうまで変更できます）") else "", Modifier.fillMaxWidth())
    HostOnly(v, "") { BigButton(tr("締め切って集計"), secondary = true) { vm.send(Intent.CloseVote) } }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = { Sound.play(com.zen.partygame.game.Sfx.TAP); onClick() },
        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
        colors = if (selected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
    ) { Text((if (selected) "✓ " else "") + label) }
}

@Composable
private fun WwAccuse(vm: AppViewModel, v: PlayerView, s: Screen.WwAccuse) {
    ArtTitle(R.drawable.icon_vote, tr("投票"))
    Caption(tr("「せーの」で{0}だと思う人を指さして、いちばん多く指された人を選んでください（同票なら話し合って1人に）", v.roleName()))
    s.candidates.forEach { id ->
        OutlinedButton(onClick = { vm.send(Intent.Accuse(id)) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
            Text(v.nameOf(id))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WwGuess(vm: AppViewModel, v: PlayerView, s: Screen.WwGuess) {
    val role = v.roleName()
    CenterArt(R.drawable.char_wolf_caught, 170.dp)
    Text(tr("{0}発見！", role), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Panel {
        Caption(tr("{0}は", role))
        BigWord(s.wolves.joinToString(tr("、")) { v.nameOf(it) }, MaterialTheme.colorScheme.secondary)
    }
    if (v.isSpyGame()) {
        val canAnswer = s.iAmWolf || (v.localMode && v.isHost)
        Text(if (canAnswer) tr("最後のチャンス！場所を当てれば逆転勝ち。下から選んでください。") else tr("スパイが場所を当てたら逆転されます。スパイが選ぶのを待っています…"))
        if (canAnswer) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.options.forEach { o -> OutlinedButton(onClick = { vm.send(Intent.SpyGuess(o)) }, shape = RoundedCornerShape(12.dp)) { Text(o) } }
        }
        return
    }
    Text(
        if (s.iAmWolf) tr("最後のチャンス！市民のお題を当てれば逆転勝ち。口で答えてください。")
        else tr("でも、ウルフが市民のお題を当てたら逆転されます。ウルフに答えてもらいましょう。"),
    )
    HostOnly(v, tr("ホストが判定します…")) {
        Caption(tr("ウルフの答えは市民のお題と…"))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.send(Intent.Judge(true)) }, Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text(tr("合っていた")) }
            Button(onClick = { vm.send(Intent.Judge(false)) }, Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) { Text(tr("違った")) }
        }
    }
}

@Composable
private fun WwResult(vm: AppViewModel, v: PlayerView, s: Screen.WwResult) {
    val citizenWin = s.winner == Team.CITIZEN
    val spy = v.isSpyGame()
    val role = v.roleName()
    CenterArt(if (citizenWin) R.drawable.char_villagers else R.drawable.char_wolf_grin, 170.dp)
    BigWord(
        if (citizenWin) (if (spy) tr("みんなの勝ち！") else tr("市民の勝ち！")) else tr("{0}の勝ち！", role),
        if (citizenWin) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
    )
    Caption(
        when (s.reason) {
            ResultReason.WOLF_CAUGHT -> if (spy) tr("スパイを見つけて、場所も守りきりました") else tr("ウルフを見つけて、逆転も防ぎました")
            ResultReason.WOLF_GUESSED -> if (spy) tr("スパイが場所を当てて逆転！") else tr("ウルフが市民のお題を当てて逆転！")
            ResultReason.CITIZEN_ACCUSED -> tr("{0}さん（{1}ではない）が最多票に…{2}の逃げ切り", v.nameOf(s.accused), role, role)
            ResultReason.TIE -> tr("票が割れて、{0}の逃げ切り", role)
        },
        Modifier.fillMaxWidth(),
    )
    Panel {
        if (spy) {
            Caption(tr("場所")); Text(s.citizenWord, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary)
            s.spyGuess?.let { sg -> Spacer(Modifier.height(8.dp)); Caption(tr("スパイの答え")); Text(sg, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary) }
        } else {
            Caption(tr("市民のお題")); Text(s.citizenWord, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(8.dp))
            Caption(tr("ウルフのお題")); Text(s.wolfWord, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(8.dp))
        Caption(role); Text(s.wolves.joinToString(tr("、")) { v.nameOf(it) }, style = MaterialTheme.typography.titleLarge)
        if (s.myWord != null && !spy) { Spacer(Modifier.height(8.dp)); Caption(tr("あなたのお題：{0}", s.myWord)) }
    }
    if (s.tally.isNotEmpty()) Panel {
        Text(tr("得票"), style = MaterialTheme.typography.titleMedium)
        s.tally.forEach { Text(tr("{0}　{1}票", v.nameOf(it.id), it.votes)) }
    }
    NextButtons(vm, v)
}

@Composable
private fun NextButtons(vm: AppViewModel, v: PlayerView, nextLabel: String = tr("もう一回")) {
    HostOnly(v, tr("ホストが次に進むのを待っています…")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(nextLabel) { vm.send(Intent.Next) }
            BigButton(if (v.localMode) tr("設定をかえる") else tr("ロビーへ（ゲームを変える）"), secondary = true) { vm.send(Intent.BackToLobby) }
        }
    }
}

@Composable
private fun Scoreboard(v: PlayerView, scores: List<VoteCount>, gained: List<VoteCount> = emptyList()) {
    Panel {
        Text(tr("得点"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        scores.forEachIndexed { i, sc ->
            val g = gained.firstOrNull { it.id == sc.id }?.votes ?: 0
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("${i + 1}. ${v.nameOf(sc.id)}" + if (sc.id == v.me) tr("（あなた）") else "", modifier = Modifier.weight(1f))
                if (g > 0) Text("+${g}  ", color = MaterialTheme.colorScheme.tertiary)
                Text(tr("{0}点", sc.votes), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ================= みんなでシンクロ =================

@Composable
private fun SyncAnswer(vm: AppViewModel, v: PlayerView, s: Screen.SyncAnswer, remaining: Long) {
    var text by rememberSaveable(s.prompt) { mutableStateOf(s.myAnswer ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(R.drawable.icon_sync, 44.dp); Spacer(Modifier.size(10.dp))
        Text(tr("みんなでシンクロ"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        if (v.remainingMs != null) Text(formatMs(remaining), style = MaterialTheme.typography.headlineMedium,
            color = if (remaining < 10_000) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
    }
    Caption(tr("みんなと同じ答えになるほど得点！"))
    Panel { BigWord(s.prompt) }
    OutlinedTextField(
        value = text, onValueChange = { text = it.take(MAX_ANSWER_LEN) }, singleLine = true,
        label = { Text(tr("あなたの答え")) }, modifier = Modifier.fillMaxWidth(),
    )
    BigButton(if (s.myAnswer == null) tr("これで答える") else tr("答えを変える"), enabled = text.isNotBlank() && text.trim() != s.myAnswer) {
        vm.send(Intent.SyncAnswer(text))
    }
    if (s.myAnswer != null) Caption(tr("あなたの答え：「{0}」（全員そろうまで変更できます）", s.myAnswer))
    Caption(tr("回答済み {0} / {1}人", s.answered.size, v.players.size), Modifier.fillMaxWidth())
    HostOnly(v, "") { BigButton(tr("締め切って答え合わせ"), secondary = true) { vm.send(Intent.CloseVote) } }
}

@Composable
private fun SyncReveal(vm: AppViewModel, v: PlayerView, s: Screen.SyncReveal) {
    ArtTitle(R.drawable.icon_sync, tr("答え合わせ"))
    Panel { Text(s.prompt, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    s.groups.forEach { g ->
        val matched = g.ids.size >= 2
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.answer, style = MaterialTheme.typography.headlineMedium, color = if (matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Caption(g.ids.joinToString(tr("、")) { v.nameOf(it) })
                }
                Text(if (matched) tr("{0}人シンクロ！", g.ids.size) else tr("ひとり"), color = if (matched) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
    }
    val noAnswer = v.players.filter { p -> s.groups.none { p.id in it.ids } }
    if (noAnswer.isNotEmpty()) Caption(tr("未回答：") + noAnswer.joinToString(tr("、")) { it.name })
    Scoreboard(v, s.scores, s.gained)
    NextButtons(vm, v, tr("次のお題へ"))
}

// ================= 質問で当てろ =================

@Composable
private fun GuessPlay(vm: AppViewModel, v: PlayerView, s: Screen.GuessPlay, remaining: Long) {
    var peek by remember(s.answer) { mutableStateOf(false) }
    val iAsk = s.asker == v.me && !v.localMode
    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(R.drawable.icon_guess, 44.dp); Spacer(Modifier.size(10.dp))
        Text(tr("質問で当てろ"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        Text(formatMs(remaining), style = MaterialTheme.typography.headlineMedium, color = if (remaining < 30_000) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
    }
    when {
        iAsk -> {
            Panel {
                Caption(tr("あなたが出題者。答えは"))
                BigWord(s.answer ?: "")
            }
            Caption(tr("みんなの質問に「はい」「いいえ」「わからない」だけで答えてください。当てられたら、当てた人を押します。"))
        }
        v.localMode -> {
            Panel {
                Caption(tr("出題者は {0}さん", v.nameOf(s.asker)))
                if (peek) BigWord(s.answer ?: "") else Text("●●●", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick = { peek = !peek }, modifier = Modifier.fillMaxWidth()) {
                Text(if (peek) tr("隠す") else tr("{0}さんだけ見る", v.nameOf(s.asker)))
            }
            Caption(tr("ほかの人は「はい」「いいえ」で答えられる質問をして、答えを当てましょう"))
        }
        else -> {
            Panel {
                Caption(tr("出題者"))
                BigWord(tr("{0}さん", v.nameOf(s.asker)))
            }
            Caption(tr("「はい」「いいえ」で答えられる質問をして、出題者の答えを当てよう。例：「それは食べられますか？」「家の中にありますか？」"))
        }
    }
    if (iAsk || v.isHost) {
        Text(tr("当てた人"), style = MaterialTheme.typography.titleMedium)
        v.players.filter { it.id != s.asker }.forEach { p ->
            OutlinedButton(onClick = { vm.send(Intent.GuessSolved(p.id)) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                Text(tr("{0}さんが当てた！", p.name))
            }
        }
        BigButton(tr("降参（答えを発表）"), secondary = true) { vm.send(Intent.GuessGiveUp) }
    } else Caption(tr("当てたら、出題者が「当てた」を押します"), Modifier.fillMaxWidth())
}

@Composable
private fun GuessResult(vm: AppViewModel, v: PlayerView, s: Screen.GuessResult) {
    CenterArt(if (s.guesser != null) R.drawable.char_villagers else R.drawable.char_wolf_grin, 160.dp)
    BigWord(if (s.guesser != null) tr("{0}さんが正解！", v.nameOf(s.guesser)) else tr("時間切れ！"), if (s.guesser != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
    Panel {
        Caption(tr("答え")); Text(s.answer, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Caption(tr("出題者：{0}さん", v.nameOf(s.asker)) + if (s.guesser != null) tr("（当てた人と出題者に1点ずつ）") else "")
    }
    Scoreboard(v, s.scores)
    NextButtons(vm, v, tr("次の出題者へ"))
}

// ================= ぶっちゃけ質問 =================

@Composable
private fun QVote(vm: AppViewModel, v: PlayerView, s: Screen.QVote) {
    ArtTitle(R.drawable.icon_question, tr("ぶっちゃけ質問"))
    Panel { BigWord(s.question) }
    Caption(tr("当てはまると思う人に投票（自分もOK）。だれが誰に入れたかは表示されません。"))
    v.players.forEach { p -> ChoiceButton(p.name + if (p.id == v.me) tr("（あなた）") else "", s.myVote == p.id) { vm.send(Intent.QVote(p.id)) } }
    Caption(tr("投票済み {0} / {1}人", s.voted.size, v.players.size), Modifier.fillMaxWidth())
    HostOnly(v, "") { BigButton(tr("締め切って発表"), secondary = true) { vm.send(Intent.CloseVote) } }
}

@Composable
private fun QReveal(vm: AppViewModel, v: PlayerView, s: Screen.QReveal) {
    ArtTitle(R.drawable.icon_question, tr("発表"))
    Panel { Text(s.question, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    val top = s.tally.firstOrNull()?.votes ?: 0
    val winners = s.tally.filter { it.votes == top && top > 0 }
    CenterArt(R.drawable.char_wolf_hello, 140.dp)
    BigWord(if (winners.isEmpty()) tr("投票なし") else winners.joinToString(tr("、")) { v.nameOf(it.id) } + tr("さん！"))
    if (s.tally.isNotEmpty()) Panel {
        s.tally.forEach { Row(Modifier.padding(vertical = 3.dp)) { Text(v.nameOf(it.id), modifier = Modifier.weight(1f)); Text(tr("{0}票", it.votes), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
    }
    NextButtons(vm, v, tr("次の質問へ"))
}

// ================= NGワード =================

@Composable
private fun NgPlay(vm: AppViewModel, v: PlayerView, s: Screen.NgPlay, remaining: Long) {
    var ask by remember { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(R.drawable.icon_ngword, 44.dp); Spacer(Modifier.size(10.dp))
        Text(tr("NGワード"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        Text(formatMs(remaining), style = MaterialTheme.typography.headlineMedium, color = if (remaining < 30_000) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
    }
    if (s.myOut) Panel {
        Text(tr("アウト！"), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
        Text(tr("あなたのNGワードは「{0}」でした。ほかの人を見張りましょう", s.entries.firstOrNull { it.id == v.me }?.word))
    } else Caption(tr("みんなのNGワードが見えます（あなたのだけ見えません）。言わせたら「言った！」"))
    s.entries.forEach { e ->
        val me = e.id == v.me
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Caption(v.nameOf(e.id) + if (me) tr("（あなた）") else "")
                    Text(
                        e.word ?: tr("？？？"),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (e.out) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        textDecoration = if (e.out) TextDecoration.LineThrough else null,
                    )
                }
                when {
                    e.out && v.isHost -> TextButton(onClick = { vm.send(Intent.UndoOut(e.id)) }) { Text(tr("取り消し")) }
                    e.out -> Text(tr("アウト"), color = MaterialTheme.colorScheme.secondary)
                    !me && e.word != null -> Button(onClick = { ask = e.id }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text(tr("言った！")) }
                }
            }
        }
    }
    HostOnly(v, "") { BigButton(tr("ゲームを終える"), secondary = true) { vm.send(Intent.EndNg) } }
    ask?.let { id ->
        val word = s.entries.firstOrNull { it.id == id }?.word
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(tr("{0}さんがアウト？", v.nameOf(id))) },
            text = { Text(tr("「{0}」と言いましたか？", word)) },
            confirmButton = { TextButton(onClick = { vm.send(Intent.ReportOut(id)); ask = null }) { Text(tr("言った！")) } },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(tr("やめる")) } },
        )
    }
}

@Composable
private fun NgResult(vm: AppViewModel, v: PlayerView, s: Screen.NgResult) {
    CenterArt(if (s.winners.isEmpty()) R.drawable.char_wolf_caught else R.drawable.char_villagers, 160.dp)
    BigWord(if (s.winners.isEmpty()) tr("全員アウト！") else tr("{0}の勝ち！", s.winners.joinToString(tr("、")) { v.nameOf(it) }))
    Panel {
        Text(tr("みんなのNGワード"), style = MaterialTheme.typography.titleMedium)
        s.entries.sortedWith(compareBy({ it.out }, { -it.outOrder })).forEach { e ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text(v.nameOf(e.id), modifier = Modifier.weight(1f))
                Text(tr("「{0}」", e.word) + if (e.out) tr("　{0}番目にアウト", e.outOrder) else tr("　生き残り"),
                    color = if (e.out) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary)
            }
        }
    }
    NextButtons(vm, v)
}

// ================= 広告休憩 =================

@Composable
private fun AdBreak(vm: AppViewModel, s: Screen.AdBreak) {
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(s.breakId) {
        if (activity == null) vm.send(Intent.AdDone(s.breakId))
        else vm.ads.show(activity) { vm.send(Intent.AdDone(s.breakId)) }
    }
    Spacer(Modifier.height(80.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Art(R.drawable.icon_tea, 120.dp)
        Spacer(Modifier.height(16.dp))
        Text(tr("ちょっと休憩…"), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Caption(tr("全員の準備ができたら次へ進みます"))
    }
}
