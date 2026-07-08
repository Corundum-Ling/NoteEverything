// ============================================================
// SoftwareEntity.kt — "软件"实体
// ============================================================
// Room Entity：映射到数据库表的 Kotlin 数据类。
// @Entity → Room 会自动创建名为 "software" 的表。
// 每个属性 → 表中的一列。
//
// @PrimaryKey(autoGenerate = true)：
// - 标记 id 为主键（唯一标识一条记录）
// - autoGenerate → Room 自动生成自增 ID

package com.corunling.noteeverything.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "software")
data class SoftwareEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,        // 软件名称，如 "Elden Ring"
    val platform: String,    // 平台：PC / Android / iOS / Switch / Other
    val iconUri: String? = null,  // 图标路径（可选，MVP 阶段暂不用）
    val category: String,    // 分类：游戏 / 工具 / 学习 / 其他
    val packageName: String? = null,  // Android 包名（用于 UsageStats 自动匹配）
    val trackMode: String = "manual", // 追踪模式："manual" | "auto"（auto = 自动同步时长）
    val pinned: Boolean = false,  // 是否置顶
    val locked: Boolean = false,   // 是否锁定（锁定后不可删除）
    val createdAt: Long = System.currentTimeMillis()  // 创建时间戳
)
