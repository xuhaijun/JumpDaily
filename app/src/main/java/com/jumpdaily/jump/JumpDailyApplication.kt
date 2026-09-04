package com.jumpdaily.jump

import android.app.Application
import androidx.work.Configuration
import com.jumpdaily.jump.di.AppContainer

/**
 * Application。实现 [Configuration.Provider] 以显式提供 WorkManager 配置：
 * 因为 AndroidManifest 里用 `tools:node="remove"` 移除了 WorkManager 默认的 App Startup 初始化器，
 * 若不在此提供 Configuration，首次调用 `WorkManager.getInstance()` 会抛
 * `IllegalStateException("WorkManager is not initialized properly")`，进而导致设置页拨动「每日提醒」开关时崩溃退出。
 */
class JumpDailyApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
