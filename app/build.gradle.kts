import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
        versionCode = 7
        versionName = "0.6.1"

        // ↓ Google のテスト用広告ID。公開前に自分の AdMob のIDに差し替える
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        buildConfigField("String", "AD_BANNER_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
        buildConfigField("String", "AD_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
        // ↓ プライバシーポリシーのURL（GitHub Pages などに置く）
        buildConfigField("String", "PRIVACY_URL", "\"https://example.com/privacy\"")
        // ↓ ウェブ版（iPhone・PC 用）を公開した URL。入れると「招待を送る」の文に載る
        buildConfigField("String", "WEB_URL", "\"https://0818134y-rgb.github.io/-/\"")
    }

    buildTypes {
        release {
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
