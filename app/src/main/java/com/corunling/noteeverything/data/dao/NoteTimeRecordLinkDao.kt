package com.corunling.noteeverything.data.dao

import androidx.room.*
import com.corunling.noteeverything.data.entity.NoteTimeRecordLink
import com.corunling.noteeverything.data.entity.TimeRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteTimeRecordLinkDao {

    // 查某条笔记关联的所有时长记录（三表 JOIN）
    @Query("""
        SELECT tr.* FROM time_records tr
        INNER JOIN note_time_record_link link ON tr.id = link.timeRecordId
        WHERE link.noteId = :noteId
        ORDER BY tr.startTime DESC
    """)
    fun getTimeRecordsForNote(noteId: Long): Flow<List<TimeRecordEntity>>

    // 查某条时长记录关联的所有笔记 ID
    @Query("SELECT link.noteId FROM note_time_record_link link WHERE link.timeRecordId = :timeRecordId")
    suspend fun getNoteIdsForTimeRecord(timeRecordId: Long): List<Long>

    // 查某条笔记关联的所有 link（用于判断勾选状态）
    @Query("SELECT * FROM note_time_record_link WHERE noteId = :noteId")
    fun getLinksForNote(noteId: Long): Flow<List<NoteTimeRecordLink>>

    // 添加关联
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: NoteTimeRecordLink): Long

    // 删除关联
    @Delete
    suspend fun delete(link: NoteTimeRecordLink)

    // 按 noteId + timeRecordId 删除一条关联
    @Query("DELETE FROM note_time_record_link WHERE noteId = :noteId AND timeRecordId = :timeRecordId")
    suspend fun deleteByNoteAndTimeRecord(noteId: Long, timeRecordId: Long)

    // 删除某条笔记的全部关联
    @Query("DELETE FROM note_time_record_link WHERE noteId = :noteId")
    suspend fun deleteAllForNote(noteId: Long)
}
