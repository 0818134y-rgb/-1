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
    val ctx = LocalContext.current
    DisposableEffect(Unit) {  // 遊んでいる間は画面を消さない
        val w = ctx.findActivity()?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler { confirmLeave = true }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text(if (v.isHost && !v.localMode) "部屋を閉じますか？" else "部屋を出ますか？") },
        text = { if (v.isHost && !v.localMode) Text("全員の接続が切れます") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leave() }) { Text("出る") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("やめる") } },
    )

    val withBanner = when (v.screen) {
        Screen.Lobby, is Screen.ArenaResult, is Screen.WwResult, is Screen.NgResult, is Screen.SyncReveal, is Screen.GuessResult, is Screen.QReveal -> true
        else -> false
    }
    val scroll = rememberScrollState()
    // お絵かき伝言の発表：新しいコマがめくられたら下までスクロール
    val revealKey = (v.screen as? Screen.DrawReveal)?.let { it.book * 100 + it.entries.size }
    LaunchedEffect(revealKey) { if (revealKey != null) { delay(120); scroll.animateScrollTo(scroll.maxValue) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (v.localMode) "1台モード" else (if (state.online) "🌐 " else "📶 ") + "部屋 ${v.roomCode}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            TextButton(onClick = { confirmLeave = true }) { Text("退出") }
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
                is Screen.ArenaResult -> ArenaResult(vm, v, s) { NextButtons(vm, v, "もう一戦") }
                is Screen.AdBreak -> AdBreak(vm, s)
                is Screen.DrawTask -> DrawTask(vm, v, s, rememberRemainingMs(v, state.receivedAt))
                is Screen.DrawReveal -> DrawReveal(vm, v, s) { NextButtons(vm, v, "もう一回") }
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
}

private fun gameSummary(st: Settings): String = when (st.game) {
    GameType.ARENA -> (if (st.arenaTurnSec == 0) "手番の制限なし" else "手番 ${st.arenaTurnSec}秒") + (if (st.arenaChaos) "・今夜のルールあり" else "")
    GameType.WORD_WOLF -> "話し合い ${st.talkSec / 60}分"
    GameType.SPY -> "質問タイム ${st.talkSec / 60}分"
    GameType.NG_WORD -> "制限時間 ${st.ngSec / 60}分"
    GameType.SYNC -> if (st.syncSec == 0) "回答時間 制限なし" else "回答時間 ${st.syncSec}秒"
    GameType.GUESS -> "制限時間 ${st.guessSec / 60}分"
    GameType.QUESTION -> "全員で匿名投票"
    GameType.DRAW -> "描く ${st.drawSec}秒・書く ${st.writeSec}秒"
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
        Caption(if (online) "オンラインの部屋コード。参加する人は「オンライン」→「部屋に参加する」でこの数字を入れます（PC版からもOK）"
            else "近くの人は「部屋に参加する」からこの番号を選んでください")
        Text(v.roomCode, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (online && v.isHost) BigButton("招待を送る（LINEなど）", secondary = true) { vm.shareInvite(v.roomCode) }
    }
    Panel {
        Text("参加者 ${v.players.size}人", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        v.players.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((if (p.isHost) "👑 " else "・ ") + p.name + (if (p.id == v.me) "（あなた）" else ""), modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                if (v.isHost && !v.localMode && !p.isHost) TextButton(onClick = { vm.send(Intent.Kick(p.id)) }) { Text("退出させる") }
            }
        }
    }
    if (v.isHost) {
        Panel {
            Text("ゲーム", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            if (!v.localMode) {
                FeaturedGame(st.game == GameType.ARENA) { set(st.copy(game = GameType.ARENA)) }
                Spacer(Modifier.height(10.dp))
            }
            val games = GameType.entries.filter { (!v.localMode || it.localOk) && it != GameType.ARENA }
            games.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    row.forEach { g -> GameCard(gameIcon(g), g.short, st.game == g, Modifier.weight(1f)) { set(st.copy(game = g)) } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(4.dp))
            when (st.game) {
                GameType.ARENA -> {
                    ChipRow("1手番の制限時間（過ぎると勝手にカードが出る）") {
                        listOf(30 to "30秒", 60 to "60秒", 90 to "90秒", 0 to "なし").forEach { (sec, label) ->
                            FilterChip(st.arenaTurnSec == sec, { set(st.copy(arenaTurnSec = sec)) }, { Text(label) })
                        }
                    }
                    ChipRow("今夜のルール（開始時にランダムで1つ追加）") {
                        FilterChip(st.arenaChaos, { set(st.copy(arenaChaos = true)) }, { Text("あり") })
                        FilterChip(!st.arenaChaos, { set(st.copy(arenaChaos = false)) }, { Text("なし（初めての人向け）") })
                    }
                }
                GameType.WORD_WOLF, GameType.SPY -> {
                    val role = if (st.game == GameType.SPY) "スパイ" else "ウルフ"
                    ChipRow(if (st.game == GameType.SPY) "質問タイム" else "話し合いの時間") {
                        listOf(120, 180, 240, 300, 420).forEach { sec -> FilterChip(st.talkSec == sec, { set(st.copy(talkSec = sec)) }, { Text("${sec / 60}分") }) }
                    }
                    ChipRow("${role}の人数") {
                        listOf(0 to "おまかせ", 1 to "1人", 2 to "2人").forEach { (n, label) -> FilterChip(st.wolves == n, { set(st.copy(wolves = n)) }, { Text(label) }) }
                    }
                    if (st.game == GameType.WORD_WOLF) ChipRow("お題のジャンル（選ばなければ全部）") {
                        v.categories.forEach { c ->
                            val on = c in st.categories
                            FilterChip(on, { set(st.copy(categories = if (on) st.categories - c else st.categories + c)) }, { Text(c) })
                        }
                    }
                }
                GameType.NG_WORD -> {
                    ChipRow("制限時間") {
                        listOf(180, 300, 600).forEach { sec -> FilterChip(st.ngSec == sec, { set(st.copy(ngSec = sec)) }, { Text("${sec / 60}分") }) }
                    }
                    ChipRow("ワードの難しさ") {
                        listOf(NgLevel.NORMAL to "ふつう", NgLevel.HARD to "ハード（口ぐせ）", NgLevel.MIX to "ミックス").forEach { (lv, label) ->
                            FilterChip(st.ngLevel == lv, { set(st.copy(ngLevel = lv)) }, { Text(label) })
                        }
                    }
                }
                GameType.SYNC -> ChipRow("回答時間") {
                    listOf(30 to "30秒", 60 to "60秒", 90 to "90秒", 0 to "制限なし").forEach { (sec, label) ->
                        FilterChip(st.syncSec == sec, { set(st.copy(syncSec = sec)) }, { Text(label) })
                    }
                }
                GameType.GUESS -> ChipRow("制限時間") {
                    listOf(120, 180, 300).forEach { sec -> FilterChip(st.guessSec == sec, { set(st.copy(guessSec = sec)) }, { Text("${sec / 60}分") }) }
                }
                GameType.QUESTION -> Caption("設定はありません。だれが誰に入れたかは表示されません。")
                GameType.DRAW -> {
                    Caption("お題を書く → 次の人が絵にする → 次の人がその絵を見て答える…を全員分くり返し、最後にどう伝わったかを発表します（3〜8人）。")
                    ChipRow("絵を描く時間") {
                        listOf(60, 90, 120, 180).forEach { sec -> FilterChip(st.drawSec == sec, { set(st.copy(drawSec = sec)) }, { Text("${sec}秒") }) }
                    }
                    ChipRow("お題を書く・答える時間") {
                        listOf(30, 45, 60, 90).forEach { sec -> FilterChip(st.writeSec == sec, { set(st.copy(writeSec = sec)) }, { Text("${sec}秒") }) }
                    }
                }
            }
        }
        val need = st.game.minPlayers
        BigButton(if (v.players.size >= need) "はじめる" else "${need}人からはじめられます", enabled = v.players.size >= need, icon = gameIcon(st.game)) { vm.send(Intent.Start) }
    } else {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Art(gameIcon(st.game), 56.dp)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(st.game.label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Caption(gameSummary(st))
                }
            }
        }
        Caption("ホストがはじめるのを待っています…", Modifier.fillMaxWidth())
    }
}

