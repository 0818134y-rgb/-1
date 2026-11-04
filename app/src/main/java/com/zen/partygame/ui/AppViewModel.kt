package com.zen.partygame.ui

import com.zen.partygame.game.Admin
import com.zen.partygame.net.Guard
import kotlinx.coroutines.Dispatchers
import com.zen.partygame.game.Bgm
import com.zen.partygame.game.Sfx
import com.zen.partygame.game.GameType
import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zen.partygame.ads.AdController
import com.zen.partygame.game.Engine
import com.zen.partygame.game.HostMsg
import com.zen.partygame.game.Intent
import com.zen.partygame.game.PROTO
import com.zen.partygame.game.PlayerView
import com.zen.partygame.game.TopicBank
import com.zen.partygame.game.WireJson
import com.zen.partygame.net.Link
import com.zen.partygame.net.NearbyLink
import com.zen.partygame.net.NetEvent
import com.zen.partygame.net.OnlineLink
import com.zen.partygame.net.RoomInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface Ui {
    data object Title : Ui
    data object LocalSetup : Ui
    data class Finding(val rooms: List<RoomInfo>, val joining: String?) : Ui
    /** オンライン：部屋コードを入れて参加 */
    data class OnlineJoin(val busy: Boolean) : Ui
    data class Room(val view: PlayerView, val receivedAt: Long, val online: Boolean = false) : Ui
}

