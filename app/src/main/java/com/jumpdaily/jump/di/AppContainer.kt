package com.jumpdaily.jump.di

import android.app.Application
import android.content.Context
import android.hardware.SensorManager
import com.jumpdaily.jump.audio.SoundPlayer
import com.jumpdaily.jump.audio.VoiceSpeaker
import com.jumpdaily.jump.data.local.AppDatabase
import com.jumpdaily.jump.data.repository.ChildrenRepository
import com.jumpdaily.jump.data.repository.JumpRepository
import com.jumpdaily.jump.data.repository.PreferencesRepository
import com.jumpdaily.jump.reminder.ReminderScheduler
import com.jumpdaily.jump.ui.viewmodel.ChildrenViewModel
import com.jumpdaily.jump.ui.viewmodel.RecordsViewModel
import com.jumpdaily.jump.ui.viewmodel.SessionViewModel
import com.jumpdaily.jump.ui.viewmodel.SettingsViewModel
import com.jumpdaily.jump.ui.viewmodel.TrainingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 手动依赖装配（无 Hilt，保持轻量）。 */
class AppContainer(app: Application) {
    private val db = AppDatabase.getInstance(app)

    val childrenRepository = ChildrenRepository(db)
    val jumpRepository = JumpRepository(db)
    val prefsRepository = PreferencesRepository(app)
    val soundPlayer = SoundPlayer(app)
    val voiceSpeaker = VoiceSpeaker(app)
    val sensorManager = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val reminderScheduler = ReminderScheduler(app)

    /** 应用级协程作用域（用于启动时套用偏好，不随页面销毁）。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // 启动时套用「离线优先」偏好：已装 eSpeak 则自动切离线引擎，任意手机音色一致
        appScope.launch { voiceSpeaker.setPreferOffline(prefsRepository.voiceOffline.first()) }
    }

    val sessionFactory = SessionViewModel.Factory(prefsRepository, childrenRepository)
    val trainingFactory = TrainingViewModel.Factory(sensorManager, soundPlayer, voiceSpeaker, jumpRepository, prefsRepository)
    val childrenFactory = ChildrenViewModel.Factory(childrenRepository, prefsRepository)
    val recordsFactory = RecordsViewModel.Factory(jumpRepository)
    val settingsFactory = SettingsViewModel.Factory(prefsRepository, reminderScheduler, soundPlayer, voiceSpeaker)
}
