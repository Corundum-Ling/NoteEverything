// ============================================================
// TimeViewModel.kt — 时长总览的 ViewModel
// ============================================================
// 加载今日/本周/本月三个维度的统计数据。

package com.corunling.noteeverything.ui.time

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.dao.SoftwareDuration
import com.corunling.noteeverything.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimeViewModel(
    private val repository: NoteEverythingRepository
) : ViewModel() {

    private val _todayStats = MutableStateFlow<List<SoftwareDuration>>(emptyList())
    val todayStats: StateFlow<List<SoftwareDuration>> = _todayStats.asStateFlow()

    private val _weekStats = MutableStateFlow<List<SoftwareDuration>>(emptyList())
    val weekStats: StateFlow<List<SoftwareDuration>> = _weekStats.asStateFlow()

    private val _monthStats = MutableStateFlow<List<SoftwareDuration>>(emptyList())
    val monthStats: StateFlow<List<SoftwareDuration>> = _monthStats.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(Period.TODAY)
    val selectedPeriod: StateFlow<Period> = _selectedPeriod.asStateFlow()

    enum class Period { TODAY, WEEK, MONTH }

    init {
        loadAll()
    }

    fun selectPeriod(period: Period) {
        _selectedPeriod.value = period
    }

    fun loadAll() {
        viewModelScope.launch {
            _todayStats.value = repository.getDailyStats(DateTimeUtils.today())
            _weekStats.value = repository.getStatsInRange(
                DateTimeUtils.startOfWeek(), DateTimeUtils.today()
            )
            _monthStats.value = repository.getStatsInRange(
                DateTimeUtils.startOfMonth(), DateTimeUtils.today()
            )
        }
    }

    class Factory(private val repository: NoteEverythingRepository) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TimeViewModel(repository) as T
        }
    }
}
