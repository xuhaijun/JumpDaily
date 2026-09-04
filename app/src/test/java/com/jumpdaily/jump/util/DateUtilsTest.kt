package com.jumpdaily.jump.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * DateUtils 纯逻辑单元测试（JVM）。
 */
class DateUtilsTest {

    @Test
    fun `dayStart zeroes the time part`() {
        val ts = dayStart(System.currentTimeMillis())
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `computeStreak counts consecutive days`() {
        val today = dayStart(System.currentTimeMillis())
        // 连续 3 天都有记录
        assertEquals(3, computeStreak(listOf(today, today - DAY_MS, today - 2 * DAY_MS)))
        // 缺中间一天 => 只从今天数 1 天
        assertEquals(1, computeStreak(listOf(today, today - 2 * DAY_MS)))
        // 空列表
        assertEquals(0, computeStreak(emptyList()))
    }

    @Test
    fun `formatDuration formats mmss`() {
        assertEquals("00:00", formatDuration(0))
        assertEquals("01:05", formatDuration(65))
        assertEquals("10:00", formatDuration(600))
        assertEquals("59:59", formatDuration(3599))
    }
}
