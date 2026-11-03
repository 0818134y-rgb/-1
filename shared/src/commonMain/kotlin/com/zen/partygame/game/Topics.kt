package com.zen.partygame.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

@Serializable
data class WwPair(val c: String, val a: String, val b: String)

@Serializable
data class NgWord(val c: String, val w: String)

@Serializable
data class TopicFile(
    val version: Int,
    val wordwolf: List<WwPair>,
    val ng: List<NgWord>,
    val spy: List<String> = emptyList(),
    val sync: List<String> = emptyList(),
    val guess: List<String> = emptyList(),
    val questions: List<String> = emptyList(),
    val drawWho: List<String> = emptyList(),
    val drawWhat: List<String> = emptyList(),
)

/** お題の山。同じ部屋の中では、全部使い切るまで同じお題を出さない */
class TopicBank(private val file: TopicFile) {
    val categories: List<String> = file.wordwolf.map { it.c }.distinct()
    private val usedPairs = mutableSetOf<Int>()
    private val usedNg = mutableSetOf<Int>()

    fun drawPair(categories: List<String>, rng: Random): WwPair {
        val pool = file.wordwolf.indices.filter { categories.isEmpty() || file.wordwolf[it].c in categories }
            .ifEmpty { file.wordwolf.indices.toList() }
        var fresh = pool.filter { it !in usedPairs }
        if (fresh.isEmpty()) {
            usedPairs.removeAll(pool.toSet()); fresh = pool
        }
        val i = fresh[rng.nextInt(fresh.size)]
        usedPairs += i
        return file.wordwolf[i]
    }

    fun drawNg(count: Int, level: NgLevel, rng: Random): List<String> {
        val pool = file.ng.indices.filter {
            when (level) {
                NgLevel.NORMAL -> file.ng[it].c == "ふつう"
                NgLevel.HARD -> file.ng[it].c == "ハード"
                NgLevel.MIX -> true
            }
        }.ifEmpty { file.ng.indices.toList() }
        var fresh = pool.filter { it !in usedNg }
        if (fresh.size < count) {
            usedNg.removeAll(pool.toSet()); fresh = pool
        }
        val picked = fresh.shuffled(rng).take(count)
        usedNg += picked
        return picked.map { file.ng[it].w }
    }

    private val usedOther = mutableMapOf<String, MutableSet<Int>>()

    /** リストから、まだ出していないものを1つ選ぶ（全部出したら最初から） */
    private fun drawFrom(key: String, list: List<String>, rng: Random): String {
        if (list.isEmpty()) return "？"
        val used = usedOther.getOrPut(key) { mutableSetOf() }
        if (used.size >= list.size) used.clear()
        val fresh = list.indices.filter { it !in used }
        val i = fresh[rng.nextInt(fresh.size)]
        used += i
        return list[i]
    }

    fun drawSpyPlace(rng: Random) = drawFrom("spy", file.spy, rng)
    fun drawSync(rng: Random) = drawFrom("sync", file.sync, rng)
    fun drawGuess(rng: Random) = drawFrom("guess", file.guess, rng)
    fun drawQuestion(rng: Random) = drawFrom("q", file.questions, rng)
    /** お絵かき伝言のおまかせお題（だれが＋何をしている） */
    fun drawPrompt(rng: Random): String =
        if (file.drawWho.isEmpty() || file.drawWhat.isEmpty()) drawGuess(rng)
        else drawFrom("dwho", file.drawWho, rng) + "が" + drawFrom("dwhat", file.drawWhat, rng)

    /** スパイが選ぶ場所の候補（正解を含めて count 個） */
    fun spyOptions(correct: String, count: Int, rng: Random): List<String> =
        (file.spy.filter { it != correct }.shuffled(rng).take(count - 1) + correct).shuffled(rng)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String) = TopicBank(json.decodeFromString(TopicFile.serializer(), text))
    }
}
