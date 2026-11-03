@file:OptIn(ExperimentalJsExport::class)

package com.zen.partygame.game

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
    fun preferArena() { engine.hostId?.let { engine.apply(it, Intent.UpdateSettings(engine.settings.copy(game = GameType.ARENA))) } }
}

@JsExport fun protoVersion(): Int = PROTO

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
