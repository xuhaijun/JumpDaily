package com.jumpdaily.jump.audio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音播报封装：训练时朗读中文鼓励语，提升儿童跳绳乐趣。
 *
 * 两条音色来源（自动择优）：
 * 1) 离线语音包：assets/voice/<sha1hex>.<ext> 预生成音频。任何手机播出的音色都完全一致、
 *    不依赖厂商 TTS 引擎，离线即用。缺某条则回退到 2)。
 *    - 静态句（不含 {n}）直接整句播放；
 *    - 动态句（含 {n}，如里程碑/完成语）拆成「前缀 + 中文数字 + 后缀」三段拼接播放，
 *      数字由 assets/voice/num_<字>.wav 逐个拼出，整句仍是同一套童音。
 * 2) 系统 TextToSpeech：跨厂商自动选用当前设备引擎（华为/小米/Google/三星…），
 *    并挑最优中文语音 + 高音高兜底「卡哇伊童音」。
 *
 * 离线引擎增强：检测到 eSpeak NG（免费开源、离线、支持中文、注册为系统 TTS 引擎）时，
 * 可在设置页开启「离线优先」，强制用 eSpeak，换任何手机都是同一套离线中文音色。
 * （注：RHVoice 官方不支持中文，故不采用。）
 *
 * 设计要点：
 * - Application 级单例（AppContainer 提供），整个 App 生命周期只持有一个 TTS 引擎，避免反复创建/泄漏。
 * - 初始化异步（onInit 回调）；初始化完成前调用 speak 的句子进队列，就绪后统一补播（最多 3 句）。
 * - 用 QUEUE_FLUSH 播报：新一句替换正在播的，避免多句重叠；离线语音包用单一 MediaPlayer 保证互斥。
 * - 语音可开关（setEnabled）；设备缺中文语音包时静默降级（音效仍正常生效）。
 */
