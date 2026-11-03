package com.zen.partygame.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zen.partygame.game.LogFmt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/** 表示する言語。日本語の文をキーにして、assets/i18n/<言語>.json から訳を引く（無ければ日本語のまま） */
object I18n {
    data class Lang(val code: String, val label: String)
    val langs = listOf(Lang("ja", "日本語"), Lang("en", "English"), Lang("ko", "한국어"), Lang("zh", "简体中文"))

    private class State(val code: String, val dict: Map<String, String>)
    private var state by mutableStateOf(State("ja", emptyMap()))
    val code: String get() = state.code

    fun lookup(key: String): String = state.dict[key] ?: key

    /** 端末の言語から最初の言語を選ぶ */
    fun systemDefault(): String = when (Locale.getDefault().language) {
        "ja" -> "ja"; "ko" -> "ko"; "zh" -> "zh"; else -> "en"
    }

    fun set(context: Context, code: String) {
        val c = langs.firstOrNull { it.code == code }?.code ?: "ja"
        val dict = runCatching {
            val text = context.assets.open("i18n/$c.json").bufferedReader().use { it.readText() }
            (Json.parseToJsonElement(text) as JsonObject).mapValues { it.value.jsonPrimitive.content }
        }.getOrDefault(emptyMap())
        state = State(c, dict)
    }
}

/** 日本語の文を今の言語にする。{0} {1} … に値を入れる */
fun tr(key: String, vararg args: Any?): String {
    var s = I18n.lookup(key)
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a?.toString() ?: "") }
    return s
}

/** アリーナの記録1行を今の言語にする */
fun formatLog(entry: String): String = LogFmt.format(entry, { I18n.lookup(it) }, tr("、"))
