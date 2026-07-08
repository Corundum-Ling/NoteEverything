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
import com.corunling.noteeverything.data.dao.CategoryDuration
import com.corunling.noteeverything.data.dao.DailyDuration
import com.corunling.noteeverything.data.dao.HourlyDuration
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

    /** 查询全部软件，置顶优先 */
    fun getAllSoftwarePinnedFirst(): Flow<List<SoftwareEntity>> = softwareDao.getAllPinnedFirst()

    suspend fun getSoftware(id: Long): SoftwareEntity? = softwareDao.getById(id)

    suspend fun createSoftware(name: String, platform: String, category: String): Long {
        return softwareDao.insert(
            SoftwareEntity(name = name, platform = platform, category = category)
        )
    }

    suspend fun updateSoftware(software: SoftwareEntity) = softwareDao.update(software)

    suspend fun deleteSoftware(software: SoftwareEntity) = softwareDao.delete(software)

    /**
     * 置顶/取消置顶软件
     * @param id 软件 ID
     * @param pinned true=置顶，false=取消置顶
     */
    suspend fun setSoftwarePinned(id: Long, pinned: Boolean) = softwareDao.updatePinned(id, pinned)

    /**
     * 锁定/解锁软件
     * @param id 软件 ID
     * @param locked true=锁定，false=解锁
     */
    suspend fun setSoftwareLocked(id: Long, locked: Boolean) = softwareDao.updateLocked(id, locked)

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

    /**
     * 置顶/取消置顶笔记
     * @param id 笔记 ID
     * @param pinned true=置顶，false=取消置顶
     */
    suspend fun setNotePinned(id: Long, pinned: Boolean) = noteDao.updatePinned(id, pinned)

    /**
     * 锁定/解锁笔记
     * @param id 笔记 ID
     * @param locked true=锁定，false=解锁
     */
    suspend fun setNoteLocked(id: Long, locked: Boolean) = noteDao.updateLocked(id, locked)

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

    /** 日期范围内的每日时长趋势 */
    suspend fun getDailyStatsInRange(startDate: String, endDate: String): List<DailyDuration> =
        timeRecordDao.getDailyStatsInRange(startDate, endDate)

    /** 日期范围内的分类时长汇总 */
    suspend fun getCategoryStatsInRange(startDate: String, endDate: String): List<CategoryDuration> =
        timeRecordDao.getCategoryStatsInRange(startDate, endDate)

    /** 某天各小时的时长分布 */
    suspend fun getHourlyStats(date: String): List<HourlyDuration> =
        timeRecordDao.getHourlyStats(date)

    suspend fun getDailyStatsInRangeFiltered(
        startDate: String, endDate: String,
        softwareIds: List<Long>
    ): List<DailyDuration> = timeRecordDao.getDailyStatsInRangeFiltered(startDate, endDate, softwareIds)

    suspend fun getHourlyStatsFiltered(date: String, softwareIds: List<Long>): List<HourlyDuration> =
        timeRecordDao.getHourlyStatsFiltered(date, softwareIds)

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

    // ════════════════════════════════════════════════
    // 标签操作
    // ════════════════════════════════════════════════

    /** 解析逗号分隔的标签字符串为列表 */
    fun parseTags(tagsStr: String?): List<String> {
        if (tagsStr.isNullOrBlank()) return emptyList()
        return tagsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    /** 将标签列表合并为逗号分隔字符串（与已有标签去重合并） */
    fun mergeTags(existingTags: String?, newTags: List<String>): String {
        val existing = parseTags(existingTags).toMutableSet()
        existing.addAll(newTags.map { it.trim().filter { c -> c != ',' } }.filter { it.isNotBlank() })
        return existing.joinToString(",")
    }

    /** 从已有标签中移除指定标签 */
    fun removeTagsFromStr(existingTags: String?, tagsToRemove: List<String>): String {
        val removeSet = tagsToRemove.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val existing = parseTags(existingTags).filter { it !in removeSet }
        return existing.joinToString(",")
    }

    /**
     * 批量给笔记添加标签（追加去重）
     * @param noteIds 笔记 ID 列表
     * @param tags 要添加的标签列表
     */
    suspend fun addTagsToNotes(noteIds: List<Long>, tags: List<String>) {
        val cleanTags = tags.map { it.trim().filter { c -> c != ',' } }.filter { it.isNotBlank() }
        if (cleanTags.isEmpty()) return
        val notes = noteDao.getByIds(noteIds)
        notes.forEach { note ->
            val merged = mergeTags(note.tags, cleanTags)
            noteDao.updateTags(note.id, merged.ifEmpty { null })
        }
    }

    /**
     * 批量从笔记移除标签
     * @param noteIds 笔记 ID 列表
     * @param tags 要移除的标签列表
     */
    suspend fun removeTagsFromNotes(noteIds: List<Long>, tags: List<String>) {
        val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanTags.isEmpty()) return
        val notes = noteDao.getByIds(noteIds)
        notes.forEach { note ->
            val removed = removeTagsFromStr(note.tags, cleanTags)
            noteDao.updateTags(note.id, removed.ifEmpty { null })
        }
    }

    /**
     * 获取多篇笔记上所有标签的并集
     */
    suspend fun getTagsUnion(noteIds: List<Long>): List<String> {
        val notes = noteDao.getByIds(noteIds)
        val tagSet = mutableSetOf<String>()
        notes.forEach { note ->
            tagSet.addAll(parseTags(note.tags))
        }
        return tagSet.toList().sorted()
    }

    /** 获取单篇笔记的标签列表 */
    suspend fun getTagsForNote(noteId: Long): List<String> {
        val note = noteDao.getById(noteId)
        return parseTags(note?.tags)
    }

    // ════════════════════════════════════════════════
    // 批量操作
    // ════════════════════════════════════════════════

    /** 一次性获取所有软件（非 Flow），用于导出时构建软件名映射 */
    suspend fun getAllSoftwareSync(): List<SoftwareEntity> = softwareDao.getAllSync()

    /** 清除所有数据（清空四张表） */
    suspend fun clearAllData() {
        linkDao.deleteAll()
        noteDao.deleteAll()
        timeRecordDao.deleteAll()
        softwareDao.deleteAll()
    }
}
