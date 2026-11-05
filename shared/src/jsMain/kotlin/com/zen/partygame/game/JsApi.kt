@file:OptIn(ExperimentalJsExport::class)

package com.zen.partygame.game

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.js.Date
import kotlin.random.Random

/**
 * PC 版（ブラウザ）からゲームの進行を動かすための入口。
 * PC がホストのとき、この中で Android と同じ Engine が動く。やりとりはすべて JSON 文字列。
 */
@JsExport
class JsHost(roomCode: String, topicsJson: String) {
    private val engine = Engine(roomCode, localMode = false, topics = TopicBank.parse(topicsJson), rng = Random.Default) { Date.now().toLong() }

    /** @return "ok" / "full" / "started" など */
    fun join(id: String, name: String, isHost: Boolean): String = when (val r = engine.join(id, name, isHost)) {
        is Engine.JoinResult.Ok -> "ok"
        Engine.JoinResult.Full -> "full"
        Engine.JoinResult.InGame -> "ingame"
    }
    fun leave(id: String) = engine.leave(id)
    /** @return 状態が変わったら true */
    fun apply(from: String, intentJson: String): Boolean {
        val intent = runCatching { WireJson.decodeFromString(Intent.serializer(), intentJson) }.getOrNull() ?: return false
        return engine.apply(from, intent)
    }
    fun tick(): Boolean = engine.tick()
    fun version(): Int = engine.version
    fun viewJson(id: String): String = WireJson.encodeToString(PlayerView.serializer(), engine.viewFor(id))
    fun playerIds(): Array<String> = engine.players.map { it.id }.toTypedArray()
    fun canJoin(): Boolean = engine.inLobby && engine.players.size < engine.maxPlayers
    /** 管理者アプリに送る部屋の様子（HostMsg の JSON）。devicesJson = {"id":"ハッシュ"}、bannedJson = ["ハッシュ"] */
    fun adminViewMsg(watching: String, devicesJson: String, bannedJson: String): String {
        val devices = runCatching { WireJson.decodeFromString<Map<String, String>>(devicesJson) }.getOrDefault(emptyMap())
        val banned = runCatching { WireJson.decodeFromString<List<String>>(bannedJson) }.getOrDefault(emptyList())
        val room = Admin.room(engine, engine.hostId ?: "h", watching, devices, banned, "web")
        return WireJson.encodeToString(HostMsg.serializer(), HostMsg.AdminViewMsg(room))
    }
    /** 管理者：今の場面を締め切って次へ */
    fun adminSkip(): Boolean = Admin.skip(engine, engine.hostId ?: "h")
    /** 管理者：ロビーへ戻す */
    fun adminLobby(): Boolean = engine.apply(engine.hostId ?: "h", Intent.BackToLobby)
    fun preferArena() { engine.hostId?.let { engine.apply(it, Intent.UpdateSettings(engine.settings.copy(game = GameType.ARENA))) } }
}

@JsExport fun protoVersion(): Int = PROTO

/** 管理者の確認に使う値（公開鍵・参加禁止リストの場所・署名の有効時間） */
@JsExport fun adminConfigJson(): String = buildJsonObject {
    put("publicKey", Admin.PUBLIC_KEY); put("bansUrl", Admin.BANS_URL); put("windowMs", Admin.SIG_WINDOW_MS); put("name", Admin.NAME)
}.toString()

/** 管理者の署名する文 */
@JsExport fun adminMessage(roomCode: String, peerId: String, ts: Double, nonce: String): String = Admin.message(roomCode, peerId, ts.toLong(), nonce)

/** 前の画面（JSON、無ければ空）と次の画面（JSON）から、鳴らす効果音の名前 */
@JsExport fun sfxCues(prevJson: String, nextJson: String): Array<String> = runCatching {
    val prev = if (prevJson.isEmpty()) null else WireJson.decodeFromString(PlayerView.serializer(), prevJson)
    Sfx.cues(prev, WireJson.decodeFromString(PlayerView.serializer(), nextJson)).toTypedArray()
}.getOrDefault(emptyArray())

/** 残り時間のカウント音を鳴らす画面か */
@JsExport fun sfxCountsDown(viewJson: String): Boolean =
    runCatching { Sfx.countsDown(WireJson.decodeFromString(PlayerView.serializer(), viewJson)) }.getOrDefault(false)

