package com.zen.partygame.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class ChunkerTest {
    @Test fun smallMessagesPassThrough() {
        val c = Chunker()
        val b = "{\"t\":\"view\"}".encodeToByteArray()
        assertEquals(1, c.split(b).size)
        assertArrayEquals(b, c.accept("a", c.split(b)[0]))
    }

    @Test fun bigMessagesAreSplitAndRejoined() {
        val tx = Chunker(); val rx = Chunker()
        val big = ByteArray(100_000) { (it % 251).toByte() }.also { it[0] = '{'.code.toByte() }
        val parts = tx.split(big)
        assertEquals(4, parts.size)
        parts.forEach { assertEquals(true, it.size <= 30_000) }
        // 順番が入れ替わっても、ほかの人の分が混ざっても元に戻る
        val other = tx.split(ByteArray(70_000) { 7 })
        val mixed = (parts.map { "a" to it } + other.map { "b" to it }).shuffled(Random(3))
        val done = mixed.mapNotNull { (from, p) -> rx.accept(from, p)?.let { from to it } }
        assertEquals(2, done.size)
        assertArrayEquals(big, done.first { it.first == "a" }.second)
        assertEquals(70_000, done.first { it.first == "b" }.second.size)
        assertNull(rx.accept("a", parts[0]))
    }
}
