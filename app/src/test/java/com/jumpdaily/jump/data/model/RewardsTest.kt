package com.jumpdaily.jump.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rewards 积分奖励体系纯逻辑单元测试（JVM）。
 *
 * 重点保护「奖品墙兑换重设计」依赖的几条不变量：
 * - 等级按积分单调递增、边界值不重不漏；
 * - 奖品解锁只由积分决定（与兑换状态解耦，兑换走 redeemed_prizes 手动确认）；
 * - 奖品库数据自洽（id 唯一、门槛严格递增），否则三态展示会出现逻辑矛盾。
 */
class RewardsTest {

    // ===== 等级 =====

    @Test
    fun `levelOf returns lowest level at zero points`() {
        val first = Rewards.LEVELS.first()
        assertEquals(first, Rewards.levelOf(0))
        assertEquals("跳绳小萌新", Rewards.levelOf(0).title)
    }

    @Test
    fun `levelOf crosses boundaries exactly at minPoints`() {
        // 每个 level 的 minPoints 恰好是升级点：积分 = minPoints 时应已进入该等级
        Rewards.LEVELS.drop(1).forEach { lv ->
            assertEquals(lv, Rewards.levelOf(lv.minPoints))
            // 差 1 分仍停在上一级
            assertEquals(
                "积分 ${lv.minPoints - 1} 不应进入 ${lv.title}",
                Rewards.LEVELS[Rewards.LEVELS.indexOf(lv) - 1],
                Rewards.levelOf(lv.minPoints - 1)
            )
        }
    }

    @Test
    fun `nextLevel is null only at max level`() {
        assertEquals(Rewards.LEVELS[1], Rewards.nextLevel(0))
        assertNull(Rewards.nextLevel(Rewards.LEVELS.last().minPoints))
        assertNull(Rewards.nextLevel(Int.MAX_VALUE / 2))
    }

    @Test
    fun `levelProgress stays within 0 to 1`() {
        assertTrue(Rewards.levelProgress(0) >= 0f)
        Rewards.LEVELS.forEach { lv ->
            assertTrue(Rewards.levelProgress(lv.minPoints) in 0f..1f)
        }
        assertEquals(1f, Rewards.levelProgress(Rewards.LEVELS.last().minPoints))
    }

    // ===== 奖品库数据自洽 =====

    @Test
    fun `prize ids are unique`() {
        val ids = Rewards.PRIZES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `prize thresholds are strictly ascending`() {
        // 门槛严格递增保证 nextPrize 唯一、解锁数量随积分单调不减
        val thresholds = Rewards.PRIZES.map { it.needPoints }
        assertEquals(thresholds, thresholds.sorted())
        assertTrue(thresholds.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `every prize has non-blank name and desc`() {
        Rewards.PRIZES.forEach { p ->
            assertTrue("${p.id} name blank", p.name.isNotBlank())
            assertTrue("${p.id} desc blank", p.desc.isNotBlank())
        }
    }

    // ===== 解锁与兑换 =====

    @Test
    fun `unlockedPrizes counts only prizes below points`() {
        val first = Rewards.PRIZES.first()
        assertEquals(1, Rewards.unlockedPrizes(first.needPoints).size)
        assertEquals(0, Rewards.unlockedPrizes(first.needPoints - 1).size)
        assertEquals(Rewards.PRIZES.size, Rewards.unlockedPrizes(Rewards.PRIZES.last().needPoints).size)
    }

    @Test
    fun `nextPrize is the first prize above points`() {
        val first = Rewards.PRIZES.first()
        val second = Rewards.PRIZES[1]
        assertEquals(first, Rewards.nextPrize(first.needPoints - 1))
        assertEquals(second, Rewards.nextPrize(first.needPoints))
        assertNull(Rewards.nextPrize(Rewards.PRIZES.last().needPoints))
    }
}
