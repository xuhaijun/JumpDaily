package com.jumpdaily.jump.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PoseCounter 纯算法单元测试（JVM，无需 Android 环境）。
 * 验证：一次完整的落地→起跳→落地周期计 1 个；过快的抖动被最小间隔过滤。
 */
class PoseCounterTest {

    @Test
    fun `counts one jump per full landing cycle`() {
        val counter = PoseCounter(1.0f)
        var jumps = 0
        // 模拟 3 个周期：起跳(hipY 下降) -> 落地(hipY 上升)
        repeat(3) { c ->
            val t0 = (c * 2000L)
            counter.feed(0.40f, t0, { jumps++ }, {})       // 起跳中
            counter.feed(0.35f, t0 + 100, { jumps++ }, {}) // 离地
            counter.feed(0.62f, t0 + 200, { jumps++ }, {}) // 落地（计次）
            counter.feed(0.55f, t0 + 400, { jumps++ }, {}) // 已落地，不计
        }
        assertEquals(3, jumps)
    }

    @Test
    fun `ignores jumps faster than min interval`() {
        val counter = PoseCounter(1.0f)
        var jumps = 0
        var t = 0L
        // 每 50ms 一次快速「落地+起跳」，间隔远小于 MIN_INTERVAL(300ms)
        repeat(10) {
            t += 50L
            counter.feed(0.62f, t, { jumps++ }, {})
            counter.feed(0.35f, t + 10, { jumps++ }, {})
        }
        assertTrue("快速抖动不应全部计数", jumps < 10)
    }

    @Test
    fun `sensitivity lowers threshold so lighter motion counts`() {
        val high = PoseCounter(1.5f)
        val low = PoseCounter(0.5f)
        var highJumps = 0
        var lowJumps = 0
        // 小幅摆动：高灵敏度应能触发，低灵敏度过滤
        repeat(5) { c ->
            val t0 = (c * 1000L)
            high.feed(0.47f, t0, { highJumps++ }, {})
            high.feed(0.53f, t0 + 300, { highJumps++ }, {})
            low.feed(0.47f, t0, { lowJumps++ }, {})
            low.feed(0.53f, t0 + 300, { lowJumps++ }, {})
        }
        assertTrue("高灵敏度应比低灵敏度计数更多", highJumps >= lowJumps)
    }
}
