// ============================================================
// NoteEverythingRepository.kt — 数据仓库
// ============================================================
// Repository 模式的核心思想：
//   ViewModel 不知道数据来自哪里（Room？网络？文件？），
//   它只知道"我调用 Repository 的方法就能拿到数据"。
//
// 这样做的好处：
//   以后如果要加云端同步，只需要改 Repository 的实现，
//   ViewModel 完全不用动 —— 这就是"依赖反转"。
//
// Repository 把三个 DAO 的零散操作组合成"业务操作"。
// 比如 createNote() 内部自动判断 type 是 "software" 还是 "free"，
// 调用方不需要关心这个逻辑。

package com.corunling.noteeverything.data

import com.corunling.noteeverything.data.dao.NoteDao
import com.corunling.noteeverything.data.dao.NoteTimeRecordLinkDao
import com.corunling.noteeverything.data.dao.SoftwareDao
import com.corunling.noteeverything.data.dao.SoftwareDuration
import com.corunling.noteeverything.data.dao.TimeRecordDao
import com.corunling.noteeverything.data.entity.NoteEntity
import com.corunling.noteeverything.data.entity.NoteTimeRecordLink
import com.corunling.noteeverything.data.entity.SoftwareEntity
import com.corunling.noteeverything.data.entity.TimeRecordEntity
import kotlinx.coroutines.flow.Flow

class NoteEverythingRepository(
    private val softwareDao: SoftwareDao,
    private val noteDao: NoteDao,
    private val timeRecordDao: TimeRecordDao,
    private val linkDao: NoteTimeRecordLinkDao
) {
    // ════════════════════════════════════════════════
    // 软件相关
    // ════════════════════════════════════════════════

    fun getAllSoftware(): Flow<List<SoftwareEntity>> = softwareDao.getAll()

    suspend fun getSoftware(id: Long): SoftwareEntity? = softwareDao.getById(id)

    suspend fun createSoftware(name: String, platform: String, category: String): Long {
        return softwareDao.insert(
            SoftwareEntity(name = name, platform = platform, category = category)
        )
    }

    suspend fun updateSoftware(software: SoftwareEntity) = softwareDao.update(software)

    suspend fun deleteSoftware(software: SoftwareEntity) = softwareDao.delete(software)

    // ════════════════════════════════════════════════
    // 笔记相关
    // ════════════════════════════════════════════════

    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAll()

    suspend fun getNoteById(id: Long): NoteEntity? = noteDao.getById(id)

    fun getNotesBySoftware(softwareId: Long): Flow<List<NoteEntity>> =
        noteDao.getBySoftware(softwareId)

    fun getFreeNotes(): Flow<List<NoteEntity>> = noteDao.getFreeNotes()

    suspend fun getLatestNoteForSoftware(softwareId: Long): NoteEntity? =
        noteDao.getLatestBySoftware(softwareId)

    /**
     * 创建笔记。如果 softwareId 不为 null 则为软件笔记，否则为自由随笔。
     * type 字段由 Repository 自动设置，调用方不需要手动指定。
     */
    suspend fun createNote(
        softwareId: Long?,
        content: String,
        timestamp: Long,
        tags: String? = null,
        imageUri: String? = null,
        location: String? = null
    ): Long {
        val type = if (softwareId != null) "software" else "free"
        return noteDao.insert(
            NoteEntity(
                softwareId = softwareId,
                content = content,
                timestamp = timestamp,
                type = type,
                tags = tags,
                imageUri = imageUri,
                location = location
            )
        )
    }

    suspend fun updateNote(note: NoteEntity) = noteDao.update(note)

    suspend fun deleteNote(note: NoteEntity) = noteDao.delete(note)

    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.search(query)

    // ════════════════════════════════════════════════
    // 时长相关
    // ════════════════════════════════════════════════

    fun getTimeRecordsBySoftware(softwareId: Long): Flow<List<TimeRecordEntity>> =
        timeRecordDao.getBySoftware(softwareId)

    suspend fun getTodayDuration(softwareId: Long, date: String): Long {
        return timeRecordDao.getTodayDurationForSoftware(softwareId, date) ?: 0
    }

    suspend fun createTimeRecord(
        softwareId: Long,
        startTime: Long,
        endTime: Long,
        durationMinutes: Long,
        date: String,
        source: String = "manual"
    ): Long {
        return timeRecordDao.insert(
            TimeRecordEntity(
                softwareId = softwareId,
                startTime = startTime,
                endTime = endTime,
                durationMinutes = durationMinutes,
                date = date,
                source = source
            )
        )
    }

    suspend fun deleteTimeRecord(timeRecord: TimeRecordEntity) =
        timeRecordDao.delete(timeRecord)

    suspend fun getDailyStats(date: String): List<SoftwareDuration> =
        timeRecordDao.getDailyStats(date)

    suspend fun getStatsInRange(startDate: String, endDate: String): List<SoftwareDuration> =
        timeRecordDao.getStatsInRange(startDate, endDate)

    // ════════════════════════════════════════════════
    // 笔记 ↔ 时长关联
    // ════════════════════════════════════════════════

    /** 获取某条笔记关联的所有时长记录 */
    fun getLinkedTimeRecords(noteId: Long): Flow<List<TimeRecordEntity>> =
        linkDao.getTimeRecordsForNote(noteId)

    /** 获取某条笔记的所有关联关系（用于 UI 勾选状态） */
    fun getLinksForNote(noteId: Long): Flow<List<NoteTimeRecordLink>> =
        linkDao.getLinksForNote(noteId)

    /** 获取某个软件今天的全部时长记录（供笔记编辑时默认全选） */
    suspend fun getTodayTimeRecordsForSoftware(softwareId: Long, date: String): List<TimeRecordEntity> {
        return timeRecordDao.getBySoftwareAndDate(softwareId, date)
    }

    /** 添加笔记-时长关联 */
    suspend fun linkNoteToTimeRecord(noteId: Long, timeRecordId: Long) {
        linkDao.insert(NoteTimeRecordLink(noteId = noteId, timeRecordId = timeRecordId))
    }

    /** 移除笔记-时长关联 */
    suspend fun unlinkNoteFromTimeRecord(noteId: Long, timeRecordId: Long) {
        linkDao.deleteByNoteAndTimeRecord(noteId, timeRecordId)
    }

    /** 批量设置笔记的关联时长（先删后加） */
    suspend fun setNoteLinks(noteId: Long, timeRecordIds: List<Long>) {
        linkDao.deleteAllForNote(noteId)
        timeRecordIds.forEach { timeRecordId ->
            linkDao.insert(NoteTimeRecordLink(noteId = noteId, timeRecordId = timeRecordId))
        }
    }
}
