// ============================================================
// SoftwareViewModel.kt — 软件列表/详情的 ViewModel
// ============================================================
// ViewModel 的职责：
// 1. 持有 UI 状态（通过 StateFlow）
// 2. 调用 Repository 执行数据操作
// 3. 在屏幕旋转等配置变更时保持数据不丢失
//
// ViewModelProvider.Factory：
// 因为 ViewModel 的构造函数需要 Repository 参数，
// 所以要自定义 Factory 来创建它。
//
// StateFlow vs LiveData：
// StateFlow 是 Kotlin 协程的"状态流"，和 Compose 配合更好。

package com.corunling.noteeverything.ui.software

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.entity.NoteEntity
import com.corunling.noteeverything.data.entity.SoftwareEntity
import com.corunling.noteeverything.data.entity.TimeRecordEntity
import com.corunling.noteeverything.util.DateTimeUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SoftwareViewModel(
    private val repository: NoteEverythingRepository
) : ViewModel() {

    // 基础软件列表
    val allSoftware = repository.getAllSoftware()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 带统计信息的软件列表。
     * allSoftware.map { ... } 对每个软件附加今日时长和最新笔记。
     */
    val softwareWithStats: StateFlow<List<SoftwareWithStats>> = allSoftware.map { list ->
        list.map { software ->
            val todayDuration = repository.getTodayDuration(software.id, DateTimeUtils.today())
            val latestNote = repository.getLatestNoteForSoftware(software.id)
            SoftwareWithStats(
                software = software,
                todayDuration = todayDuration,
                latestNote = latestNote?.content?.take(50)  // 只取前 50 字用作预览
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getTimeRecords(softwareId: Long): Flow<List<TimeRecordEntity>> =
        repository.getTimeRecordsBySoftware(softwareId)

    fun getNotes(softwareId: Long): Flow<List<NoteEntity>> =
        repository.getNotesBySoftware(softwareId)

    fun deleteSoftware(software: SoftwareEntity) {
        viewModelScope.launch { repository.deleteSoftware(software) }
    }

    fun deleteTimeRecord(record: TimeRecordEntity) {
        viewModelScope.launch { repository.deleteTimeRecord(record) }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch { repository.deleteNote(note) }
    }

    // 自定义 Factory
    class Factory(private val repository: NoteEverythingRepository) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SoftwareViewModel(repository) as T
        }
    }
}

// 软件 + 统计信息的数据类
data class SoftwareWithStats(
    val software: SoftwareEntity,
    val todayDuration: Long,
    val latestNote: String?
)
