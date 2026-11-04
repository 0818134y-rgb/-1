package com.zen.partygame.net

import android.content.Context
import android.util.Base64
import com.zen.partygame.game.Admin
import com.zen.partygame.game.BanList
import com.zen.partygame.game.WireJson
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

/** 管理者の署名つき申し込み（参加の申し込みに付けて送る） */
data class AdminProof(val ts: Long, val nonce: String, val sig: String)

/**
 * 荒らし対策：端末番号・全部屋共通の参加禁止リスト・管理者の署名。
 * 端末番号はインストールごとのランダムな番号（広告IDなどとは無関係）。リストや管理者には SHA-256 にした値だけを渡す。
 */
object Guard {
    @Volatile var bans = BanList()
        private set
    private var fetchedAt = 0L

    /** この端末の番号（初回に作って保存） */
    fun deviceId(context: Context): String {
        val p = context.getSharedPreferences("guard", Context.MODE_PRIVATE)
        return p.getString("did", null) ?: UUID.randomUUID().toString().also { p.edit().putString("did", it).apply() }
    }

    fun hash(did: String): String = if (did.isBlank()) "" else
        MessageDigest.getInstance("SHA-256").digest(did.toByteArray()).joinToString("") { "%02x".format(it) }

    /** 参加禁止リストを読み直す（5分に1回まで。失敗したら前のまま）。IO スレッドで呼ぶ */
    fun refreshBans(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - fetchedAt < 5 * 60_000L) return
        fetchedAt = now
        runCatching {
            val c = URL(Admin.BANS_URL + "?t=" + now).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
        }.getOrNull()?.let { text -> runCatching { WireJson.decodeFromString(BanList.serializer(), text) }.getOrNull()?.let { bans = it } }
    }

    // ---- 署名（ECDSA P-256 / SHA-256）。ブラウザ（WebCrypto）と同じ「r と s を並べた64バイト」の形でやりとりする ----

    fun verifyAdmin(roomCode: String, peerId: String, p: AdminProof?, now: Long = System.currentTimeMillis()): Boolean {
        if (p == null) return false
        if (kotlin.math.abs(now - p.ts) > Admin.SIG_WINDOW_MS) return false
        return runCatching {
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(Admin.PUBLIC_KEY, Base64.DEFAULT)))
            val s = Signature.getInstance("SHA256withECDSA")
            s.initVerify(key); s.update(Admin.message(roomCode, peerId, p.ts, p.nonce).toByteArray())
            s.verify(rawToDer(Base64.decode(p.sig, Base64.NO_WRAP)))
        }.getOrDefault(false)
    }

    /** 管理者アプリ：申し込みに付ける署名を作る（privateKey = PKCS#8 を base64） */
    fun signAdmin(privateKey: String, roomCode: String, peerId: String): AdminProof {
        val ts = System.currentTimeMillis()
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.decode(privateKey, Base64.DEFAULT)))
        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(key); s.update(Admin.message(roomCode, peerId, ts, nonce).toByteArray())
        return AdminProof(ts, nonce, Base64.encodeToString(derToRaw(s.sign()), Base64.NO_WRAP))
    }

    internal fun rawToDer(raw: ByteArray): ByteArray {
        require(raw.size == 64)
        fun int(b: ByteArray): ByteArray {
            var x = b.dropWhile { it == 0.toByte() }.toByteArray()
            if (x.isEmpty()) x = byteArrayOf(0)
            if (x[0].toInt() and 0x80 != 0) x = byteArrayOf(0) + x
            return byteArrayOf(0x02, x.size.toByte()) + x
        }
        val body = int(raw.copyOfRange(0, 32)) + int(raw.copyOfRange(32, 64))
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    internal fun derToRaw(der: ByteArray): ByteArray {
        var i = 2
        fun next(): ByteArray {
            require(der[i] == 0x02.toByte()); val len = der[i + 1].toInt(); val v = der.copyOfRange(i + 2, i + 2 + len); i += 2 + len
            val t = v.dropWhile { it == 0.toByte() }.toByteArray()
            return ByteArray(32 - t.size) + t
        }
        return next() + next()
    }
}
