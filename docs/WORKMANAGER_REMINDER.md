# WorkManager 每日提醒工程化

> 基于 JumpDaily 真实代码：`reminder/ReminderScheduler.kt`、`reminder/ReminderWorker.kt`、
> `JumpDailyApplication.kt`（`Configuration.Provider`）、`AndroidManifest.xml`、`SettingsViewModel.kt`/`SettingsScreen.kt`。
> 当前**提醒已真实可用**；本文先讲清现状，再给出工程化进阶（精确时间、Doze/厂商限制、多孩子差异化）。

---

## 1. 现状：已经做了什么

### 1.1 调度器（真实代码）

`reminder/ReminderScheduler.kt`：

```kotlin
class ReminderScheduler(private val context: Context) {
    fun schedule(hour: Int, minute: Int) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(HOUR_OF_DAY, hour); set(MINUTE, minute)
            set(SECOND, 0); set(MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) target.add(DAY_OF_MONTH, 1)
        val delay = target.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_reminder", ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }
    fun cancel() = WorkManager.getInstance(context).cancelUniqueWork("daily_reminder")
}
```

### 1.2 Worker（真实代码）

`reminder/ReminderWorker.kt`：`doWork()` 在 `jump_reminder` 渠道（已 `createNotificationChannel`）弹通知 id `1001`，`PendingIntent` 回 `MainActivity`。

### 1.3 初始化坑（已踩并已修）

`JumpDailyApplication.kt` 实现 `Configuration.Provider`：

```kotlin
class JumpDailyApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
```

根因：`AndroidManifest.xml` 第 47~57 行用 `tools:node="remove"` 删掉了 WorkManager 的 App Startup 初始化器
（避免多余的 ContentProvider 拖慢启动）。**副作用**：WorkManager 不再自动初始化，必须显式提供 `Configuration`，
否则拨「每日提醒」开关时 `WorkManager.getInstance()` 抛 `IllegalStateException` 崩溃。本项目已用 `Configuration.Provider` 修掉。

### 1.4 触发链路

```
SettingsScreen 开关/时间变化
  → SettingsViewModel.setReminder(enabled,h,m)
  → prefs.setReminder(...) 写 DataStore
  → scheduler.schedule(h,m) / scheduler.cancel()   // AppContainer 注入了 ReminderScheduler
```

---

## 2. 工程化问题：当前实现的局限

### 2.1 ⚠️ PeriodicWorkRequest **不保证精确时间**

`PeriodicWorkRequestBuilder(24h)` + `setInitialDelay(delay)`：

- `setInitialDelay` 只控制**首次**触发，之后严格按「上一次执行时刻 + 24h」排期，**会漂移**（系统批量执行、Doze 延后都会累计偏移）。
- WorkManager 对周期任务有内置「弹性窗口」（flex），实际触发可能在目标时刻附近 **±数小时**。
- 对「每天 19:00 提醒」这类**固定时刻**诉求，不够准。

**进阶方案 A（推荐，保留 WorkManager）**：用 `setPeriodic(24h, flex)` 缩小弹性，并在 `ReminderWorker` 里读当前时间、
若不在「允许窗口」（如 18:55~19:10）则 `Result.retry()` 或发一个「时间校准」二次调度——较 hack。
**进阶方案 B（精确，改用 AlarmManager）**：

```kotlin
// 每天精确时刻：用 AlarmManager.setExactAndAllowWhileIdle（配合 BOOT 重排）
val am = ctx.getSystemService(AlarmManager::class.java)
val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, ReminderReceiver::class.java),
            FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTime, pi)
```

`ReminderReceiver`（`BootReceiver` 监听 `RECEIVE_BOOT_COMPLETED`，Manifest 已声明该权限）在开机后重排次日闹钟。
**注意**：Android 12+ `setExact` 受限（需 `SCHEDULE_EXACT_ALARM` 权限或 `USE_EXACT_ALARM`）；儿童 App 用 `AlarmManager` 更稳但需处理权限。

### 2.2 Doze / 待机桶 / 厂商后台限制

