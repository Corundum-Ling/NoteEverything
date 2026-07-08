// ============================================================
// DateTimeUtils.kt — 日期时间工具类
// ============================================================
// 集中管理所有日期格式化和计算逻辑。
// 用 object 声明（Kotlin 单例），不需要实例化。

package com.corunling.noteeverything.util

import java.text.SimpleDateFormat
import java.util.*

object DateTimeUtils {

    /** 今天的日期字符串：YYYY-MM-DD */
    fun today(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    /** 当前时间戳（毫秒） */
    fun now(): Long = System.currentTimeMillis()

    /** 今天 00:00:00 的时间戳（用于手动录入时长时计算起点） */
    fun todayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** 时间戳 → HH:mm（只显示时间） */
    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /** 时间戳 → MM/dd HH:mm（显示日期+时间） */
    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /** 分钟数 → "Xh Ym" 或 "Ym" 格式 */
    fun formatDuration(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    /** 本周一的日期 */
    fun startOfWeek(): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(cal.time)
    }

    /** 本月第一天的日期 */
    fun startOfMonth(): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(cal.time)
    }

    /** 昨天的日期 */
    fun yesterday(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(cal.time)
    }

    /** N 天前的日期 */
    fun daysAgo(n: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -n)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(cal.time)
    }

    /** 毫秒时间戳 → YYYY-MM-DD */
    fun millisToDateStr(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(millis))
    }
}
