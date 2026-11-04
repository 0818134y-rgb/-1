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
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.zen.partygame.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 広告のまとめ役。
 * 1. 起動時に同意の確認（ヨーロッパなど同意が必要な地域では、Google の同意画面が出る）
 * 2. 広告を出してよければ AdMob を始め、全画面広告を先読みしておく
 * 3. 広告休憩（全員同時）のときだけ全画面広告を出す。読み込めていなければ何もせず即座に完了とする
 */
class AdController(context: Context) {
    private val app = context.applicationContext
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(app)
    private val started = AtomicBoolean(false)
    private var ad: InterstitialAd? = null
    private var loadedAt = 0L
    private var loading = false

    private val _ready = MutableStateFlow(false)
    /** 広告を出してよい状態になったか（バナーはこれを見て出す） */
    val ready: StateFlow<Boolean> = _ready
    private val _privacyButton = MutableStateFlow(false)
    /** 「広告の設定」ボタンを出す必要があるか（同意が必要な地域だけ） */
    val privacyButton: StateFlow<Boolean> = _privacyButton

    /** 起動時に呼ぶ */
    fun init(activity: Activity) {
        if (BuildConfig.ADMIN) return   // 管理者アプリには広告を出さない（自分の広告を見たり押したりしないように）
        val params = ConsentRequestParameters.Builder().build()
        consent.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                refreshPrivacyButton()
                if (consent.canRequestAds()) startAds()
            }
        }, {
            // 確認できなくても、前回までに同意が取れていれば広告を出せる
            if (consent.canRequestAds()) startAds()
        })
        // 前回の起動で同意済みなら、確認の返事を待たずに始める（早く広告を読み込める）
        if (consent.canRequestAds()) startAds()
    }

    /** 「広告の設定」画面を開く（あとから同意を変えられるように） */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { refreshPrivacyButton() }
    }

    private fun refreshPrivacyButton() {
        _privacyButton.value = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun startAds() {
        if (!started.compareAndSet(false, true)) return
        MobileAds.initialize(app) { _ready.value = true; preload() }
    }

    fun preload() {
        if (!started.get() || loading || (ad != null && !expired())) return
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
        if (BuildConfig.ADMIN) { onDone(); return }
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
