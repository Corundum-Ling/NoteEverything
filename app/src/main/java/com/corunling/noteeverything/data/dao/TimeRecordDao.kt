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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(timeRecord: TimeRecordEntity): Long

    @Update
    suspend fun update(timeRecord: TimeRecordEntity)

    @Delete
    suspend fun delete(timeRecord: TimeRecordEntity)
}
