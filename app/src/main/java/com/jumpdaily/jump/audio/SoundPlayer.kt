package com.jumpdaily.jump.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RawRes
import com.jumpdaily.jump.R
import java.util.concurrent.Executors

/**
 * 反馈音效播放器。
 *
 * 设计：优先播放 res/raw 下的**童音音效**素材（snd_*.wav）。这些素材由
 * `tools/gen_sound_effects.py` 生成：TTS 朗读「哇！」「好棒！」「加油！」等短句 →
 * 升调 + 时长补偿做成稚嫩童音 → 再叠一层清脆铃音，听感是「小朋友在为你欢呼」而不是电子音。
 * 想换音色改脚本里的文案/音高重新生成即可；若素材缺失或播放失败，自动回退到 AudioTrack
 * 实时合成，保证功能永不中断。
 *
 * 对外接口（reward / star / correction / cheer / tick / complete）保持不变，调用方无感知。
 */
class SoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var soundOn = true
    private val vibrator = runCatching {
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }.getOrNull()

    private companion object { const val TAG = "SoundPlayer" }

    fun setEnabled(on: Boolean) { soundOn = on }

    /** 优先播放素材文件；创建或播放失败时执行 fallback（合成音兜底）。 */
    private fun play(@RawRes resId: Int, fallback: () -> Unit) {
        if (!soundOn) return
        executor.execute {
            try {
                val mp = MediaPlayer.create(appContext, resId) ?: run { fallback(); return@execute }
                mp.setOnErrorListener { _, _, _ -> mp.release(); runCatching { fallback() }; true }
                mp.setOnCompletionListener { it.release() }
                mp.start()
            } catch (e: Exception) {
                runCatching { fallback() }
            }
        }
    }

    private fun vibrate() {
        vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(120)
        }
    }

    // ===== 对外接口：素材优先，失败回退合成 =====
    fun reward() { if (soundOn) vibrate(); play(R.raw.snd_reward) { synthReward() } }
    fun star() = play(R.raw.snd_star) { synthStar() }
    fun correction() = play(R.raw.snd_correction) { synthCorrection() }
    fun cheer() = play(R.raw.snd_cheer) { synthCheer() }
    fun tick() = play(R.raw.snd_tick) { synthTick() }
    fun complete() { if (soundOn) vibrate(); play(R.raw.snd_complete) { synthComplete() } }

    /** 成就解锁专属旋律：上行大调琶音 + 高音闪光，比普通 star 更「隆重」。 */
    fun badge() = runSynth { vibrate(); synthBadge() }
    /** 连续打卡里程碑（如 7 天大奖章）专属号角：更长更辉煌的胜利旋律。 */
    fun fanfare() = runSynth { vibrate(); synthFanfare() }

    /** 仅走合成路径（新旋律暂无音频素材，直接合成，避免引用不存在的 raw 资源）。 */
    private fun runSynth(block: () -> Unit) {
        if (!soundOn) return
        // 兜底：合成在后台线程执行，任何异常都不能冒泡成崩溃
        executor.execute { runCatching { block() } }
    }

    // ===== 合成回退（保留原 AudioTrack 实现，零素材兜底）=====
    private fun tone(freq: Float, ms: Int, gain: Float = 0.3f) {
        if (!soundOn) return
        val sr = 44100
        val n = (sr * ms / 1000f).toInt().coerceAtLeast(1)
        val buf = ShortArray(n)
        val inc = 2 * Math.PI * freq / sr
        val attack = (n * 0.1f).toInt().coerceAtLeast(1)
        val release = (n * 0.2f).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            val env = when {
                i < attack -> i.toFloat() / attack
                i > n - release -> (n - i).toFloat() / release
                else -> 1f
            }
            val s = kotlin.math.sin(inc * i) * gain * env
            buf[i] = (s * 32767).toInt().coerceIn(-32767, 32767).toShort()
        }
        // 双路径兜底：先走静态模式（精确长度、开销最小），失败再走流模式（兼容性最好）。
        // 两条都失败时只打日志，绝不把异常抛到调用线程。
        if (writeAndPlay(buf, n, ms, stream = false)) return
        if (writeAndPlay(buf, n, ms, stream = true)) return
        Log.w(TAG, "tone failed: no usable AudioTrack path (freq=$freq, ms=$ms)")
    }

    /** 单声道 16bit PCM 的最小缓冲区字节数（获取失败时按 100ms 估算兜底）。 */
    private fun minBufferBytes(sr: Int): Int {
        val min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return if (min > 0) min else sr / 10 * 2
    }

    /**
     * 写出并播放一段 PCM 数据，成功返回 true。
     *
     * 关键点：MODE_STATIC 下必须显式给出缓冲区字节数，否则部分 ROM（如 EMUI）的
     * AudioTrack.Builder 会因 bufferSize=0 抛 UnsupportedOperationException: Invalid audio buffer size.
     * 这里统一用经典构造函数（内部自带 audioBuffSizeCheck，兼容性优于 Builder），
     * 且缓冲区大小取「数据实际字节数」与「系统最小缓冲」的较大值。
     */
    private fun writeAndPlay(buf: ShortArray, n: Int, ms: Int, stream: Boolean): Boolean {
        val sr = 44100
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sr)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        // 16bit 单声道：1 采样 = 2 字节；流模式需要更大的缓冲区以容纳整段数据
        val bufferBytes = if (stream) (n * 2).coerceAtLeast(minBufferBytes(sr)) else (n * 2).coerceAtLeast(1024)
        val mode = if (stream) AudioTrack.MODE_STREAM else AudioTrack.MODE_STATIC

        var track: AudioTrack? = null
        return try {
            track = AudioTrack(attrs, fmt, bufferBytes, mode, AudioManager.AUDIO_SESSION_ID_GENERATE)
            if (track.state != AudioTrack.STATE_INITIALIZED) return false
            // 静态模式：一次性写完再播放；流模式：先 play 再写入，由 AudioTrack 阻塞缓冲
            if (!stream) track.write(buf, 0, n)
            track.play()
            if (stream) track.write(buf, 0, n)
            // 等待播放结束（留 600ms 余量，避免尾部被截断）
            var guard = 0
            while (track.playState == AudioTrack.PLAYSTATE_PLAYING && guard < ms + 600) {
                Thread.sleep(20)
                guard += 20
            }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "writeAndPlay failed(stream=$stream): ${e.message}")
            false
        } finally {
            runCatching { track?.stop() }
            runCatching { track?.release() }
        }
    }

    private fun synthJingle(notes: List<Pair<Float, Int>>) {
        for ((f, d) in notes) {
            tone(f, d, 0.3f)
            Thread.sleep((d * 0.15).toLong())
        }
    }

    private fun synthReward() = synthJingle(listOf(523.25f to 110, 659.25f to 110, 783.99f to 110, 1046.5f to 220))
    private fun synthStar() = synthJingle(listOf(987.77f to 90, 1318.5f to 160))
    private fun synthCorrection() = synthJingle(listOf(440f to 160, 349.23f to 220))
    private fun synthCheer() = synthJingle(listOf(587.33f to 100, 698.46f to 100, 880f to 180))
    private fun synthTick() = tone(1200f, 40, 0.18f)
    private fun synthComplete() = synthJingle(listOf(523.25f to 140, 659.25f to 140, 783.99f to 140, 1046.5f to 140, 783.99f to 140, 1046.5f to 420))
    private fun synthBadge() = synthJingle(listOf(
        523.25f to 120, 659.25f to 120, 783.99f to 120, 1046.5f to 220, 1318.5f to 320
    ))
    private fun synthFanfare() = synthJingle(listOf(
        392f to 150, 523.25f to 150, 659.25f to 150, 783.99f to 220,
        659.25f to 120, 783.99f to 150, 1046.5f to 380
    ))
}
