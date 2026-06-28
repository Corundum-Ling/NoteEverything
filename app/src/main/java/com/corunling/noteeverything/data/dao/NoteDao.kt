// ============================================================
// NoteDao.kt — "笔记/随笔"表的数据访问对象
// ============================================================
// 核心查询：
// - getBySoftware：查某个软件下的所有笔记
// - getFreeNotes：查所有自由随笔（softwareId IS NULL）
// - search：全文搜索笔记内容

package com.corunling.noteeverything.data.dao

import androidx.room.*
import com.corunling.noteeverything.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // 全部笔记，按时间倒序（最新的在前）
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAll(): Flow<List<NoteEntity>>

    // 某个软件下的所有笔记
    @Query("SELECT * FROM notes WHERE softwareId = :softwareId ORDER BY timestamp DESC")
    fun getBySoftware(softwareId: Long): Flow<List<NoteEntity>>

    // 所有自由随笔（不关联任何软件）
    @Query("SELECT * FROM notes WHERE softwareId IS NULL ORDER BY timestamp DESC")
    fun getFreeNotes(): Flow<List<NoteEntity>>

    // 按 ID 查单条
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    // 某个软件的最新一条笔记（软件列表卡片上显示预览用）
    @Query("SELECT * FROM notes WHERE softwareId = :softwareId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestBySoftware(softwareId: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    // 搜索笔记内容
    @Query("SELECT * FROM notes WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<NoteEntity>>

    // 清空所有笔记
    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    // 批量查询（用于批量操作标签）
    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<NoteEntity>

    // 置顶/取消置顶
    @Query("UPDATE notes SET pinned = :pinned WHERE id = :id")
    suspend fun updatePinned(id: Long, pinned: Boolean)

    // 锁定/解锁
    @Query("UPDATE notes SET locked = :locked WHERE id = :id")
    suspend fun updateLocked(id: Long, locked: Boolean)

    // 批量更新标签
    @Query("UPDATE notes SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: Long, tags: String?)
}
