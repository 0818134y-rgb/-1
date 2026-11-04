package com.zen.partygame.ui

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zen.partygame.BuildConfig
import com.zen.partygame.game.Admin
import com.zen.partygame.game.AdminPlayer
import com.zen.partygame.game.AdminRoom
import com.zen.partygame.game.BanEntry
import com.zen.partygame.game.BanList
import com.zen.partygame.game.HostMsg
import com.zen.partygame.game.Intent
import com.zen.partygame.game.WireJson
import com.zen.partygame.net.Guard
import com.zen.partygame.net.NetEvent
import com.zen.partygame.net.OnlineLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface AdminUi {
    data object Home : AdminUi
    data class Connecting(val code: String) : AdminUi
    data class Room(val room: AdminRoom) : AdminUi
    data object Bans : AdminUi
}

/**
 * 管理者アプリ（gradle assembleRelease -Padmin で作るものだけで動く）。
 * 部屋コードで、署名つきの申し込みをしてどの部屋にも入り、退出・BAN・部屋を閉じる・ロビーへ戻す・次へ進める・お知らせ・画面を見る、ができる。
 * 全部屋共通の BAN は wachapa.com/bans.json を GitHub 経由で書きかえる（GitHub のトークンはこのアプリの設定に入れる）。
 */
class AdminViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("admin", Context.MODE_PRIVATE)
    private val _ui = MutableStateFlow<AdminUi>(AdminUi.Home)
    val ui: StateFlow<AdminUi> = _ui
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages
    val lastCode = MutableStateFlow(prefs.getString("code", "") ?: "")
    val token = MutableStateFlow(prefs.getString("token", "") ?: "")
    /** 全部屋共通の BAN リスト（読み込んだもの） */
    val bans = MutableStateFlow<BanList?>(null)
    val busy = MutableStateFlow(false)

    private var link: OnlineLink? = null
    private var linkJob: Job? = null

    private fun say(t: String) { _messages.tryEmit(t) }

    fun setToken(v: String) { token.value = v.trim(); prefs.edit().putString("token", v.trim()).apply() }

    /** BAN した人のメモ（名前など）。公開のリストには入れず、この端末だけに残す */
    fun note(hash: String): String = prefs.getString("note_$hash", "") ?: ""
    private fun setNote(hash: String, text: String) = prefs.edit().putString("note_$hash", text).apply()

    // ================= 部屋に入る =================

    fun connect(rawCode: String) {
        val code = rawCode.filter { it.isDigit() }
        if (code.length != 6) { say(ja("部屋コードは{0}けたの数字です", 6)); return }
        if (BuildConfig.ADMIN_KEY.isBlank()) { say(ja("このアプリには管理者の鍵が入っていません")); return }
        lastCode.value = code; prefs.edit().putString("code", code).apply()
        disconnect()
        val myId = "zpga-" + ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val proof = Guard.signAdmin(BuildConfig.ADMIN_KEY, code, myId)
        val meta = buildJsonObject {
            put("did", "admin")
            put("admin", buildJsonObject { put("ts", proof.ts); put("nonce", proof.nonce); put("sig", proof.sig) })
        }.toString()
        val l = OnlineLink(getApplication()); link = l
        linkJob = viewModelScope.launch { l.events.collect { onNet(it) } }
        _ui.value = AdminUi.Connecting(code)
        l.requestJoin(code, Admin.NAME, meta, myId)
    }

    fun disconnect() {
        linkJob?.cancel(); linkJob = null
        link?.stopAll(); link = null
        if (_ui.value !is AdminUi.Bans) _ui.value = AdminUi.Home
    }

    private fun onNet(ev: NetEvent) {
        when (ev) {
            is NetEvent.Received -> when (val m = runCatching { WireJson.decodeFromString(HostMsg.serializer(), ev.bytes.decodeToString()) }.getOrNull()) {
                is HostMsg.AdminViewMsg -> {
                    _ui.value = AdminUi.Room(m.room)
                    onRoom?.invoke(m.room)
                }
                is HostMsg.Bye -> { say(m.reason); disconnect() }
                is HostMsg.Notice -> say(ja(m.text, *m.args.toTypedArray()))
                is HostMsg.ViewMsg, null -> Unit
            }
            NetEvent.RoomNotFound -> { say(ja("部屋が見つかりません。部屋コードを確かめてください（アプリの版が違うときも入れません）")); disconnect() }
            is NetEvent.ConnectFailed -> { say(if (ev.rejected) ja("入れませんでした（相手のアプリが古い・管理者の確認に失敗）") else ja("接続できませんでした。もう一度試してください")); disconnect() }
            is NetEvent.Disconnected -> { say(ja("部屋との接続が切れました")); disconnect() }
            is NetEvent.Error -> say(ev.message)
            else -> Unit
        }
    }

    /** 画面を見ている間、部屋の様子が届くたびに呼ばれる */
    var onRoom: ((AdminRoom) -> Unit)? = null

    fun op(op: String, target: String = "", text: String = "") {
        val l = link ?: return
        l.send(listOf(OnlineLink.HOST), WireJson.encodeToString(Intent.serializer(), Intent.AdminOp(op, target, text)).encodeToByteArray())
    }

    /** この部屋から追い出し、全部屋共通の BAN リストにも入れる */
    fun ban(p: AdminPlayer, global: Boolean) {
        op(Admin.Op.BAN, p.id)
        if (!global) return
        if (p.device.isBlank() || p.device == Guard.hash("admin")) { say(ja("この人は端末番号がわからないため、この部屋からの追い出しだけ行いました")); return }
        setNote(p.device, p.name)
        editBans { list -> if (list.has(p.device)) list else list.copy(bans = list.bans + BanEntry(p.device, today())) }
    }

    fun unbanGlobal(hash: String) = editBans { list -> list.copy(bans = list.bans.filter { it.h != hash }) }

    fun addBanByHash(raw: String) {
        val h = raw.trim().lowercase()
        if (!Regex("^[0-9a-f]{64}$").matches(h)) { say(ja("端末番号（64文字の英数字）を入れてください")); return }
        editBans { list -> if (list.has(h)) list else list.copy(bans = list.bans + BanEntry(h, today())) }
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // ================= 全部屋共通の BAN リスト =================

    fun openBans() { _ui.value = AdminUi.Bans; loadBans() }
    fun closeBans() { _ui.value = if (link != null) (_ui.value.takeIf { it is AdminUi.Room } ?: AdminUi.Home) else AdminUi.Home }

    fun loadBans() = viewModelScope.launch {
        busy.value = true
        bans.value = withContext(Dispatchers.IO) {
            runCatching { github("GET", null).second }.getOrNull()
                ?: runCatching { WireJson.decodeFromString(BanList.serializer(), URL(Admin.BANS_URL + "?t=" + System.currentTimeMillis()).readText()) }.getOrNull()
        } ?: BanList()
        busy.value = false
    }

    /** GitHub の bans.json を読んで書きかえる（公開サイトには1分ほどで反映される） */
    private fun editBans(change: (BanList) -> BanList) {
        if (token.value.isBlank()) { say(ja("全部屋共通の BAN には GitHub のトークンが必要です（BANリストの画面で設定）")); return }
        viewModelScope.launch {
            busy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val (sha, cur) = github("GET", null)
                    val next = change(cur)
                    github("PUT", next, sha)
                    next
                }
            }
            busy.value = false
            result.onSuccess { bans.value = it; say(ja("全部屋共通の BAN リストを更新しました（反映まで1〜5分）")) }
                .onFailure { say(ja("BAN リストを更新できませんでした：{0}", it.message ?: "")) }
        }
    }

    /** GitHub の Contents API。GET は (sha, 中身)、PUT は書きこみ */
    private fun github(method: String, body: BanList?, sha: String? = null): Pair<String?, BanList> {
        val url = URL("https://api.github.com/repos/${Admin.BANS_REPO}/contents/${Admin.BANS_PATH}")
        val c = url.openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 10000; c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (token.value.isNotBlank()) c.setRequestProperty("Authorization", "Bearer " + token.value)
        if (method == "PUT") {
            val text = WireJson.encodeToString(BanList.serializer(), body!!) + "\n"
            val payload = buildJsonObject {
                put("message", "BAN リストを更新（管理者アプリ）")
                put("content", Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP))
                if (sha != null) put("sha", sha)
            }.toString()
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(payload.toByteArray()) }
        }
        val code = c.responseCode
        if (method == "GET" && code == 404) return null to BanList()
        if (code !in 200..299) {
            val err = runCatching { c.errorStream?.bufferedReader()?.readText() }.getOrNull() ?: ""
            throw IllegalStateException("GitHub $code ${err.take(120)}")
        }
        val resp = c.inputStream.bufferedReader().use { it.readText() }
        if (method == "PUT") return null to body!!
        val o = WireJson.parseToJsonElement(resp).jsonObject
        val content = o["content"]?.jsonPrimitive?.content ?: ""
        val text = String(Base64.decode(content.replace("\n", ""), Base64.DEFAULT))
        return o["sha"]?.jsonPrimitive?.content to WireJson.decodeFromString(BanList.serializer(), text)
    }

    /** 画面の見た目テスト用 */
    internal fun showForTest(ui: AdminUi, list: BanList? = null) { _ui.value = ui; bans.value = list }

    override fun onCleared() { disconnect() }
}
