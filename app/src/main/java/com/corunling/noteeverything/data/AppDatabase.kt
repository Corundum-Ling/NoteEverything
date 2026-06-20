// ============================================================
// AppDatabase.kt — Room 数据库定义
// ============================================================
// 这是整个数据库的"入口"类。
//
// @Database 注解告诉 Room：
// - entities：这个数据库包含哪些表
// - version：数据库版本号（每次改表结构都要 +1）
// - exportSchema：是否导出 schema 文件（开发阶段关掉即可）
//
// 使用方式（在 App.kt 中）：
//   val db = AppDatabase.build(context)
//   val allSoftware = db.softwareDao().getAll()
//
// Room.databaseBuilder 创建的是单例（确保只有一个数据库连接）。

package com.corunling.noteeverything.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.corunling.noteeverything.data.dao.NoteDao
import com.corunling.noteeverything.data.dao.NoteTimeRecordLinkDao
import com.corunling.noteeverything.data.dao.SoftwareDao
import com.corunling.noteeverything.data.dao.TimeRecordDao
import com.corunling.noteeverything.data.entity.NoteEntity
import com.corunling.noteeverything.data.entity.NoteTimeRecordLink
import com.corunling.noteeverything.data.entity.SoftwareEntity
import com.corunling.noteeverything.data.entity.TimeRecordEntity

@Database(
    entities = [
        SoftwareEntity::class,
        NoteEntity::class,
        TimeRecordEntity::class,
        NoteTimeRecordLink::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun softwareDao(): SoftwareDao
    abstract fun noteDao(): NoteDao
    abstract fun timeRecordDao(): TimeRecordDao
    abstract fun noteTimeRecordLinkDao(): NoteTimeRecordLinkDao

    companion object {
        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "noteeverything.db"
            )
                // MVP 阶段：改表结构时直接重建数据库（数据会丢失）
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
