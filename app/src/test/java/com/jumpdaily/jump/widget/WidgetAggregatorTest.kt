package com.jumpdaily.jump.widget

import com.jumpdaily.jump.data.model.WidgetSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 固化「async 异常延迟暴露」的正确行为：
 * WidgetAggregator 用 coroutineScope + 逐一 await 实现 fail-fast——
 * 任一并行源抛错时，异常必须**上抛**给调用方，绝不能被静默吞掉。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetAggregatorTest {

    @Test
    fun `aggregate rethrows when one source fails instead of swallowing`() = runTest {
        // 注意：assertThrows 的 lambda 不是协程体，不能直接调 suspend 的 aggregate，
        // 故用 try/catch 包在 runTest 协程体内捕获（这也是结构化并发测试的标准写法）。
        var thrown: Throwable? = null
        try {
            WidgetAggregator.aggregate(
                today = { 10 },
                streak = { throw RuntimeException("streak source died") },
                name = { "小朋友" },
                total = { 100 }
            )
        } catch (e: Throwable) {
            thrown = e
        }
        assertNotNull("异常必须上抛，不能被吞掉", thrown)
        assertTrue("上抛的应是源异常类型", thrown is RuntimeException)
        assertEquals("streak source died", thrown?.message)
    }

    @Test
    fun `aggregate returns combined snapshot when all sources succeed`() = runTest {
        val s = WidgetAggregator.aggregate(
            today = { 12 },
            streak = { 3 },
            name = { "小明" },
            total = { 200 }
        )
        assertEquals(WidgetSnapshot("小明", 12, 3, 200), s)
    }
}
