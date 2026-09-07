package com.jumpdaily.jump.ui.viewmodel

import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jumpdaily.jump.audio.Encouragements
import com.jumpdaily.jump.audio.SoundPlayer
import com.jumpdaily.jump.audio.VoicePriority
import com.jumpdaily.jump.audio.VoiceSpeaker
import com.jumpdaily.jump.data.local.entities.JumpRecord
import com.jumpdaily.jump.data.repository.JumpRepository
import com.jumpdaily.jump.data.model.CountMode
import com.jumpdaily.jump.data.model.Rewards
import com.jumpdaily.jump.data.repository.PreferencesRepository
import com.jumpdaily.jump.sensor.JumpDetector
import com.jumpdaily.jump.sensor.JumpListener
import com.jumpdaily.jump.ui.components.DanmakuEvent
import com.jumpdaily.jump.ui.components.Feedback
import com.jumpdaily.jump.ui.components.FeedbackType
import com.jumpdaily.jump.ui.components.MascotState
import com.jumpdaily.jump.util.DAY_MS
import com.jumpdaily.jump.util.dayStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 一次训练的结果摘要。 */
data class TrainingResult(
    val count: Int,
    val durationSec: Int,
    val calories: Int,
    val maxStreak: Int,
    val avgCadence: Int,
    /** 本次训练获得的积分（结果页展示，给孩子「这一趟赚了多少」的直观成就感）。 */
    val points: Int = 0
)

/**
 * 训练页 ViewModel：驱动传感器计数、实时反馈与结果保存。
 *
 * 反馈策略：
 * - 每累计 5 个 => 报数庆祝（彩带 + 音效 + 吉祥物开心 + 语音播报「已经跳了 N 个」）
 * - 频率偏低（<45 个/分）=> 加油鼓励
 * - 陀螺仪检测到挥手机 => 动作不规范纠正
 * - 暂停时传感器与计时一起停，恢复后续算（计时不在暂停期间累加）
 */
