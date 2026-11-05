package com.zen.partygame.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 管理者（運営）の仕組み。サーバーがないので、管理者アプリだけが持つ秘密鍵で「署名」した参加の申し込みを、
 * 各部屋のホストが下の公開鍵で確かめてから管理者として受け入れる（署名の確認は Android / ブラウザの標準の暗号機能で行う）。
 * 公開鍵は誰に見られても問題ない。秘密鍵は管理者アプリの中だけ（リポジトリには入れない）。
 */
object Admin {
    /** ECDSA P-256 の公開鍵（SubjectPublicKeyInfo を base64） */
    const val PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEb3T0hRqscPFGO2NZaXuz9Vj/BlK3G6RQsOT34BoYI7R8Hz03W8P5s8btjRuCpegkOX2vu0n7Dwj8lnltxi/nNw=="
    /** 署名の有効時間（端末の時計のずれも見込む） */
    const val SIG_WINDOW_MS = 10 * 60_000L
    /** 全部屋共通の参加禁止リスト（GitHub Pages に置く。管理者アプリが GitHub 経由で書きかえる） */
    const val BANS_URL = "https://wachapa.com/bans.json"
    const val BANS_REPO = "0818134y-rgb/-"
    const val BANS_PATH = "bans.json"
    const val NAME = "🛡管理者"

    /** 署名する文。peerId = 管理者の接続ID、nonce = 使い回し防止の乱数 */
    fun message(roomCode: String, peerId: String, ts: Long, nonce: String) = "wachapa-admin|$roomCode|$peerId|$ts|$nonce"

    /** 管理者が送れる操作 */
    object Op {
        const val KICK = "kick"         // target を退出させる
        const val BAN = "ban"           // target を退出させ、この部屋に二度と入れない（全部屋共通の BAN は管理者アプリがリストに書く）
        const val CLOSE = "close"       // 部屋を閉じる（全員の接続を切る）
        const val LOBBY = "lobby"       // 遊んでいるゲームを止めてロビーへ
        const val NEXT = "next"         // 話し合い・投票・回答などを締め切って次へ進める
        const val NOTICE = "notice"     // 全員にお知らせを出す（text）
        const val WATCH = "watch"       // target の画面を見る（空ならホストの画面）
        const val UNBAN = "unban"       // この部屋での BAN を取り消す（target = 端末番号のハッシュ）
    }

    /** 管理者に見せる部屋の様子。devices = 参加者 id → 端末番号のハッシュ */
    fun room(e: Engine, hostId: String, watching: String, devices: Map<String, String>, banned: List<String>, hostApp: String): AdminRoom {
        val target = watching.takeIf { w -> e.players.any { it.id == w } } ?: hostId
        val view = e.viewFor(target)
        val screen = runCatching { WireJson.encodeToJsonElement(Screen.serializer(), view.screen).jsonObject["t"]!!.jsonPrimitive.content }.getOrDefault("?")
        return AdminRoom(
            roomCode = e.roomCode, game = view.settings.game, screen = screen,
            players = e.players.map { AdminPlayer(it.id, it.name, it.isHost, devices[it.id] ?: "") },
            watching = target, view = view, banned = banned, hostApp = hostApp,
        )
    }

    /** 今の場面を締め切って次へ（話し合い終了・投票の締め切り・ヒント待ちを飛ばす・結果から次のラウンド・お題の確認待ちを飛ばす…の順に試す） */
    fun skip(e: Engine, hostId: String): Boolean =
        listOf(Intent.EndTalk, Intent.CloseVote, Intent.ScaleBegin, Intent.EndNg, Intent.GuessGiveUp, Intent.Next).any { e.apply(hostId, it) } ||
            e.skipTable() ||
            // お題の確認待ち：まだ確認していない人の分も確認したことにする
            e.players.map { it.id }.map { e.apply(it, Intent.ConfirmWord()) }.any { it }
}

/** 部屋の参加者（管理者に見せる情報） */
@Serializable
data class AdminPlayer(val id: String, val name: String, val isHost: Boolean = false, val device: String = "")

/** 管理者アプリに送る部屋の様子 */
@Serializable
data class AdminRoom(
    val roomCode: String,
    val game: GameType,
    /** 今の画面の種類（lobby / wwTalk / scale …） */
    val screen: String,
    val players: List<AdminPlayer>,
    /** 見ている人の id（空ならホスト） */
    val watching: String = "",
    /** 見ている人の画面 */
    val view: PlayerView? = null,
    /** この部屋で BAN した端末（ハッシュ） */
    val banned: List<String> = emptyList(),
    /** ホストの端末の種類（android / web） */
    val hostApp: String = "",
)

/** 全部屋共通の参加禁止リスト（bans.json）。h = 端末番号の SHA-256（元に戻せない形） */
@Serializable
data class BanEntry(val h: String, val at: String = "")

@Serializable
data class BanList(val v: Int = 1, val bans: List<BanEntry> = emptyList()) {
    fun has(hash: String) = hash.isNotEmpty() && bans.any { it.h == hash }
}
