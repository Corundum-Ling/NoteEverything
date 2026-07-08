// ============================================================
// SoftwareDao.kt — "软件"表的数据访问对象
// ============================================================
// DAO = Data Access Object，定义对数据库的增删改查操作。
// Room 会根据这些接口自动生成实现代码（编译时）。
//
// 返回值类型的选择：
// - Flow<T>：返回一个"数据流"，数据库内容变化时自动推送新数据。
//   Compose 的 collectAsState() 可以直接订阅，UI 自动刷新。
// - suspend fun：一次性查询，需要在协程中调用。
//
// 学习要点：Flow 是 Room + Compose 的核心配合模式——
// Room 发出 Flow → ViewModel 转换 → UI collectAsState → 界面自动更新

package com.corunling.noteeverything.data.dao

import androidx.room.*
import com.corunling.noteeverything.data.entity.SoftwareEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SoftwareDao {

    // 查询全部，按分类和名称排序
    @Query("SELECT * FROM software ORDER BY category, name")
    fun getAll(): Flow<List<SoftwareEntity>>

    // 按 ID 查单个（详细页用）
    @Query("SELECT * FROM software WHERE id = :id")
    suspend fun getById(id: Long): SoftwareEntity?

    // 插入新软件，返回自动生成的 ID
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(software: SoftwareEntity): Long

    // 更新已有软件
    @Update
    suspend fun update(software: SoftwareEntity)

    // 删除软件
    @Delete
    suspend fun delete(software: SoftwareEntity)

    // 按名称搜索（LIKE 模糊匹配）
    @Query("SELECT * FROM software WHERE name LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<SoftwareEntity>>

    // 按包名查询（用于 UsageStats 自动匹配）
    @Query("SELECT * FROM software WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): SoftwareEntity?

    // 获取所有已使用的包名（用于添加软件时去重）
    @Query("SELECT packageName FROM software WHERE packageName IS NOT NULL")
    suspend fun getAllPackageNames(): List<String>

    // 一次性查询全部（非 Flow），用于导出时获取软件名映射
    @Query("SELECT * FROM software ORDER BY category, name")
    suspend fun getAllSync(): List<SoftwareEntity>

    // 清空所有软件条目
    @Query("DELETE FROM software")
    suspend fun deleteAll()

    // 置顶/取消置顶
    @Query("UPDATE software SET pinned = :pinned WHERE id = :id")
    suspend fun updatePinned(id: Long, pinned: Boolean)

    // 锁定/解锁
    @Query("UPDATE software SET locked = :locked WHERE id = :id")
    suspend fun updateLocked(id: Long, locked: Boolean)

    // 查询全部，置顶优先
    @Query("SELECT * FROM software ORDER BY pinned DESC, category, name")
    fun getAllPinnedFirst(): Flow<List<SoftwareEntity>>
}