/** 今の画面で流すBGMの名前（部屋の外なら空文字を渡す → タイトルの曲） */
@JsExport fun bgmTrack(viewJson: String): String = runCatching {
    Bgm.track(if (viewJson.isEmpty()) null else WireJson.decodeFromString(PlayerView.serializer(), viewJson))
}.getOrDefault(Bgm.TITLE)

/** アリーナの記録1行を、その端末の言語にする（tr = 日本語の型 → 訳） */
@JsExport fun formatLog(entry: String, tr: (String) -> String, sep: String): String = LogFmt.format(entry, tr, sep)

/** お絵かき伝言の絵：[[色, 太さ, [x, y, x, y, …]], …] の JSON ⇔ 送る文字列 */
@JsExport fun drawingEncode(strokesJson: String): String {
    val arr = runCatching { WireJson.parseToJsonElement(strokesJson) as JsonArray }.getOrNull() ?: return ""
    val strokes = arr.mapNotNull { e ->
        runCatching {
            val a = e as JsonArray
            val pts = (a[2] as JsonArray).map { (it as kotlinx.serialization.json.JsonPrimitive).content.toDouble().toInt() }.toIntArray()
            DrawingCodec.Stroke(
                (a[0] as kotlinx.serialization.json.JsonPrimitive).content.toInt(),
                (a[1] as kotlinx.serialization.json.JsonPrimitive).content.toInt(),
                if (pts.size % 2 == 0) pts else pts.copyOf(pts.size - 1),
            )
        }.getOrNull()
    }
    return DrawingCodec.encode(strokes)
}

@JsExport fun drawingDecode(text: String): String = buildString {
    append('[')
    DrawingCodec.decode(text).forEachIndexed { i, s ->
        if (i > 0) append(',')
        append('[').append(s.color).append(',').append(s.width).append(",[")
        append(s.points.joinToString(","))
        append("]]")
    }
    append(']')
}

/** 文字数の上限に対して、あと何点描けるか */
@JsExport fun drawingPointsLeft(strokesJson: String): Int {
    val enc = drawingEncode(strokesJson)
    return ((MAX_DRAWING_CHARS - enc.length) * 3 / 8).coerceAtLeast(0)
}

/** カードの一覧（名前・文・絵など）。PC 版の表示に使う */
@JsExport fun arenaCardsJson(): String = JsonArray(ArenaCards.all.map { d ->
    buildJsonObject {
        put("id", d.id); put("name", d.name); put("text", d.text); put("star", d.star); put("art", d.art)
        put("fx", d.fx.name); put("target", d.target.name)
        d.cover?.let { put("cover", it) }; d.coverName?.let { put("coverName", it) }
    }
}).toString()

/** やおよろずバトルのカード一覧 */
@JsExport fun yaoCardsJson(): String = JsonArray(YaoCards.all.map { d ->
    buildJsonObject {
        put("id", d.id); put("name", d.name); put("text", d.text); put("kind", d.kind.name); put("emoji", d.emoji)
        put("value", d.value); put("mp", d.mp); put("cost", d.cost); put("needsTarget", d.needsTarget); put("allOthers", d.allOthers)
    }
}).toString()

/** ゲームの種類と今夜のルールの一覧 */
@JsExport fun metaJson(): String = buildJsonObject {
    put("games", JsonArray(GameType.entries.map { g ->
        buildJsonObject { put("id", g.name); put("label", g.label); put("short", g.short); put("min", g.minPlayers) }
    }))
    put("chaos", JsonObject(Chaos.entries.associate { c -> c.name to buildJsonObject { put("title", c.title); put("text", c.text); put("social", c.social) } }))
    put("proto", PROTO)
    put("maxPlayers", MAX_PLAYERS_NEARBY)
    put("noseWindowMs", ArenaGame.NOSE_WINDOW_MS)
    put("draw", buildJsonObject {
        put("w", DrawingCodec.W); put("h", DrawingCodec.H); put("maxChars", MAX_DRAWING_CHARS); put("maxText", MAX_DRAW_TEXT_LEN)
        put("colors", JsonArray(DrawingCodec.COLORS.map { kotlinx.serialization.json.JsonPrimitive("#" + (it and 0xFFFFFF).toString(16).padStart(6, '0')) }))
        put("widths", JsonArray(DrawingCodec.WIDTHS.map { kotlinx.serialization.json.JsonPrimitive(it) }))
    })
}.toString()
