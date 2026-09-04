package com.jumpdaily.jump.util

const val DAY_MS = 24L * 60 * 60 * 1000

/** 取某个时间戳所在「当天 0 点」的时间戳，用于按天分组。 */
fun dayStart(ts: Long): Long {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = ts
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** 计算「连续打卡天数」：从今天（或昨天）往前数连续有记录的天数。 */
fun computeStreak(dayStarts: List<Long>): Int {
    if (dayStarts.isEmpty()) return 0
    val set = dayStarts.toSet()
    var cursor = if (set.contains(dayStart(System.currentTimeMillis()))) {
        dayStart(System.currentTimeMillis())
    } else {
        dayStart(System.currentTimeMillis()) - DAY_MS
    }
    var streak = 0
    while (set.contains(cursor)) {
        streak++
        cursor -= DAY_MS
    }
    return streak
}

/** 友好日期：MM.dd */
fun formatMonthDay(ts: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    return "%02d.%02d".format(cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
}

/** 友好日期时间：MM.dd HH:mm（历史记录列表条目用，展示具体训练时刻）。 */
fun formatDateMinutes(ts: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    return "%02d.%02d %02d:%02d".format(
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH),
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE)
    )
}

/** 秒 -> mm:ss */
fun formatDuration(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%02d:%02d".format(m, s)
}