/** 一押しゲームの大きなボタン（ロビーの一番上） */
@Composable
private fun FeaturedGame(selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(22.dp))
            .border(BorderStroke(if (selected) 4.dp else 2.dp, if (selected) Lantern else Berry), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        Image(painterResource(R.drawable.arena_hero), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Row(
            Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Berry).padding(horizontal = 10.dp, vertical = 4.dp),
        ) { Text("一押し！ NEW", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Black) }
        if (selected) Row(
            Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Lantern).padding(horizontal = 10.dp, vertical = 4.dp),
        ) { Text("✓ 選択中", color = Night, fontWeight = FontWeight.Black) }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xE015102D)).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Art(R.drawable.icon_arena, 36.dp); Spacer(Modifier.size(8.dp))
                Text("なんでもアリーナ", style = MaterialTheme.typography.headlineMedium, color = Lantern)
            }
            Text("何が起きるかわからない理不尽カードバトル（2〜8人）", style = MaterialTheme.typography.bodyMedium, color = Soft)
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
            .clickable(onClick = onClick)
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
private fun PlayerView.roleName() = if (settings.game == GameType.SPY) "スパイ" else "ウルフ"
private fun PlayerView.isSpyGame() = settings.game == GameType.SPY

// ================= ワードウルフ／スパイ =================

