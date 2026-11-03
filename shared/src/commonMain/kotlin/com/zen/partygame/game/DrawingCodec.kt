package com.zen.partygame.game

/**
 * お絵かき伝言の絵を、小さな文字列にして送るための形式。
 * 画用紙は横 256 × 縦 192 のマス目。線ごとに [色の番号][太さの番号][点の数 2バイト][x,y …] を並べ、
 * URL で使える base64（-, _、= なし）にする。Android もブラウザも同じこれを使う。
 */
object DrawingCodec {
    const val W = 256
    const val H = 192

    /** 色（ARGB）。0 番は消しゴム（画用紙の白） */
    val COLORS: List<Long> = listOf(
        0xFFFFFFFF, 0xFF1C1838, 0xFF7A7A85, 0xFFE8403A, 0xFFF28C28, 0xFFF5C932,
        0xFF4CB050, 0xFF2BB3A4, 0xFF3A7BE0, 0xFF8A55D0, 0xFFF06FA6, 0xFF8B5A3C,
    )
    /** 太さ（マス目の単位） */
    val WIDTHS: List<Int> = listOf(2, 4, 8, 16)

    class Stroke(val color: Int, val width: Int, val points: IntArray) {
        val size get() = points.size / 2
    }

    /** 文字数の上限に収まるように、入りきらない分の線は捨てる */
    fun encode(strokes: List<Stroke>, maxChars: Int = MAX_DRAWING_CHARS): String {
        val maxBytes = maxChars / 4 * 3
        val out = ArrayList<Byte>()
        for (s in strokes) {
            val n = minOf(s.size, 0xFFFF)
            if (n == 0) continue
            if (out.size + 4 + n * 2 > maxBytes) break
            out += s.color.coerceIn(0, COLORS.size - 1).toByte()
            out += s.width.coerceIn(0, WIDTHS.size - 1).toByte()
            out += (n ushr 8).toByte(); out += n.toByte()
            for (k in 0 until n) {
                out += s.points[2 * k].coerceIn(0, W - 1).toByte()
                out += s.points[2 * k + 1].coerceIn(0, H - 1).toByte()
            }
        }
        return base64(out.toByteArray())
    }

    /** 壊れたデータでも落ちないように、読めたところまでを返す */
    fun decode(text: String): List<Stroke> {
        val b = unbase64(text) ?: return emptyList()
        val list = ArrayList<Stroke>()
        var i = 0
        while (i + 4 <= b.size) {
            val color = (b[i].toInt() and 0xFF).coerceIn(0, COLORS.size - 1)
            val width = (b[i + 1].toInt() and 0xFF).coerceIn(0, WIDTHS.size - 1)
            val n = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            i += 4
            val avail = minOf(n, (b.size - i) / 2)
            val pts = IntArray(avail * 2)
            for (k in 0 until avail) {
                pts[2 * k] = b[i + 2 * k].toInt() and 0xFF
                pts[2 * k + 1] = (b[i + 2 * k + 1].toInt() and 0xFF).coerceAtMost(H - 1)
            }
            i += avail * 2
            if (avail > 0) list += Stroke(color, width, pts)
            if (avail < n) break
        }
        return list
    }

    /** 今の線をあとどれだけ足せるか（点の数）。画面の「インク残り」表示用 */
    fun pointsLeft(strokes: List<Stroke>, maxChars: Int = MAX_DRAWING_CHARS): Int {
        val used = strokes.sumOf { 4 + it.size * 2 }
        return ((maxChars / 4 * 3 - used - 4) / 2).coerceAtLeast(0)
    }

    private const val ABC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private fun base64(b: ByteArray): String = buildString {
        var i = 0
        while (i < b.size) {
            val n = minOf(3, b.size - i)
            var v = 0
            for (k in 0 until 3) v = (v shl 8) or (if (k < n) b[i + k].toInt() and 0xFF else 0)
            for (k in 0..n) append(ABC[(v ushr (18 - 6 * k)) and 63])
            i += 3
        }
    }

    private fun unbase64(s: String): ByteArray? {
        val out = ArrayList<Byte>()
        var v = 0; var bits = 0
        for (c in s) {
            val d = ABC.indexOf(c)
            if (d < 0) return null
            v = (v shl 6) or d; bits += 6
            if (bits >= 8) { bits -= 8; out += ((v ushr bits) and 0xFF).toByte() }
        }
        return out.toByteArray()
    }
}
