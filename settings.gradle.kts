// ============================================================
// settings.gradle.kts — 项目设置
// ============================================================
// 1. pluginManagement：从哪里下载 Gradle 插件
// 2. dependencyResolutionManagement：从哪里下载项目依赖
// 3. rootProject.name：项目显示名称
// 4. include：声明此项目包含哪些子模块

pluginManagement {
    repositories {
        google()        // Android 官方仓库
        mavenCentral()  // 通用 Maven 中央仓库
        gradlePluginPortal() // Gradle 插件门户
    }
}

dependencyResolutionManagement {
    // FAIL_ON_PROJECT_REPOS 是个安全设置：
    // 禁止子模块在自己的 build.gradle 里单独声明 repositories，
    // 强制所有依赖统一从这里解析，避免"幽灵依赖"
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NoteEverything"
include(":app")  // 只有一个 app 模块（MVP 阶段不需要拆 :core）