- WorkManager 在 **Doze**（息屏久置）下会被推迟到维护窗口；周期任务无法 `setExpedited`（加急只对 OneTime 有效）。
- **华为 / 小米**等国产 ROM 有「自启动管理 / 省电模式」，会把 WorkManager 的后台执行**直接杀掉**或限制——
  这是本项目「不同渠道到达率差异大」的根本原因（见 FLAVOR_ADVANCED.md）。
- 缓解：重要提醒走**厂商推送通道**（华为 Push Kit / 小米 Mi Push）做保活，WorkManager 仅作兜底。

### 2.3 多孩子差异化的缺失

当前只有**一条** `daily_reminder`，不区分孩子。多孩子场景下：

```kotlin
// 为每个孩子独立排程（workDataOf 带 childId；唯一名加后缀）
WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
    "daily_reminder_${child.id}",
    UPDATE,
    PeriodicWorkRequestBuilder<ReminderWorker>(24, HOURS)
        .setInputData(workDataOf("child_id" to child.id))
        .setInitialDelay(delay, MILLIS).build()
)
```

`ReminderWorker.doWork()` 读出 `child_id`，通知文案带上孩子昵称（「XX，该跳绳啦～」）。
取消时同样按 `child_id` 维度 `cancelUniqueWork`。

### 2.4 通知渠道已正确

`ReminderWorker` 对 `jump_reminder` 调 `createNotificationChannel`（minSdk 26 强制渠道，已遵循），
`IMPORTANCE_DEFAULT`。Android 13+ 的 `POST_NOTIFICATIONS` 权限已在 `SettingsScreen` 申请流程里处理
（未授权则回退开关，见 `SettingsScreen.kt` 第 113~131 行）。

---

## 3. 可靠性与测试

### 3.1 开机后是否还会提醒？

`AndroidManifest.xml` 注释写「让 WorkManager 在开机后仍能按时提醒」。
**澄清**：WorkManager 2.x 会把周期性任务持久化到内部 Room 数据库，**自身已能在开机后自动恢复**——
删 `WorkManagerInitializer` 只是去掉 App Startup 的自动初始化入口，与「开机恢复」无关。
真正需要 `RECEIVE_BOOT_COMPLETED` 的是**AlarmManager 方案 B**（Receiver 手动重排）。不要误解注释。

### 3.2 单元测试

```kotlin
@RunWith(AndroidJUnit4::class)
class ReminderSchedulerTest {
    @get:Rule val wmRule = WorkManagerTestInitHelper.getTestInitHelperRule()
    @Test fun schedulesUniqueDaily() {
        val wm = WorkManager.getInstance(context)
        ReminderScheduler(context).schedule(19, 0)
        val ops = wm.getWorkInfosForUniqueWork("daily_reminder").get()
        assertEquals(1, ops.size)
        assertEquals(ENQUEUED, ops.first().state)
    }
}
```

### 3.3 防止「开着却不提醒」

`SettingsScreen` 已做：授权失败 → `setReminder(false,...)` 回退开关。
补充：每次进设置页、`onResume` 时 `WorkManager.getWorkInfosForUniqueWork("daily_reminder")`
核对实际状态与开关 UI 是否一致，防止系统清后台后「开关亮着却不再响」。

---

## 4. 渠道差异化提醒策略（与 FLAVOR_ADVANCED 联动）

| 渠道 | 提醒保活手段 |
|------|-------------|
| official | 仅 WorkManager（兜底）+ 通知渠道 |
| huawei | WorkManager + **华为 Push Kit**（即便 App 被杀，云端推送也能拉起提醒，到达率最高） |
| xiaomi | WorkManager + **小米 Mi Push**（同理，绕过 MIUI 自启动限制） |

实现：在 `ReminderScheduler.schedule()` 里读 `BuildConfig.FLAVOR` / `CHANNEL`，
huawei/xiaomi 渠道改为「调用厂商 Push SDK 注册定时任务」，official 走 WorkManager。
具体接入见 [FLAVOR_ADVANCED.md](./FLAVOR_ADVANCED.md)。

---

## 5. 状态

- ✅ 提醒**已真实可用**：调度/Worker/渠道/权限申请/初始化坑均已落地。
- ⚠️ 进阶待做：精确时刻（AlarmManager 桥接）、多孩子独立排程、huawei/xiaomi 厂商推送保活（见上表）。
- 触发链路可靠，单测可加 `WorkManagerTestInitHelper` 防护回归。
