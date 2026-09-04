package com.jumpdaily.jump.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.util.ArrayDeque

/**
 * 基于手机传感器的跳绳计数器。
 *
 * 思路（启发式，适合儿童趣味场景，非医疗/竞技级精度）：
 * 1. 加速度计检测到「落地冲击峰值」即记一个跳绳；
 * 2. 用「腾空（加速度骤降）」状态过滤掉起跳那一次冲击，避免一次跳被记两下；
 * 3. 陀螺仪平均转速过高 => 认为手机被大幅度挥动 => 提示「动作不规范、请站稳」；
 * 4. 实时计算频率（个/分钟）交给上层做加油/节奏反馈。
 *
 * 注：手机方案对握姿敏感，若要竞技级精度应改用摄像头 + 姿态估计。
 *
 * 回调可能在传感器线程触发，消费方请自行切回主线程更新 UI。
 */
interface JumpListener {
    /** 完成一个跳绳 */
    fun onJump()

    /** 实时频率（个/分钟） */
    fun onCadence(cadence: Int)

    /** 动作不规范提醒（已做节流，不会刷屏） */
    fun onFormIssue()
}

class JumpDetector(
    private val sensorManager: SensorManager,
    private val listener: JumpListener,
    sensitivity: Float = 1.0f
) {
    companion object {
        private const val TAG = "JumpDetector"
        private const val G = 9.80665f
        private const val MIN_INTERVAL_MS = 250L // 最快约 240 个/分，过滤抖动
        private const val FORM_COOLDOWN_MS = 4000L
        private const val FORM_EVAL_MS = 2000L
        private const val GYRO_FORM_THRESHOLD = 1.6f // rad/s 平均转速，超过认为在挥手机
    }

    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    @Volatile private var sensitivity = sensitivity.coerceIn(0.5f, 1.5f)

    private val recentJumps = ArrayDeque<Long>()
    private val lock = Any()

    private var lastPeakTs = 0L
    private var airborne = false

    private var lastFormWarnTs = 0L
    private var gyroSum = 0.0
    private var gyroCount = 0
    private var lastFormEvalTs = 0L

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> handleAccel(event)
                Sensor.TYPE_GYROSCOPE -> handleGyro(event)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun setSensitivity(value: Float) {
        sensitivity = value.coerceIn(0.5f, 1.5f)
    }

    fun start() {
        if (accelerometer == null) {
            Log.w(TAG, "设备无加速度传感器，计数不可用")
            return
        }
        sensorManager.registerListener(
            sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME
        )
        if (gyroscope != null) {
            sensorManager.registerListener(
                sensorListener, gyroscope, SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(sensorListener)
        synchronized(lock) { recentJumps.clear() }
        airborne = false
        lastPeakTs = 0L
    }

    private fun handleAccel(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]
        val mag = kotlin.math.sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
        val now = System.currentTimeMillis()

        // 腾空：加速度明显低于静止重力 => 标记 airborne
        if (mag < G * 0.5f) airborne = true

        // 落地冲击阈值：灵敏度越高阈值越低（更容易计数）
        val peakThreshold = (G * 1.6f) / sensitivity
        if (airborne && mag > peakThreshold && now - lastPeakTs > MIN_INTERVAL_MS) {
            airborne = false
            lastPeakTs = now
            registerJump(now)
        }
    }

    private fun registerJump(now: Long) {
        val cadence: Int
        synchronized(lock) {
            recentJumps.addLast(now)
            if (recentJumps.size > 12) recentJumps.removeFirst()
            cadence = computeCadence()
        }
        listener.onJump()
        listener.onCadence(cadence)
    }

    /** 用最近几次间隔估算频率（个/分钟）。 */
    private fun computeCadence(): Int {
        if (recentJumps.size < 2) return 0
        val it = recentJumps.iterator()
        var prev = it.next()
        var sum = 0L
        var n = 0
        while (it.hasNext()) {
            val cur = it.next()
            sum += (cur - prev)
            prev = cur
            n++
        }
        if (n == 0 || sum == 0L) return 0
        val avgInterval = sum.toFloat() / n
        return (60000f / avgInterval).toInt().coerceIn(0, 300)
    }

    private fun handleGyro(event: SensorEvent) {
        val gx = event.values[0]
        val gy = event.values[1]
        val gz = event.values[2]
        val gm = kotlin.math.sqrt((gx * gx + gy * gy + gz * gz).toDouble()).toFloat()
        val now = System.currentTimeMillis()
        gyroSum += gm
        gyroCount++
        if (now - lastFormEvalTs >= FORM_EVAL_MS) {
            val avg = if (gyroCount > 0) (gyroSum / gyroCount).toFloat() else 0f
            gyroSum = 0.0
            gyroCount = 0
            lastFormEvalTs = now
            if (avg > GYRO_FORM_THRESHOLD && now - lastFormWarnTs > FORM_COOLDOWN_MS) {
                lastFormWarnTs = now
                listener.onFormIssue()
            }
        }
    }
}
