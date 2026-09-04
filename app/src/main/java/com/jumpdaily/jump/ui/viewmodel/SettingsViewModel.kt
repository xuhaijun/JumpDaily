package com.jumpdaily.jump.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jumpdaily.jump.audio.SoundPlayer
import com.jumpdaily.jump.audio.VoiceSpeaker
import com.jumpdaily.jump.data.model.CountMode
import com.jumpdaily.jump.data.repository.DAYS_PER_WEEK
import com.jumpdaily.jump.data.repository.DEFAULT_DAILY_GOAL
import com.jumpdaily.jump.data.repository.DEFAULT_WEEKLY_GOAL
import com.jumpdaily.jump.data.repository.PreferencesRepository
import com.jumpdaily.jump.reminder.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 设置页：提醒开关与时间、音效、背景音乐、检测灵敏度。 */
class SettingsViewModel(
    private val prefs: PreferencesRepository,
    private val scheduler: ReminderScheduler,
    private val soundPlayer: SoundPlayer,
    private val voiceSpeaker: VoiceSpeaker
) : ViewModel() {

    val reminderEnabled: StateFlow<Boolean> =
        prefs.reminderEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val reminderHour: StateFlow<Int> =
        prefs.reminderHour.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 19)
    val reminderMinute: StateFlow<Int> =
        prefs.reminderMinute.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val soundEnabled: StateFlow<Boolean> =
        prefs.soundEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val voiceEnabled: StateFlow<Boolean> =
        prefs.voiceEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    /** 离线优先（eSpeak）开关状态。 */
    val voiceOffline: StateFlow<Boolean> =
        prefs.voiceOffline.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val sensitivity: StateFlow<Float> =
        prefs.sensitivity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)
    val dailyGoal: StateFlow<Int> =
        prefs.dailyGoal.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_DAILY_GOAL)
    val weeklyGoal: StateFlow<Int> =
        prefs.weeklyGoal.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_WEEKLY_GOAL)
    val darkTheme: StateFlow<Boolean> =
        prefs.darkTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    /** 当前计数方式（传感器 / 摄像头）。 */
    val countMode: StateFlow<CountMode> =
        prefs.countMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CountMode.SENSOR)

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) = viewModelScope.launch {
        prefs.setReminder(enabled, hour, minute)
        try {
            if (enabled) scheduler.schedule(hour, minute) else scheduler.cancel()
        } catch (e: Exception) {
            // 提醒调度失败不应影响开关状态或导致页面崩溃（极端情况下 WorkManager 初始化异常）
            e.printStackTrace()
        }
    }

    fun setSound(enabled: Boolean) {
        soundPlayer.setEnabled(enabled)
        viewModelScope.launch { prefs.setSoundEnabled(enabled) }
    }
    fun setVoice(enabled: Boolean) {
        voiceSpeaker.setEnabled(enabled)
        viewModelScope.launch { prefs.setVoiceEnabled(enabled) }
    }
    /** 离线优先：持久化开关并立即驱动 VoiceSpeaker 切换引擎（已装 eSpeak 才生效）。 */
    fun setVoiceOffline(enabled: Boolean) {
        voiceSpeaker.setPreferOffline(enabled)
        viewModelScope.launch { prefs.setVoiceOffline(enabled) }
    }
    fun setSensitivity(value: Float) = viewModelScope.launch { prefs.setSensitivity(value) }
    /**
     * 调整每日目标时，每周目标按「每日 × 7」自动同步，避免两个目标各说各话
     * （例如每日调到 800，每周应自动变 5600，而不是停留在旧的 4200）。
     */
    fun setDailyGoal(value: Int) = viewModelScope.launch {
        prefs.setDailyGoal(value)
        prefs.setWeeklyGoal(value * DAYS_PER_WEEK)
    }

    /** 单独微调每周目标（不反推每日目标，保留家长按周安排的自由度）。 */
    fun setWeeklyGoal(value: Int) = viewModelScope.launch { prefs.setWeeklyGoal(value) }
    fun setDarkTheme(enabled: Boolean) = viewModelScope.launch { prefs.setDarkTheme(enabled) }
    fun setCountMode(mode: CountMode) = viewModelScope.launch { prefs.setCountMode(mode) }

    class Factory(
        private val prefs: PreferencesRepository,
        private val scheduler: ReminderScheduler,
        private val soundPlayer: SoundPlayer,
        private val voiceSpeaker: VoiceSpeaker
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java))
                return SettingsViewModel(prefs, scheduler, soundPlayer, voiceSpeaker) as T
            throw IllegalArgumentException("Unknown VM: $modelClass")
        }
    }
}