/**
 * 画面とゲームの橋渡し。
 * ホストのときは [Engine] をこの端末で動かし、各参加者に見せる内容を送る（近く = Nearby、オンライン = PeerJS）。
 * 参加者のときは操作をホストへ送り、届いた内容を表示するだけ。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("pg", Context.MODE_PRIVATE)
    /** 表示の言語（ja / en / ko / zh）。お題やカードも、部屋を作った人の言語になる */
    val lang = MutableStateFlow(prefs.getString("lang", null) ?: I18n.systemDefault())
    init { I18n.set(app, lang.value) }
    private val topicCache = mutableMapOf<String, TopicBank>()
    /** 部屋を作るときのお題（その言語のものが無ければ日本語） */
    private fun topics(): TopicBank = topicCache.getOrPut(lang.value) {
        val a = getApplication<Application>().assets
        val file = if (lang.value == "ja") "topics.json" else "topics_${lang.value}.json"
        TopicBank.parse(runCatching { a.open(file) }.getOrElse { a.open("topics.json") }.bufferedReader().use { it.readText() })
    }

    fun setLang(code: String) {
        lang.value = code; prefs.edit().putString("lang", code).apply()
        I18n.set(getApplication(), code)
    }
    val ads = AdController(app)

    private val _ui = MutableStateFlow<Ui>(Ui.Title)
    val ui: StateFlow<Ui> = _ui
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages
    val name = MutableStateFlow(prefs.getString("name", "") ?: "")
    /** つなぎ方：false = 近くで（Bluetooth/Wi-Fi）、true = オンライン（離れていても・PCとも） */
    val online = MutableStateFlow(prefs.getBoolean("online", false))
    /** 効果音のオン/オフ */
    val sound = MutableStateFlow(prefs.getBoolean("sound", true))
    init {
        Sound.init(app); Sound.enabled = sound.value && !com.zen.partygame.BuildConfig.ADMIN
        viewModelScope.launch(Dispatchers.IO) { Guard.refreshBans(force = true) }   // 参加禁止リスト（自分が入っていればオンラインの部屋は作れない）
    }
    /** BGM のオン/オフ */
    val music = MutableStateFlow(prefs.getBoolean("music", true))
    init {
        Music.init(app); Music.enabled = music.value && !com.zen.partygame.BuildConfig.ADMIN
        // 画面に合わせて曲を変える（部屋の外はタイトルの曲）
        viewModelScope.launch { _ui.collect { u -> Music.play(Bgm.track((u as? Ui.Room)?.view)) } }
    }
    /** 前回入れた部屋コード */
    val lastCode = MutableStateFlow(prefs.getString("code", "") ?: "")

    private var link: Link? = null
    private var linkOnline = false
    private var linkJob: Job? = null
    private var engine: Engine? = null
    private var tickJob: Job? = null
    private var lastVersion = -1

    // ホスト側
    private val pendingNames = mutableMapOf<String, String>()
    private val connected = linkedSetOf<String>()
    // 管理者・荒らし対策（ホスト側）
    /** 管理者の接続 → 見ている参加者の id */
    private val admins = linkedMapOf<String, String>()
    private val pendingAdmins = mutableSetOf<String>()
    /** 参加者 id → 端末番号のハッシュ */
    private val devices = mutableMapOf<String, String>()
    private val pendingDevices = mutableMapOf<String, String>()
    /** この部屋で管理者が BAN した端末 */
    private val sessionBans = linkedSetOf<String>()
    private val myDevice by lazy { Guard.hash(Guard.deviceId(getApplication())) }
    private var bansJob: Job? = null
    // 参加者側
    private var hostEndpoint: String? = null
    private val rooms = linkedMapOf<String, RoomInfo>()

    fun setName(v: String) {
        name.value = v.take(10)
        prefs.edit().putString("name", name.value).apply()
    }

    fun setSound(v: Boolean) {
        sound.value = v; Sound.enabled = v; prefs.edit().putBoolean("sound", v).apply()
        if (v) Sound.play(Sfx.TAP)
    }

    fun setMusic(v: Boolean) {
        music.value = v; Music.enabled = v; prefs.edit().putBoolean("music", v).apply()
    }

    /** 画面が変わったら、前の画面と比べて効果音を鳴らす */
    private var lastView: PlayerView? = null
    private fun showRoom(view: PlayerView) {
        Sound.playAll(Sfx.cues(lastView, view))
        lastView = view
        _ui.value = Ui.Room(view, SystemClock.elapsedRealtime(), linkOnline)
    }

    fun setOnline(v: Boolean) {
        online.value = v
        prefs.edit().putBoolean("online", v).apply()
    }

    private fun say(text: String) { _messages.tryEmit(text) }
    fun notify(text: String) = say(text)

    // ================= 部屋を作る／1台モード =================

    fun host() {
        reset()
        val isOnline = online.value
        if (isOnline && Guard.bans.has(myDevice)) { say(tr("この端末はオンラインの部屋を作れません")); return }
        val code = if (isOnline) newOnlineCode() else newCode()
        val e = Engine(code, localMode = false, topics = topics(), rng = Random.Default) { SystemClock.elapsedRealtime() }
        e.join(HOST_ID, name.value, isHost = true)
        e.apply(HOST_ID, Intent.UpdateSettings(e.settings.copy(game = GameType.ARENA)))   // 一押しを最初から選んでおく
        engine = e
        devices[HOST_ID] = myDevice
        val l = openLink(isOnline)
        l.startHosting(code, e.players.first().name)
        startTicking(); publish(force = true)
        // 全部屋共通の参加禁止リストを読み、部屋を開いている間は5分ごとに読み直す
        if (isOnline) bansJob = viewModelScope.launch(Dispatchers.IO) { while (isActive) { Guard.refreshBans(); delay(5 * 60_000L) } }
    }

    fun startLocal(names: List<String>) {
        reset()
        val e = Engine(newCode(), localMode = true, topics = topics(), rng = Random.Default) { SystemClock.elapsedRealtime() }
        names.forEachIndexed { i, n -> e.join(if (i == 0) HOST_ID else "l${i}", n, isHost = i == 0) }
        engine = e
        startTicking(); publish(force = true)
    }

    fun openLocalSetup() { _ui.value = Ui.LocalSetup }

    // ================= 部屋に参加する =================

    fun find() {
        reset()
        _ui.value = Ui.Finding(emptyList(), null)
        (openLink(false) as NearbyLink).startDiscovery()
    }

    fun openOnlineJoin() { reset(); _ui.value = Ui.OnlineJoin(busy = false) }

    /** オンライン：部屋コードで参加 */
    fun joinOnline(rawCode: String) {
        val code = rawCode.filter { it.isDigit() }
        if (code.length != ONLINE_CODE_LEN) { say(tr("部屋コードは{0}けたの数字です", ONLINE_CODE_LEN)); return }
        lastCode.value = code; prefs.edit().putString("code", code).apply()
        reset()
        _ui.value = Ui.OnlineJoin(busy = true)
        val meta = kotlinx.serialization.json.buildJsonObject { put("did", kotlinx.serialization.json.JsonPrimitive(Guard.deviceId(getApplication()))) }.toString()
        openLink(true).requestJoin(code, name.value.ifBlank { tr("ゲスト") }, meta)
    }

    /** 部屋コードをLINEなどで送る */
    fun shareInvite(code: String) {
        val web = com.zen.partygame.BuildConfig.WEB_URL
        val text = tr("わちゃパで遊ぼう！\nアプリで「オンライン」→「部屋に参加する」を押して、部屋コード {0} を入れてね", code) +
            if (web.isNotBlank()) tr("\niPhone・PCはこちらから：{0}", web) else ""
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text) }
        getApplication<Application>().startActivity(android.content.Intent.createChooser(send, tr("招待を送る")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun join(room: RoomInfo) {
        val l = link ?: return
        _ui.value = Ui.Finding(rooms.values.toList(), room.endpointId)
        l.requestJoin(room.endpointId, name.value.ifBlank { tr("ゲスト") })
    }

    // ================= 操作 =================

    // ================= 管理者アプリ：参加者の画面を見るだけ =================

    /** null でなければ「見るだけ」の状態。退出ボタンなどで呼ばれる */
    var onWatchExit: (() -> Unit)? = null
        private set

    fun watch(view: PlayerView, onExit: () -> Unit) {
        onWatchExit = onExit
        _ui.value = Ui.Room(view, SystemClock.elapsedRealtime(), true)
    }

    fun stopWatching() { onWatchExit = null; _ui.value = Ui.Title }

    fun send(intent: Intent) {
        if (onWatchExit != null) return
        val e = engine
        if (e != null) {
            if (e.apply(HOST_ID, intent)) publish()
        } else {
            val h = hostEndpoint ?: return
            link?.send(listOf(h), WireJson.encodeToString(Intent.serializer(), intent).encodeToByteArray())
        }
    }

    fun leave() {
        onWatchExit?.let { it(); return }
        val e = engine; val l = link
        val sayBye = e != null && l != null && connected.isNotEmpty()
        if (sayBye) sendTo(connected.toList(), HostMsg.Bye(tr("ホストが部屋を閉じました")))
        reset(stopNow = !sayBye)
        if (sayBye) viewModelScope.launch { delay(300); l?.stopAll() }  // さよならが届くのを待ってから切る
        _ui.value = Ui.Title
    }

    override fun onCleared() { reset() }

    // ================= 内部 =================

    private fun openLink(isOnline: Boolean): Link {
        val l: Link = if (isOnline) OnlineLink(getApplication()) else NearbyLink(getApplication())
        link = l; linkOnline = isOnline
        linkJob = viewModelScope.launch { l.events.collect { onNet(it) } }
        return l
    }

    private fun reset(stopNow: Boolean = true) {
        tickJob?.cancel(); tickJob = null
        bansJob?.cancel(); bansJob = null
        admins.clear(); pendingAdmins.clear(); devices.clear(); pendingDevices.clear(); sessionBans.clear()
        linkJob?.cancel(); linkJob = null
        if (stopNow) link?.stopAll()
        link = null; engine = null; hostEndpoint = null; lastVersion = -1; lastView = null
        pendingNames.clear(); connected.clear(); rooms.clear()
    }

    private fun startTicking() {
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(250)
                val e = engine ?: break
                if (e.tick()) publish()
            }
        }
    }

    /** ホスト：状態が変わっていれば、自分の画面と各参加者に送る */
    private fun publish(force: Boolean = false) {
        val e = engine ?: return
        if (!force && e.version == lastVersion) return
        lastVersion = e.version
        showRoom(e.viewFor(HOST_ID))
        val l = link ?: return
        val ids = e.players.map { it.id }.toSet()
        for (id in connected.toList()) {
            if (id in ids) sendTo(listOf(id), HostMsg.ViewMsg(e.viewFor(id)))
            else {  // ホストに退出させられた人
                connected.remove(id)
                sendTo(listOf(id), HostMsg.Bye(tr("ホストに退出させられました")))
                viewModelScope.launch { delay(500); l.disconnect(id) }
            }
        }
        admins.keys.toList().forEach { sendAdminView(e, it) }
    }

    // ================= 管理者（ホスト側） =================

    private fun sendAdminView(e: Engine, adminId: String) {
        val room = Admin.room(e, HOST_ID, admins[adminId] ?: "", devices, sessionBans.toList(), "android")
        sendTo(listOf(adminId), HostMsg.AdminViewMsg(room))
    }

    private fun handleAdmin(e: Engine, from: String, op: Intent.AdminOp) {
        val l = link ?: return
        when (op.op) {
            Admin.Op.KICK, Admin.Op.BAN -> {
                val t = op.target
                if (op.op == Admin.Op.BAN) devices[t]?.takeIf { it.isNotEmpty() }?.let { sessionBans += it }
                if (t == HOST_ID) { closeByAdmin(); return }
                val who = e.players.firstOrNull { it.id == t }?.name ?: return
                if (connected.remove(t)) {
                    sendTo(listOf(t), HostMsg.Bye(tr("管理者により退出させられました")))
                    viewModelScope.launch { delay(500); l.disconnect(t) }
                }
                e.leave(t)
                sendTo(connected.toList(), HostMsg.Notice("🛡 {0}さんは管理者により退出させられました", listOf(who)))
                say(tr("🛡 {0}さんは管理者により退出させられました", who))
                publish(force = true)
            }
            Admin.Op.CLOSE -> closeByAdmin()
            Admin.Op.LOBBY -> if (e.apply(HOST_ID, Intent.BackToLobby)) publish()
            Admin.Op.NEXT -> if (Admin.skip(e, HOST_ID)) publish()
            Admin.Op.NOTICE -> op.text.trim().replace("\n", " ").take(100).takeIf { it.isNotEmpty() }?.let {
                sendTo(connected.toList(), HostMsg.Notice("🛡 管理者：{0}", listOf(it)))
                say(tr("🛡 管理者：{0}", it))
            }
            Admin.Op.WATCH -> admins[from] = op.target
            Admin.Op.UNBAN -> sessionBans.remove(op.target)
        }
        if (from in admins) sendAdminView(e, from)
    }

    /** 管理者が部屋を閉じる：全員を切って、ホストもタイトルへ */
    private fun closeByAdmin() {
        val l = link
        val everyone = connected.toList() + admins.keys
        sendTo(everyone, HostMsg.Bye(tr("管理者が部屋を閉じました")))
        reset(stopNow = false)
        viewModelScope.launch { delay(400); l?.stopAll() }
        _ui.value = Ui.Title
        say(tr("管理者が部屋を閉じました"))
    }

    private fun sendTo(ids: List<String>, msg: HostMsg) {
        link?.send(ids, WireJson.encodeToString(HostMsg.serializer(), msg).encodeToByteArray())
    }

    private fun onNet(ev: NetEvent) {
        val e = engine
        if (e != null) onHostNet(e, ev) else onClientNet(ev)
    }

    private fun onHostNet(e: Engine, ev: NetEvent) {
        val l = link ?: return
        when (ev) {
            is NetEvent.JoinRequest -> {
                val device = Guard.hash(ev.did)
                when {
                    ev.proto != PROTO -> l.reject(ev.endpointId)
                    // 管理者（署名を確かめる）：ゲーム中・満員でも入れる
                    ev.admin != null -> if (linkOnline && Guard.verifyAdmin(e.roomCode, ev.endpointId, ev.admin)) { pendingAdmins += ev.endpointId; l.accept(ev.endpointId) }
                        else l.reject(ev.endpointId)   // 署名がおかしい「管理者」は入れない
                    // 参加禁止の端末（この部屋で BAN された／全部屋共通のリストにある）
                    device.isNotEmpty() && (device in sessionBans || Guard.bans.has(device)) -> l.reject(ev.endpointId)
                    !e.inLobby || e.players.size >= e.maxPlayers -> l.reject(ev.endpointId)
                    else -> { pendingNames[ev.endpointId] = ev.name; pendingDevices[ev.endpointId] = device; l.accept(ev.endpointId) }
                }
            }
            is NetEvent.Connected -> {
                if (pendingAdmins.remove(ev.endpointId)) {
                    admins[ev.endpointId] = ""
                    sendTo(connected.toList(), HostMsg.Notice("🛡 管理者が部屋の様子を確認しています"))
                    say(tr("🛡 管理者が部屋の様子を確認しています"))
                    sendAdminView(e, ev.endpointId)
                    return
                }
                pendingDevices.remove(ev.endpointId)?.let { devices[ev.endpointId] = it }
                val n = pendingNames.remove(ev.endpointId) ?: tr("ゲスト")
                when (e.join(ev.endpointId, n)) {
                    is Engine.JoinResult.Ok -> { connected += ev.endpointId; publish(force = true) }
                    else -> {
                        sendTo(listOf(ev.endpointId), HostMsg.Bye(tr("ゲーム中のため参加できませんでした")))
                        viewModelScope.launch { delay(500); l.disconnect(ev.endpointId) }
                    }
                }
            }
            is NetEvent.Disconnected -> {
                pendingNames.remove(ev.endpointId); pendingAdmins.remove(ev.endpointId); pendingDevices.remove(ev.endpointId)
                if (admins.remove(ev.endpointId) != null) return
                if (connected.remove(ev.endpointId)) {
                    val who = e.players.firstOrNull { it.id == ev.endpointId }?.name
                    e.leave(ev.endpointId); publish()
                    if (who != null) say(tr("{0}さんが抜けました", who))
                }
            }
            is NetEvent.Received -> {
                if (ev.endpointId in admins) {
                    val op = runCatching { WireJson.decodeFromString(Intent.serializer(), ev.bytes.decodeToString()) }.getOrNull()
                    if (op is Intent.AdminOp) handleAdmin(e, ev.endpointId, op)
                    return
                }
                if (ev.endpointId !in connected) return
                val intent = runCatching {
                    WireJson.decodeFromString(Intent.serializer(), ev.bytes.decodeToString())
                }.getOrNull() ?: return
                if (intent is Intent.AdminOp) return   // 管理者でない人の管理者操作は無視
                if (e.apply(ev.endpointId, intent)) publish()
            }
            is NetEvent.ConnectFailed -> pendingNames.remove(ev.endpointId)
            is NetEvent.Error -> say(ev.message)
            NetEvent.CodeTaken -> if (connected.isEmpty()) host()   // めったにないが、別の部屋とコードがかぶったら作り直す
            is NetEvent.RoomFound, is NetEvent.RoomLost, NetEvent.RoomNotFound -> Unit
        }
    }

    private fun onClientNet(ev: NetEvent) {
        when (ev) {
            is NetEvent.RoomFound -> { rooms[ev.room.endpointId] = ev.room; refreshFinding() }
            is NetEvent.RoomLost -> { rooms.remove(ev.endpointId); refreshFinding() }
            is NetEvent.Connected -> { hostEndpoint = ev.endpointId; (link as? NearbyLink)?.stopDiscovery() }
            is NetEvent.ConnectFailed -> {
                say(if (ev.rejected) tr("参加できませんでした（ゲーム中・満員・アプリの版が違う、のどれかです）") else tr("接続できませんでした。もう一度試してください"))
                (_ui.value as? Ui.Finding)?.let { _ui.value = it.copy(joining = null) }
                if (_ui.value is Ui.OnlineJoin) { reset(); _ui.value = Ui.OnlineJoin(busy = false) }
            }
            NetEvent.RoomNotFound -> {
                say(tr("部屋が見つかりません。部屋コードを確かめてください（アプリの版が違うときも入れません）"))
                reset(); _ui.value = Ui.OnlineJoin(busy = false)
            }
            NetEvent.CodeTaken -> Unit
            is NetEvent.Received -> {
                if (ev.endpointId != hostEndpoint) return
                when (val msg = runCatching { WireJson.decodeFromString(HostMsg.serializer(), ev.bytes.decodeToString()) }.getOrNull()) {
                    is HostMsg.ViewMsg -> showRoom(msg.view)
                    is HostMsg.Bye -> { reset(); _ui.value = Ui.Title; say(msg.reason) }
                    is HostMsg.Notice -> say(tr(msg.text, *msg.args.toTypedArray()))
                    is HostMsg.AdminViewMsg, null -> Unit
                }
            }
            is NetEvent.Disconnected -> if (ev.endpointId == hostEndpoint) {
                val wasInRoom = _ui.value is Ui.Room
                reset(); _ui.value = Ui.Title
                if (wasInRoom) say(tr("ホストとの接続が切れました"))
            }
            is NetEvent.Error -> say(ev.message)
            is NetEvent.JoinRequest -> link?.reject(ev.endpointId)
        }
    }

    private fun refreshFinding() {
        (_ui.value as? Ui.Finding)?.let { _ui.value = it.copy(rooms = rooms.values.sortedBy { r -> r.code }) }
    }

    private fun newCode() = (1000 + Random.nextInt(9000)).toString()
    private fun newOnlineCode() = (100000 + Random.nextInt(900000)).toString()

    companion object {
        const val HOST_ID = "h"
        const val ONLINE_CODE_LEN = 6
    }
}
