package com.zen.partygame.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import com.zen.partygame.R

/**
 * 効果音（res/raw/sfx_*.ogg。tools/make_sfx.py で作ったもの）。SoundPool で、すぐ・重ねて鳴らせる。
 * オン/オフは [enabled]。同じ音が短い間に何度も鳴らないようにしている。
 */
object Sound {
    private val files = mapOf(
        "tap" to R.raw.sfx_tap,
        "card_play" to R.raw.sfx_card_play,
        "card_draw" to R.raw.sfx_card_draw,
        "your_turn" to R.raw.sfx_your_turn,
        "tick" to R.raw.sfx_tick,
        "tick_last" to R.raw.sfx_tick_last,
        "buzzer" to R.raw.sfx_buzzer,
        "win" to R.raw.sfx_win,
        "lose" to R.raw.sfx_lose,
        "reveal" to R.raw.sfx_reveal,
        "page" to R.raw.sfx_page,
        "sent" to R.raw.sfx_sent,
        "join" to R.raw.sfx_join,
        "bomb" to R.raw.sfx_bomb,
        "alert" to R.raw.sfx_alert,
        "sync" to R.raw.sfx_sync,
        "start" to R.raw.sfx_start,
        "vote" to R.raw.sfx_vote,
        "out" to R.raw.sfx_out,
    )
    private var pool: SoundPool? = null
    private val ids = mutableMapOf<String, Int>()
    private val lastAt = mutableMapOf<String, Long>()
    @Volatile var enabled = true

    fun init(context: Context) {
        if (pool != null) return
        val p = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        ).build()
        files.forEach { (name, res) -> ids[name] = p.load(context.applicationContext, res, 1) }
        pool = p
    }

    fun play(name: String, volume: Float = 1f) {
        if (!enabled) return
        val p = pool ?: return
        val id = ids[name] ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - (lastAt[name] ?: 0L) < 60) return
        lastAt[name] = now
        p.play(id, volume, volume, 1, 0, 1f)
    }

    fun playAll(names: List<String>) = names.forEach { play(it) }
}
