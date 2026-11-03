package com.zen.partygame.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random

class DrawGameTest {
    private var clock = 1_000_000L
    private lateinit var bank: TopicBank

    @Before fun setUp() {
        val f = listOf(File("app/src/main/assets/topics.json"), File("../app/src/main/assets/topics.json")).first { it.exists() }
        bank = TopicBank.parse(f.readText())
    }

    private fun engine(n: Int, seed: Int = 1): Engine {
        val e = Engine("1234", false, bank, Random(seed)) { clock }
        e.join("h", "ホスト", isHost = true)
        for (i in 1 until n) e.join("p$i", "P$i")
        e.apply("h", Intent.UpdateSettings(Settings(game = GameType.DRAW)))
        return e
    }

    private fun Engine.task(id: String) = viewFor(id).screen as Screen.DrawTask

    private fun pic(seed: Int) = DrawingCodec.encode(listOf(DrawingCodec.Stroke(1 + seed % 10, 1, intArrayOf(seed % 200, 10, 100, 100, 200, 150))))

    /** 全員の手番をこなす。書く・答えるは「何手目・だれ」が分かる文、描くはその文を絵の点に埋めた絵 */
    private fun Engine.playStep() {
        val ids = players.map { it.id }
        for (id in ids) {
            val t = task(id)
            val intent = when (t.kind) {
                DrawKind.WRITE -> Intent.DrawSubmit(t.step, text = "お題-$id")
                DrawKind.DRAW -> Intent.DrawSubmit(t.step, drawing = pic(t.step * 10 + ids.indexOf(id)))
                DrawKind.GUESS -> Intent.DrawSubmit(t.step, text = "答え${t.step}-$id")
            }
            assertTrue(apply(id, intent))
        }
    }

    @Test fun drawingCodecRoundTrip() {
        val strokes = listOf(
            DrawingCodec.Stroke(3, 2, intArrayOf(0, 0, 255, 191, 128, 96)),
            DrawingCodec.Stroke(0, 3, intArrayOf(10, 20)),
        )
        val back = DrawingCodec.decode(DrawingCodec.encode(strokes))
        assertEquals(2, back.size)
        assertEquals(listOf(0, 0, 255, 191, 128, 96), back[0].points.toList())
        assertEquals(3, back[0].color); assertEquals(2, back[0].width)
        assertEquals(0, back[1].color); assertEquals(listOf(10, 20), back[1].points.toList())
        // 壊れたデータでも落ちない
        assertTrue(DrawingCodec.decode("!!!").isEmpty())
        DrawingCodec.decode("AQEAZAAA")   // 点の数が足りない
    }

    @Test fun encodeRespectsSizeLimit() {
        val big = (0 until 50).map { s -> DrawingCodec.Stroke(1, 1, IntArray(1000) { (it * 7 + s) % 190 }) }
        val text = DrawingCodec.encode(big)
        assertTrue(text.length <= MAX_DRAWING_CHARS)
        assertTrue(DrawingCodec.decode(text).isNotEmpty())
    }

    @Test fun chainsPassAroundAndRevealInOrder() {
        for (n in 3..8) {
            val e = engine(n, seed = n)
            e.apply("h", Intent.Start)
            val first = e.task("h")
            assertEquals(DrawKind.WRITE, first.kind); assertEquals(n, first.steps)
            assertTrue(first.suggestion!!.isNotBlank())
            repeat(n) { step ->
                // 描く番には前の人の文、答える番には前の人の絵が来る
                for (p in e.players) {
                    val t = e.task(p.id)
                    assertEquals(step, t.step)
                    when (t.kind) {
                        DrawKind.DRAW -> assertTrue(t.text!!.startsWith("お題-") || t.text!!.startsWith("答え"))
                        DrawKind.GUESS -> assertTrue(t.drawing!!.isNotEmpty())
                        DrawKind.WRITE -> Unit
                    }
                }
                e.playStep()
            }
            // 発表：本の数 = 人数、各本の長さ = 人数、全員が1回ずつ関わる
            var r = e.viewFor("h").screen as Screen.DrawReveal
            assertEquals(n, r.books); assertEquals(1, r.entries.size)
            val seenBooks = mutableSetOf<Int>()
            var guard = 0
            while (!r.finished && guard++ < 200) {
                seenBooks += r.book
                if (r.entries.size == r.total) {
                    assertEquals(n, r.total)
                    assertEquals(e.players.map { it.id }.toSet(), r.entries.map { it.by }.toSet())
                    assertEquals("お題-${r.owner}", r.entries[0].text)
                    r.entries.forEachIndexed { i, en ->
                        assertEquals(if (i == 0) DrawKind.WRITE else if (i % 2 == 1) DrawKind.DRAW else DrawKind.GUESS, en.kind)
                    }
                }
                assertFalse(e.apply("p1", Intent.Next))   // めくるのはホストだけ
                assertTrue(e.apply("h", Intent.Next))
                r = e.viewFor("h").screen as Screen.DrawReveal
            }
            assertTrue(r.finished); assertEquals(n, seenBooks.size)
            // 発表が終われば次のラウンドへ
            assertTrue(e.apply("h", Intent.Next))
            assertEquals(DrawKind.WRITE, e.task("h").kind)
        }
    }

