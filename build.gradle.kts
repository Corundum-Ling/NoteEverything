// ============================================================
// 根 build.gradle.kts — 声明插件版本（不应用，只声明）
// ============================================================
// Gradle 的约定：根脚本用 "version ... apply false"
// 意思是"声明有这个插件，但不在此处应用"。
// 子模块（:app）会在自己的 build.gradle.kts 里真正 apply。
plugins {
    id("com.android.application") version "8.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Room 需要 kapt（Kotlin Annotation Processing Tool）来生成代码
    id("org.jetbrains.kotlin.kapt") version "1.9.24" apply false
}
