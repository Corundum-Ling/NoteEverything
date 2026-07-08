// ============================================================
// AutoTracker.kt — 自动时长获取（UsageStatsManager 封装）
// ============================================================
// 使用 Android 系统 UsageStatsManager 查询各 App 的使用时长。
// 匹配已记录的软件（通过 packageName），自动创建 TimeRecord。
//
// 权限要求：
//   android.permission.PACKAGE_USAGE_STATS（特殊权限）
//   需要用户前往"使用情况访问权限"页面手动开启。
//
// 数据流：
//   用户为软件设置包名 → AutoTracker 每天查询 UsageStats
//   → 匹配到软件 → 创建/更新 source="auto" 的时长记录
//
// 学习要点：
// - PACKAGE_USAGE_STATS 是"特殊权限"，不能用普通 requestPermissions。
// - Settings.ACTION_USAGE_ACCESS_SETTINGS 打开系统设置页面让用户手动开。
// - UsageStatsManager.queryUsageStats(INTERVAL_DAILY, start, end)
//   返回指定时间段内所有 App 的使用统计。

package com.corunling.noteeverything.data.auto

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.entity.TimeRecordEntity
import com.corunling.noteeverything.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 一条自动获取的使用记录。
 * 对应 UsageStatsManager 中一个 App 在一天内的统计。
 */
data class AutoUsageRecord(
    val packageName: String,              // Android 包名，如 "com.example.game"
    val totalTimeInForeground: Long,      // 前台总时长（毫秒）
    val firstTimeStamp: Long,             // 首次使用时间（毫秒）
    val lastTimeStamp: Long               // 最后使用时间（毫秒）
)

/**
 * 自动时长追踪器。
 * 通过 UsageStatsManager 获取系统 App 使用统计，匹配并创建时长记录。
 *
 * 使用方式：
 *   val tracker = AutoTracker(context)
 *   if (tracker.checkPermission()) {
 *       val count = tracker.syncYesterday(repository)
 *   }
 */
class AutoTracker(private val context: Context) {

    /**
     * 检查是否有"使用情况访问权限"。
     * 如果返回 false，需要引导用户去系统设置中开启。
     */
    fun checkPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 获取跳转到"使用情况访问权限"系统设置页面的 Intent。
     * 如果用户未授予权限，使用此 Intent 打开设置页面引导手动开启。
     */
    fun getPermissionIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 获取指定日期的 App 使用统计。
     * @param dateStr "YYYY-MM-DD" 格式的日期
     * @return 该日所有 App 的使用记录列表（仅包含有前台时长的）
     */
    suspend fun fetchUsageForDate(dateStr: String): List<AutoUsageRecord> = withContext(Dispatchers.IO) {
        if (!checkPermission()) return@withContext emptyList()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // 将 "YYYY-MM-DD" 转换为毫秒时间戳
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val date = sdf.parse(dateStr) ?: return@withContext emptyList()
        val startOfDay = date.time
        val endOfDay = startOfDay + 24 * 60 * 60 * 1000L

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            endOfDay
        )

        if (usageStatsList.isNullOrEmpty()) return@withContext emptyList()

