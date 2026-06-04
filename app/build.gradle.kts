// ============================================================
// app/build.gradle.kts — Android 应用模块的构建配置
// ============================================================
// 这是最核心的配置文件。每当你添加新依赖库时，都在这里加。
//
// 关键概念：
// - compileSdk：编译时使用的 Android SDK 版本（34 = Android 14）
// - minSdk：最低支持的 Android 版本（26 = Android 8.0）
// - targetSdk：目标版本（告诉系统你的 App 针对此版本优化过）
// - Compose BOM：统一管理所有 Compose 库的版本，避免版本冲突

plugins {
    id("com.android.application")      // Android 应用插件
    id("org.jetbrains.kotlin.android") // Kotlin Android 支持
    id("org.jetbrains.kotlin.kapt")    // Kotlin 注解处理（Room 需要）
}

android {
    // namespace：代替 AndroidManifest.xml 中的 package 属性
    namespace = "com.corunling.noteeverything"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.corunling.noteeverything"
        minSdk = 26       // Android 8.0，覆盖约 95% 设备
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // ===== Compose 配置 =====
    buildFeatures {
        compose = true   // 启用 Jetpack Compose
    }
    composeOptions {
        // Compose 编译器版本，必须与 Kotlin 版本匹配
        // Kotlin 1.9.24 → Compose Compiler 1.5.14
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    // ===== Java/Kotlin 编译选项 =====
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // ===== 排除冲突文件 =====
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ─── Compose BOM ──────────────────────────────────────
    // BOM = Bill of Materials，统一管理 Compose 全家桶的版本。
    // 加了 BOM 后，下面的 compose 库可以不用写版本号。
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))

    // Activity + Compose 粘合层
    implementation("androidx.activity:activity-compose:1.9.2")

    // Material3（Material You 设计风格）
    implementation("androidx.compose.material3:material3")

    // Material Icons 扩展库（提供更多图标）
    implementation("androidx.compose.material:material-icons-extended")

    // Compose 基础 UI
    implementation("androidx.compose.ui:ui")

    // Compose 预览工具（仅在 debug 时需要）
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ─── 导航 ────────────────────────────────────────────
    // Navigation Compose：在 Composable 页面之间跳转
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ─── Lifecycle（管理 UI 生命周期）─────────────────────
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")

    // ─── Room（本地 SQLite 数据库）─────────────────────────
    // Room 是 SQLite 的抽象层，用注解生成数据库代码
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")    // Kotlin 扩展（协程支持）
    kapt("androidx.room:room-compiler:2.6.1")          // 注解处理器

    // ─── Kotlin 协程 ──────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
