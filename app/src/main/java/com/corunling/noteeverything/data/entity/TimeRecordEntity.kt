// ============================================================
// TimeRecordEntity.kt — "时长记录"实体
// ============================================================
// 时长记录必须关联一个 Software（通过 softwareId 外键）。
// 记录每次的起止时间和持续时长。
//
// date 字段用字符串 "YYYY-MM-DD" 格式存储。
// 为什么要冗余存储 date？因为 Room 的 SQL 查询中，直接用
// WHERE date = '2026-06-04' 比 WHERE startTime BETWEEN ... 更简单高效。
//
// source 字段区分数据来源：
//   "manual" → 用户手动输入时/分
//   "timer"  → 内置计时器自动计算

package com.corunling.noteeverything.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_records")
data class TimeRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val softwareId: Long,           // 必填：关联哪个软件
    val startTime: Long,            // 开始时间戳（毫秒）
    val endTime: Long,              // 结束时间戳（毫秒）
    val durationMinutes: Long,      // 持续时长（分钟），方便直接查询和统计
    val date: String,               // "YYYY-MM-DD"，冗余字段，加速按天查询
    val source: String = "manual"   // "manual" | "timer"
)
