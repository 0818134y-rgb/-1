import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

/** プロジェクトの一番上にある設定ファイルを読む（公開リポジトリには入れない） */
fun props(name: String) = Properties().apply { rootProject.file(name).takeIf { it.exists() }?.inputStream()?.use { load(it) } }
// 本物の広告ID（AdMob で作ったもの）。monetization.properties に書く。無ければテスト用の広告になる
val ads = props("monetization.properties")
// アプリの署名（Google Play に上げる鍵）。keystore.properties に書く
val keys = props("keystore.properties")
// Google のテスト用広告ID（開発中はこれ。自分で本物の広告をタップすると AdMob の規約違反になるため）
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testBanner = "ca-app-pub-3940256099942544/9214589741"
val testInterstitial = "ca-app-pub-3940256099942544/1033173712"
val realAppId: String? = ads.getProperty("admob.appId")
fun q(v: String) = "\"$v\""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zen.partygame"
    compileSdk = 36

    defaultConfig {
        // Google Play に一度出すと変えられない。公開前に決めること
        applicationId = "com.zen.partygame"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "0.9.1"

        // ↓ ウェブ版（iPhone・PC 用）を公開した URL。入れると「招待を送る」の文に載る
        val web = ads.getProperty("site.url") ?: ads.getProperty("site.domain")?.let { "https://$it/" } ?: "https://0818134y-rgb.github.io/-/"
        buildConfigField("String", "WEB_URL", q(web))
        // ↓ プライバシーポリシー（ウェブ版と同じ場所に置いてある）
        buildConfigField("String", "PRIVACY_URL", q(web + "privacy.html"))
    }

    signingConfigs {
        if (keys.getProperty("storeFile") != null) create("release") {
            storeFile = rootProject.file(keys.getProperty("storeFile"))
            storePassword = keys.getProperty("storePassword")
            keyAlias = keys.getProperty("keyAlias")
            keyPassword = keys.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            // 開発中・テスト版は必ずテスト用の広告
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "AD_BANNER_ID", q(testBanner))
            buildConfigField("String", "AD_INTERSTITIAL_ID", q(testInterstitial))
        }
        release {
            // 公開版は本物の広告（monetization.properties が無いときはテスト用。そのままでは公開できないよう下で止める）
            manifestPlaceholders["admobAppId"] = realAppId ?: testAppId
            buildConfigField("String", "AD_BANNER_ID", q(ads.getProperty("admob.banner") ?: testBanner))
            buildConfigField("String", "AD_INTERSTITIAL_ID", q(ads.getProperty("admob.interstitial") ?: testInterstitial))
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// 本物の広告IDが入っていない公開版は作らない（-PallowTestAds で試しに作るときだけ通す）
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        if (realAppId == null && !project.hasProperty("allowTestAds"))
            throw GradleException("monetization.properties に AdMob の本物の広告IDがありません（MONETIZE.md を参照）")
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(project(":shared"))
    implementation(libs.serialization.json)
    implementation(libs.play.nearby)
    implementation(libs.play.ads)
    testImplementation(libs.junit)
    // 画面の見た目確認（JVM上でComposeを描いて画像にする）
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