class VoiceSpeaker(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var voiceOn = true
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var preferOffline = false
    private val ready = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val pending = ArrayDeque<String>()

    /** 当前 TTS 引擎与所选语音摘要（供设置页展示 / 跨设备诊断）。 */
    @Volatile private var engineInfo = "初始化中"

    /** assets/voice 目录是否存在（决定离线语音包路径是否可用）。 */
    @Volatile private var assetReady = false

    /** 离线语音包文件名集合（assets/voice 下），用于快速命中判断，避免反复 openFd 探测。 */
    @Volatile private var assetNames: Set<String> = emptySet()

    /** 离线语音包播放用的单一 MediaPlayer，保证多句互斥（类似 QUEUE_FLUSH）。 */
    @Volatile private var mediaPlayer: MediaPlayer? = null

    private companion object {
        const val TAG = "VoiceSpeaker"
        /** eSpeak NG：免费开源、离线、支持中文、注册为系统 TTS 引擎；跨厂商一致，是「离线语音包」首选补充引擎。 */
        const val ESPEAK_PKG = "com.reecedunn.espeak"
        const val ESPEAK_FDROID = "https://f-droid.org/en/packages/com.reecedunn.espeak/"
        /** 离线语音包支持的扩展名（按此顺序探测 assets/voice/<sha1>.<ext>）。 */
        val VOICE_EXTS = listOf("ogg", "mp3", "wav")

        /**
         * 把整数转成中文数字串，每位对应 assets/voice/num_<字>.wav。
         * 支持 1 ~ 99999999；超范围返回 null（交由系统 TTS 兜底）。
         * 例：15->"十五"，100->"一百"，105->"一百零五"，12345->"一万二千三百四十五"。
         */
        private fun toChineseNum(n: Int): String? {
            if (n <= 0) return null           // 跳绳计数不会 <= 0；兜底 TTS
            if (n >= 100_000_000) return null // 没有「亿」音频
            // 拆成 4 位一节（个节、万节）
            val sections = ArrayList<Int>()
            var x = n
            while (x > 0) { sections.add(x % 10000); x /= 10000 }
            val bigUnits = arrayOf("", "万")
            val sb = StringBuilder()
            for (i in sections.size - 1 downTo 0) {
                val sec = sections[i]
                if (sec == 0) continue
                val secStr = fourDigits(sec)
                // 节间补「零」：上一节已有内容、本节 < 1000、且末尾非「零」时
                if (sb.isNotEmpty() && sec < 1000 && sb.last() != '零') sb.append('零')
                sb.append(secStr).append(bigUnits[i])
            }
            return if (sb.isEmpty()) null else sb.toString()
        }

        /** 把 [1,9999] 的整数转成中文（不带「万」）。 */
        private fun fourDigits(section: Int): String {
            val digits = "零一二三四五六七八九"
            val units = arrayOf("千", "百", "十", "")
            val sb = StringBuilder()
            var needZero = false
            var value = section
            for (i in 0..3) {
                val weight = intArrayOf(1000, 100, 10, 1)[i]
                val d = value / weight
                value %= weight
                if (d == 0) {
                    if (sb.isNotEmpty()) needZero = true
                } else {
                    if (needZero) { sb.append('零'); needZero = false }
                    // 口语：10~19 的「十」前不加「一」（如 "十五" 而非 "一十五"）
                    val isLeadingTen = (weight == 10 && d == 1 && section < 20)
                    if (!isLeadingTen) sb.append(digits[d])
                    sb.append(units[i])
                }
            }
            return sb.toString()
        }
    }

    init {
        // 预热 assets/voice 可读性（目录不存在也不会崩溃，仅关闭离线包路径）
        runCatching {
            val names = appContext.assets.list("voice")
            assetReady = names != null
            // 关键：assets.list 返回的是【不带目录前缀】的文件名（如 "abc.wav"），
            // 而 resolveAssetName 用 "voice/abc.wav" 做匹配。这里必须手动补上 "voice/" 前缀，
            // 否则缓存永远匹配不上，离线语音包会静默回退系统 TTS（真机踩过的坑）。
            assetNames = names?.map { "voice/$it" }?.toSet() ?: emptySet()
            appendDiag("asset_voice_count=${names?.size ?: -1}")
            appendDiag("asset_prefix_fixed sample=${assetNames.firstOrNull() ?: "none"}")
        }
        buildTts(null)
    }

    /** 创建 TTS 实例；engine 为 null 用系统默认引擎，否则用指定引擎包（如 eSpeak）。 */
    private fun buildTts(engine: String?) {
        runCatching {
            tts = if (engine == null) TextToSpeech(appContext, onInit())
            else TextToSpeech(appContext, onInit(), engine)
            appendDiag("constructed engine=${engine ?: "default"}")
        }.onFailure { e -> appendDiag("construct_failed ${e.message}"); Log.e(TAG, "TTS 引擎创建异常（设备可能缺少 TTS 引擎）", e) }
    }

    /** 每次新建一个 OnInitListener 实例（华为/D8 下存成 val 属性会导致回调不触发，内联/新建实例正常）。 */
    private fun onInit() = TextToSpeech.OnInitListener { status ->
        appendDiag("oninit status=$status")
        runCatching {
            if (status == TextToSpeech.SUCCESS) {
                // 优先简体中文；不可用则回退到系统默认（中文系统上默认即中文）
                val engine = tts?.defaultEngine ?: "未知"
                val r = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                appendDiag("init_ok lang=$r engine=$engine")
                Log.i(TAG, "TTS 初始化成功, 引擎=$engine, setLanguage(简体中文)=$r")
                if (r != TextToSpeech.LANG_AVAILABLE && r != TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                    val r2 = tts?.setLanguage(Locale.getDefault())
                    appendDiag("init_ok_fallback lang=$r2")
                    Log.i(TAG, "中文包不可用, 回退 setLanguage(系统默认)=$r2")
                }
                // 跨设备兼容：各家默认引擎（华为/小米/Google/三星/OPPO…）自带语音列表不同，
                // 统一打分挑「儿童>女声>任意中文>离线优先」的语音；挑不到则靠高音高(1.45)兜底。
                val pickedName = runCatching {
                    val zh = tts?.voices?.filter { it.locale.language == "zh" } ?: emptyList()
                    appendDiag("zh_voices=${zh.size}")
                    zh.mapNotNull { v -> scoreVoice(v)?.let { s -> s to v } }
                        .maxByOrNull { it.first }
                        ?.let { (_, v) ->
                            tts?.voice = v
                            Log.i(TAG, "选用语音=${v.name}")
                            v.name
                        }
                }.getOrElse { e -> Log.w(TAG, "语音选择失败，沿用默认引擎语音", e); null }
                val tag = if (preferOffline) "离线" else "系统"
                engineInfo = "引擎=$engine($tag) 语音=${pickedName ?: "默认(高音高兜底)"}"
                appendDiag("engine_info=$engineInfo")
                // 卡哇伊童音：高音高(1.45，比默认更尖细可爱) + 正常语速
                tts?.setPitch(1.45f)
                tts?.setSpeechRate(1.0f)
                ready.set(true)
                Log.i(TAG, "TTS 就绪, $engineInfo")
                drain()
            } else {
                appendDiag("init_failed status=$status")
                Log.e(TAG, "TTS 初始化失败 status=$status（设备可能缺少可用 TTS 引擎）")
            }
        }.onFailure { e -> Log.e(TAG, "TTS 初始化回调异常", e) }
    }

    /** 把 TTS 初始化结果落盘到外部文件目录，便于在 logcat 不可靠时确认引擎就绪情况。 */
    private fun appendDiag(line: String) {
        runCachingDiag(line)
    }

    private fun runCachingDiag(line: String) {
        runCatching {
            val dir = appContext.getExternalFilesDir(null) ?: return@runCatching
            val f = File(dir, "tts_init.txt")
            f.appendText("${System.currentTimeMillis()} $line\n")
            f.setReadable(true, false) // 设为全局可读，便于 adb shell 远程读取确认
        }
    }

    fun setEnabled(on: Boolean) { voiceOn = on }

    /** 朗读一句话：优先离线语音包（音色一致），缺失则回退系统 TTS。emoji 会被 TTS 忽略。 */
    fun speak(text: String) {
        if (!voiceOn || text.isBlank()) return
        executor.execute {
            // 1) 离线语音包：任意手机音色一致；命中即播放并结束
            if (assetReady && playAsset(text)) return@execute
            // 2) 回退系统 TTS
            val t = tts
            if (t == null || !ready.get()) {
                // 初始化未完成：先入队（最多 3 句，丢弃最旧的）
                synchronized(pending) {
                    pending.addLast(text)
                    while (pending.size > 3) pending.removeFirst()
                }
                return@execute
            }
            runCatching { t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jd_${System.nanoTime()}") }
        }
    }

    /**
     * 朗读带 {n} 占位符的句子：用离线语音包「前缀 + 中文数字 + 后缀」拼接，整句同一音色。
     * 离线片段缺失时回退系统 TTS（把 {n} 替换为真实数字）。
     */
    fun speak(template: String, number: Int) {
        if (!voiceOn || template.isBlank()) return
        executor.execute {
            if (assetReady && playTemplateAsset(template, number)) return@execute
            // 离线片段不齐：回退系统 TTS（童音兜底）
            val finalText = template.replace("{n}", number.toString())
            val t = tts
            if (t == null || !ready.get()) {
                synchronized(pending) {
                    pending.addLast(finalText)
                    while (pending.size > 3) pending.removeFirst()
                }
                return@execute
            }
            runCatching { t.speak(finalText, TextToSpeech.QUEUE_FLUSH, null, "jd_${System.nanoTime()}") }
        }
    }

    /**
     * 只报数字、不带单位「个」：用于满整十时的干脆报数（跳到 30 就只念「三十」）。
     * 走离线 num_X.wav 拼接，音色与整句一致；片段缺失时回退系统 TTS 念阿拉伯数字。
     */
    fun speakNumber(number: Int) {
        if (!voiceOn) return
        executor.execute {
            if (assetReady && playNumberAsset(number)) return@execute
            val t = tts
            if (t == null || !ready.get()) {
                synchronized(pending) {
                    pending.addLast(number.toString())
                    while (pending.size > 3) pending.removeFirst()
                }
                return@execute
            }
            runCatching { t.speak(number.toString(), TextToSpeech.QUEUE_FLUSH, null, "jd_${System.nanoTime()}") }
        }
    }

    /** 拼接播放纯数字（num_X.wav 序列）；全部片段命中返回 true。 */
    private fun playNumberAsset(number: Int): Boolean {
        val cn = toChineseNum(number) ?: return false
        val names = mutableListOf<String>()
        for (ch in cn) {
            val n = resolveAssetName("num_$ch") ?: return false
            names.add(n)
        }
        appendDiag("play_num n=$number cn=$cn")
        playListSequential(names, 0)
        return true
    }

    /** 尝试拼接播放动态句（前缀 + 数字 + 后缀）。全部片段命中返回 true。 */
    private fun playTemplateAsset(template: String, number: Int): Boolean {
        val parts = template.split("{n}", limit = 2)
        val pre = parts[0]
        val suf = if (parts.size > 1) parts[1] else ""
        val segs = mutableListOf<String>()
        if (pre.isNotEmpty()) segs.add(pre)
        val cn = toChineseNum(number) ?: return false // 数字超出音频范围，回退 TTS
        for (ch in cn) segs.add("num_$ch")
        if (suf.isNotEmpty()) segs.add(suf)
        val names = mutableListOf<String>()
        for (seg in segs) {
            val n = resolveAssetName(seg) ?: return false // 任一片段缺失即回退
            names.add(n)
        }
        appendDiag("play_seq n=${names.size} tmpl=$template")
        playListSequential(names, 0)
        return true
    }

    /** 尝试播放离线语音包；命中返回 true。任意手机音色完全一致。 */
    private fun playAsset(text: String): Boolean {
        val name = resolveAssetName(text) ?: return false
        appendDiag("play_asset $name")
        playListSequential(listOf(name), 0)
        return true
    }

    /** 解析某段为 assets 路径：num_X -> voice/num_X.wav；其它文本 -> voice/<sha1>.<ext>。找不到返回 null。 */
    private fun resolveAssetName(part: String): String? {
        if (part.startsWith("num_")) {
            val candidate = "voice/$part.wav"
            return if (candidate in assetNames) candidate else null
        }
        val key = sha1(part)
        for (ext in VOICE_EXTS) {
            val candidate = "voice/$key.$ext"
            if (candidate in assetNames) return candidate
        }
        return null
    }

    /**
     * 顺序播放已解析的 assets 路径列表（互斥，类似 QUEUE_FLUSH）。
     * 靠 setOnCompletionListener 链式播下一句；任一段出错则停止整段。
     * 每个片段独立 openFd，播放完（completion/error）才 close，避免 fd 被提前回收导致播放失败。
     */
    private fun playListSequential(names: List<String>, index: Int) {
        if (index >= names.size) return
        val name = names[index]
        val afd = runCatching { appContext.assets.openFd(name) }.getOrNull() ?: return
        val mp = MediaPlayer()
        // 停止并释放上一句，保证互斥
        mediaPlayer?.let { runCatching { it.stop() }; runCatching { it.release() } }
        mediaPlayer = mp
        try {
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        } catch (e: Exception) {
            runCatching { afd.close() }
            runCatching { mp.release() }
            mediaPlayer = null
            return
        }
        mp.setOnCompletionListener {
            runCatching { afd.close() }
            runCatching { it.release() }
            if (mediaPlayer === it) mediaPlayer = null
            playListSequential(names, index + 1) // 播下一句
        }
        mp.setOnErrorListener { mp2, _, _ ->
            runCatching { afd.close() }
            runCatching { mp2.release() }
            if (mediaPlayer === mp2) mediaPlayer = null
            true
        }
        runCatching { mp.prepare(); mp.start() }
    }

    /** 文本 -> SHA-1 十六进制，用作语音包文件名，确保「同一句同一音频」。 */
    private fun sha1(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** 初始化就绪后，把排队期间积压的句子补播出去。 */
    private fun drain() {
        val t = tts ?: return
        while (true) {
            val text = synchronized(pending) { if (pending.isEmpty()) null else pending.removeFirst() } ?: return
            runCatching { t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jd_${System.nanoTime()}") }
        }
    }

    /**
     * 给一个中文语音打分，分越高越优先：儿童音 > 女声 > 任意中文音；需要联网的语音降权（优先离线）。
     * 返回 null 表示该语音完全不适合（非中文且无儿童/女声特征）。
     */
    private fun scoreVoice(v: Voice): Int? {
        val n = v.name.lowercase()
        var score = 1
        if (v.locale.language == "zh") score += 2
        score += when {
            "儿童" in n || "child" in n || "kid" in n -> 8
            "女" in n || "female" in n || "woman" in n || "girl" in n -> 5
            else -> 0
        }
        if (v.isNetworkConnectionRequired) score -= 3 // 联网语音降权，优先本地已安装的
        return score
    }

    /** 设备是否已安装 eSpeak NG 离线中文引擎。 */
    fun espeakAvailable(): Boolean =
        runCatching { appContext.packageManager.getPackageInfo(ESPEAK_PKG, 0) != null }.getOrDefault(false)

    /**
     * 设置「离线优先」：开启且已安装 eSpeak 时，用 eSpeak 引擎重新初始化，
     * 换任何手机都是同一套离线中文音色；未安装则忽略（保持系统 TTS）。
     */
    fun setPreferOffline(on: Boolean) {
        preferOffline = on
        if (!on) return
        if (!espeakAvailable()) {
            appendDiag("prefer_offline skipped: espeak 未安装")
            Log.i(TAG, "离线优先已开，但 eSpeak 未安装，维持系统 TTS")
            return
        }
        runCatching { tts?.shutdown() }
        ready.set(false)
        buildTts(ESPEAK_PKG)
    }

    /** 当前 TTS 引擎与所选语音摘要（供设置页展示 / 跨设备诊断）。 */
    fun engineInfo(): String = engineInfo

    /**
     * 引导用户去系统下载/安装 TTS 语音包（含中文）。
     * 调用系统 [TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA]，跳转厂商语音下载页
     * （华为/小米/Google/三星…各不相同）；装好后下次启动即可用更自然的中文语音，
     * 解决「换非本机、缺中文语音包」时只能靠高音高兜底的问题。
     */
    fun requestVoiceDataInstall(context: Context) {
        runCatching {
            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { e -> Log.w(TAG, "跳转语音包下载失败", e) }
    }

    /** 跳转 F-Droid 下载 eSpeak NG 离线中文引擎（深链）。 */
    fun requestEspeakInstall(context: Context) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ESPEAK_FDROID))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { e -> Log.w(TAG, "跳转 eSpeak 下载失败", e) }
    }

    fun shutdown() {
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
    }
}
