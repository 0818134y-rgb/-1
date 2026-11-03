package com.zen.partygame.net

import com.zen.partygame.ui.tr
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.zen.partygame.game.PROTO
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * オンライン対戦（離れていても・PCとも遊べる）。
 * 見えない WebView の中で PC 版と同じ通信スクリプト（assets/online/net.js + PeerJS）を動かし、結果を NetEvent にして流す。
 * 自前のサーバーは使わない（相手を見つける仲介だけ PeerJS の無料サーバー）。
 */
@SuppressLint("SetJavaScriptEnabled")
class OnlineLink(context: Context) : Link {
    private val main = Handler(Looper.getMainLooper())
    private val _events = MutableSharedFlow<NetEvent>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<NetEvent> = _events
    private var ready = false
    private val queue = mutableListOf<String>()
    private var web: WebView? = null

    init {
        val app = context.applicationContext
        val peer = app.assets.open("online/peerjs.min.js").bufferedReader().use { it.readText() }
        val net = app.assets.open("online/net.js").bufferedReader().use { it.readText() }
        val html = "<!doctype html><html><head><meta charset=\"utf-8\"></head><body>" +
            "<script>${peer}</script><script>${net}</script>" +
            "<script>PgNet.onEvent=function(e){AndroidNet.emit(JSON.stringify(e));};AndroidNet.ready();</script></body></html>"
        web = WebView(app).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(Bridge(), "AndroidNet")
            // https のふりをした置き場所から読み込む（WebRTC は安全な場所でしか動かないため）
            loadDataWithBaseURL("https://partygame.invalid/", html, "text/html", "utf-8", null)
        }
    }

    private inner class Bridge {
        @JavascriptInterface fun ready() = main.post { ready = true; queue.forEach { run(it) }; queue.clear() }
        @JavascriptInterface fun emit(json: String) { parse(json)?.let { _events.tryEmit(it) } }
    }

    private fun parse(json: String): NetEvent? {
        val o = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
        fun str(k: String) = (o[k] as? JsonPrimitive)?.content ?: ""
        return when (str("ev")) {
            "join" -> NetEvent.JoinRequest(str("id"), str("name"), o["proto"]?.jsonPrimitive?.intOrNull ?: -1)
            "connected" -> NetEvent.Connected(str("id"))
            "failed" -> when (str("reason")) {
                "notfound" -> NetEvent.RoomNotFound
                "rejected" -> NetEvent.ConnectFailed(HOST, rejected = true)
                else -> NetEvent.ConnectFailed(HOST, rejected = false)
            }
            "data" -> NetEvent.Received(str("id"), str("text").encodeToByteArray())
            "closed" -> NetEvent.Disconnected(str("id"))
            "taken" -> NetEvent.CodeTaken
            "error" -> NetEvent.Error(if (str("message") == "network") tr("インターネットにつながりません。電波を確かめてください") else tr("通信エラー（{0}）", str("message")))
            else -> null
        }
    }

    private fun call(js: String) = main.post { if (ready) run(js) else queue += js }
    private fun run(js: String) { web?.evaluateJavascript(js, null) }
    private fun q(s: String) = JsonPrimitive(s).toString()

    override fun startHosting(roomCode: String, hostName: String) { call("PgNet.host(${PROTO}, ${q(roomCode)}, ${q(hostName)})") }
    override fun accept(endpointId: String) { call("PgNet.accept(${q(endpointId)})") }
    override fun reject(endpointId: String) { call("PgNet.reject(${q(endpointId)})") }
    /** endpointId には部屋コードを渡す */
    override fun requestJoin(endpointId: String, myName: String) { call("PgNet.join(${PROTO}, ${q(endpointId)}, ${q(myName)})") }
    override fun send(endpointIds: List<String>, bytes: ByteArray) {
        if (endpointIds.isEmpty()) return
        call("PgNet.send(${JsonArray(endpointIds.map { JsonPrimitive(it) })}, ${q(bytes.decodeToString())})")
    }
    override fun disconnect(endpointId: String) { call("PgNet.disconnect(${q(endpointId)})") }
    override fun stopAll() {
        call("PgNet.stop()")
        main.postDelayed({ web?.destroy(); web = null }, 1500)
    }

    companion object {
        /** 参加者から見たホストの id */
        const val HOST = "host"
    }
}