        // 按包名去重：某些 Android 版本可能返回同包名的多条记录
        usageStatsList
            .filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .mapValues { (_, statsList) ->
                // 同包名多条时取总时长最大的一条
                statsList.maxByOrNull { it.totalTimeInForeground } ?: statsList.first()
            }
            .values
            .toList()  // 只保留去重后的 UsageStats
            .map { stats ->
                // 用 lastTimeUsed 作为最后使用时间
                // 开始时间 = 最后使用时间 - 前台总时长（估算）
                val lastUsed = stats.lastTimeUsed
                val firstUsed = lastUsed - stats.totalTimeInForeground
                AutoUsageRecord(
                    packageName = stats.packageName,
                    totalTimeInForeground = stats.totalTimeInForeground,
                    firstTimeStamp = firstUsed,
                    lastTimeStamp = lastUsed
                )
            }
            .filter { it.totalTimeInForeground >= 60_000 }  // 过滤掉不足 1 分钟的短暂使用
    }

    /**
     * 同步昨日使用数据到数据库：
     * 1. 查询各 App 的使用时长
     * 2. 匹配已记录的软件（通过 packageName）
     * 3. 创建或更新 source="auto" 的时长记录
     *
     * @param repository 数据仓库
     * @return 本次同步创建/更新的记录数
     */
    suspend fun syncYesterday(repository: NoteEverythingRepository): Int {
        val yesterday = DateTimeUtils.yesterday()
        return syncDate(repository, yesterday)
    }

    /**
     * 同步最近 N 天到数据库。
     * 逐天查询，但每查一天时窗口前后各扩 24h（共 48h），确保 UTC 桶边界不落空。
     * 用 lastTimeUsed 判定该记录实际属于哪一天。
     *
     * @param repository 数据仓库
     * @param endDate 结束日期 "YYYY-MM-DD"
     * @param daysBack 往前多少天（含 endDate 当日），默认 7
     * @return 本次同步创建/更新的总记录数
     */
    suspend fun syncRange(repository: NoteEverythingRepository, endDate: String, daysBack: Int = 7): Int {
        if (!checkPermission()) return 0
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // 查询全部可用数据（0 ~ MAX），让系统吐出所有历史，自己过滤日期范围
        val allStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, 0L, Long.MAX_VALUE
        ) ?: return 0

        // 计算允许的最早日期
        val endCal = java.util.Calendar.getInstance().apply {
            time = sdf.parse(endDate) ?: return 0
        }
        val startCal = java.util.Calendar.getInstance().apply {
            time = endCal.time
            add(java.util.Calendar.DAY_OF_MONTH, -(daysBack - 1))
        }
        val startMs = startCal.timeInMillis
        val endMs = endCal.timeInMillis + 24 * 60 * 60 * 1000L

        // 按日期+包名聚合（同一包名同一天可能有多条桶数据）
        val dateMap = mutableMapOf<String, MutableMap<String, Long>>()
        val lastUsedMap = mutableMapOf<String, MutableMap<String, Long>>()
        for (stats in allStats) {
            if (stats.totalTimeInForeground <= 0) continue
            // 只保留范围内的数据
            if (stats.lastTimeUsed < startMs || stats.lastTimeUsed > endMs) continue
            val date = sdf.format(java.util.Date(stats.lastTimeUsed))
            val pkg = stats.packageName
            dateMap.getOrPut(date) { mutableMapOf() }.merge(pkg, stats.totalTimeInForeground, Long::plus)
            lastUsedMap.getOrPut(date) { mutableMapOf() }.merge(pkg, stats.lastTimeUsed, ::maxOf)
        }

        var total = 0
        for ((dateStr, pkgs) in dateMap) {
            for ((pkg, totalMs) in pkgs) {
                val durationMinutes = totalMs / 60_000
                if (durationMinutes <= 0) continue
                val lastUsed = lastUsedMap[dateStr]?.get(pkg) ?: continue
                val firstUsed = lastUsed - totalMs

                val software = repository.getSoftwareByPackageName(pkg) ?: continue
                val existing = repository.getAutoRecordForSoftwareAndDate(software.id, dateStr)
                if (existing != null) {
                    if (existing.durationMinutes != durationMinutes || existing.endTime != lastUsed) {
                        repository.updateTimeRecord(existing.copy(durationMinutes = durationMinutes, endTime = lastUsed, startTime = firstUsed))
                        total++
                    }
                } else {
                    repository.createTimeRecord(softwareId = software.id, startTime = firstUsed, endTime = lastUsed, durationMinutes = durationMinutes, date = dateStr, source = "auto")
                    total++
                }
            }
        }
        return total
    }

    /**
     * 智能同步：从上次同步日到昨天。
     * 如果从未同步过（lastSyncDate 为空），默认同步近 7 天。
     *
     * @param repository 数据仓库
     * @param lastSyncDate 上次同步日期 "YYYY-MM-DD"，空表示从未同步
     * @return 本次同步创建/更新的总记录数
     */
    suspend fun syncRecent(repository: NoteEverythingRepository, lastSyncDate: String? = null): Int {
        val today = DateTimeUtils.today()
        if (lastSyncDate.isNullOrBlank()) {
            // 首次同步：从 6 天前到今（共 7 天）
            return syncRange(repository, today, 7)
        }
        // 计算 lastSyncDate 到 today 有多少天，最少拉 7 天确保覆盖
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val last = sdf.parse(lastSyncDate) ?: return syncRange(repository, today, 7)
        val todayDate = sdf.parse(today) ?: return 0
        val diffMs = todayDate.time - last.time
        val days = (diffMs / (24 * 60 * 60 * 1000L)).toInt().coerceIn(1, 365)
        return syncRange(repository, today, days + 1)
    }

    /**
     * 同步指定日期的数据。
     */
    suspend fun syncDate(repository: NoteEverythingRepository, dateStr: String): Int {
        if (!checkPermission()) return 0

        val records = fetchUsageForDate(dateStr)
        var count = 0

        for (record in records) {
            // 按包名查找已记录的软件
            val software = repository.getSoftwareByPackageName(record.packageName)
            if (software == null) {
                // 未匹配到软件：跳过（用户还没添加这个软件或没设包名）
                continue
            }

            // 检查是否已有该软件当天的自动记录
            val existing = repository.getAutoRecordForSoftwareAndDate(software.id, dateStr)
            val durationMinutes = record.totalTimeInForeground / 60_000

            if (durationMinutes <= 0) continue

            if (existing != null) {
                // 已有记录：更新时长（UsageStats 是累积值，取最新）
                if (existing.durationMinutes != durationMinutes || existing.endTime != record.lastTimeStamp) {
                    repository.updateTimeRecord(
                        existing.copy(
                            durationMinutes = durationMinutes,
                            endTime = record.lastTimeStamp,
                            startTime = record.firstTimeStamp
                        )
                    )
                    count++
                }
            } else {
                // 无记录：新建
                repository.createTimeRecord(
                    softwareId = software.id,
                    startTime = record.firstTimeStamp,
                    endTime = record.lastTimeStamp,
                    durationMinutes = durationMinutes,
                    date = dateStr,
                    source = "auto"
                )
                count++
            }
        }

        return count
    }
}