@Composable
private fun SpyCard() {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Art(R.drawable.icon_spy, 64.dp); Spacer(Modifier.size(12.dp))
            Column {
                Text("あなたはスパイ", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
                Caption("みんなの質問や答えから場所を推理しよう。バレないように！")
            }
        }
    }
}

@Composable
private fun WwReveal(vm: AppViewModel, v: PlayerView, s: Screen.WwReveal) {
    val done = v.me in s.confirmed
    val spyGame = v.isSpyGame()
    ArtTitle(gameIcon(v.settings.game), if (spyGame) "場所" else "お題")
    if (s.spy) SpyCard() else {
        Caption(if (spyGame) "あなたがいる場所（ほかの人に見せないで）" else "あなたのお題（ほかの人に見せないで）")
        Panel { BigWord(s.word) }
        Caption(
            if (spyGame) "全員同じ場所にいますが、1人だけ場所を知らないスパイがまぎれています。場所がバレないように質問し合いましょう。"
            else "多数派（市民）か少数派（ウルフ）かは、まだ誰にも分かりません。話しながら探りましょう。",
        )
    }
    BigButton(if (done) "確認しました" else "確認した", enabled = !done) { vm.send(Intent.ConfirmWord()) }
    Caption("確認済み ${s.confirmed.size} / ${v.players.size}人", Modifier.fillMaxWidth())
}

