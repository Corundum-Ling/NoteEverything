// ============================================================
// NoteEntity.kt — "笔记/随笔"实体
// ============================================================
// 关键设计：softwareId 可为空。
//   softwareId != null → 绑定到某个软件的感想笔记
//   softwareId == null → 自由随笔（不关联任何软件）
//
// type 字段：
//   "software" → 关联软件的笔记
//   "free"     → 自由随笔
//
// 学习要点：
// - Room 中 @Entity 的每个属性默认都是 NOT NULL。
// - 如果你想让某个列可为空，在 Kotlin 中用 ?（可空类型）即可。

package com.corunling.noteeverything.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val softwareId: Long? = null,  // null = 自由随笔，不关联任何软件
    val content: String,           // 正文内容
    val timestamp: Long,           // 笔记的时间点（用户可手动调整）
    val type: String,              // "software" 或 "free"
    val tags: String? = null,      // 标签，逗号分隔（如 "战斗,BOSS"）
    val imageUri: String? = null,  // 图片路径（可选）
    val location: String? = null,  // 地点描述（可选）
    val pinned: Boolean = false,   // 是否置顶
    val locked: Boolean = false,   // 是否锁定（锁定后不可删除）
    val createdAt: Long = System.currentTimeMillis()
)
