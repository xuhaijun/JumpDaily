# JumpDaily 技术栈核心思想与用法详解

> 配套文档：[ARCHITECTURE.md](./ARCHITECTURE.md)（分层架构总览）
> 本文对架构文档第 2 节「技术栈」逐项展开：**核心思想 → 本项目实际用法（含真实文件路径与代码）→ 关键 API → 踩坑注意**。
> 所有示例均取自 `D:\SmallTools\JumpDaily` 当前代码。

---

## 目录

1. [Kotlin（语言与协程）](#1-kotlin语言与协程)
2. [Jetpack Compose（声明式 UI）](#2-jetpack-compose声明式-ui)
3. [Material3（设计系统）](#3-material3设计系统)
4. [Navigation Compose（页面路由）](#4-navigation-compose页面路由)
5. [Room（本地结构化存储）](#5-room本地结构化存储)
6. [DataStore（键值偏好）](#6-datastore键值偏好)
7. [SensorManager（传感器计数）](#7-sensormanager传感器计数)
8. [CameraX（相机预览与帧分析）](#8-camerax相机预览与帧分析)
9. [MediaPipe Tasks Vision（姿态估计）](#9-mediapipe-tasks-vision姿态估计)
10. [Lottie Compose（矢量动画）](#10-lottie-compose矢量动画)
11. [WorkManager（定时任务）](#11-workmanager定时任务)
12. [音效：MediaPlayer + AudioTrack](#12-音效mediaplayer--audiotrack)
13. [手动依赖注入（AppContainer）](#13-手动依赖注入appcontainer)
14. [Gradle / AGP 构建体系](#14-gradle--agp-构建体系)

---

## 1. Kotlin（语言与协程）

### 核心思想
Kotlin 是 JVM 上现代化的静态语言：空安全、`data class`、扩展函数、`sealed class`、属性委托（`by`）、协程（轻量级线程，用 `suspend` + `Flow` 表达异步流）。Android 官方首选语言，Jetpack 全家桶均围绕它设计。

### 本项目用法
- **`StateFlow` 作为状态容器**：ViewModel 用 `MutableStateFlow` + `asStateFlow()` 暴露不可变状态，配合 `stateIn(WhileSubscribed(5000), 初始值)` 转热流。
  ```kotlin
  // RecordsViewModel.kt
  private val _childId = MutableStateFlow<Long?>(null)
  val childId: StateFlow<Long?> = _childId.asStateFlow()
  val stats: StateFlow<Stats> = _childId.flatMapLatest { id ->
      id?.let { repo.stats(it) } ?: flowOf(Stats())
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Stats())
  ```
- **`sealed class` 表达路由**：`ui/navigation/AppRoot.kt` 用 `sealed class Screen(val route: String)` 枚举所有页面，比裸字符串安全。
- **扩展函数**：`util/DateUtils.kt` 把 `dayStart()`、`computeStreak()` 作为顶层函数；`private val Context.dataStore by preferencesDataStore(...)` 是属性委托 + 扩展属性的结合。
- **`lateinit`**：`JumpDailyApplication.container` 用 `lateinit var`，在 `onCreate` 赋值避免可空判断。

### 关键 API
- `viewModelScope.launch { }`：ViewModel 作用域协程，自动随 `onCleared` 取消。
- `flow.first()`：取流的首个值（一次性读取，如 `childrenRepo.children.first()`）。
- `combine(a,b,c,d,e){...}`：多流合并（见 `JumpRepository.stats`）。

### 踩坑注意
- **`= 同名()` 递归委托**：若 ViewModel 内 `JumpListener` 的 `onJump()` 直接 `= onJump()` 会无限递归；本项目用块体 `override fun onJump() { this@TrainingViewModel.onJump() }` 显式转发。
- **`@Volatile`**：跨线程可见的标志（`running`、`soundOn`、`sensitivity`）需加，避免 JIT 重排读到旧值。

---

## 2. Jetpack Compose（声明式 UI）

### 核心思想
用 `@Composable` 函数**描述 UI 长什么样**，而不是命令式地「创建并设置」。状态变化触发**重组（Recomposition）**——仅重算受影响的 Composable。核心原则：**状态提升（State Hoisting）**：UI 无状态、状态上移到 ViewModel；UI 通过 `value` 读、通过回调写。

### 本项目用法
- 全部页面是 `@Composable`（`ui/screens/*.kt`），状态来自 `viewModel()` + `collectAsStateWithLifecycle()`：
  ```kotlin
  // CameraTrainingScreen.kt
  val count by vm.count.collectAsStateWithLifecycle()
  Text("$count", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Color.White)
  ```
- **生命周期安全收集**：`collectAsStateWithLifecycle(initialValue=, lifecycle=)`（非 StateFlow 的 DataStore Flow 必须带 `initialValue`，否则编译报错）。
- **`remember` / `remember { ... }`**：保持跨重组的对象（如 `PreviewView`、`Executors.newSingleThreadExecutor()`），避免每次重组重建。
- **副作用 API**：`LaunchedEffect`（进入组合执行一次/依赖变化执行）、`DisposableEffect`（离开组合释放资源，如 `detector?.stop()`）。

### 关键 API
- `setContent { }`：`MainActivity` 用它在 Activity 里挂 Compose 树。
- `mutableStateOf()` / `by` 委托：局部可变状态（如 `var hasPermission by remember { ... }`）。
- `Box` / `Column` / `Row` / `Spacer`：基础布局，等价于 FrameLayout/LinearLayout。

### 踩坑注意
- **`by` 委托必须 import `getValue`**：`import androidx.compose.runtime.getValue`，否则 `val x by ...` 报「unresolved reference」。
- **重组要纯**：Composable 内不要做 IO/耗时；相机预览、模型下载都放进 `LaunchedEffect(Dispatchers.IO)`。
- **`AndroidView`**：传统 View（CameraX `PreviewView`）通过 `AndroidView(factory = { previewView })` 嵌入 Compose。

---

## 3. Material3（设计系统）

### 核心思想
Material3 提供一套「主题驱动」的组件：所有颜色/字形取自 `MaterialTheme.colorScheme` / `MaterialTheme.typography`，换肤只需换 Theme，组件不用改。本项目用**糖果卡哇伊**配色（低饱和高明亮）。

### 本项目用法
- `ui/theme/Theme.kt` 定义 `Light` / `Dark` 两套 `Colors`，`JumpDailyTheme(darkTheme)` 包裹整棵树。
- `ui/theme/Color.kt` 自定义品牌色：`PurplePrimary(0xFF6A3FE8)`、`PinkSecondary`、`SunYellow`、`Mint`、`CreamBg`、`ErrorDeep(0xFFD6336E)` 等。
- 组件统一取色而非硬编码：
  ```kotlin
  // AppRoot.kt 底部栏
  NavigationBarItem(
      selected = selected,
      colors = NavigationBarItemDefaults.colors(
          selectedIconColor = MaterialTheme.colorScheme.primary,
          indicatorColor = MaterialTheme.colorScheme.primaryContainer
      )
  )
  ```

### 关键 API
- `MaterialTheme.colorScheme.{primary,onPrimary,surface,background,error,tertiary,...}`
- `Scaffold(bottomBar = {...})`：自带内容内边距（`innerPadding`），底部栏/顶部栏由框架管理。
- `NavigationBar` / `NavigationBarItem`：底部导航标准件。

### 踩坑注意
- **`Typography` 不要写死 `color = Ink`**：否则深色模式下深紫字落在深紫底不可读。本项目已改为继承 `onSurface`。
- **对比度**：浅色按钮（如 `secondary` 浅粉）配白字会看不清，本项目 `CuteButton` 改为按底色明暗自动选字色。
- Material3 的 `Surface`/`Scaffold` 的 `containerColor` 才是背景色字段名（旧 Material 是 `backgroundColor`）。

---

## 4. Navigation Compose（页面路由）

### 核心思想
用 `NavHost` + `composable(route)` 声明式地描述路由表，导航用 `navController.navigate(route)`；返回栈由框架托管。配合 `Scaffold` 底部栏实现 Tab 切换。

### 本项目用法
- `ui/navigation/AppRoot.kt`：`Screen` 密封类定义 9 条路由，`NavHost(startDestination = Splash)` 注册。
- 底部栏 4 个 Tab（`Home/Stats/History/Achievements`），训练/摄像头训练/启动页隐藏底部栏：
  ```kotlin
  val showBottomBar = currentRoute != Screen.Training.route
      && currentRoute != Screen.Splash.route
      && currentRoute != Screen.CameraTraining.route
  ```
- Tab 切换用 `popUpTo(Home){saveState=true}; launchSingleTop=true; restoreState=true` 保留各页状态。

### 关键 API
- `rememberNavController()` / `currentBackStackEntryAsState()`
- `nav.popBackStack()`：结算页「完成」返回上一页。

### 踩坑注意
- `NavHostController` 类型需 `import androidx.navigation.NavHostController`，在 Screen 内引用时易漏 import 报 `Unresolved reference`。
- 沉浸式页面（训练）不要放进底部栏的 `popUpTo` 链，否则返回栈错乱。

---

## 5. Room（本地结构化存储）

### 核心思想
Room 是 SQLite 的对象映射（ORM）层：`@Entity` 标实体、`@Dao` 写查询、`@Database` 持有实例；编译期用 `kapt` 生成实现，查询返回 `Flow` 即自动响应式。

### 本项目用法
- `data/local/entities/`：`Child`、`JumpRecord` 两个实体（`@PrimaryKey(autoGenerate=true)`、`@Index` 加速按 `childId/date` 查询）。
- `data/local/JumpRecordDao.kt`：查询返回 `Flow`，Room 在数据变化自动发射新列表：
  ```kotlin
  @Query("SELECT * FROM jump_records WHERE childId = :childId ORDER BY date DESC")
  fun observeByChild(childId: Long): Flow<List<JumpRecord>>
  ```
- `AppDatabase` 单例（`@Volatile INSTANCE` + `synchronized`），`fallbackToDestructiveMigration()`（演示期结构变更直接重建库，免迁移样板）。

### 关键 API
- `@Insert` / `@Update` / `@Delete` / `@Query`（`suspend fun` 或返回 `Flow`）。
- `Room.databaseBuilder(ctx, AppDatabase::class.java, "jump_daily.db")`。

### 踩坑注意
- **`kapt` 必须启用**：`app/build.gradle.kts` 有 `id("org.jetbrains.kotlin.kapt")` + `kapt("androidx.room:room-compiler")`，否则注解不生成、运行期崩溃。
- **破坏性迁移**：`fallbackToDestructiveMigration` 升级 DB 版本会清空数据，正式版要写 `Migration` 或 `autoMigrations`。
- 删除孩子需联动删记录（`ChildrenRepository.delete` 里 `db.jumpRecordDao().deleteByChild(child.id)`），否则留孤儿数据。

---

## 6. DataStore（键值偏好）

### 核心思想
DataStore 是 SharedPreferences 的现代替代：类型安全、基于 `Flow`、异步（`edit{}` 挂起）、无主线程 ANR。偏好型 `Preferences` 用 `preferencesDataStore` 委托创建。

### 本项目用法
- `PreferencesRepository.kt`：`private val Context.dataStore by preferencesDataStore(name = "jump_prefs")`。
- 每个偏好一个 `Key`，读取 `map { it[Key] ?: 默认 }` 得 `Flow`：
  ```kotlin
  val darkTheme: Flow<Boolean> =
      context.dataStore.data.map { it[Keys.DARK_THEME] ?: false }
  suspend fun setDarkTheme(enabled: Boolean) =
      context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
  ```

### 关键 API
- `longPreferencesKey` / `booleanPreferencesKey` / `intPreferencesKey` / `floatPreferencesKey` / `stringPreferencesKey`。
- `coerceIn(...)`：写入前夹紧范围（如 `sensitivity` 限 0.5~1.5）。

### 踩坑注意
- **`local.properties` 以 ISO-8859-1 读**：若在那里写中文注释会乱码，本项目 `local.properties` 注释已改成纯 ASCII。
- DataStore 文件不可手动编辑；清数据用「应用设置 → 清除存储」。

---

## 7. SensorManager（传感器计数）

### 核心思想
Android 把传感器（加速度计、陀螺仪…）统一抽象为 `Sensor` + `SensorEventListener`；注册后，**回调在传感器线程**（`onSensorChanged`），需自行切主线程更新 UI。采样率用 `SENSOR_DELAY_*`（GAME/UI/FASTEST）。

### 本项目用法
- `sensor/JumpDetector.kt`：注册加速度计（落地冲击计数）+ 陀螺仪（挥手机提醒）。
- 启发式：落地冲击 `mag > G*1.6/sensitivity` 且已 `airborne`（先有 `mag<0.5G` 腾空）才计一次，避免起跳被重复记；用 `ArrayDeque` 存最近 12 次间隔估频率（个/分）。
- 通过 `JumpListener` 接口回调 `onJump()/onCadence()/onFormIssue()`，与摄像头方案共用。

### 关键 API
- `sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)`
- `registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)`
- `event.values[0..2]`：x/y/z 轴值。

### 踩坑注意
- **回调在子线程**：本项目回调里直接更新 `MutableStateFlow`（线程安全），UI 收集在主线程；不要在 `onSensorChanged` 里直接 `setText`。
- **无加速度计设备**：`getDefaultSensor` 返回 null，需判空跳过（日志告警）。
- `MIN_INTERVAL_MS=250` 去抖：防手抖误触，也限制了最高 ~240 个/分。

---

## 8. CameraX（相机预览与帧分析）

### 核心思想
CameraX 是相机任务的抽象：用 `Preview`（预览）+ `ImageAnalysis`（逐帧分析）等 `UseCase` 绑定到生命周期，自动处理旋转、分辨率、前后摄。`ImageAnalysis` 通过 `ImageAnalysis.Analyzer` 回调拿到每帧 `ImageProxy`。

### 本项目用法
- `CameraTrainingScreen.kt`：
  ```kotlin
  val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
  val analysis = ImageAnalysis.Builder()
      .setTargetResolution(android.util.Size(640, 480))
      .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
      .build()
  analysis.setAnalyzer(executor) { proxy ->
      val bmp = proxy.toBitmap()
      detector?.processFrame(rotateBitmap(bmp, proxy.imageInfo.rotationDegrees))
      proxy.close()   // 必须 close，否则后续帧被卡住
  }
  cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
  ```

### 关键 API
- `ProcessCameraProvider.getInstance(ctx)`（带 `Future`，用 `ContextCompat.getMainExecutor` 监听）。
- `proxy.toBitmap()`：把 `ImageProxy` 转 `Bitmap`（注意 YUV→RGB 开销）。
- `STRATEGY_KEEP_ONLY_LATEST`：丢弃积压帧，只处理最新，防延迟堆积。

### 踩坑注意
- **必须 `proxy.close()`**：漏关会导致相机不再出帧（卡死）。
- **旋转校正**：前置/不同设备有 `rotationDegrees`，本项目 `rotateBitmap` 用 `Matrix.postRotate` 校正，否则 MediaPipe 输入方向错、关键点歪。
- 权限：`AndroidManifest` 需 `CAMERA`，运行时 `RequestPermission` 申请。

---

## 9. MediaPipe Tasks Vision（姿态估计）

### 核心思想
Google MediaPipe Tasks Vision 提供预训练模型的高层 API：`PoseLandmarker` 输入图像、输出 33 个人体关键点（归一化坐标）。`LIVE_STREAM` 模式异步逐帧检测，结果通过 `setResultListener` 回调；模型用 `BaseOptions.setModelAssetPath` 指定本地 `.task` 文件。

### 本项目用法
- `camera/PoseJumpDetector.kt`：后台线程 `createFromOptions` 加载模型；`processFrame(bitmap)` 调 `detectAsync`；结果取髋中心（LEFT_HIP=23/RIGHT_HIP=24）y 喂 `PoseCounter`。
  ```kotlin
  val options = PoseLandmarkerOptions.builder()
      .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelPath).build())
      .setRunningMode(RunningMode.LIVE_STREAM)
      .setResultListener { result, _ -> onResult(result) }
      .build()
  landmarker = PoseLandmarker.createFromOptions(context, options)
  ```
- `camera/PoseModelProvider.kt`：模型三级回退 `assets/pose_landmarker.task` → `filesDir` 缓存 → 联网下载（需 `INTERNET` 权限 + IO 协程，避免主线程 ANR）。

### 关键 API
- `BitmapImageBuilder(bitmap).build()` → `MPImage`。
- `lm.detectAsync(mpImage, frameTs)`：LIVE_STREAM 要求 `frameTs` 单调递增。
- `result.landmarks()[0]`：第 0 个人，`person[23].y()` 取髋 y（0=图顶，1=底）。

### 踩坑注意
- **模型文件约 5MB**：国内 `storage.googleapis.com` 可能直连失败，推荐把 `pose_landmarker.task` 放 `app/src/main/assets/`。
- **`tasks-vision` 坐标**：正确为 `com.google.mediapipe:tasks-vision:0.10.14`（不是 `mediapipe_tasks_vision`）。
- 混淆：release 需保留 MediaPipe 原生库；本项目 `minifyEnabled=false` 暂未触发。

---

## 10. Lottie Compose（矢量动画）

### 核心思想
Lottie 用 Bodymovin 导出的 JSON 描述 After Effects 动画，运行时渲染为矢量（清晰、体积小、可换肤），替代手绘 Canvas 动画。

### 本项目用法
- `ui/components/LottiePlayer.kt`：`LottieRes(@RawRes resId)` 从 `res/raw` 加载 JSON 循环播放。
  ```kotlin
  val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
  LottieAnimation(composition = composition, iterations = iterations, modifier = modifier)
  ```
- 资产：`celebration.json`（达标彩带）、`star_spin.json`（启动页旋转星），由 `tools/gen_assets.py` 生成。

### 关键 API
- `LottieCompositionSpec.RawRes` / `Asset` / `Url`。
- `iterations = Int.MAX_VALUE` 无限循环；`LottieAnimation(composition, progress)` 也可手动控制进度。

### 踩坑注意
- `composition` 是 `State<LottieComposition?>`（加载中 null），`LottieAnimation` 自动处理空，无需额外判空。
- 依赖：`com.airbnb.android:lottie-compose:6.5.2`。

---

## 11. WorkManager（定时任务）

### 核心思想
WorkManager 是「可靠的后台任务」框架：保证即使应用退出/设备重启也会执行，自动选择底层机制（JobScheduler/AlarmManager）。适合**周期/延迟**任务（如每日提醒），不适合精确即时任务。

### 本项目用法
- `reminder/ReminderScheduler.kt`：算到下一次提醒时刻的 `initialDelay`，建 24h 周期任务：
  ```kotlin
  val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
      .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
  WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
      "daily_reminder", ExistingPeriodicWorkPolicy.UPDATE, request)
  ```
- `ReminderWorker.doWork()`：建通知渠道 + 弹通知，点按回 `MainActivity`。

### 关键 API
- `PeriodicWorkRequestBuilder<W>(重复周期, 单位)`。
- `enqueueUniquePeriodicWork(name, UPDATE, req)`：`UPDATE` 策略避免重复排队。
- `cancelUniqueWork("daily_reminder")`：关闭提醒。

### 踩坑注意
- **周期下限 15 分钟**：24h 合法；但不能做「每 5 秒」。
- Android 8+ 必须建 `NotificationChannel`（`ReminderWorker` 里已建 `jump_reminder`）。
- 通知权限：Android 13+ 在 `MainActivity` 申请 `POST_NOTIFICATIONS`，否则不弹。

---

## 12. 音效：MediaPlayer + AudioTrack

### 核心思想
- `MediaPlayer`：播放音频文件（素材），一行搞定。
- `AudioTrack`：更底层，直接写 PCM 采样 buffer，可**实时合成**音调（无需素材文件）。
本项目策略：**素材优先，合成兜底**——素材缺失/播放失败自动回退 AudioTrack，保证反馈永不中断。

### 本项目用法
- `audio/SoundPlayer.kt`：`play(resId){ fallback }` 先 `MediaPlayer.create` 播 `res/raw` 素材，异常/错误监听里 `runCatching { fallback() }` 调合成音。
  ```kotlin
  fun reward() { if (soundOn) vibrate(); play(R.raw.snd_reward) { synthReward() } }
  ```
- `tone(freq, ms)`：`44100Hz` 采样，正弦波 +  Attack/Release 包络，写 `AudioTrack` 播放。
- `reward/complete` 附带振动（`Vibrator.vibrate`）。

### 关键 API
- `MediaPlayer.create(ctx, resId)` / `setOnCompletionListener{ release() }`。
- `AudioTrack.Builder().setAudioFormat(...).build()` + `track.write(buf,0,n)`。
- `Executors.newSingleThreadExecutor()`：播放放子线程，不阻塞 UI。

### 踩坑注意
- **`MediaPlayer` 必须 `release()`**：否则句柄泄漏；本项目在 `setOnCompletionListener`/`setOnErrorListener` 释放。
- 合成音用 `MODE_STATIC` + 轮询 `playState` 等播完再 `release`，避免提前释放。
- `soundOn=false` 时直接 return，连振动也跳过。

---

## 13. 手动依赖注入（AppContainer）

### 核心思想
依赖注入 = 把「对象怎么创建、依赖谁」从使用处抽离到统一装配点。本项**不用 Hilt**，而是手写 `AppContainer` 在 `Application.onCreate` 一次性 `new` 全部单例与 VM 工厂。优点：零注解处理、可读；缺点：依赖图变大后接线成本高。

### 本项目用法
- `di/AppContainer.kt`：
  ```kotlin
  class AppContainer(app: Application) {
      private val db = AppDatabase.getInstance(app)
      val jumpRepository = JumpRepository(db)
      val prefsRepository = PreferencesRepository(app)
      val soundPlayer = SoundPlayer(app)
      val sessionFactory = SessionViewModel.Factory(prefsRepository, childrenRepository)
      // ... 其余 VM 工厂
  }
  ```
- `JumpDailyApplication` 持有 `lateinit var container`，`MainActivity` 取 `application.container` 传给 `AppRoot`，页面 `viewModel(factory = container.xxxFactory)` 拿 VM。

### 关键 API
- `ViewModelProvider.Factory`：每个 VM 自带 `Factory`（`create(modelClass)` 里 `return XxxViewModel(...) as T`），`UNCHECKED_CAST` 抑制。
- `viewModel(factory = ...)`：Compose 侧获取带参 VM 的标准方式。

### 踩坑注意
- VM 若需 Activty 作用域共享（如 `SessionViewModel`），确保 `viewModel(factory=)` 多次调用拿到同一实例——本项在 `AppRoot` 顶层创建后向下传递，避免重复构造丢状态。
- 新增依赖：记得在 `AppContainer` 接线 + 在对应 VM 的 `Factory` 透传，否则 `Unknown VM` 异常。

---

## 14. Gradle / AGP 构建体系

### 核心思想
Gradle 是构建引擎，AGP（Android Gradle Plugin）提供 Android 构建任务，`kapt` 处理注解（`@Dao`/`@Entity` 生成代码），Compose 编译器是独立插件、版本须与 Kotlin 对齐。

### 本项目版本矩阵（务必锁定）
| 组件 | 版本 | 原因 |
|------|------|------|
| Kotlin | 1.9.24 | 主语言 |
| Compose Compiler | 1.5.14 | **必须匹配 Kotlin 1.9.24**（1.5.15 要求 Kotlin 1.9.25） |
| AGP | 8.5.2 | — |
| Gradle wrapper | 8.9 | AGP 8.5.2 **不兼容 Gradle 9**（9.0 移除 AGP 调用的 `Configuration.fileCollection(Spec)`） |
| compileSdk/targetSdk/minSdk | 34/34/26 | Android 8.0+ |

### 本项目用法
- `app/build.gradle.kts`：`kotlinCompilerExtensionVersion = "1.5.14"`、`id("org.jetbrains.kotlin.kapt")`。
- 签名：`signingConfigs.release` 从根 `local.properties` 读 `KEYSTORE_*`，缺失则跳过（不影响 debug）。
- 依赖源：本机用阿里云镜像 `maven.aliyun.com/repository/google`（含 `tasks-vision`），仓库未配 `google()`。

### 踩坑注意
- **Compose 与 Kotlin 版本错配** → 编译报 `This version of the Compose Compiler requires Kotlin version 1.9.25`。对齐即可。
- **Gradle 版本错配** → `Unable to find method '...Configuration.fileCollection(Spec)'`。锁 8.9。
- **`Unresolved reference 'util'`** → `signingConfigs` 内 `java.util.Properties()` 需在文件顶部 `import java.util.Properties` 后改用 `Properties()`。
- `packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"`：排除 MediaPipe 许可文件冲突。

---

> 本文与 [ARCHITECTURE.md](./ARCHITECTURE.md) 互为补充：架构文档讲「怎么分层」，本文讲「每层用了什么技术、为什么这么用」。如某技术需进一步展开（如 Room Migration 实战、MediaPipe 自定义模型训练），可在对应章节续写。
