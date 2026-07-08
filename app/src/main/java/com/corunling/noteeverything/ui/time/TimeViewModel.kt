package com.corunling.noteeverything.ui.time

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.corunling.noteeverything.data.NoteEverythingRepository
import com.corunling.noteeverything.data.dao.CategoryDuration
import com.corunling.noteeverything.data.dao.SoftwareDuration
import com.corunling.noteeverything.data.entity.SoftwareEntity
import com.corunling.noteeverything.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TimeViewModel(
    private val repository: NoteEverythingRepository
) : ViewModel() {

    enum class Period { TODAY, WEEK, MONTH, CUSTOM }

    private val _selectedPeriod = MutableStateFlow(Period.TODAY)
    val selectedPeriod: StateFlow<Period> = _selectedPeriod.asStateFlow()

    // 各 Tab 独立保存日期范围
    private var todayStart = DateTimeUtils.today()
    private var todayEnd = DateTimeUtils.today()
    private var weekStart = DateTimeUtils.startOfWeek()
    private var weekEnd = DateTimeUtils.today()
    private var monthStart = DateTimeUtils.startOfMonth()
    private var monthEnd = DateTimeUtils.today()
    private var customStart = DateTimeUtils.daysAgo(29)
    private var customEnd = DateTimeUtils.today()

    // 当前显示的日期范围
    private val _rangeStart = MutableStateFlow(DateTimeUtils.today())
    val rangeStart: StateFlow<String> = _rangeStart.asStateFlow()
    private val _rangeEnd = MutableStateFlow(DateTimeUtils.today())
    val rangeEnd: StateFlow<String> = _rangeEnd.asStateFlow()
    private val _rangeLabel = MutableStateFlow("")
    val rangeLabel: StateFlow<String> = _rangeLabel.asStateFlow()
    private val _showArrows = MutableStateFlow(true)
    val showArrows: StateFlow<Boolean> = _showArrows.asStateFlow()

    // 统计数据
    private val _rankingStats = MutableStateFlow<List<SoftwareDuration>>(emptyList())
    val rankingStats: StateFlow<List<SoftwareDuration>> = _rankingStats.asStateFlow()
    private val _dailyTrends = MutableStateFlow<List<LineChartPoint>>(emptyList())
    val dailyTrends: StateFlow<List<LineChartPoint>> = _dailyTrends.asStateFlow()
    private val _categoryStats = MutableStateFlow<List<CategoryDuration>>(emptyList())
    val categoryStats: StateFlow<List<CategoryDuration>> = _categoryStats.asStateFlow()
    private val _totalMinutes = MutableStateFlow(0L)
    val totalMinutes: StateFlow<Long> = _totalMinutes.asStateFlow()
    private val _dailyAvg = MutableStateFlow(0L)
    val dailyAvg: StateFlow<Long> = _dailyAvg.asStateFlow()
    private val _daysCount = MutableStateFlow(0)
    val daysCount: StateFlow<Int> = _daysCount.asStateFlow()
    private val _topSoftwareName = MutableStateFlow("")
    val topSoftwareName: StateFlow<String> = _topSoftwareName.asStateFlow()
    private val _topSoftwareMinutes = MutableStateFlow(0L)
    val topSoftwareMinutes: StateFlow<Long> = _topSoftwareMinutes.asStateFlow()
    private val _softwareList = MutableStateFlow<List<SoftwareEntity>>(emptyList())
    val softwareList: StateFlow<List<SoftwareEntity>> = _softwareList.asStateFlow()
    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    // 已播放过入场的 ID（防止滚动重播）
    private val _animatedBarIds = MutableStateFlow(setOf<Long>())
    val animatedBarIds: StateFlow<Set<Long>> = _animatedBarIds.asStateFlow()
    fun markBarAnimated(id: Long) { _animatedBarIds.value = _animatedBarIds.value + id }
    fun resetAnimations() { _animatedBarIds.value = emptySet() }

    init {
        loadSoftwareList()
        applyPeriod(Period.TODAY)
    }

    fun selectPeriod(period: Period) {
        saveCurrentRange()
        resetAnimations()
        _selectedPeriod.value = period
        _showArrows.value = period != Period.CUSTOM
        applyPeriod(period)
    }

    fun previousPeriod() {
        val period = _selectedPeriod.value
        if (period == Period.CUSTOM) return
        val (s, e) = computeDateRange(period, _rangeStart.value, _rangeEnd.value, backward = true)
        setRange(period, s, e)
    }

    fun nextPeriod() {
        val period = _selectedPeriod.value
        if (period == Period.CUSTOM) return
        val (s, e) = computeDateRange(period, _rangeStart.value, _rangeEnd.value, backward = false)
        setRange(period, s, e)
    }

    fun navigateToDay(date: String) {
        resetAnimations()
        _selectedPeriod.value = Period.TODAY
        setRange(Period.TODAY, date, date)
    }

    fun navigateToWeek(start: String, end: String) {
        resetAnimations()
        _selectedPeriod.value = Period.WEEK
        setRange(Period.WEEK, start, end)
    }

    fun navigateToMonth(start: String, end: String) {
        resetAnimations()
        _selectedPeriod.value = Period.MONTH
        setRange(Period.MONTH, start, end)
    }

    fun setCustomRange(start: String, end: String) {
        resetAnimations()
        customStart = start; customEnd = end
        _selectedPeriod.value = Period.CUSTOM
        _showArrows.value = false
        setRange(Period.CUSTOM, start, end)
    }

    fun applyFilter(filter: FilterState) {
        _filterState.value = filter
        applyPeriod(_selectedPeriod.value)
    }

    // ════════════════════════════════════════════
    // 内部
    // ════════════════════════════════════════════

    /** 保存当前 Tab 的日期到其独立存储 */
    private fun saveCurrentRange() {
        when (_selectedPeriod.value) {
            Period.TODAY -> { todayStart = _rangeStart.value; todayEnd = _rangeEnd.value }
            Period.WEEK -> { weekStart = _rangeStart.value; weekEnd = _rangeEnd.value }
            Period.MONTH -> { monthStart = _rangeStart.value; monthEnd = _rangeEnd.value }
            Period.CUSTOM -> {}
        }
    }

    /** 加载指定 Tab 的存储日期并刷新 */
    private fun applyPeriod(period: Period) {
        val (s, e) = when (period) {
            Period.TODAY -> todayStart to todayEnd
            Period.WEEK -> weekStart to weekEnd
            Period.MONTH -> monthStart to monthEnd
            Period.CUSTOM -> customStart to customEnd
        }
        _rangeStart.value = s; _rangeEnd.value = e
        updateRangeLabel(period, s, e)
        loadData(s, e)
    }

    /** 设置当前 Tab 的日期范围并刷新 */
    private fun setRange(period: Period, start: String, end: String) {
        // 同步更新存储
        when (period) {
            Period.TODAY -> { todayStart = start; todayEnd = end }
            Period.WEEK -> { weekStart = start; weekEnd = end }
            Period.MONTH -> { monthStart = start; monthEnd = end }
            Period.CUSTOM -> { customStart = start; customEnd = end }
        }
        _rangeStart.value = start; _rangeEnd.value = end
        updateRangeLabel(period, start, end)
        loadData(start, end)
    }

    private fun loadData(start: String, end: String) {
        viewModelScope.launch {
            val filter = _filterState.value
            val rawRanking = repository.getStatsInRange(start, end)
            _rankingStats.value = if (filter.selectedSoftwareIds.isEmpty()) rawRanking
            else rawRanking.filter { it.softwareId in filter.selectedSoftwareIds }

            if (_selectedPeriod.value == Period.TODAY) {
                val rawHourly = repository.getHourlyStats(start)
                val hourlyMap = rawHourly.associate { it.hour to it.total }
                _dailyTrends.value = (0..23).map { h ->
                    LineChartPoint(label = "${h}:00", value = (hourlyMap[h] ?: 0L).toFloat())
                }
            } else {
                val rawDaily = repository.getDailyStatsInRange(start, end)
                val dailyMap = rawDaily.associate { it.date to it.total }
                _dailyTrends.value = fillDailyGaps(start, end, dailyMap)
            }

            val rawCategory = repository.getCategoryStatsInRange(start, end)
            _categoryStats.value = if (filter.selectedCategories.isEmpty()) rawCategory
            else rawCategory.filter { it.category in filter.selectedCategories }

            val allStats = repository.getStatsInRange(start, end)
            val filteredStats = if (filter.selectedSoftwareIds.isEmpty()) allStats
            else allStats.filter { it.softwareId in filter.selectedSoftwareIds }

            val total = filteredStats.sumOf { it.total }
            _totalMinutes.value = total

            val daysWithRecords = if (_selectedPeriod.value == Period.TODAY) 1
            else repository.getDailyStatsInRange(start, end).size
            _daysCount.value = daysWithRecords.coerceAtLeast(1)
            _dailyAvg.value = if (daysWithRecords > 0) total / daysWithRecords else 0

            val top = filteredStats.maxByOrNull { it.total }
            if (top != null) {
                val sw = repository.getSoftware(top.softwareId)
                _topSoftwareName.value = sw?.name ?: "已删除"
                _topSoftwareMinutes.value = top.total
            } else {
                _topSoftwareName.value = ""
                _topSoftwareMinutes.value = 0L
            }
        }
    }

    private fun loadSoftwareList() {
        viewModelScope.launch {
            _softwareList.value = repository.getAllSoftwareSync()
        }
    }

    private fun updateRangeLabel(period: Period, start: String, end: String) {
        val today = DateTimeUtils.today()
        _rangeLabel.value = when (period) {
            Period.TODAY -> {
                if (start == today) "今天 (${formatShortDate(start)})"
                else formatFullDate(start)
            }
            Period.WEEK -> {
                if (dateInRange(today, start, end)) "本周 (${formatShortDate(start)} - ${formatShortDate(end)})"
                else "${formatFullDate(start)} - ${formatFullDate(end)}"
            }
            Period.MONTH -> formatMonthLabel(start)
            Period.CUSTOM -> "${formatFullDate(start)} - ${formatFullDate(end)}"
        }
    }

    /** 检测日期是否在范围内 */
    private fun dateInRange(date: String, rangeStart: String, rangeEnd: String): Boolean {
        return date >= rangeStart && date <= rangeEnd
    }

    private fun fillDailyGaps(start: String, end: String, dataMap: Map<String, Long>): List<LineChartPoint> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfShort = SimpleDateFormat("M/d", Locale.getDefault())
        val result = mutableListOf<LineChartPoint>()
        val cal = Calendar.getInstance()
        var current = sdf.parse(start) ?: return emptyList()
        val endDate = sdf.parse(end) ?: return emptyList()
        cal.time = current
        while (!cal.time.after(endDate)) {
            val dateStr = sdf.format(cal.time)
            val shortLabel = try { sdfShort.format(cal.time) } catch (_: Exception) { dateStr }
            result.add(LineChartPoint(label = shortLabel, value = (dataMap[dateStr] ?: 0L).toFloat()))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return result
    }

    private fun computeDateRange(period: Period, currStart: String, currEnd: String, backward: Boolean): Pair<String, String> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val dir = if (backward) -1 else 1
        return when (period) {
            Period.TODAY -> currStart to currEnd
            Period.WEEK -> {
                cal.time = sdf.parse(currStart) ?: return currStart to currEnd
                cal.add(Calendar.DAY_OF_MONTH, 7 * dir)
                val ns = sdf.format(cal.time)
                cal.add(Calendar.DAY_OF_MONTH, 6); ns to sdf.format(cal.time)
            }
            Period.MONTH -> {
                cal.time = sdf.parse(currStart) ?: return currStart to currEnd
                cal.add(Calendar.MONTH, dir)
                val ns = sdf.format(cal.time)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                ns to sdf.format(cal.time)
            }
            Period.CUSTOM -> currStart to currEnd
        }
    }

    private fun formatShortDate(dateStr: String): String = try {
        val p = dateStr.split("-"); "${p[1].toInt()}/${p[2].toInt()}"
    } catch (_: Exception) { dateStr }

    private fun formatFullDate(dateStr: String): String = try {
        val p = dateStr.split("-"); "${p[0]}/${p[1].toInt()}/${p[2].toInt()}"
    } catch (_: Exception) { dateStr }

    private fun formatMonthLabel(dateStr: String): String = try {
        val p = dateStr.split("-"); "${p[0]}年${p[1].toInt()}月"
    } catch (_: Exception) { dateStr }

    class Factory(private val repository: NoteEverythingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TimeViewModel(repository) as T
    }
}
