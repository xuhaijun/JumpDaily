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

    /** 某个孩子当前累计积分。 */
    fun points(childId: Long): Flow<Int> = context.dataStore.data.map { it[pointsKey(childId)] ?: 0 }

    /** 某个孩子已获得的奖品 id 集合。 */
    fun earnedPrizes(childId: Long): Flow<Set<String>> =
        context.dataStore.data.map { it[prizesKey(childId)] ?: emptySet() }

    /** 增减积分；返回变动后的总积分（下限 0，不会变负）。 */
    suspend fun addPoints(childId: Long, delta: Int): Int {
        var next = 0
        context.dataStore.edit {
            next = ((it[pointsKey(childId)] ?: 0) + delta).coerceAtLeast(0)
            it[pointsKey(childId)] = next
        }
        return next
    }

    /** 记录一个已获得的奖品（重复调用无副作用）。 */
    suspend fun addPrize(childId: Long, prizeId: String) =
        context.dataStore.edit { it[prizesKey(childId)] = (it[prizesKey(childId)] ?: emptySet()) + prizeId }
}
