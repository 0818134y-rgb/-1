package com.zen.partygame.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zen.partygame.game.Admin
import com.zen.partygame.game.AdminPlayer
import com.zen.partygame.game.AdminRoom

/** 管理者アプリの文言（管理者は日本語だけなので、訳のリストには入れない） */
internal fun ja(key: String, vararg args: Any?): String = tr(key, *args)

/** 管理者アプリの画面（-Padmin で作ったアプリだけ） */
@Composable
fun AdminApp(admin: AdminViewModel, vm: AppViewModel) {
    val ui by admin.ui.collectAsState()
    val appUi by vm.ui.collectAsState()
    // 参加者の画面を見ているとき（見るだけ。押しても何も送らない）
    if (vm.onWatchExit != null && appUi is Ui.Room) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ja("👁 見ているだけです（操作はできません）"), color = Lantern, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { admin.onRoom = null; vm.stopWatching() }) { Text(ja("管理画面に戻る")) }
            }
            RoomScreen(vm, appUi as Ui.Room)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (val s = ui) {
            AdminUi.Home -> AdminHome(admin)
            is AdminUi.Connecting -> {
                Text(ja("🛡 部屋 {0} に入っています…", s.code), style = MaterialTheme.typography.headlineMedium)
                CircularProgressIndicator()
                OutlinedButton(onClick = { admin.disconnect() }) { Text(ja("やめる")) }
            }
            is AdminUi.Room -> AdminRoomScreen(admin, vm, s.room)
            AdminUi.Bans -> AdminBans(admin)
        }
    }
}

@Composable
private fun AdminHome(admin: AdminViewModel) {
    val last by admin.lastCode.collectAsState()
    var code by rememberSaveable { mutableStateOf(last) }
    Text(ja("🛡 わちゃパ管理"), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
    Caption(ja("オンラインの部屋コードを入れると、管理者としてその部屋に入れます（ゲーム中・満員でも入れます）。入ると部屋の全員に「管理者が確認しています」と表示されます。"))
    OutlinedTextField(
        value = code, onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) }, singleLine = true,
        label = { Text(ja("部屋コード（6けた）")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    BigButton(ja("管理者として入る"), enabled = code.length == 6) { admin.connect(code) }
    BigButton(ja("全部屋共通の BAN リスト"), secondary = true) { admin.openBans() }
    Caption(ja("このアプリには管理者の鍵が入っています。人に渡したり、どこかに公開したりしないでください。近くで（Bluetooth）の部屋には入れません。"))
}

@Composable
private fun AdminRoomScreen(admin: AdminViewModel, vm: AppViewModel, r: AdminRoom) {
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var banTarget by remember { mutableStateOf<AdminPlayer?>(null) }
    var notice by rememberSaveable { mutableStateOf("") }
    confirm?.let { (text, run) ->
        AlertDialog(
            onDismissRequest = { confirm = null }, title = { Text(text) },
            confirmButton = { TextButton(onClick = { confirm = null; run() }) { Text(ja("実行する")) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(ja("やめる")) } },
        )
    }
    banTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { banTarget = null }, title = { Text(ja("{0}さんを BAN しますか？", p.name)) },
            text = { Text(ja("「この部屋だけ」はこの部屋に二度と入れなくします。「全部屋共通」は、どの部屋にも入れず、オンラインの部屋も作れなくします（GitHub のトークンが必要）。")) },
            confirmButton = { TextButton(onClick = { banTarget = null; admin.ban(p, global = true) }) { Text(ja("全部屋共通")) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { banTarget = null }) { Text(ja("やめる")) }
                    TextButton(onClick = { banTarget = null; admin.ban(p, global = false) }) { Text(ja("この部屋だけ")) }
                }
            },
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(ja("🛡 部屋 {0}", r.roomCode), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { admin.disconnect() }) { Text(ja("出る")) }
    }
    Caption(ja("ゲーム：{0} ／ 画面：{1} ／ ホスト：{2}", tr(r.game.label), r.screen, if (r.hostApp == "web") ja("ブラウザ") else ja("Androidアプリ")))

    Panel {
        Text(ja("参加者 {0}人", r.players.size), style = MaterialTheme.typography.titleMedium)
        r.players.forEach { p ->
            Spacer(Modifier.height(8.dp))
            Text((if (p.isHost) "👑 " else "・ ") + p.name, style = MaterialTheme.typography.titleMedium)
            Caption(ja("端末番号 {0}", p.device.take(12).ifEmpty { "—" }) + if (r.watching == p.id) ja("（いま見ている）") else "")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    admin.op(Admin.Op.WATCH, p.id)
                    r.view?.let { v -> if (r.watching == p.id) vm.watch(v) { admin.onRoom = null; vm.stopWatching() } }
                    admin.onRoom = { room -> if (room.watching == p.id) room.view?.let { v -> vm.watch(v) { admin.onRoom = null; vm.stopWatching() } } }
                }) { Text(ja("画面を見る")) }
                OutlinedButton(onClick = {
                    confirm = (if (p.isHost) ja("ホストを退出させると部屋が閉じます。{0}さんを退出させますか？", p.name) else ja("{0}さんを退出させますか？", p.name)) to { admin.op(Admin.Op.KICK, p.id) }
                }) { Text(ja("退出")) }
                OutlinedButton(onClick = { banTarget = p }) { Text(ja("BAN"), color = Berry) }
            }
        }
    }

    Panel {
        Text(ja("部屋の操作"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(ja("次へ進める（締め切る・飛ばす）"), secondary = true) { admin.op(Admin.Op.NEXT) }
            BigButton(ja("ゲームを止めてロビーへ"), secondary = true) { confirm = ja("ゲームを止めてロビーに戻しますか？") to { admin.op(Admin.Op.LOBBY) } }
            BigButton(ja("部屋を閉じる（全員の接続を切る）")) { confirm = ja("部屋を閉じますか？ 全員の接続が切れます") to { admin.op(Admin.Op.CLOSE) } }
        }
    }

    Panel {
        Text(ja("全員にお知らせ"), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = notice, onValueChange = { notice = it.take(100) }, label = { Text(ja("例：荒らし行為はやめてください")) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        BigButton(ja("お知らせを送る"), enabled = notice.isNotBlank()) { admin.op(Admin.Op.NOTICE, text = notice); notice = "" }
    }

    if (r.banned.isNotEmpty()) Panel {
        Text(ja("この部屋で BAN した端末"), style = MaterialTheme.typography.titleMedium)
        r.banned.forEach { h ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.take(12) + "  " + admin.note(h), modifier = Modifier.weight(1f))
                TextButton(onClick = { admin.op(Admin.Op.UNBAN, h) }) { Text(ja("取り消す")) }
            }
        }
    }
    BigButton(ja("全部屋共通の BAN リスト"), secondary = true) { admin.openBans() }
}

