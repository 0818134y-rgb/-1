import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// ゲームの進行（Engine・ルール・通信の形式）。Android アプリと PC 版（JavaScript）で同じコードを使う
kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    js(IR) {
        outputModuleName.set("partygame")
        nodejs()
        binaries.executable()
        compilerOptions {
            moduleKind.set(JsModuleKind.MODULE_UMD)
            target.set("es2015")
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.serialization.json)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}
