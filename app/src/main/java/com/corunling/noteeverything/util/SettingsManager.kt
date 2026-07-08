// ============================================================
// SettingsManager.kt — 设置持久化管理器
// ============================================================
// 基于 Jetpack DataStore Preferences，提供 KV 存储。
// 当前仅存储 darkMode 开关，后续可扩展。
//
// 使用方式（在 Activity / Composable 中）：
//   val app = context.applicationContext as App
//   val settings by app.settingsManager.settingsFlow.collectAsState()
//   // settings.darkMode → Boolean
//
// 写入设置：
//   scope.launch { app.settingsManager.setDarkMode(true) }
//
// DataStore 是 SharedPreferences 的现代化替代，优势：
// - 基于 Kotlin 协程和 Flow，响应式
// - 在主线程安全调用（自动切 IO 线程）
// - 类型安全（Preferences 键值对）
// - 无 ANR 风险（SP 的 commit/apply 问题）
// - 支持数据迁移

package com.corunling.noteeverything.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore 实例是 Context 的扩展属性，保证全局单例
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "noteeverything_settings")

/** 从 DataStore 反序列化得到的设置快照 */
data class AppSettings(
    val darkMode: Boolean = false,
    val showLineChart: Boolean = true,
    val showDonutChart: Boolean = true,
    val showRanking: Boolean = true
)

class SettingsManager(private val context: Context) {

    companion object {
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val SHOW_LINE_CHART_KEY = booleanPreferencesKey("show_line_chart")
        private val SHOW_DONUT_CHART_KEY = booleanPreferencesKey("show_donut_chart")
        private val SHOW_RANKING_KEY = booleanPreferencesKey("show_ranking")
    }

    /** 设置流：收集此 Flow 以响应式监听设置变化 */
    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            darkMode = prefs[DARK_MODE_KEY] ?: false,
            showLineChart = prefs[SHOW_LINE_CHART_KEY] ?: true,
            showDonutChart = prefs[SHOW_DONUT_CHART_KEY] ?: true,
            showRanking = prefs[SHOW_RANKING_KEY] ?: true
        )
    }

    /** 切换深色模式并持久化 */
    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_MODE_KEY] = enabled
        }
    }

    /** 切换折线图显示 */
    suspend fun setShowLineChart(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SHOW_LINE_CHART_KEY] = enabled
        }
    }

    /** 切换环形图显示 */
    suspend fun setShowDonutChart(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SHOW_DONUT_CHART_KEY] = enabled
        }
    }

    /** 切换排行列表显示 */
    suspend fun setShowRanking(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SHOW_RANKING_KEY] = enabled
        }
    }
}