@Composable
private fun AdminBans(admin: AdminViewModel) {
    val list by admin.bans.collectAsState()
    val busy by admin.busy.collectAsState()
    val token by admin.token.collectAsState()
    var tokenText by rememberSaveable { mutableStateOf(token) }
    var hash by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }
    confirm?.let { h ->
        AlertDialog(
            onDismissRequest = { confirm = null }, title = { Text(ja("BAN を解除しますか？")) }, text = { Text(h.take(12) + "  " + admin.note(h)) },
            confirmButton = { TextButton(onClick = { confirm = null; admin.unbanGlobal(h) }) { Text(ja("解除する")) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(ja("やめる")) } },
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(ja("全部屋共通の BAN"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { admin.closeBans() }) { Text(ja("戻る")) }
    }
    Caption(ja("ここにある端末は、どの部屋にも入れず、オンラインの部屋も作れません。wachapa.com の bans.json に、端末番号を元に戻せない形（ハッシュ）で保存されます。"))
    if (busy) CircularProgressIndicator()
    Panel {
        val bans = list?.bans.orEmpty()
        Text(ja("{0}件", bans.size), style = MaterialTheme.typography.titleMedium)
        bans.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.h.take(12) + "…  " + admin.note(b.h))
                    Caption(b.at)
                }
                TextButton(onClick = { confirm = b.h }) { Text(ja("解除")) }
            }
        }
        if (bans.isEmpty() && !busy) Caption(ja("まだありません"))
    }
    Panel {
        Text(ja("端末番号で BAN する"), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = hash, onValueChange = { hash = it.trim().take(64) }, singleLine = true, label = { Text(ja("端末番号（64文字）")) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        BigButton(ja("追加する"), enabled = hash.length == 64) { admin.addBanByHash(hash); hash = "" }
    }
    Panel {
        Text(ja("GitHub のトークン"), style = MaterialTheme.typography.titleMedium)
        Caption(ja("BAN リストの書きかえに使います。GitHub の Settings → Developer settings → Fine-grained tokens で、リポジトリ「-」だけに Contents の Read and write を許可したトークンを作って貼りつけてください。この端末にだけ保存されます。"))
        OutlinedTextField(
            value = tokenText, onValueChange = { tokenText = it.trim() }, singleLine = true, label = { Text(ja("github_pat_…")) },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(ja("保存"), enabled = tokenText != token, modifier = Modifier.weight(1f)) { admin.setToken(tokenText); admin.loadBans() }
            Spacer(Modifier.width(0.dp))
        }
    }
    OutlinedButton(onClick = { admin.loadBans() }) { Text(ja("読み直す")) }
}