class TrainingViewModel(
    private val sensorManager: SensorManager,
    private val soundPlayer: SoundPlayer,
    private val voiceSpeaker: VoiceSpeaker,
    private val jumpRepository: JumpRepository,
    private val prefs: PreferencesRepository
) : ViewModel() {

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    private val _elapsed = MutableStateFlow(0)
    val elapsed: StateFlow<Int> = _elapsed.asStateFlow()

    private val _cadence = MutableStateFlow(0)
    val cadence: StateFlow<Int> = _cadence.asStateFlow()

    /** 当前连击数（连续不间断跳的个数）；UI 在连击较高时显示「🔥 连击 x N」，让连击奖励看得见。 */
    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _mascot = MutableStateFlow(MascotState.IDLE)
    val mascot: StateFlow<MascotState> = _mascot.asStateFlow()

    private val _feedback = MutableStateFlow<Feedback?>(null)
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()

    private val _confetti = MutableStateFlow(false)
    val confetti: StateFlow<Boolean> = _confetti.asStateFlow()

    private val _result = MutableStateFlow<TrainingResult?>(null)
    val result: StateFlow<TrainingResult?> = _result.asStateFlow()

    /** 本次训练是否达成了「每日目标」（用于结果页专属庆祝）。 */
    private val _goalReached = MutableStateFlow(false)
    val goalReached: StateFlow<Boolean> = _goalReached.asStateFlow()

    /** 当前孩子的「每日目标」，用于训练页环形进度与中途达标庆祝。 */
    private val _dailyGoal = MutableStateFlow(0)
    val dailyGoal: StateFlow<Int> = _dailyGoal.asStateFlow()

    // ===== 积分与奖励（儿童向即时正反馈，见 data/model/Rewards.kt） =====

    /** 本次训练已获得的积分（内存累计，UI 实时显示）。 */
    private val _sessionPoints = MutableStateFlow(0)
    val sessionPoints: StateFlow<Int> = _sessionPoints.asStateFlow()

    /** 该孩子跨训练累计的总积分。 */
    private val _totalPoints = MutableStateFlow(0)
    val totalPoints: StateFlow<Int> = _totalPoints.asStateFlow()

    /** 弹幕飘屏事件（「+10」、奖品解锁等），UI 收集后消费。 */
    private val _danmaku = MutableStateFlow<List<DanmakuEvent>>(emptyList())
    val danmaku: StateFlow<List<DanmakuEvent>> = _danmaku.asStateFlow()

    /** 待落盘的积分增量：每跳都写 DataStore 太重，攒一段时间批量 flush。 */
    private var pendingPoints = 0
    private var flushJob: Job? = null
    private var pointsJob: Job? = null
    /** 已发过奖的连击档位，避免同一连击数反复加分。 */
    private val comboAwarded = mutableSetOf<Int>()
    /** 已解锁并写入 DataStore 的奖品 id（避免重复弹）。 */
    private val earnedPrizeIds = mutableSetOf<String>()
    private var danmakuSeq = 0L

    private val _sensitivity = MutableStateFlow(1.0f)
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** 当前会话的计数方式（start 时记录）；null = 无会话。浮条「继续」据此回到正确的训练页。 */
    private val _sessionMode = MutableStateFlow<CountMode?>(null)
    val sessionMode: StateFlow<CountMode?> = _sessionMode.asStateFlow()
    private var childId: Long? = null
    private var lastJumpTs = 0L
    private var currentStreak = 0
    private var maxStreak = 0
    private var lastFeedbackTs = 0L
    private var ticker: Job? = null
    /** 本次训练是否已触发过「中途达标」庆祝，避免重复弹。 */
    private var goalCelebrated = false

    private val detector = JumpDetector(
        sensorManager,
        object : JumpListener {
            override fun onJump() { this@TrainingViewModel.onJump() }
            override fun onCadence(c: Int) { this@TrainingViewModel.onCadence(c) }
            override fun onFormIssue() { this@TrainingViewModel.onFormIssue() }
        },
        1.0f
    )

    init {
        viewModelScope.launch { prefs.soundEnabled.collect { soundPlayer.setEnabled(it) } }
        viewModelScope.launch { prefs.voiceEnabled.collect { voiceSpeaker.setEnabled(it) } }
        viewModelScope.launch {
            prefs.sensitivity.collect { v ->
                _sensitivity.value = v
                if (!_running.value) detector.setSensitivity(v)
            }
        }
        viewModelScope.launch { prefs.dailyGoal.collect { _dailyGoal.value = it } }
    }

    fun start(childId: Long, mode: CountMode = CountMode.SENSOR) {
        this.childId = childId
        _sessionMode.value = mode
        // 开新会话 = 旧的挂起快照作废（跨进程恢复弹框不再出现）
        viewModelScope.launch { prefs.clearSuspendedSession() }
        detector.setSensitivity(_sensitivity.value)
        _count.value = 0
        _elapsed.value = 0
        _cadence.value = 0
        _streak.value = 0
        maxStreak = 0
        currentStreak = 0
        lastJumpTs = 0
        lastFeedbackTs = 0
        _result.value = null
        _confetti.value = false
        _feedback.value = null
        _goalReached.value = false
        _paused.value = false
        _running.value = true
        goalCelebrated = false
        // 积分/奖励状态复位
        _sessionPoints.value = 0
        _danmaku.value = emptyList()
        comboAwarded.clear()
        pendingPoints = 0
        // 订阅该孩子总积分（积分 flush 落盘后会自动回流刷新）
        pointsJob?.cancel()
        pointsJob = viewModelScope.launch { prefs.points(childId).collect { _totalPoints.value = it } }
        // 已解锁奖品基线：避免把历史奖品在本次训练里重复弹一遍
        viewModelScope.launch {
            earnedPrizeIds.clear()
            earnedPrizeIds.addAll(prefs.earnedPrizes(childId).first())
        }
        startFlushLoop()
        if (mode == CountMode.SENSOR) detector.start()
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (!_running.value) break
                _elapsed.value += 1
            }
        }
        voiceSpeaker.speak(pick("start", startPool).display, VoicePriority.START)
    }

    fun stop() {
        // 注意：暂停期间 _running 也是 false，但会话仍在继续，必须允许「结束并保存」，
        // 否则孩子在暂停状态下点结束会静默丢弃整次成绩。
        if (!_running.value && !_paused.value) return
        detector.stop()
        ticker?.cancel()
        pointsJob?.cancel()
        flushJob?.cancel()
        // 会话正式结束：跨进程挂起快照作废（若残留）
        viewModelScope.launch { prefs.clearSuspendedSession() }
        // 把训练末尾攒着的积分一并落盘，否则最后几分会丢
        viewModelScope.launch { flushPoints() }
        _running.value = false
        _paused.value = false
        val c = _count.value
        val dur = _elapsed.value
        val cid = childId
        if (c > 0 && cid != null) {
            val calories = (c * 0.12).toInt()
            val avgCad = if (dur > 0) (c * 60 / dur) else 0
            viewModelScope.launch {
                // 落库前先取当天已计数，用于判断「本次训练是否跨过每日目标线」
                val beforeToday = jumpRepository.todayCount(cid).first()
                jumpRepository.save(
                    JumpRecord(
                        childId = cid, count = c, durationSec = dur, calories = calories,
                        maxStreak = maxStreak, avgCadence = avgCad,
                        date = dayStart(System.currentTimeMillis())
                    )
                )
                val afterToday = jumpRepository.todayCount(cid).first()
                val goal = prefs.dailyGoal.first()
                // 之前未达标、本次后才达标 => 触发专属庆祝
                if (goal > 0 && afterToday >= goal && beforeToday < goal) {
                    _goalReached.value = true
                    soundPlayer.reward()
                }
            }
            _result.value = TrainingResult(c, dur, calories, maxStreak, avgCad, _sessionPoints.value)
            val completeMsg = pick("complete", completePool, c)
            // 等「达成目标」音效（若有）先响完再播完成语，避免两段声音叠在一起
            fxThenVoice(600) { voiceSpeaker.speak(completeMsg.template, c, VoicePriority.CELEBRATE) }
        } else {
            _result.value = TrainingResult(0, dur, 0, 0, 0)
        }
    }

    /** 摄像头姿态计数回调入口：转发到统一的计数 / 节奏反馈逻辑。 */
    fun onPoseJump() = onJump()
    fun onPoseCadence(c: Int) = onCadence(c)

    fun clearResult() { _result.value = null; _goalReached.value = false }

    /** 暂停：停止传感器与计时，保留已计数；并把快照落盘，供冷启动后「继续/放弃」恢复。 */
    fun pause() {
        if (_running.value && !_paused.value) {
            detector.stop()
            ticker?.cancel()
            _running.value = false
            _paused.value = true
            // 落盘挂起快照（count=0 时 setSuspendedSession 内部会跳过，没啥可恢复的）
            val cid = childId
            val mode = _sessionMode.value
            if (cid != null && mode != null) {
                viewModelScope.launch {
                    prefs.setSuspendedSession(cid, _count.value, _elapsed.value, mode, _sessionPoints.value)
                }
            }
        }
    }

    /**
     * 跨进程恢复挂起会话（2026-09-04）：冷启动后用户在弹框选「继续」时调用。
     * 只还原状态不启动传感器/计时（保持挂起态），浮条显示上次的个数与时长，
     * 点浮条回到对应计数方式页后再 resume() 接着跳。
     */
    fun restoreSuspended(childId: Long, count: Int, elapsedSec: Int, mode: CountMode, sessionPoints: Int) {
        if (_running.value || _paused.value) return // 已有活会话，忽略
        this.childId = childId
        _sessionMode.value = mode
        _count.value = count
        _elapsed.value = elapsedSec
        _sessionPoints.value = sessionPoints
        _paused.value = true
        _running.value = false
    }

    /** 继续：恢复传感器与计时（计时在暂停期间不累加，恢复后接着算）。 */
    fun resume(childId: Long) {
        if (_paused.value) {
            detector.setSensitivity(_sensitivity.value)
            _running.value = true
            _paused.value = false
            detector.start()
            ticker?.cancel() // 兜底：确保旧计时协程已停，避免暂停/继续抖动时累加两个计时器
            ticker = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    if (!_running.value) break
                    _elapsed.value += 1
                }
            }
        }
    }

    private fun onJump() {
        // 暂停/未开始时不计数：摄像头模式下暂停期间页面分析器仍在跑，
        // 不加守卫会出现「暂停了还在偷偷涨数」的 bug（传感器模式已由 detector.stop 兜底）
        if (!_running.value) return
        val now = System.currentTimeMillis()
        val interval = now - lastJumpTs
        lastJumpTs = now
        val continued = interval in 1..1500
        currentStreak = if (continued) currentStreak + 1 else 1
        if (!continued) comboAwarded.clear() // 新开一段连击，档位奖励重新计
        if (currentStreak > maxStreak) maxStreak = currentStreak
        _streak.value = currentStreak
        _count.value += 1
        _mascot.value = MascotState.JUMPING
        viewModelScope.launch {
            delay(180)
            if (_mascot.value == MascotState.JUMPING) _mascot.value = MascotState.IDLE
        }
        // 每跳 1 个 +1 分：积分数字持续跳动，孩子随时看见「在涨」
        awardPoints(Rewards.POINT_PER_JUMP)

        when {
            // 满整十：+10 分 + 弹幕 + 奖励音效（积分与视觉反馈保持每 10 个，规则不变）
            _count.value % 10 == 0 -> {
                awardPoints(Rewards.BONUS_EVERY_TEN)
                pushDanmaku("+${Rewards.BONUS_EVERY_TEN}", "太棒啦")
                triggerReward("太棒啦！+10 🎉")
                // 报数改为每 20 个一次（2026-09-07）：120 个/分时约 10 秒一句。
                // 此前每 10 个报一次 = 每 5 秒一句，是全页最大的语音噪音源。
                // 优先级 COUNT：落在静默窗口内会被直接丢弃，绝不抢走鼓励语。
                if (_count.value % 20 == 0) {
                    fxThenVoice(400) { voiceSpeaker.speakNumber(_count.value, VoicePriority.COUNT) }
                }
            }
            // 2026-09-05：去掉「每 5 个里程碑语音播报」——训练中说话太频繁会打断跳绳节奏
            // （语音资源 milestonePool 保留备用，不再触发）
        }

        // 连击奖励：连续不间断跳到 20 / 50 / 100 时额外加分 + 弹幕喝彩
        Rewards.COMBO_BONUS.forEach { (need, bonus) ->
            if (currentStreak == need && comboAwarded.add(need)) {
                awardPoints(bonus)
                pushDanmaku("+$bonus", "连跳 $currentStreak 个不断", "🔥")
                fxThenVoice(fx = { soundPlayer.reward() }) {
                    voiceSpeaker.speak(pick("combo", comboPool, currentStreak).template, currentStreak, VoicePriority.MILESTONE)
                }
            }
        }

        // 跨过每日目标线：即时放彩带 + 吉祥物欢呼（仅庆祝一次）
        val goal = _dailyGoal.value
        if (goal > 0 && _count.value >= goal && !goalCelebrated) {
            goalCelebrated = true
            _goalReached.value = true
            awardPoints(Rewards.BONUS_DAILY_GOAL)
            pushDanmaku("+${Rewards.BONUS_DAILY_GOAL}", "达成今日目标", "🎯")
            celebrateGoal()
        }
    }

    // ===== 积分与奖励：发放 / 批量落盘 / 弹幕 =====

    /** 发放积分：切回主线程累加，避免与 flush 协程竞争 pendingPoints。 */
    private fun awardPoints(delta: Int) {
        if (delta <= 0) return
        viewModelScope.launch {
            pendingPoints += delta
            _sessionPoints.value += delta
        }
    }

    /** 启动积分批量落盘循环：每 3 秒把攒下的增量一次性写进 DataStore（避免每跳一次写一次磁盘）。 */
    private fun startFlushLoop() {
        flushJob?.cancel()
        flushJob = viewModelScope.launch {
            while (true) {
                delay(3000)
                flushPoints()
            }
        }
    }

    /** 把 pendingPoints 写入 DataStore，并顺带检查是否有奖品跨过门槛。 */
    private suspend fun flushPoints() {
        val delta = pendingPoints
        if (delta == 0) return
        pendingPoints = 0
        val cid = childId ?: return
        val total = prefs.addPoints(cid, delta)
        _totalPoints.value = total
        checkPrizeUnlock(total)
    }

    /**
     * 积分跨过奖品门槛时的「解锁」庆祝：弹幕 + 语音 + 音效，每个奖品只弹一次。
     * ⚠️ 这里写入的 earned_prizes 集合只用于庆祝去重，≠ 孩子真正兑换；
     * 真实兑换在「我的」页奖品墙手动确认，存到 redeemed_prizes（见 PreferencesRepository）。
     */
    private fun checkPrizeUnlock(total: Int) {
        val cid = childId ?: return
        Rewards.PRIZES.forEach { prize ->
            if (total >= prize.needPoints && earnedPrizeIds.add(prize.id)) {
                viewModelScope.launch { prefs.addPrize(cid, prize.id) }
                pushDanmaku(prize.name, "积分奖品解锁啦", prize.emoji)
                fxThenVoice(fx = { soundPlayer.reward() }) {
                    voiceSpeaker.speak(pick("prize", prizePool).display, VoicePriority.CELEBRATE)
                }
            }
        }
    }

    /**
     * 推一条弹幕飘屏。2026-09-07：并发上限由 4 条降到 2 条——
     * 整十奖励、连击、目标达成同时命中时会一次飘 4 条，横穿画面挡住孩子看自己。
     */
    private fun pushDanmaku(text: String, sub: String = "", emoji: String = "") {
        _danmaku.value = (_danmaku.value + DanmakuEvent(danmakuSeq++, text, sub, emoji)).takeLast(2)
    }

    /**
     * 音效 → 语音顺序播放（2026-09-04）：先响短音效，停顿 [delayMs] 后再播鼓励语，
     * 解决用户反馈的「加油声还没完就冒出另外的提示音」——此前音效与语音同帧触发，
     * SoundPool 音效会叠在长鼓励语上，听起来混作一团。
     */
    private fun fxThenVoice(delayMs: Long = 500, fx: (() -> Unit)? = null, voice: () -> Unit) {
        fx?.invoke()
        viewModelScope.launch {
            delay(delayMs)
            voice()
        }
    }

    /** UI 播完一条弹幕后回调，把它从队列移除。 */
    fun consumeDanmaku(id: Long) { _danmaku.value = _danmaku.value.filter { it.id != id } }

    // 节奏反馈的节流间隔（2026-09-07 拉长）：原值 4s/6s 太密，孩子刚被鼓励完下一句又压上来
    private companion object {
        /** 节奏偏慢时的加油间隔。 */
        const val CHEER_INTERVAL_MS = 10_000L
        /** 节奏很稳时的喝彩间隔。 */
        const val STEADY_INTERVAL_MS = 12_000L
    }

    private fun onCadence(c: Int) {
        _cadence.value = c
        if (!_running.value) return
        val now = System.currentTimeMillis()

        // ⚠️ 三条节奏反馈**必须共用 lastFeedbackTs**（2026-09-07）：
        // 此前礼物语用独立的 lastGiftTs，而节奏 120 同时落在 GOOD_CADENCE_RANGE(60~140)
        // 与 steady(>=110) 两个区间，两个计时器互不知情 → 每 12 秒必然两句同帧撞车，
        // 听感就是「一句把另一句掐断」。共用同一时间戳后三分支天然互斥，同一时刻只说一句。
        when {
            // 跳得又快又好：定期冒「小奖品 / 积分奖励」，让孩子越跳越有劲
            c in Rewards.GOOD_CADENCE_RANGE && now - lastFeedbackTs > Rewards.GOOD_CADENCE_INTERVAL_MS -> {
                lastFeedbackTs = now
                val gift = Rewards.QUICK_GIFTS.random()
                awardPoints(Rewards.BONUS_GOOD_CADENCE)
                pushDanmaku("+${Rewards.BONUS_GOOD_CADENCE}", gift.second, gift.first)
                // 优先级最低：静默窗口内直接丢弃，绝不打断正经鼓励语
                voiceSpeaker.speak(pick("prize", prizePool).display, VoicePriority.HINT)
            }
            // 节奏偏慢：加油打气
            c in 1..45 && now - lastFeedbackTs > CHEER_INTERVAL_MS -> {
                lastFeedbackTs = now
                val msg = pick("cheer", cheerSlowPool)
                triggerCheer(msg.display)
                fxThenVoice { voiceSpeaker.speak(msg.display, VoicePriority.COACH) }
            }
            // 节奏很稳：喝彩
            c >= 110 && now - lastFeedbackTs > STEADY_INTERVAL_MS -> {
                lastFeedbackTs = now
                val msg = pick("steady", steadyPool)
                triggerReward("节奏超稳！加油！")
                fxThenVoice { voiceSpeaker.speak(msg.display, VoicePriority.COACH) }
            }
        }
    }

    private fun onFormIssue() {
        if (!_running.value) return
        val now = System.currentTimeMillis()
        if (now - lastFeedbackTs > 4000) {
            lastFeedbackTs = now
            val msg = pick("correction", correctionPool)
            _feedback.value = Feedback(FeedbackType.CORRECTION, "${msg.display}🤔")
            fxThenVoice(fx = { soundPlayer.correction() }) { voiceSpeaker.speak(msg.display, VoicePriority.COACH) }
            _mascot.value = MascotState.SAD
            viewModelScope.launch {
                delay(900)
                if (_mascot.value == MascotState.SAD) _mascot.value = MascotState.IDLE
            }
        }
    }

    private fun triggerReward(text: String) {
        _feedback.value = Feedback(FeedbackType.REWARD, text)
        _confetti.value = true
        soundPlayer.reward()
        _mascot.value = MascotState.HAPPY
        viewModelScope.launch {
            delay(900)
            if (_mascot.value == MascotState.HAPPY) _mascot.value = MascotState.IDLE
        }
    }

    private fun triggerCheer(msg: String) {
        _feedback.value = Feedback(FeedbackType.CHEER, msg)
        soundPlayer.cheer()
        _mascot.value = MascotState.CHEER
        viewModelScope.launch {
            delay(900)
            if (_mascot.value == MascotState.CHEER) _mascot.value = MascotState.IDLE
        }
    }

    /** 训练中跨过每日目标：专属庆祝（彩带 + 欢呼 + 奖励音）。 */
    private fun celebrateGoal() {
        val msg = pick("goal", goalPool)
        _feedback.value = Feedback(FeedbackType.REWARD, "🎯 ${msg.display}")
        _confetti.value = true
        fxThenVoice(fx = { soundPlayer.reward() }) { voiceSpeaker.speak(msg.display, VoicePriority.CELEBRATE) }
        _mascot.value = MascotState.HAPPY
        viewModelScope.launch {
            delay(1200)
            if (_mascot.value == MascotState.HAPPY) _mascot.value = MascotState.IDLE
        }
    }

    fun consumeFeedback() { _feedback.value = null }
    fun consumeConfetti() { _confetti.value = false }

    // ===== 儿童鼓励语语料池（按场景分组，随机轮换，且避免连续两句重复） =====
    // 语料集中维护在 audio/Encouragements.kt，便于离线生成「语音包」；此处仅引用。
    // 注：milestonePool（每 5 个报数）已停用不再引用，语料与语音包资源仍保留备用。
    private val cheerSlowPool = Encouragements.cheerSlowPool
    private val steadyPool = Encouragements.steadyPool
    private val correctionPool = Encouragements.correctionPool
    private val goalPool = Encouragements.goalPool
    private val completePool = Encouragements.completePool
    private val comboPool = Encouragements.comboPool
    private val prizePool = Encouragements.prizePool
    private val startPool = Encouragements.startPool

    private val lastSpoken = mutableMapOf<String, String>()

    /** 一句鼓励语：template 含 {n} 占位（供 VoiceSpeaker 拼接离线数字音频），display 为最终展示文本。 */
    private data class Phrase(val template: String, val display: String)

    /**
     * 从语料池随机选一句；同一场景下避开上一句，避免孩子反复听到同一句话。
     * 返回的 template 保留 {n}（供 VoiceSpeaker 拼接离线数字音频），display 为替换后的展示文本。
     */
    private fun pick(scene: String, pool: List<String>, n: Int? = null): Phrase {
        val last = lastSpoken[scene]
        val candidates = if (pool.size > 1) pool.filter { it != last }.ifEmpty { pool } else pool
        val chosen = candidates.random()
        lastSpoken[scene] = chosen
        val display = n?.let { chosen.replace("{n}", it.toString()) } ?: chosen
        return Phrase(chosen, display)
    }

    override fun onCleared() {
        detector.stop()
        pointsJob?.cancel()
        flushJob?.cancel()
        super.onCleared()
    }

    class Factory(
        private val sensorManager: SensorManager,
        private val soundPlayer: SoundPlayer,
        private val voiceSpeaker: VoiceSpeaker,
        private val jumpRepository: JumpRepository,
        private val prefs: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TrainingViewModel::class.java))
                return TrainingViewModel(sensorManager, soundPlayer, voiceSpeaker, jumpRepository, prefs) as T
            throw IllegalArgumentException("Unknown VM: $modelClass")
        }
    }
}
