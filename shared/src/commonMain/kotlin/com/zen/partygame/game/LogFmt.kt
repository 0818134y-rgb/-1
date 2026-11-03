package com.zen.partygame.game

/**
 * 端末ごとの言語で表示できるように、ゲームの記録（ログ）を「訳す文の型＋値」の形で送る。
 * 1件 = START 型 (SEP 値)* END。値の中にさらに1件を入れられる（訳す言葉）。それ以外の値（名前・数）はそのまま。
 * 型が LIST なら、最初の値が見出し、残りを「、」でつなぐ。型が CARD_RULE+カードID なら、そのカードの文の2文目（ルール）。
 */
object LogFmt {
    const val START = '\u0002'
    const val END = '\u0003'
    const val SEP = '\u001F'
    const val LIST = "\u0005"
    const val CARD_RULE = "\u0006"

    fun entry(tpl: String, args: List<Any?>): String = buildString {
        append(START).append(tpl)
        for (a in args) append(SEP).append(a?.toString() ?: "?")
        append(END)
    }

    fun isEntry(s: String) = s.startsWith(START)

    /** @param tr 日本語の型 → その言語の型、 @param sep 並べるときの区切り */
    fun format(text: String, tr: (String) -> String, sep: String): String {
        if (!isEntry(text)) return text
        var i = 0
        fun parse(): String {
            // text[i] == START
            i++
            val parts = mutableListOf<String>()
            val cur = StringBuilder()
            while (i < text.length) {
                val c = text[i]
                when (c) {
                    START -> cur.append(parse())
                    SEP -> { parts += cur.toString(); cur.clear(); i++ }
                    END -> { i++; parts += cur.toString(); return render(parts, tr, sep) }
                    else -> { cur.append(c); i++ }
                }
            }
            parts += cur.toString()
            return render(parts, tr, sep)
        }
        return parse() + text.substring(i)
    }

    private fun render(parts: List<String>, tr: (String) -> String, sep: String): String {
        val tpl = parts[0]; val args = parts.drop(1)
        if (tpl == LIST) return (args.firstOrNull() ?: "") + args.drop(1).joinToString(sep)
        if (tpl.startsWith(CARD_RULE)) return afterFirstSentence(tr(ArenaCards.def(tpl.substring(1)).text))
        var out = tr(tpl)
        args.forEachIndexed { k, a -> out = out.replace("{$k}", a) }
        return out
    }
}

/** カードの文の「2文目から」（1文目は効果、残りがルールの説明） */
fun afterFirstSentence(s: String): String {
    val i = s.indexOf('。').takeIf { it >= 0 } ?: s.indexOf(". ").takeIf { it >= 0 }?.plus(1) ?: return s
    return s.substring(i + 1).trim()
}