    @Test fun timeoutFillsBlanksAndAdvances() {
        val e = engine(3)
        e.apply("h", Intent.Start)
        e.apply("h", Intent.DrawSubmit(0, text = "ねこが寝ている"))
        // 締め切り（＋猶予）まで待つと、出していない人はおまかせお題になる
        clock += 45_000 + DRAW_GRACE_MS + 1
        assertTrue(e.tick())
        val t = e.task("h")
        assertEquals(1, t.step); assertEquals(DrawKind.DRAW, t.kind)
        // 描く手番の残り時間は猶予を含まない
        val rem = e.viewFor("h").remainingMs!!
        assertTrue(rem in 89_000..90_000)
        // 古い手番の送信は無視
        assertFalse(e.apply("p1", Intent.DrawSubmit(0, text = "遅れた")))
        // ホストは締め切りを待たずに進められる
        assertTrue(e.apply("h", Intent.CloseVote))
        assertEquals(2, e.task("h").step)
    }

    @Test fun leaverIsSkippedWithBlanks() {
        val e = engine(4)
        e.apply("h", Intent.Start)
        e.apply("h", Intent.DrawSubmit(0, text = "a")); e.apply("p1", Intent.DrawSubmit(0, text = "b")); e.apply("p2", Intent.DrawSubmit(0, text = "c"))
        e.leave("p3")
        assertEquals(1, e.task("h").step)
        // 残り3人で最後まで
        repeat(3) { e.playStep() }
        val r = e.viewFor("h").screen as Screen.DrawReveal
        assertEquals(4, r.books); assertEquals(4, r.total)
        // 2人を下回ったらロビーへ
        val e2 = engine(3); e2.apply("h", Intent.Start); e2.leave("p1"); e2.leave("p2")
        assertTrue(e2.viewFor("h").screen is Screen.Lobby)
    }

    @Test fun rejectsBadDrawingData() {
        val e = engine(3)
        e.apply("h", Intent.Start)
        e.playStep()
        e.apply("h", Intent.DrawSubmit(1, drawing = "<script>"))
        e.apply("p1", Intent.DrawSubmit(1, drawing = "A".repeat(MAX_DRAWING_CHARS + 100)))
        e.apply("p2", Intent.DrawSubmit(1, drawing = pic(1)))
        // 次の人が見る絵：不正なものは空、長すぎるものは切り詰め
        val drawings = e.players.map { e.task(it.id).drawing!! }
        assertTrue(drawings.contains(""))
        assertTrue(drawings.all { it.length <= MAX_DRAWING_CHARS })
    }

    @Test fun viewsStaySmallForNearby() {
        // 8人で毎回上限いっぱいの絵を描いても、1回に送る内容が大きくなりすぎない（分割送信の確認用に大きさを出す）
        val e = engine(8)
        e.apply("h", Intent.Start)
        val full = DrawingCodec.encode((0 until 200).map { s -> DrawingCodec.Stroke(1, 1, IntArray(200) { (it * 13 + s) % 190 }) })
        assertTrue(full.length > MAX_DRAWING_CHARS - 600)
        repeat(8) {
            for (p in e.players) {
                val t = e.task(p.id)
                e.apply(p.id, if (t.kind == DrawKind.DRAW) Intent.DrawSubmit(t.step, drawing = full) else Intent.DrawSubmit(t.step, text = "あ".repeat(40)))
            }
        }
        var max = 0
        repeat(70) {
            max = maxOf(max, WireJson.encodeToString(PlayerView.serializer(), e.viewFor("p1")).encodeToByteArray().size)
            e.apply("h", Intent.Next)
        }
        println("largest reveal view: $max bytes")
        assertTrue(max < 120_000)
    }
}