@Composable
private fun WwRevealLocal(vm: AppViewModel, v: PlayerView, s: Screen.WwRevealLocal) {
    val next = s.entries.firstOrNull { it.id !in s.confirmed } ?: return
    var showing by rememberSaveable(next.id) { mutableStateOf(false) }
    val name = v.nameOf(next.id)
    ArtTitle(R.drawable.icon_pass, if (v.isSpyGame()) "順番に場所を見る" else "順番にお題を見る")
    Caption("${s.confirmed.size + 1} / ${s.entries.size}人目")
    Panel {
        Text("${name}さんの番", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        when {
            !showing -> Caption("${name}さん以外は画面を見ないでください", Modifier.fillMaxWidth())
            next.spy -> BigWord("あなたはスパイ", MaterialTheme.colorScheme.secondary)
            else -> BigWord(next.word)
        }
    }
    if (!showing) BigButton(if (v.isSpyGame()) "場所を見る" else "お題を見る") { showing = true }
    else BigButton("覚えた（隠して次の人へ）") { vm.send(Intent.ConfirmWord(forPlayer = next.id)) }
}

@Composable
private fun WwTalk(vm: AppViewModel, v: PlayerView, s: Screen.WwTalk, remaining: Long) {
    var peek by remember { mutableStateOf(false) }
    val spyGame = v.isSpyGame()
    Text(if (spyGame) "質問タイム" else "話し合い", style = MaterialTheme.typography.headlineMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Art(R.drawable.icon_timer, 64.dp)
        Spacer(Modifier.size(12.dp))
        Text(formatMs(remaining), style = MaterialTheme.typography.displayLarge, color = if (remaining < 30_000) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
    }
    when {
        s.spy -> SpyCard()
        s.word != null -> Panel(Modifier.clickable { peek = !peek }) {
            Caption(if (spyGame) "あなたがいる場所（タップで表示／非表示）" else "あなたのお題（タップで表示／非表示）")
            if (peek) BigWord(s.word ?: "") else Text("●●●", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        else -> Caption(if (spyGame) "順番に質問し合って、場所を知らないスパイを探しましょう" else "お題について話し合って、ウルフ（違うお題の人）を探しましょう")
    }
    if (spyGame) Caption("例：「ここには何をしに来ますか？」「今どんな服を着ていますか？」")
    HostOnly(v, "時間になると投票に進みます") {
        BigButton("切り上げて投票へ", secondary = true) { vm.send(Intent.EndTalk) }
    }
}

@Composable
private fun WwVote(vm: AppViewModel, v: PlayerView, s: Screen.WwVote) {
    val role = v.roleName()
    ArtTitle(R.drawable.icon_vote, if (s.runoff) "決選投票" else "投票")
    Caption(if (s.runoff) "同票だった人の中から、${role}だと思う人を選んでください" else "${role}だと思う人を選んでください")
    s.candidates.filter { it != v.me }.forEach { id -> ChoiceButton(v.nameOf(id), s.myVote == id) { vm.send(Intent.Vote(id)) } }
    Caption("投票済み ${s.voted.size} / ${v.players.size}人" + if (s.myVote != null) "（全員そろうまで変更できます）" else "", Modifier.fillMaxWidth())
    HostOnly(v, "") { BigButton("締め切って集計", secondary = true) { vm.send(Intent.CloseVote) } }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
        colors = if (selected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
    ) { Text((if (selected) "✓ " else "") + label) }
}

@Composable
private fun WwAccuse(vm: AppViewModel, v: PlayerView, s: Screen.WwAccuse) {
    ArtTitle(R.drawable.icon_vote, "投票")
    Caption("「せーの」で${v.roleName()}だと思う人を指さして、いちばん多く指された人を選んでください（同票なら話し合って1人に）")
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
    Text("${role}発見！", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Panel {
        Caption("${role}は")
        BigWord(s.wolves.joinToString("、") { v.nameOf(it) }, MaterialTheme.colorScheme.secondary)
    }
    if (v.isSpyGame()) {
        val canAnswer = s.iAmWolf || (v.localMode && v.isHost)
        Text(if (canAnswer) "最後のチャンス！場所を当てれば逆転勝ち。下から選んでください。" else "スパイが場所を当てたら逆転されます。スパイが選ぶのを待っています…")
        if (canAnswer) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.options.forEach { o -> OutlinedButton(onClick = { vm.send(Intent.SpyGuess(o)) }, shape = RoundedCornerShape(12.dp)) { Text(o) } }
        }
        return
    }
    Text(
        if (s.iAmWolf) "最後のチャンス！市民のお題を当てれば逆転勝ち。口で答えてください。"
        else "でも、ウルフが市民のお題を当てたら逆転されます。ウルフに答えてもらいましょう。",
    )
    HostOnly(v, "ホストが判定します…") {
        Caption("ウルフの答えは市民のお題と…")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.send(Intent.Judge(true)) }, Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("合っていた") }
            Button(onClick = { vm.send(Intent.Judge(false)) }, Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) { Text("違った") }
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
        if (citizenWin) (if (spy) "みんなの勝ち！" else "市民の勝ち！") else "${role}の勝ち！",
        if (citizenWin) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
    )
    Caption(
        when (s.reason) {
            ResultReason.WOLF_CAUGHT -> if (spy) "スパイを見つけて、場所も守りきりました" else "ウルフを見つけて、逆転も防ぎました"
            ResultReason.WOLF_GUESSED -> if (spy) "スパイが場所を当てて逆転！" else "ウルフが市民のお題を当てて逆転！"
            ResultReason.CITIZEN_ACCUSED -> "${v.nameOf(s.accused)}さん（${role}ではない）が最多票に…${role}の逃げ切り"
            ResultReason.TIE -> "票が割れて、${role}の逃げ切り"
        },
        Modifier.fillMaxWidth(),
    )
    Panel {
        if (spy) {
            Caption("場所"); Text(s.citizenWord, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary)
            s.spyGuess?.let { sg -> Spacer(Modifier.height(8.dp)); Caption("スパイの答え"); Text(sg, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary) }
        } else {
            Caption("市民のお題"); Text(s.citizenWord, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(8.dp))
            Caption("ウルフのお題"); Text(s.wolfWord, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(8.dp))
        Caption(role); Text(s.wolves.joinToString("、") { v.nameOf(it) }, style = MaterialTheme.typography.titleLarge)
        if (s.myWord != null && !spy) { Spacer(Modifier.height(8.dp)); Caption("あなたのお題：${s.myWord}") }
    }
    if (s.tally.isNotEmpty()) Panel {
        Text("得票", style = MaterialTheme.typography.titleMedium)
        s.tally.forEach { Text("${v.nameOf(it.id)}　${it.votes}票") }
    }
    NextButtons(vm, v)
}

@Composable
private fun NextButtons(vm: AppViewModel, v: PlayerView, nextLabel: String = "もう一回") {
    HostOnly(v, "ホストが次に進むのを待っています…") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(nextLabel) { vm.send(Intent.Next) }
            BigButton(if (v.localMode) "設定をかえる" else "ロビーへ（ゲームを変える）", secondary = true) { vm.send(Intent.BackToLobby) }
        }
    }
}

@Composable
private fun Scoreboard(v: PlayerView, scores: List<VoteCount>, gained: List<VoteCount> = emptyList()) {
    Panel {
        Text("得点", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        scores.forEachIndexed { i, sc ->
            val g = gained.firstOrNull { it.id == sc.id }?.votes ?: 0
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("${i + 1}. ${v.nameOf(sc.id)}" + if (sc.id == v.me) "（あなた）" else "", modifier = Modifier.weight(1f))
                if (g > 0) Text("+$g  ", color = MaterialTheme.colorScheme.tertiary)
                Text("${sc.votes}点", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
        Text("みんなでシンクロ", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        if (v.remainingMs != null) Text(formatMs(remaining), style = MaterialTheme.typography.headlineMedium,
            color = if (remaining < 10_000) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
    }
    Caption("みんなと同じ答えになるほど得点！")
    Panel { BigWord(s.prompt) }
    OutlinedTextField(
        value = text, onValueChange = { text = it.take(MAX_ANSWER_LEN) }, singleLine = true,
        label = { Text("あなたの答え") }, modifier = Modifier.fillMaxWidth(),
    )
    BigButton(if (s.myAnswer == null) "これで答える" else "答えを変える", enabled = text.isNotBlank() && text.trim() != s.myAnswer) {
        vm.send(Intent.SyncAnswer(text))
    }
    if (s.myAnswer != null) Caption("あなたの答え：「${s.myAnswer}」（全員そろうまで変更できます）")
    Caption("回答済み ${s.answered.size} / ${v.players.size}人", Modifier.fillMaxWidth())
    HostOnly(v, "") { BigButton("締め切って答え合わせ", secondary = true) { vm.send(Intent.CloseVote) } }
}

@Composable
private fun SyncReveal(vm: AppViewModel, v: PlayerView, s: Screen.SyncReveal) {
    ArtTitle(R.drawable.icon_sync, "答え合わせ")
    Panel { Text(s.prompt, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    s.groups.forEach { g ->
        val matched = g.ids.size >= 2
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.answer, style = MaterialTheme.typography.headlineMedium, color = if (matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Caption(g.ids.joinToString("、") { v.nameOf(it) })
                }
                Text(if (matched) "${g.ids.size}人シンクロ！" else "ひとり", color = if (matched) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
    }
    val noAnswer = v.players.filter { p -> s.groups.none { p.id in it.ids } }
    if (noAnswer.isNotEmpty()) Caption("未回答：" + noAnswer.joinToString("、") { it.name })
    Scoreboard(v, s.scores, s.gained)
    NextButtons(vm, v, "次のお題へ")
}

// ================= 質問で当てろ =================

@Composable
private fun GuessPlay(vm: AppViewModel, v: PlayerView, s: Screen.GuessPlay, remaining: Long) {
    var peek by remember(s.answer) { mutableStateOf(false) }
    val iAsk = s.asker == v.me && !v.localMode
    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(R.drawable.icon_guess, 44.dp); Spacer(Modifier.size(10.dp))
        Text("質問で当てろ", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        Text(formatMs(remaining), style = MaterialTheme.typography.headlineMedium, color = if (remaining < 30_000) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
    }
    when {
        iAsk -> {
            Panel {
                Caption("あなたが出題者。答えは")
                BigWord(s.answer ?: "")
            }
            Caption("みんなの質問に「はい」「いいえ」「わからない」だけで答えてください。当てられたら、当てた人を押します。")
        }
        v.localMode -> {
            Panel {
                Caption("出題者は ${v.nameOf(s.asker)}さん")
                if (peek) BigWord(s.answer ?: "") else Text("●●●", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick = { peek = !peek }, modifier = Modifier.fillMaxWidth()) {
                Text(if (peek) "隠す" else "${v.nameOf(s.asker)}さんだけ見る")
            }
            Caption("ほかの人は「はい」「いいえ」で答えられる質問をして、答えを当てましょう")
        }
        else -> {
            Panel {
                Caption("出題者")
                BigWord("${v.nameOf(s.asker)}さん")
            }
            Caption("「はい」「いいえ」で答えられる質問をして、出題者の答えを当てよう。例：「それは食べられますか？」「家の中にありますか？」")
        }
    }
    if (iAsk || v.isHost) {
        Text("当てた人", style = MaterialTheme.typography.titleMedium)
        v.players.filter { it.id != s.asker }.forEach { p ->
            OutlinedButton(onClick = { vm.send(Intent.GuessSolved(p.id)) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                Text("${p.name}さんが当てた！")
            }
        }
        BigButton("降参（答えを発表）", secondary = true) { vm.send(Intent.GuessGiveUp) }
    } else Caption("当てたら、出題者が「当てた」を押します", Modifier.fillMaxWidth())
}

@Composable
private fun GuessResult(vm: AppViewModel, v: PlayerView, s: Screen.GuessResult) {
    CenterArt(if (s.guesser != null) R.drawable.char_villagers else R.drawable.char_wolf_grin, 160.dp)
    BigWord(if (s.guesser != null) "${v.nameOf(s.guesser)}さんが正解！" else "時間切れ！", if (s.guesser != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
    Panel {
        Caption("答え"); Text(s.answer, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Caption("出題者：${v.nameOf(s.asker)}さん" + if (s.guesser != null) "（当てた人と出題者に1点ずつ）" else "")
    }
    Scoreboard(v, s.scores)
    NextButtons(vm, v, "次の出題者へ")
}

// ================= ぶっちゃけ質問 =================

@Composable
private fun QVote(vm: AppViewModel, v: PlayerView, s: Screen.QVote) {
    ArtTitle(R.drawable.icon_question, "ぶっちゃけ質問")
    Panel { BigWord(s.question) }
    Caption("当てはまると思う人に投票（自分もOK）。だれが誰に入れたかは表示されません。")
    v.players.forEach { p -> ChoiceButton(p.name + if (p.id == v.me) "（あなた）" else "", s.myVote == p.id) { vm.send(Intent.QVote(p.id)) } }
    Caption("投票済み ${s.voted.size} / ${v.players.size}人", Modifier.fillMaxWidth())
    HostOnly(v, "") { BigButton("締め切って発表", secondary = true) { vm.send(Intent.CloseVote) } }
}

@Composable
private fun QReveal(vm: AppViewModel, v: PlayerView, s: Screen.QReveal) {
    ArtTitle(R.drawable.icon_question, "発表")
    Panel { Text(s.question, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    val top = s.tally.firstOrNull()?.votes ?: 0
    val winners = s.tally.filter { it.votes == top && top > 0 }
    CenterArt(R.drawable.char_wolf_hello, 140.dp)
    BigWord(if (winners.isEmpty()) "投票なし" else winners.joinToString("、") { v.nameOf(it.id) } + "さん！")
    if (s.tally.isNotEmpty()) Panel {
        s.tally.forEach { Row(Modifier.padding(vertical = 3.dp)) { Text(v.nameOf(it.id), modifier = Modifier.weight(1f)); Text("${it.votes}票", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
    }
    NextButtons(vm, v, "次の質問へ")
}

// ================= NGワード =================

@Composable
private fun NgPlay(vm: AppViewModel, v: PlayerView, s: Screen.NgPlay, remaining: Long) {
    var ask by remember { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(R.drawable.icon_ngword, 44.dp); Spacer(Modifier.size(10.dp))
        Text("NGワード", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        Text(formatMs(remaining), style = MaterialTheme.typography.headlineMedium, color = if (remaining < 30_000) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
    }
    if (s.myOut) Panel {
        Text("アウト！", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
        Text("あなたのNGワードは「${s.entries.firstOrNull { it.id == v.me }?.word}」でした。ほかの人を見張りましょう")
    } else Caption("みんなのNGワードが見えます（あなたのだけ見えません）。言わせたら「言った！」")
    s.entries.forEach { e ->
        val me = e.id == v.me
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Caption(v.nameOf(e.id) + if (me) "（あなた）" else "")
                    Text(
                        e.word ?: "？？？",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (e.out) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        textDecoration = if (e.out) TextDecoration.LineThrough else null,
                    )
                }
                when {
                    e.out && v.isHost -> TextButton(onClick = { vm.send(Intent.UndoOut(e.id)) }) { Text("取り消し") }
                    e.out -> Text("アウト", color = MaterialTheme.colorScheme.secondary)
                    !me && e.word != null -> Button(onClick = { ask = e.id }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("言った！") }
                }
            }
        }
    }
    HostOnly(v, "") { BigButton("ゲームを終える", secondary = true) { vm.send(Intent.EndNg) } }
    ask?.let { id ->
        val word = s.entries.firstOrNull { it.id == id }?.word
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text("${v.nameOf(id)}さんがアウト？") },
            text = { Text("「$word」と言いましたか？") },
            confirmButton = { TextButton(onClick = { vm.send(Intent.ReportOut(id)); ask = null }) { Text("言った！") } },
            dismissButton = { TextButton(onClick = { ask = null }) { Text("やめる") } },
        )
    }
}

@Composable
private fun NgResult(vm: AppViewModel, v: PlayerView, s: Screen.NgResult) {
    CenterArt(if (s.winners.isEmpty()) R.drawable.char_wolf_caught else R.drawable.char_villagers, 160.dp)
    BigWord(if (s.winners.isEmpty()) "全員アウト！" else "${s.winners.joinToString("、") { v.nameOf(it) }}の勝ち！")
    Panel {
        Text("みんなのNGワード", style = MaterialTheme.typography.titleMedium)
        s.entries.sortedWith(compareBy({ it.out }, { -it.outOrder })).forEach { e ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text(v.nameOf(e.id), modifier = Modifier.weight(1f))
                Text("「${e.word}」" + if (e.out) "　${e.outOrder}番目にアウト" else "　生き残り",
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
        Text("ちょっと休憩…", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Caption("全員の準備ができたら次へ進みます")
    }
}
