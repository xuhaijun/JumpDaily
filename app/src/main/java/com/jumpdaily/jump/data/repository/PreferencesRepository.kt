package com.jumpdaily.jump.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import com.jumpdaily.jump.data.model.CountMode
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 全局偏好：当前选中的孩子、提醒设置、音效开关、检测灵敏度。
 * 用 DataStore 持久化，替代 SharedPreferences，类型安全且可被 Compose 观察。
 */
private val Context.dataStore by preferencesDataStore(name = "jump_prefs")

/** 一周天数：每周目标 = 每日目标 × 该系数。 */
const val DAYS_PER_WEEK = 7
/** 每日跳绳目标默认值（个）。UI 与 DataStore 共用，避免两处默认值不一致。 */
const val DEFAULT_DAILY_GOAL = 600
/** 每周跳绳目标默认值（个）：每日 600 × 7。 */
const val DEFAULT_WEEKLY_GOAL = DEFAULT_DAILY_GOAL * DAYS_PER_WEEK

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val CURRENT_CHILD_ID = longPreferencesKey("current_child_id")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val VOICE_OFFLINE = booleanPreferencesKey("voice_offline")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val SEED_DONE = booleanPreferencesKey("seed_done")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val WEEKLY_GOAL = intPreferencesKey("weekly_goal")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val COUNT_MODE = stringPreferencesKey("count_mode")
        val PRIVACY_ACCEPTED = booleanPreferencesKey("privacy_accepted")
        val SEEN_BADGE_IDS = stringSetPreferencesKey("seen_badge_ids")
        val CELEBRATION_SEEDED = booleanPreferencesKey("celebration_seeded")
        val LAST_STREAK_MEDAL = intPreferencesKey("last_streak_medal")
    }

    val currentChildId: Flow<Long?> = context.dataStore.data.map { it[Keys.CURRENT_CHILD_ID] }

    val reminderEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.REMINDER_ENABLED] ?: false }
    val reminderHour: Flow<Int> = context.dataStore.data.map { it[Keys.REMINDER_HOUR] ?: 19 }
    val reminderMinute: Flow<Int> = context.dataStore.data.map { it[Keys.REMINDER_MINUTE] ?: 0 }

    val soundEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SOUND_ENABLED] ?: true }
    /** 语音播报（TTS 鼓励语）开关，默认开启。 */
    val voiceEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.VOICE_ENABLED] ?: true }
    /** 离线优先：开启后若已安装 eSpeak 则强制用离线中文引擎，任意手机音色一致。默认关闭（用系统 TTS）。 */
    val voiceOffline: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.VOICE_OFFLINE] ?: false }
    val musicEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.MUSIC_ENABLED] ?: true }
    val sensitivity: Flow<Float> = context.dataStore.data.map { it[Keys.SENSITIVITY] ?: 1.0f }

    /** 每日跳绳目标（个），默认 600。 */
    val dailyGoal: Flow<Int> = context.dataStore.data.map { it[Keys.DAILY_GOAL] ?: DEFAULT_DAILY_GOAL }
    /** 每周跳绳目标（个），默认 4200（每日 600 × 7）。 */
    val weeklyGoal: Flow<Int> = context.dataStore.data.map { it[Keys.WEEKLY_GOAL] ?: DEFAULT_WEEKLY_GOAL }
    /** 是否启用夜间模式。 */
    val darkTheme: Flow<Boolean> = context.dataStore.data.map { it[Keys.DARK_THEME] ?: false }
    /** 计数方式：sensor=传感器启发式，camera=摄像头姿态估计。默认摄像头（更安全、计数更准）。 */
    val countMode: Flow<CountMode> = context.dataStore.data.map {
        CountMode.fromKey(it[Keys.COUNT_MODE] ?: CountMode.CAMERA.key)
    }

    /** 是否已同意隐私政策（首启需同意才能进入应用）。 */
    val privacyAccepted: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PRIVACY_ACCEPTED] ?: false }

    /** 已经向孩子展示过「解锁庆祝」的徽章 id 集合（避免重复弹窗）。 */
    val seenBadgeIds: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.SEEN_BADGE_IDS] ?: emptySet() }

    /** 成就庆祝功能是否已做过「首次基线种子化」（把历史成就标记为已看，避免老用户一打开就弹历史）。 */
    val celebrationSeeded: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.CELEBRATION_SEEDED] ?: false }

    /** 已经庆祝过的最高「连续打卡里程碑」（7 的倍数）；用于只在新跨过的里程碑弹大奖章。 */
    val lastStreakMedal: Flow<Int> =
        context.dataStore.data.map { it[Keys.LAST_STREAK_MEDAL] ?: 0 }

    val seedDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.SEED_DONE] ?: false }

    suspend fun setCurrentChildId(id: Long) =
        context.dataStore.edit { it[Keys.CURRENT_CHILD_ID] = id }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) =
        context.dataStore.edit {
            it[Keys.REMINDER_ENABLED] = enabled
            it[Keys.REMINDER_HOUR] = hour
            it[Keys.REMINDER_MINUTE] = minute
        }

    suspend fun setSoundEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }

    suspend fun setVoiceEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.VOICE_ENABLED] = enabled }
    suspend fun setVoiceOffline(enabled: Boolean) =
        context.dataStore.edit { it[Keys.VOICE_OFFLINE] = enabled }

    suspend fun setMusicEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.MUSIC_ENABLED] = enabled }

    suspend fun setSensitivity(value: Float) =
        context.dataStore.edit { it[Keys.SENSITIVITY] = value.coerceIn(0.5f, 1.5f) }

    suspend fun setDailyGoal(value: Int) =
        context.dataStore.edit { it[Keys.DAILY_GOAL] = value.coerceIn(10, 2000) }
    suspend fun setWeeklyGoal(value: Int) =
        context.dataStore.edit { it[Keys.WEEKLY_GOAL] = value.coerceIn(50, 10000) }
    suspend fun setDarkTheme(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    suspend fun setCountMode(mode: CountMode) =
        context.dataStore.edit { it[Keys.COUNT_MODE] = mode.key }

    suspend fun setPrivacyAccepted(accepted: Boolean) =
        context.dataStore.edit { it[Keys.PRIVACY_ACCEPTED] = accepted }

    suspend fun setSeenBadgeIds(ids: Set<String>) =
        context.dataStore.edit { it[Keys.SEEN_BADGE_IDS] = ids }

    suspend fun setCelebrationSeeded() =
        context.dataStore.edit { it[Keys.CELEBRATION_SEEDED] = true }

    suspend fun setLastStreakMedal(milestone: Int) =
        context.dataStore.edit { it[Keys.LAST_STREAK_MEDAL] = milestone }

    suspend fun markSeeded() = context.dataStore.edit { it[Keys.SEED_DONE] = true }

    // ===== 积分与奖励（按孩子隔离：key 里带 childId，多孩子互不干扰） =====

    private fun pointsKey(childId: Long) = intPreferencesKey("points_$childId")
    private fun prizesKey(childId: Long) = stringSetPreferencesKey("earned_prizes_$childId")
    private fun redeemedKey(childId: Long) = stringSetPreferencesKey("redeemed_prizes_$childId")

    /** 某个孩子当前累计积分。 */
    fun points(childId: Long): Flow<Int> = context.dataStore.data.map { it[pointsKey(childId)] ?: 0 }

    /**
     * 已「庆祝过」的奖品 id 集合（积分达标时由 TrainingViewModel 自动写入）。
     * ⚠️ 语义注意：这个集合只用于「解锁庆祝动画只弹一次」的去重，
     * 不代表孩子真正兑换了奖品——「已兑换」请用 [redeemedPrizes]。
     */
    fun earnedPrizes(childId: Long): Flow<Set<String>> =
        context.dataStore.data.map { it[prizesKey(childId)] ?: emptySet() }

    /** 某个孩子已真正兑换（找爸妈兑现过）的奖品 id 集合，仅由兑换操作手动写入。 */
    fun redeemedPrizes(childId: Long): Flow<Set<String>> =
        context.dataStore.data.map { it[redeemedKey(childId)] ?: emptySet() }

    /** 增减积分；返回变动后的总积分（下限 0，不会变负）。 */
    suspend fun addPoints(childId: Long, delta: Int): Int {
        var next = 0
        context.dataStore.edit {
            next = ((it[pointsKey(childId)] ?: 0) + delta).coerceAtLeast(0)
            it[pointsKey(childId)] = next
        }
        return next
    }

    /** 标记一个奖品已庆祝过（防止每次进训练都重复弹解锁动画；重复调用无副作用）。 */
    suspend fun addPrize(childId: Long, prizeId: String) =
        context.dataStore.edit { it[prizesKey(childId)] = (it[prizesKey(childId)] ?: emptySet()) + prizeId }

    /** 记录一次真实兑换（孩子在奖品墙点「兑换」并确认后调用）。 */
    suspend fun redeemPrize(childId: Long, prizeId: String) =
        context.dataStore.edit { it[redeemedKey(childId)] = (it[redeemedKey(childId)] ?: emptySet()) + prizeId }

    /** 取消兑换（误点或家长撤销时用，把奖品退回「可兑换」状态）。 */
    suspend fun unredeemPrize(childId: Long, prizeId: String) =
        context.dataStore.edit { it[redeemedKey(childId)] = (it[redeemedKey(childId)] ?: emptySet()) - prizeId }

    // ===== 跳绳浮条位置记忆（全局，不按孩子隔离） =====
    // 2026-09-04：key 升级为 v2——用户要求浮条默认停靠右侧，旧 key 里存着测试期拖到左侧的
    // 位置记忆，换新 key 等效一次「位置重置」，之后仍正常记忆拖动位置。

    private val floatBarEdgeKey = stringPreferencesKey("float_bar_edge_v2")
    private val floatBarYKey = floatPreferencesKey("float_bar_y_v2")

    /** 浮条吸附边（"L"/"R"）。 */
    fun floatBarEdge(): Flow<String> = context.dataStore.data.map { it[floatBarEdgeKey] ?: "R" }

    /** 浮条垂直位置比例（0~1，相对可用高度）。 */
    fun floatBarY(): Flow<Float> = context.dataStore.data.map { it[floatBarYKey] ?: 0.62f }

    /** 记录浮条拖动后的吸附位置。 */
    suspend fun setFloatBarPos(edge: String, yRatio: Float) =
        context.dataStore.edit {
            it[floatBarEdgeKey] = edge
            it[floatBarYKey] = yRatio
        }

    // ===== 暂停挂起会话的跨进程持久化（2026-09-04） =====
    // 场景：训练中暂停（或直接退出 App）后进程被杀，Activity 级 TrainingViewModel 的
    // 内存状态没了。把「孩子 / 已跳个数 / 已用时 / 计数方式 / 本次积分」落盘，
    // 下次冷启动弹框询问「继续还是放弃」，实现真正的跨进程挂起续跳。

    /** 一条挂起中的会话快照。 */
    data class SuspendedSession(
        val childId: Long,
        val count: Int,
        val elapsedSec: Int,
        val mode: CountMode,
        val sessionPoints: Int
    )

    private val susChildKey = longPreferencesKey("suspended_child_id")
    private val susCountKey = intPreferencesKey("suspended_count")
    private val susElapsedKey = intPreferencesKey("suspended_elapsed")
    private val susModeKey = stringPreferencesKey("suspended_mode")
    private val susPointsKey = intPreferencesKey("suspended_points")

    /** 当前是否有挂起中的会话快照（null = 无）。 */
    fun suspendedSession(): Flow<SuspendedSession?> = context.dataStore.data.map { p ->
        val child = p[susChildKey] ?: return@map null
        val count = p[susCountKey] ?: return@map null
        SuspendedSession(
            childId = child,
            count = count,
            elapsedSec = p[susElapsedKey] ?: 0,
            mode = CountMode.fromKey(p[susModeKey] ?: CountMode.CAMERA.key),
            sessionPoints = p[susPointsKey] ?: 0
        )
    }

    /** 保存/覆盖挂起会话快照（pause 时调用；count=0 不存，避免「0 个还问继续」）。 */
    suspend fun setSuspendedSession(childId: Long, count: Int, elapsedSec: Int, mode: CountMode, sessionPoints: Int) {
        if (count <= 0) return
        context.dataStore.edit {
            it[susChildKey] = childId
            it[susCountKey] = count
            it[susElapsedKey] = elapsedSec
            it[susModeKey] = mode.key
            it[susPointsKey] = sessionPoints
        }
    }

    /** 清除挂起会话快照（放弃 / 正式结束保存 / 开新会话时调用）。 */
    suspend fun clearSuspendedSession() =
        context.dataStore.edit {
            it.remove(susChildKey); it.remove(susCountKey); it.remove(susElapsedKey)
            it.remove(susModeKey); it.remove(susPointsKey)
        }
}
