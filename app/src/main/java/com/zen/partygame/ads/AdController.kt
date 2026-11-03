package com.zen.partygame.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.zen.partygame.BuildConfig

/**
 * 全画面広告の先読みと表示。ネットがあるうちに読み込んでおき、
 * 広告休憩（全員同時）のときだけ出す。読み込めていなければ何もせず即座に完了とする。
 */
class AdController(context: Context) {
    private val app = context.applicationContext
    private var ad: InterstitialAd? = null
    private var loadedAt = 0L
    private var loading = false
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        MobileAds.initialize(app) { preload() }
    }

    fun preload() {
        if (loading || (ad != null && !expired())) return
        loading = true
        InterstitialAd.load(app, BuildConfig.AD_INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    ad = loaded; loadedAt = SystemClock.elapsedRealtime(); loading = false
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null; loading = false
                }
            })
    }

    /** 読み込んだ広告は1時間で使えなくなるので、少し手前で捨てる */
    private fun expired() = SystemClock.elapsedRealtime() - loadedAt > 55 * 60 * 1000L

    /** 広告を出す。閉じた・出せなかった・無かった、のいずれでも [onDone] を1回だけ呼ぶ */
    fun show(activity: Activity, onDone: () -> Unit) {
        val current = ad?.takeIf { !expired() }
        ad = null
        if (current == null) { onDone(); preload(); return }
        var done = false
        val finish = { if (!done) { done = true; onDone(); preload() } }
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finish()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = finish()
        }
        current.show(activity)
    }
}
