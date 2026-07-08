// ============================================================
// SyncManager.kt — 同步数据合并管理器
// ============================================================
// 负责将外部同步来的数据合并到本地数据库。
// 核心逻辑：找到匹配的软件 → 去重 → 创建/更新记录。
//
// 匹配策略（按优先级）：
//   1. packageName 匹配（最强关联）
//   2. softwareName 模糊匹配（备选，有待打磨）
//
// 去重策略：
//   同一个 software + date + source 最多一条记录。
//   如果已存在则更新（取总时长更大的一方）。
//
// 当前为接口定义 + 基础实现阶段。
// 电脑端联调时需扩展通信方式和数据源注册。

package com.corunling.noteeverything.data.sync

import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.entity.TimeRecordEntity
import com.corunling.noteeverything.util.DateTimeUtils

/**
 * 同步统计结果。
 */
data class SyncResult(
    val totalRecords: Int,       // 本次同步的记录总数
    val matchedRecords: Int,     // 成功匹配到软件并导入的记录数
    val skippedRecords: Int      // 未匹配到软件而跳过的记录数
)

/**
 * 外部数据同步管理器。
 *
 * 使用方式：
 *   val result = SyncManager.importRecords(repository, records)
 */
object SyncManager {

    /**
     * 将外部同步记录导入本地数据库。
     *
     * @param repository 数据仓库
     * @param records 外部同步的记录列表
     * @param source 数据来源标识，如 "pc_sync"
     * @return 同步统计结果
     */
    suspend fun importRecords(
        repository: NoteEverythingRepository,
        records: List<SyncRecord>,
        source: String = "pc_sync"
    ): SyncResult {
        if (records.isEmpty()) return SyncResult(0, 0, 0)

        var matched = 0
        var skipped = 0

        for (record in records) {
            // 尝试按包名匹配本地软件
            var software = if (record.packageName != null) {
                repository.getSoftwareByPackageName(record.packageName)
            } else null

            // 如果包名没匹配到，尝试按软件名匹配
            // TODO: 后续实现模糊匹配，先跳过
            if (software == null) {
                skipped++
                continue
            }

            // 检查是否已存在相同来源的记录（去重）
            val existing = repository.getAutoRecordForSoftwareAndDate(software.id, record.date)
                ?: run {
                    // 兼容旧字段：检查同 source 的记录
                    null
                }

            if (existing != null && existing.source == source) {
                // 已存在：更新时长（取较大值）
                val newDuration = maxOf(existing.durationMinutes, record.totalMinutes)
                if (newDuration != existing.durationMinutes) {
                    repository.updateTimeRecord(
                        existing.copy(durationMinutes = newDuration)
                    )
                }
            } else {
                // 新建记录
                val now = DateTimeUtils.now()
                repository.createTimeRecord(
                    softwareId = software.id,
                    startTime = now - record.totalMinutes * 60_000,
                    endTime = now,
                    durationMinutes = record.totalMinutes,
                    date = record.date,
                    source = source
                )
            }

            matched++
        }

        return SyncResult(
            totalRecords = records.size,
            matchedRecords = matched,
            skippedRecords = skipped
        )
    }
}
