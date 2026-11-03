package com.zen.partygame.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zen.partygame.R
import com.zen.partygame.game.DrawEntry
import com.zen.partygame.game.DrawKind
import com.zen.partygame.game.DrawingCodec
import com.zen.partygame.game.Intent
import com.zen.partygame.game.MAX_DRAWING_CHARS
import com.zen.partygame.game.MAX_DRAW_TEXT_LEN
import com.zen.partygame.game.PlayerView
import com.zen.partygame.game.Screen

private val Paper = Color(0xFFF6EBD9)
private val drawColors = DrawingCodec.COLORS.map { Color(it) }
private const val MAX_BYTES = MAX_DRAWING_CHARS / 4 * 3

/** 描きかけの線（点を足していく） */
private class LiveStroke(val color: Int, val width: Int) {
    val pts = mutableStateListOf<Int>()
    fun toStroke() = DrawingCodec.Stroke(color, width, pts.toIntArray())
}

private fun DrawScope.paintStrokes(strokes: List<DrawingCodec.Stroke>) {
    drawRect(Color.White)
    val k = size.width / DrawingCodec.W
    for (s in strokes) paintStroke(s.color, s.width, s.points.asList(), k)
}

private fun DrawScope.paintStroke(color: Int, width: Int, p: List<Int>, k: Float) {
    if (p.size < 2) return
    val c = drawColors[color]
    val w = DrawingCodec.WIDTHS[width] * k
    if (p.size == 2) { drawCircle(c, w / 2, Offset((p[0] + .5f) * k, (p[1] + .5f) * k)); return }
    val path = Path().apply {
        moveTo((p[0] + .5f) * k, (p[1] + .5f) * k)
        var i = 2
        while (i + 1 < p.size) { lineTo((p[i] + .5f) * k, (p[i + 1] + .5f) * k); i += 2 }
    }
    drawPath(path, c, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 送られてきた絵を見るだけの画用紙 */
@Composable
fun DrawingView(drawing: String, modifier: Modifier = Modifier) {
    val strokes = remember(drawing) { DrawingCodec.decode(drawing) }
    Canvas(
        modifier.fillMaxWidth().widthIn(max = 560.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(3.dp, Paper), RoundedCornerShape(14.dp)),
    ) { paintStrokes(strokes) }
}

@Composable
private fun DrawHeader(title: String, s: Screen.DrawTask, v: PlayerView, remaining: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(R.drawable.icon_draw, 44.dp); Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Caption("${s.step + 1} / ${s.steps}")
        }
        if (v.remainingMs != null) Text(formatMs(remaining), style = MaterialTheme.typography.headlineMedium,
            color = if (remaining < 10_000) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun DrawTask(vm: AppViewModel, v: PlayerView, s: Screen.DrawTask, remaining: Long) {
    // 描きかけの絵・入力は手番ごとに持つ（ほかの人が送って画面が更新されても消えない）
    val strokes = remember(s.step) { mutableStateListOf<LiveStroke>() }
    var text by rememberSaveable(s.step) { mutableStateOf("") }
    var sent by remember(s.step) { mutableStateOf(false) }
    var color by rememberSaveable { mutableIntStateOf(1) }
    var width by rememberSaveable { mutableIntStateOf(1) }
    var askBlank by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }

    fun submit() {
        if (sent || s.submitted) return
        sent = true
        if (s.kind == DrawKind.DRAW) vm.send(Intent.DrawSubmit(s.step, drawing = DrawingCodec.encode(strokes.map { it.toStroke() })))
        else vm.send(Intent.DrawSubmit(s.step, text = text.trim()))
    }
    // 時間切れになったら、そこまでの分を自動で送る
    LaunchedEffect(s.step, remaining <= 0L && v.remainingMs != null) {
        if (remaining <= 0L && v.remainingMs != null) submit()
    }

    DrawHeader(when (s.kind) { DrawKind.WRITE -> tr("お題を書こう"); DrawKind.DRAW -> tr("絵を描こう"); DrawKind.GUESS -> tr("この絵は何？") }, s, v, remaining)
    when (s.kind) {
        DrawKind.WRITE -> Caption(tr("次の人がこれを絵にします。おもしろい文を書こう！（例：{0}）", s.suggestion ?: ""))
        DrawKind.DRAW -> Panel { Caption(tr("お題")); BigWord(s.text ?: "", MaterialTheme.colorScheme.onSurface) }
        DrawKind.GUESS -> { Caption(tr("前の人が描いた絵です。何を描いたのか、文で答えよう")); DrawingView(s.drawing ?: "") }
    }

    if (s.submitted || sent) {
        Panel {
            Text(tr("送りました！"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Caption(tr("みんながそろうと次へ進みます"), Modifier.fillMaxWidth())
        }
    } else if (s.kind == DrawKind.DRAW) {
        DrawPad(strokes, color, width)
        DrawTools(color, width, strokes, onColor = { color = it }, onWidth = { width = it },
            onUndo = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1) }, onClear = { if (strokes.isNotEmpty()) askClear = true })
        BigButton(tr("できた！")) { if (strokes.isEmpty()) askBlank = true else submit() }
    } else {
        OutlinedTextField(
            value = text, onValueChange = { text = it.replace("\n", "").take(MAX_DRAW_TEXT_LEN) },
            label = { Text(if (s.kind == DrawKind.WRITE) tr("お題を書く（{0}文字まで）", MAX_DRAW_TEXT_LEN) else tr("この絵は…")) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.kind == DrawKind.WRITE) BigButton(tr("おまかせのお題"), Modifier.weight(1f), secondary = true) { text = s.suggestion ?: "" }
            BigButton(tr("これで決定"), Modifier.weight(1f), enabled = text.isNotBlank()) { submit() }
        }
    }
    val left = v.players.filter { it.id !in s.done }
    Caption(tr("できた人 {0} / {1}人", s.done.size, v.players.size) + if (left.isNotEmpty() && left.size <= 3) tr("（まだ：{0}）", left.joinToString(tr("、")) { it.name }) else "",
        Modifier.fillMaxWidth())
    if (v.isHost && s.done.size < v.players.size) TextButton(onClick = { vm.send(Intent.CloseVote) }, modifier = Modifier.fillMaxWidth()) {
        Text(tr("待たずに次へ（まだの人は白紙）"))
    }

    if (askBlank) AlertDialog(
        onDismissRequest = { askBlank = false },
        title = { Text(tr("何も描いていません")) }, text = { Text(tr("このまま白紙で送りますか？")) },
        confirmButton = { TextButton(onClick = { askBlank = false; submit() }) { Text(tr("白紙で送る")) } },
        dismissButton = { TextButton(onClick = { askBlank = false }) { Text(tr("描く")) } },
    )
    if (askClear) AlertDialog(
        onDismissRequest = { askClear = false },
        title = { Text(tr("全部消しますか？")) }, text = { Text(tr("描いた絵がすべて消えます")) },
        confirmButton = { TextButton(onClick = { askClear = false; strokes.clear() }) { Text(tr("消す")) } },
        dismissButton = { TextButton(onClick = { askClear = false }) { Text(tr("やめる")) } },
    )
}

private fun bytesUsed(strokes: List<LiveStroke>) = strokes.sumOf { 4 + it.pts.size }

@Composable
private fun DrawPad(strokes: MutableList<LiveStroke>, color: Int, width: Int) {
    Canvas(
        Modifier.fillMaxWidth().widthIn(max = 560.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(3.dp, Lantern), RoundedCornerShape(14.dp))
            .pointerInput(color, width) {
                // 画面のスクロールより先に指の動きを受け取って、線にする
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    if (MAX_BYTES - bytesUsed(strokes) < 10) return@awaitEachGesture
                    fun grid(o: Offset) = intArrayOf(
                        (o.x / size.width * DrawingCodec.W).toInt().coerceIn(0, DrawingCodec.W - 1),
                        (o.y / size.height * DrawingCodec.H).toInt().coerceIn(0, DrawingCodec.H - 1),
                    )
                    val cur = LiveStroke(color, width)
                    grid(down.position).let { cur.pts.add(it[0]); cur.pts.add(it[1]) }
                    strokes.add(cur)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) { ch.consume(); break }
                        val p = grid(ch.position)
                        val n = cur.pts.size
                        if (kotlin.math.abs(p[0] - cur.pts[n - 2]) + kotlin.math.abs(p[1] - cur.pts[n - 1]) >= 2 &&
                            MAX_BYTES - bytesUsed(strokes) > 6
                        ) { cur.pts.add(p[0]); cur.pts.add(p[1]) }
                        ch.consume()
                    }
                }
            },
    ) {
        drawRect(Color.White)
        val k = size.width / DrawingCodec.W
        for (s in strokes) paintStroke(s.color, s.width, s.pts, k)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrawTools(
    color: Int, width: Int, strokes: List<LiveStroke>,
    onColor: (Int) -> Unit, onWidth: (Int) -> Unit, onUndo: () -> Unit, onClear: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        drawColors.forEachIndexed { i, c ->
            if (i == 0) return@forEachIndexed
            Box(
                Modifier.size(34.dp).scale(if (color == i) 1.12f else 1f).clip(CircleShape).background(c)
                    .border(BorderStroke(3.dp, if (color == i) Lantern else Color(0xFF4A4578)), CircleShape).clickable { onColor(i) },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        DrawingCodec.WIDTHS.forEachIndexed { i, w ->
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(BorderStroke(2.dp, if (width == i) Lantern else MaterialTheme.colorScheme.surfaceContainerHigh), RoundedCornerShape(12.dp))
                    .clickable { onWidth(i) },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size((4 + w * 1.4f).dp).clip(CircleShape).background(Soft)) }
        }
        Spacer(Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ToolButton(tr("消しゴム"), color == 0, Modifier.weight(1f)) { onColor(0) }
        ToolButton(tr("1つ戻す"), false, Modifier.weight(1f), onUndo)
        ToolButton(tr("全部消す"), false, Modifier.weight(1f), onClear)
    }
    val used = bytesUsed(strokes).toFloat() / MAX_BYTES
    LinearProgressIndicator(
        progress = { (1f - used).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = if (used > .85f) Berry else Mint, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    if (used > .97f) Caption(tr("インクがなくなりました（「1つ戻す」で減らせます）"))
}

@Composable
private fun ToolButton(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, if (on) Berry else MaterialTheme.colorScheme.surfaceContainerHigh),
    ) { Text(label, color = if (on) Berry else Soft, maxLines = 1) }
}

@Composable
fun DrawReveal(vm: AppViewModel, v: PlayerView, s: Screen.DrawReveal, next: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(R.drawable.icon_draw, 44.dp); Spacer(Modifier.size(10.dp))
        Column {
            Text(tr("{0}さんのお題から", s.ownerName), style = MaterialTheme.typography.headlineMedium)
            Caption(tr("{0} / {1}冊目　{2} / {3}コマ", s.book + 1, s.books, s.entries.size, s.total))
        }
    }
    s.entries.forEachIndexed { i, e -> RevealPage(e, newest = i == s.entries.size - 1 && !s.finished) }
    if (s.finished) {
        Caption(tr("全員の発表が終わりました！"), Modifier.fillMaxWidth())
        next()
    } else if (v.isHost) {
        val endOfBook = s.entries.size >= s.total
        BigButton(if (!endOfBook) tr("次をめくる") else if (s.book + 1 < s.books) tr("次の人の流れへ") else tr("発表おわり")) { vm.send(Intent.Next) }
    } else Caption(tr("ホストがめくるのを待っています…"), Modifier.fillMaxWidth())
}

@Composable
private fun RevealPage(e: DrawEntry, newest: Boolean) {
    val pop = remember(e) { Animatable(if (newest) 0.94f else 1f) }
    LaunchedEffect(e) { pop.animateTo(1f) }
    Panel(Modifier.scale(pop.value).then(if (newest) Modifier.border(BorderStroke(2.dp, Lantern), RoundedCornerShape(20.dp)) else Modifier)) {
        Caption(e.byName + when (e.kind) { DrawKind.WRITE -> tr("さんのお題"); DrawKind.DRAW -> tr("さんが描いた絵"); DrawKind.GUESS -> tr("さんの答え") })
        Spacer(Modifier.height(6.dp))
        if (e.kind == DrawKind.DRAW) DrawingView(e.drawing)
        else Text(
            e.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Night,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Paper).padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
