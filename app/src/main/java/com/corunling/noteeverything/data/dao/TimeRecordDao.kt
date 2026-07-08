// ============================================================
// TimeRecordDao.kt — "时长记录"表的数据访问对象
// ============================================================
// 关键查询：
// - getByDate：按天筛选（利用冗余的 date 字段）
// - getTodayDurationForSoftware：今天某个软件的总时长
// - getDailyStats：按天统计各软件时长（GROUP BY + SUM）
// - getStatsInRange：按日期范围统计
//
// SoftwareDuration 是一个"查询结果"类（非 Entity），
// 用于承载 GROUP BY 聚合查询的返回值。

package com.corunling.noteeverything.data.dao

import androidx.room.*
import com.corunling.noteeverything.data.entity.TimeRecordEntity
import kotlinx.coroutines.flow.Flow

// 聚合查询结果：某个软件的时长总和
data class SoftwareDuration(
    val softwareId: Long,
    val total: Long
)

// 按天的时长聚合（用于趋势图）
data class DailyDuration(
    val date: String,
    val total: Long
)

// 按小时的时长聚合（今日趋势图用）
data class HourlyDuration(
    val hour: Int,
    val total: Long
)

// 按分类的时长聚合（用于环形图）
data class CategoryDuration(
    val category: String,
    val total: Long
)

@Dao
interface TimeRecordDao {

    @Query("SELECT * FROM time_records ORDER BY startTime DESC")
    fun getAll(): Flow<List<TimeRecordEntity>>

    @Query("SELECT * FROM time_records WHERE softwareId = :softwareId ORDER BY startTime DESC")
    fun getBySoftware(softwareId: Long): Flow<List<TimeRecordEntity>>

    @Query("SELECT * FROM time_records WHERE date = :date")
    fun getByDate(date: String): Flow<List<TimeRecordEntity>>

    @Query("SELECT * FROM time_records WHERE softwareId = :softwareId AND date = :date ORDER BY startTime ASC")
    suspend fun getBySoftwareAndDate(softwareId: Long, date: String): List<TimeRecordEntity>

    // 今天某个软件的总时长（返回 SUM，可能为 null 即没记录）
    @Query("""
        SELECT SUM(durationMinutes) FROM time_records
        WHERE softwareId = :softwareId AND date = :date
    """)
    suspend fun getTodayDurationForSoftware(softwareId: Long, date: String): Long?

    // 当天各软件时长排行
    @Query("""
        SELECT softwareId, SUM(durationMinutes) as total
        FROM time_records
        WHERE date = :date
        GROUP BY softwareId
        ORDER BY total DESC
    """)
    suspend fun getDailyStats(date: String): List<SoftwareDuration>

    // 日期范围内的各软件时长排行（本周/本月用）
    @Query("""
        SELECT softwareId, SUM(durationMinutes) as total
        FROM time_records
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY softwareId
        ORDER BY total DESC
    """)
    suspend fun getStatsInRange(startDate: String, endDate: String): List<SoftwareDuration>

    // 日期范围内的每日时长趋势（用于折线图）
    @Query("""
        SELECT date, SUM(durationMinutes) as total
        FROM time_records
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getDailyStatsInRange(startDate: String, endDate: String): List<DailyDuration>

    // 某天各小时的时长分布（今日趋势图用）
    @Query("""
        SELECT CAST(strftime('%H', startTime / 1000, 'unixepoch') AS INTEGER) as hour,
               SUM(durationMinutes) as total
        FROM time_records
        WHERE date = :date
        GROUP BY hour
        ORDER BY hour ASC
    """)
    suspend fun getHourlyStats(date: String): List<HourlyDuration>

    // 按软件筛选后的每日时长趋势
    @Query("""
        SELECT date, SUM(durationMinutes) as total
        FROM time_records
        WHERE date BETWEEN :startDate AND :endDate
        AND softwareId IN (:softwareIds)
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getDailyStatsInRangeFiltered(
        startDate: String, endDate: String,
        softwareIds: List<Long>
    ): List<DailyDuration>

    // 按软件筛选后的各小时时长分布
    @Query("""
        SELECT CAST(strftime('%H', startTime / 1000, 'unixepoch') AS INTEGER) as hour,
               SUM(durationMinutes) as total
        FROM time_records
        WHERE date = :date AND softwareId IN (:softwareIds)
        GROUP BY hour
        ORDER BY hour ASC
    """)
    suspend fun getHourlyStatsFiltered(date: String, softwareIds: List<Long>): List<HourlyDuration>

    // 日期范围内的分类时长汇总（用于环形图）
    @Query("""
        SELECT s.category, SUM(t.durationMinutes) as total
        FROM time_records t
        INNER JOIN software s ON t.softwareId = s.id
        WHERE t.date BETWEEN :startDate AND :endDate
        GROUP BY s.category
        ORDER BY total DESC
    """)
    suspend fun getCategoryStatsInRange(startDate: String, endDate: String): List<CategoryDuration>

    // 按软件+日期+来源查询自动记录（用于自动同步的去重/更新判断）
    @Query("SELECT * FROM time_records WHERE softwareId = :softwareId AND date = :date AND source = 'auto' LIMIT 1")
    suspend fun getAutoRecord(softwareId: Long, date: String): TimeRecordEntity?

    // 清空所有时长记录
    @Query("DELETE FROM time_records")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(timeRecord: TimeRecordEntity): Long

    @Update
    suspend fun update(timeRecord: TimeRecordEntity)

    @Delete
    suspend fun delete(timeRecord: TimeRecordEntity)
}
