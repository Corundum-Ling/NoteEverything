// ============================================================
// App.kt — 自定义 Application 类
// ============================================================
// Application 是整个 App 的入口（比任何 Activity 都早创建）。
// 我们在这里初始化 Room 数据库和 Repository，
// 这样所有页面都能通过 (application as App) 拿到同一个实例。
//
// 注意：Application 是全局单例，Android 系统保证只创建一次。
// 所以 database 和 repository 也只会初始化一次。

package com.corunling.noteeverything

import android.app.Application
import com.corunling.noteeverything.data.AppDatabase
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.util.SettingsManager

class App : Application() {

    // lazy：第一次访问时才初始化，不是 Application 一创建就初始化
    // 好处：如果 App 启动后没用到数据库（比如被系统杀死后在恢复），不会浪费资源
    val database: AppDatabase by lazy {
        AppDatabase.build(this)
    }

    val repository: NoteEverythingRepository by lazy {
        NoteEverythingRepository(
            database.softwareDao(),
            database.noteDao(),
            database.timeRecordDao(),
            database.noteTimeRecordLinkDao()
        )
    }

    /** 设置管理器（DataStore 持久化） */
    val settingsManager: SettingsManager by lazy {
        SettingsManager(this)
    }
}
