package com.jumpdaily.jump.camera

import java.util.ArrayDeque

/**
 * 基于人体姿态（髋部归一化 y）的跳绳计数算法。纯逻辑、无 Android 依赖，便于单元测试。
 *
 * 原理：BlazePose 输出 33 个归一化关键点（y 取值 0=图像顶部，1=底部）。
 * 跳绳时身体随节奏上下：起跳 => 髋中心 y 减小；落地 => 髋中心 y 增大。
 * 用滞回（hysteresis）检测一次完整的「落地→起跳→落地」周期，计为一个跳绳。
 */
class PoseCounter(private val sensitivity: Float = 1.0f) {
    private val recent = ArrayDeque<Long>()
    var count = 0
        private set
    private var baseline = 0.5f
    private var airborne = false
    private var lastJumpTs = 0L

    companion object {
        private const val MIN_INTERVAL_MS = 300L
    }

    /** 喂入一帧的髋中心归一化 y(0~1)，完成一次跳跃时触发回调。 */
    fun feed(hipY: Float, now: Long, onJump: () -> Unit, onCadence: (Int) -> Unit) {
        // 指数滑动基线：自适应孩子与镜头的距离造成的整体上下位移。
        // 0.05 权重比原 0.04 稍快，能更跟手地适应孩子前后挪动，又不会被单次跳动带偏。
        baseline = baseline * 0.95f + hipY * 0.05f
        val dev = hipY - baseline
        // 灵敏度越高，触发阈值越小（越容易计数）；基础阈值 0.018 比原 0.025 更低，小跳也能识别
        val thr = 0.018f / sensitivity.coerceIn(0.5f, 1.6f)
        if (!airborne && dev < -thr) {
            airborne = true // 离地起跳
        } else if (airborne && dev > -thr * 0.3f && now - lastJumpTs > MIN_INTERVAL_MS) {
            // 回到基线附近（不必超过基线）即记为一次落地：对小跳/低跳更友好，减少漏计。
            // 配合上方「需先离地 thr 才置 airborne」，仍保证一次完整起跳-落地只计 1 下。
            airborne = false
            count++
            lastJumpTs = now
            synchronized(recent) {
                recent.addLast(now)
                if (recent.size > 12) recent.removeFirst()
            }
            onJump()
            onCadence(estimateCadence())
        }
    }

    /** 用最近若干次间隔估算频率（个/分钟）。 */
    private fun estimateCadence(): Int {
        synchronized(recent) {
            if (recent.size < 2) return 0
            val it = recent.iterator()
            var prev = it.next()
            var sum = 0L
            var n = 0
            while (it.hasNext()) {
                val cur = it.next()
                sum += cur - prev
                prev = cur
                n++
            }
            if (n == 0 || sum == 0L) return 0
            return (60000f / (sum / n)).toInt().coerceIn(0, 300)
        }
    }

    fun reset() {
        count = 0
        recent.clear()
        airborne = false
        lastJumpTs = 0L
        baseline = 0.5f
    }
}
