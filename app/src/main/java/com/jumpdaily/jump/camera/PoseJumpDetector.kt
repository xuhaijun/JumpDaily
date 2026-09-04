package com.jumpdaily.jump.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.PoseLandmarkerOptions
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.jumpdaily.jump.sensor.JumpListener
import java.io.File
import java.util.concurrent.Executors

/**
 * 基于 MediaPipe PoseLandmarker 的摄像头跳绳计数器。
 *
 * 采用 LIVE_STREAM 模式逐帧检测人体 33 关键点，取「髋中心」归一化 y 交给 [PoseCounter] 计次，
 * 通过 [JumpListener] 回调，与传感器方案（JumpDetector）保持同一套对外接口，训练页可无缝切换。
 *
 * 说明：createFromOptions 会加载模型（建议在后台线程调用）；检测结果在 MediaPipe 内部线程回调。
 */
class PoseJumpDetector(
    private val context: Context,
    private val modelPath: String,
    private val listener: JumpListener,
    sensitivity: Float = 1.3f,
    /** 每帧最新关键点回调（归一化 x,y∈[0,1]），为空则不绘制骨架。 */
    private val onLandmarks: ((List<Pair<Float, Float>>) -> Unit)? = null,
    /** 初始化失败回调（如当前 ABI 不支持 MediaPipe 原生库），用于 UI 友好降级而非崩溃。 */
    private val onInitError: (Throwable) -> Unit = {}
) {
    companion object {
        private const val TAG = "PoseJumpDetector"
        // MediaPipe PoseLandmarker 关键点索引：23=左髋 24=右髋
        private const val LEFT_HIP = 23
        private const val RIGHT_HIP = 24
    }

    private val counter = PoseCounter(sensitivity)
    private var landmarker: PoseLandmarker? = null
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var running = false
    private var frameTs = 0L

    /** 在后台线程创建 PoseLandmarker（加载模型）。 */
    fun create() {
        executor.execute {
            try {
                val options = PoseLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelPath).build())
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinPosePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setResultListener { result, _ -> onResult(result) }
                    .setErrorListener { e -> Log.wtf(TAG, "pose detect error", e) }
                    .build()
                landmarker = PoseLandmarker.createFromOptions(context, options)
            } catch (e: Throwable) {
                // 捕获 Throwable（含 UnsatisfiedLinkError）：MediaPipe 原生库仅提供 ARM 架构，
                // 在 x86/x86_64 模拟器上 System.loadLibrary 会抛 LinkageError，必须兜住以免崩溃。
                // 诊断：把完整 cause 链 + 堆栈写到外部存储文件，release 下 Log.wtf 可能被 R8 吞掉，
                // 走文件可 100% 拿到真凶（adb pull 读取）。
                val sb = StringBuilder()
                sb.append("ROOT: ${e.javaClass.name}: ${e.message}\n")
                var c: Throwable? = e.cause
                var depth = 1
                while (c != null) {
                    sb.append("CAUSE[$depth]: ${c.javaClass.name}: ${c.message}\n")
                    c = c.cause
                    depth++
                }
                sb.append("STACK:\n").append(e.stackTraceToString())
                try {
                    val dir = context.getExternalFilesDir(null)
                    if (dir != null) {
                        File(dir, "pose_error.txt").writeText(sb.toString())
                    }
                } catch (_: Throwable) {
                    // 忽略写文件失败
                }
                Log.wtf(TAG, "create PoseLandmarker failed\n$sb", e)
                onInitError(e)
            }
        }
    }

    fun start() { running = true }

    fun stop() {
        running = false
        runCatching { landmarker?.close() }
        landmarker = null
    }

    /** 处理一帧（已做旋转校正的 Bitmap）。 */
    fun processFrame(bitmap: Bitmap) {
        if (!running) return
        val lm = landmarker ?: return
        try {
            val mpImage: MPImage = BitmapImageBuilder(bitmap).build()
            // LIVE_STREAM 要求时间戳单调递增
            frameTs = maxOf(frameTs + 1, System.currentTimeMillis())
            lm.detectAsync(mpImage, frameTs)
        } catch (e: Exception) {
            Log.wtf(TAG, "detectAsync failed", e)
        }
    }

    private fun onResult(result: PoseLandmarkerResult) {
        if (!running) return
        val persons = result.landmarks()
        if (persons.isNullOrEmpty()) return
        val person = persons[0]
        if (person.size <= RIGHT_HIP) return
        val hipY = (person[LEFT_HIP].y() + person[RIGHT_HIP].y()) / 2f
        // 提取整副骨架（归一化坐标）供 UI 叠加层绘制，让孩子看到自己的跳绳姿态
        val pts = person.map { it.x() to it.y() }
        onLandmarks?.invoke(pts)
        counter.feed(
            hipY, System.currentTimeMillis(),
            onJump = listener::onJump,
            onCadence = listener::onCadence
        )
    }
}
