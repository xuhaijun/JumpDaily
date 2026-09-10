package com.jumpdaily.jump.camera

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 提供 MediaPipe PoseLandmarker 模型文件路径。
 *
 * 优先级：
 * 1) 打包在 `assets/pose_landmarker.task`（离线、稳定，推荐）；
 * 2) 已下载到应用私有目录 filesDir 的缓存；
 * 3) 首次运行时从官方地址下载到 filesDir 并缓存（需联网）。
 *
 * 模型（约 5MB，lite/float16 版）：
 * https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task
 *
 * 备注：本机开发环境无法下载该模型；请手动把 .task 文件放入
 * `app/src/main/assets/`（保持文件名 `pose_landmarker.task`），即可离线使用。
 */
object PoseModelProvider {
    private const val MODEL_URL =
        "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
    private const val ASSET_NAME = "pose_landmarker.task"
    private const val FILE_NAME = "pose_landmarker.task"
    // 模型加载（仅当 assets 缺失需联网下载时）整体超时：超时即放弃下载、走预置降级。
    // 阻塞式 HttpURLConnection 无法被协程取消真正打断，故该超时是「调用方快速放弃」的兜底，
    // 底层 IO 线程由下方 connection readTimeout 兜底回收（见 download()）。
    private const val DOWNLOAD_TIMEOUT_MS = 15_000L

    /** 返回可用模型路径（assets 优先；否则确保已下载到 filesDir）。下载失败抛异常，由调用方处理。 */
    @Synchronized
    fun getModelPath(context: Context): String {
        val cached = File(context.filesDir, FILE_NAME)
        // 1) assets 内有模型：复制到 filesDir 供 MediaPipe 读取
        val assetBytes = runCatching { context.assets.open(ASSET_NAME).use { it.readBytes() } }.getOrNull()
        if (assetBytes != null) {
            if (!cached.exists() || cached.length() != assetBytes.size.toLong()) {
                cached.writeBytes(assetBytes)
            }
            return cached.absolutePath
        }
        // 2) 已下载缓存
        if (cached.exists() && cached.length() > 0) return cached.absolutePath
        // 3) 运行时下载（需 INTERNET 权限 + 联网）
        download(MODEL_URL, cached)
        if (!cached.exists() || cached.length() == 0L) {
            throw IllegalStateException("pose model download failed: $MODEL_URL")
        }
        return cached.absolutePath
    }

    private fun download(url: String, target: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            // 与 DOWNLOAD_TIMEOUT_MS 接近：即使调用方已超时放弃，底层 IO 线程也会在此上界自行回收，避免孤儿线程长期挂起
            readTimeout = 20_000
            setRequestProperty("User-Agent", "AiTiaoSheng/1.0")
        }
        conn.inputStream.use { input -> target.outputStream().use { out -> input.copyTo(out) } }
        conn.disconnect()
    }

    /**
     * 挂起版模型路径获取：在 [Dispatchers.IO] 上执行阻塞的 assets 拷贝/联网下载，
     * 并用 [withTimeoutOrNull] 兜底——超时（如极端慢网）返回 `null` 而非抛异常，
     * 由调用方优雅降级（不绑定相机、提示手动放置模型）。
     *
     * 普通异常（下载失败等）也会透出，调用方用 `runCatching` 兜底即可。
     */
    suspend fun getModelPathSafely(context: Context): String? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) { getModelPath(context) }
        }
}
