package com.zen.partygame.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.zen.partygame.R

/**
 * 場面ごとのBGM（res/raw/bgm_*.ogg。tools/make_bgm.py で作ったもの）。
 * 曲が変わるときは前の曲を小さくしながら次の曲を大きくする（クロスフェード）。
 * アプリが裏に回ったら止め、戻ったら続きから流す。オン/オフは [enabled]。
 * 他のアプリの音楽を止めないよう、オーディオフォーカスは取らない（重ねて鳴る）。
 */
object Music {
    private val files = mapOf(
        "title" to R.raw.bgm_title,
        "lobby" to R.raw.bgm_lobby,
        "talk" to R.raw.bgm_talk,
        "tension" to R.raw.bgm_tension,
        "arena" to R.raw.bgm_arena,
        "draw" to R.raw.bgm_draw,
        "result" to R.raw.bgm_result,
        "break" to R.raw.bgm_break,
    )
    /** 効果音より小さく。曲そのものの大きさは make_bgm.py でそろえてある */
    private const val VOLUME = 0.42f
    private const val FADE_MS = 700L
    private const val STEP_MS = 35L

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private var want: String? = null       // 流したい曲
    private var current: Player? = null    // 今流している曲
    private val fading = mutableListOf<Player>()
    private var foreground = true
    var enabled = true
        set(v) { field = v; main.post { sync() } }

    private class Player(val track: String, val mp: MediaPlayer) {
        var vol = 0f
        var target = 0f
        fun apply() = runCatching { mp.setVolume(vol, vol) }
    }

    fun init(context: Context) { app = context.applicationContext }

    /** 今の場面の曲（同じ曲ならそのまま流し続ける） */
    fun play(track: String) = main.post { want = track; sync() }

    fun onForeground(v: Boolean) = main.post {
        foreground = v
        if (!v) { current?.let { runCatching { if (it.mp.isPlaying) it.mp.pause() } }; fading.forEach { release(it) }; fading.clear() }
        else sync()
    }

    private fun sync() {
        if (!::app.isInitialized) return
        val track = want?.takeIf { enabled && foreground }
        val cur = current
        if (track == null) {
            // オフにしたときはすぐ止める。裏に回ったときは onForeground で一時停止している
            if (!enabled) { cur?.let { release(it) }; current = null; fading.forEach { release(it) }; fading.clear() }
            return
        }
        if (cur != null && cur.track == track) {
            runCatching { if (!cur.mp.isPlaying) cur.mp.start() }
            cur.target = VOLUME; tick(); return
        }
        // 新しい曲を始めて、前の曲は消えていくように
        if (cur != null) { cur.target = 0f; fading += cur }
        val res = files[track] ?: return
        val mp = runCatching {
            MediaPlayer.create(app, res, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(), 0)
        }.getOrNull() ?: return
        mp.isLooping = true
        val p = Player(track, mp); p.apply(); p.target = VOLUME
        runCatching { mp.start() }
        current = p
        tick()
    }

    private var ticking = false
    private fun tick() {
        if (ticking) return
        ticking = true
        main.post(object : Runnable {
            override fun run() {
                val step = VOLUME * STEP_MS / FADE_MS
                var moving = false
                (listOfNotNull(current) + fading).forEach { p ->
                    if (p.vol != p.target) {
                        p.vol = if (p.vol < p.target) minOf(p.target, p.vol + step) else maxOf(p.target, p.vol - step)
                        p.apply(); moving = true
                    }
                }
                fading.filter { it.vol <= 0f }.forEach { release(it) }
                fading.removeAll { it.vol <= 0f }
                if (moving) main.postDelayed(this, STEP_MS) else ticking = false
            }
        })
    }

    private fun release(p: Player) = runCatching { p.mp.stop(); p.mp.release() }
}
