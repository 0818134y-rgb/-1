package com.zen.partygame.net

/**
 * 大きなメッセージを小分けにして送り、受け取った側で元に戻す。
 * ふつうのメッセージ（JSON は '{' で始まる）はそのまま。分けたものは先頭が 0x01：
 * [0x01][番号 4バイト][何番目 2バイト][全部で何個 2バイト][中身]
 */
class Chunker(private val maxSize: Int = 30_000) {
    private var nextId = 1
    private val pending = HashMap<String, HashMap<Int, Array<ByteArray?>>>()

    fun split(bytes: ByteArray): List<ByteArray> {
        if (bytes.size <= maxSize) return listOf(bytes)
        val body = maxSize - HEADER
        val total = (bytes.size + body - 1) / body
        val id = nextId++
        return (0 until total).map { i ->
            val from = i * body; val to = minOf(bytes.size, from + body)
            val out = ByteArray(HEADER + (to - from))
            out[0] = 1
            putInt(out, 1, id); putShort(out, 5, i); putShort(out, 7, total)
            bytes.copyInto(out, HEADER, from, to)
            out
        }
    }

    /** @return そろったメッセージ（まだ途中なら null） */
    fun accept(from: String, bytes: ByteArray): ByteArray? {
        if (bytes.isEmpty() || bytes[0] != 1.toByte() || bytes.size < HEADER) return bytes
        val id = getInt(bytes, 1); val i = getShort(bytes, 5); val total = getShort(bytes, 7)
        if (total <= 0 || i !in 0 until total) return null
        val byId = pending.getOrPut(from) { HashMap() }
        val parts = byId.getOrPut(id) { arrayOfNulls(total) }
        if (parts.size != total) return null
        parts[i] = bytes.copyOfRange(HEADER, bytes.size)
        if (parts.any { it == null }) {
            // 古い途中のものがたまらないように
            if (byId.size > 8) byId.keys.sorted().take(byId.size - 8).forEach { byId.remove(it) }
            return null
        }
        byId.remove(id)
        val out = ByteArray(parts.sumOf { it!!.size }); var at = 0
        for (p in parts) { p!!.copyInto(out, at); at += p.size }
        return out
    }

    fun forget(from: String) { pending.remove(from) }

    private companion object {
        const val HEADER = 9
        fun putInt(b: ByteArray, at: Int, v: Int) { for (k in 0 until 4) b[at + k] = (v ushr (24 - 8 * k)).toByte() }
        fun getInt(b: ByteArray, at: Int): Int { var v = 0; for (k in 0 until 4) v = (v shl 8) or (b[at + k].toInt() and 0xFF); return v }
        fun putShort(b: ByteArray, at: Int, v: Int) { b[at] = (v ushr 8).toByte(); b[at + 1] = v.toByte() }
        fun getShort(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
    }
}
