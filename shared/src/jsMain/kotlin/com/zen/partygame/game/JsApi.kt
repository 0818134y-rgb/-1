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
}.toString()
