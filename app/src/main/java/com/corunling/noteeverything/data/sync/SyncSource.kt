// ============================================================
// SyncSource.kt — 外部时长数据接入接口（Probe）
// ============================================================
// 定义电脑端/其他设备同步过来的时长数据的标准格式。
// 当前为接口定义阶段，电脑端实施时再接入具体通信方式。
//
// SyncSource 是"数据源"抽象：
//   - 手机本地 AutoTracker → source="auto"
//   - 电脑端 Probe → source="pc_sync"
//   - 未来 Steam/其他平台 → 各自的 source
//
// 学习要点：
// - 通过接口抽象数据来源，Repository 不需要知道数据来自哪里。
// - 后续添加新数据源只需要实现 SyncSource 接口即可。

package com.corunling.noteeverything.data.sync

/**
 * 一条外部同步过来的时长记录。
 *
 * @param packageName 应用包名（跨平台标识）
 * @param softwareName 软件名称（备选匹配字段）
 * @param platform 平台标识："PC" / "Android" / "iOS" / "Switch" / "Other"
 * @param category 分类
 * @param totalMinutes 总时长（分钟）
 * @param date 日期 "YYYY-MM-DD"
 * @param sourceId 数据源唯一标识（用于去重）
 */
data class SyncRecord(
    val packageName: String? = null,
    val softwareName: String? = null,
    val platform: String,
    val category: String = "其他",
    val totalMinutes: Long,
    val date: String,
    val sourceId: String? = null
)

/**
 * 外部数据源接口。
 * 实现此接口以接入不同来源的时长数据。
 *
 * 当前暂无实现，等待电脑端联调时激活。
 */
interface SyncSource {
    /** 数据源名称，如 "pc_probe"、"steam" */
    val name: String

    /**
     * 拉取指定日期范围内的时长记录。
     * @param startDate 起始日期 "YYYY-MM-DD"
     * @param endDate 结束日期 "YYYY-MM-DD"
     * @return 记录列表
     */
    suspend fun fetchRecords(startDate: String, endDate: String): List<SyncRecord>
}
