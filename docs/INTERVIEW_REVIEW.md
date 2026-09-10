# JumpDaily · Android 高级工程师面试复习提纲

> 本提纲**直接服务于你的面试主线**，并**完整复用本项目已沉淀的 25 份深度文档 + 真实源码**。
> 用法：每个维度给出「高频考点 → 本项目实战佐证 → 文档/代码索引」。复习时**先看考点，再点开对应文档对照本项目代码**，用真实工程把八股答出深度。
> 文档体系总入口见 [ARCHITECTURE.md 第 12 节](./ARCHITECTURE.md)。

---

## 0. 复习策略（先读这段）

| 阶段 | 目标 | 动作 |
|------|------|------|
| 第 1 周 | Framework 地基 | 精读 `ANDROID_ADVANCED_CORE.md` + `COROUTINE_EXCEPTION_TIMEOUT.md`，手推 Activity 启动 / 事件分发 / 绘制流程 |
| 第 2 周 | 性能 + 协程/流 | 精读 `COMPOSE_RECOMPOSITION.md` + `CAMERAX_THROTTLE.md` + `R8_RELEASE.md`，能现场讲出本项目卡顿根因与修复 |
| 第 3 周 | 架构 + Jetpack | 精读 `COROUTINE_FLOW_VM.md` + `NAVIGATION_ARCH.md` + `DATASTORE_MULTICHILD.md` + `ROOM_MIGRATION.md` |
| 第 4 周 | 工程化 + 发布 + 模拟面试 | 精读 `CI_PIPELINE.md` + `STORE_PUBLISHING.md` + `TESTING.md`，刷文末 30 道自测题，对着 `SOURCE_TOUR.md` 走查源码 |

**面试加分心法**：每个知识点都准备一句「我在 JumpDaily 里是这样落地的」——把八股变成项目实战，区分度立现。

---

## 1. Android Framework 与底层机制（必考）

### 1.1 Binder / IPC
- **考点**：为什么 Android 用 Binder 不用共享内存？一次跨进程调用几次拷贝？`ServiceManager`、`AIDL` Stub/Proxy、`oneway`、Binder 线程池、死亡通知 `linkToDeath`。
- **本项目佐证**：`ReminderWorker` 通过 `WorkManager` 调度（`WORKMANAGER_REMINDER.md`），其底层跨进程由系统 `IPackageManager`/`IActivityManager` Binder 调用完成；理解 Binder 才能讲清「为什么后台提醒会被厂商杀」。
- **索引**：`ANDROID_ADVANCED_CORE.md` §Framework 源码 / `WORKMANAGER_REMINDER.md` §Doze 与厂商限制。

### 1.2 Handler / Looper / MessageQueue
- **考点**：一个线程怎么有多个 Looper？`Message` 复用池、`Barrier`（同步屏障）与 `Choreographer` 的异步消息、IdleHandler、主线程 `Looper.loop()` 死循环为什么不会卡？
- **本项目佐证**：CameraX 的 `addListener(Runnable, ContextCompat.getMainExecutor(ctx))`（`CameraTrainingScreen.kt:268`）本质就是把相机初始化结果 post 到主线程消息队列；`withTimeoutOrNull` 协程取消依赖 `Handler` 定时。
- **索引**：`COROUTINE_EXCEPTION_TIMEOUT.md` §协作式取消；`CAMERAX_THROTTLE.md` §主线程执行器。

### 1.3 四大组件启动与 AMS/WMS
- **考点**：`Activity` 启动全流程（App→AMS→Zygote→ApplicationThread→ActivityThread→performLaunchActivity）、`onCreate` 之前做了什么、`onSaveInstanceState` 时机、`WMS` 与窗口层级、`ViewRootImpl`。
- **本项目佐证**：`JumpDailyApplication` 因 Manifest 移除了 `WorkManagerInitializer` 而需自己实现 `Configuration.Provider`（`WORKMANAGER_REMINDER.md`）；首页返回用 `finishAffinity` 而非逐页 pop（`NAVIGATION_ARCH.md`）。
- **索引**：`NAVIGATION_ARCH.md` §首页返回 finishAffinity；`WORKMANAGER_REMINDER.md` §Configuration.Provider 坑。

### 1.4 View 绘制与事件分发
- **考点**：`Measure→Layout→Draw` 三次遍历、`requestLayout`/`invalidate` 区别、事件分发 `dispatchTouchEvent→onInterceptTouchEvent→onTouchEvent`、滑动冲突、`Choreographer` 与 VSYNC。
- **本项目佐证**：摄像头页卡顿根因 = 每帧骨架写 Compose state 触发整页重组 + 相机预览（`COMPOSE_RECOMPOSITION.md`）；`FloatingJumpBar` 拖动吸边用 `detectDragGestures` + `change.consume()` 防误触（`COMPOSE_GESTURE_LAYOUT.md`）。
- **索引**：`COMPOSE_RECOMPOSITION.md` §重组范围对比；`COMPOSE_GESTURE_LAYOUT.md` §FloatingJumpBar 实战。

### 1.5 ART / 内存
- **考点**：ART 与 Dalvik 区别、AOT/JIT、GC 类型（CMS/CC）、对象分配、大对象、内存碎片。
- **索引**：`ANDROID_ADVANCED_CORE.md` §ART；`R8_RELEASE.md` §release 包体积。

---

## 2. 性能优化（必考，最易现场编程）

### 2.1 卡顿 / 掉帧
- **考点**：16.6ms 帧预算、`Choreographer.FrameCallback`、`Profile GPU Rendering`、过度绘制、`RecyclerView` 复用、`DiffUtil`、`RecyclerView.ItemAnimator`、主线程耗时。
- **本项目实战（重点背）**：
  - 摄像头页卡顿根因：每帧骨架 `skeleton.value = pts` 写在组合体里 → 整棵子树（含 `AndroidView` 相机预览）每帧重组。修复：骨架**只在 Canvas 绘制阶段读**（`onLandmarks` 里只更新 `hasPose` 翻转位，`CameraTrainingScreen.kt:185-190`）。
  - 高频 state（计时/积分/计数）下沉到子组件 `collectAsStateWithLifecycle`，父页只留 `running/paused/result` 等低频 state。
  - `ImageAnalysis` 帧节流 66ms（15fps）省 50%+ 推理开销（`CAMERAX_THROTTLE.md`）。
- **索引**：`COMPOSE_RECOMPOSITION.md`（全篇）、`CAMERAX_THROTTLE.md`、`COMPOSE_ANIMATION.md`。

### 2.2 内存泄漏 / ANR
- **考点**：常见泄漏（匿名内部类持 Activity、`Flow` 未取消、`static` 集合、监听器未反注册）、`LeakCanary`、`StrictMode`、ANR 三类（输入/广播/Service）、`BroadcastQueue` 超时。
- **本项目实战**：
  - `viewModelScope` 自动取消（`TrainingViewModel.kt:146-156` 的 collect 循环，离开页面自动取消）。
  - `future.get()` 已加超时保护（`CameraTrainingScreen.kt:226` 改为 `future.get(10_000L, TimeUnit.MILLISECONDS)`），避免相机初始化在主线程无限挂起 → ANR。
  - TTS `OnInitListener` 必须每次 `new` 实例，否则华为 EMUI 回调不触发（`R8_RELEASE.md` §TTS 坑）。
- **索引**：`COROUTINE_EXCEPTION_TIMEOUT.md`；`R8_RELEASE.md`；`TTS_ARBITRATION.md`。

### 2.3 启动优化 / 包体积
- **考点**：冷启动三阶段、`<profileable>`/`<profileinstaller>`、`Baseline Profile`、`R8`/`ProGuard`、`AndResGuard`、仅 64 位、资源混淆、只留中文。
- **本项目实战**：`profileinstaller:1.3.1` 已引入但 `baseline-prof.txt` 尚未生成（`BASELINE_PROFILE.md`）；`minifyEnabled` 现状、仅 ARM ABI、仅中文本地化（`R8_RELEASE.md`）；APK 命名与多渠道（`TECH_STACK.md` §14）。
- **索引**：`BASELINE_PROFILE.md`、`R8_RELEASE.md`、`FLAVOR_ADVANCED.md`。

---

## 3. Kotlin 语言（必考）

### 3.1 协程
- **考点**：挂起函数本质（CPS 变换 + 状态机）、`suspend` 不是线程、`CoroutineScope`/`Job`/`Dispatcher`、`viewModelScope`/`lifecycleScope`、协作式取消（`isActive`/`ensureActive`）、`supervisorScope` 异常不连坐、`launch` vs `async` 异常暴露时机、`withContext` 切换线程、`Mutex`、`Channel`、`Flow` 冷流热流。
- **本项目实战**：
  - `coroutineScope` vs `supervisorScope`：Worker/VM 内并行子任务失败隔离（`COROUTINE_EXCEPTION_TIMEOUT.md`）。
  - `stateIn(WhileSubscribed(5000))` 三参数防泄漏（`COROUTINE_FLOW_VM.md`）。
  - `flatMapLatest`/`combine` 在 `RecordsViewModel`/`SessionViewModel`（`COROUTINE_FLOW_VM.md`）。
  - 一次性事件用 `StateFlow` 会合并的局限 + `Channel`/`SharedFlow` 取舍（`COROUTINE_FLOW_VM.md` §一次性事件）。
- **索引**：`COROUTINE_FLOW_VM.md`、`COROUTINE_EXCEPTION_TIMEOUT.md`。

### 3.2 语言特性
- **考点**：`data class`/`sealed class`/`enum`、`inline`/`noinline`/`crossinline`、`reified`、属性委托（`by lazy`/`by viewModels`/`by remember`）、`inline class`/`value class`、协变逆变、`@JvmOverloads`/`@JvmStatic`。
- **本项目实战**：`Screen` 密封类作路由表（`NAVIGATION_ARCH.md`）；`Room` 的 `@Entity data class`；`AppDatabase` 伴生单例 `by lazy`；`AppContainer` 手动 DI。
- **索引**：`KOTLIN_ANDROID_USAGE.md`、`NAVIGATION_ARCH.md`、`ROOM_MIGRATION.md`。

### 3.3 序列化
- **考点**：`kotlinx.serialization` vs `Parcelable` vs `Serializable` vs `Gson`、`@Serializable`、proto、`DataStore` 的 protobuf 内部机制。
- **本项目实战**：`datastore-preferences`（protobuf 文件 `jump_prefs.preferences_pb`）、`SuspendedSession` 手写逐字段映射；Typed DataStore + `@Serializable` 为进阶蓝图（`DATASTORE_KOTLIN_SERIALIZATION.md`）。
- **索引**：`DATASTORE_KOTLIN_SERIALIZATION.md`、`DATASTORE_MULTICHILD.md`。

---

## 4. 架构与模块化（必考，高阶重灾区）

### 4.1 MVVM / MVI / 单向数据流
- **考点**：`ViewModel` 不持 View 引用、`UiState` 不可变、`StateFlow`/`LiveData` 区别、事件 vs 状态、`SideEffect`、`Store`/`Reducer`、`Unidirectional Data Flow`。
- **本项目实战**：`_uiState: MutableStateFlow` + 只读出口（`TrainingViewModel.kt:60-126`）；`statsLoaded` 真实首帧标志解决「每次开 App 弹成就」（`COROUTINE_FLOW_VM.md`）；`AppContainer` 手动 DI 分层。
- **索引**：`COROUTINE_FLOW_VM.md`、`SOURCE_TOUR.md` §四步阅读顺序。

### 4.2 Repository / 数据源隔离
- **考点**：`Repository` 屏蔽数据源（本地/远程）、`mediator`、`Paging`、离线优先。
- **本项目实战**：`JumpRepository`/`ChildrenRepository`/`PreferencesRepository` 分层；积分/奖品按 `childId` 动态 key 隔离、每 3 秒批量 flush（`DATASTORE_MULTICHILD.md`）。
- **索引**：`DATASTORE_MULTICHILD.md`、`ROOM_MIGRATION.md`。

### 4.3 模块化 / 组件化
- **考点**：`implementation`/`api` 边界、`:feature`/`:core`/`:data` 模块、`dynamic-feature`、路由框架、模块间解耦。
- **本项目现状**：单 module 扁平化，**模块化拆分是待实施蓝图**（`ANDROID_ADVANCED_CORE.md` §架构与模块化；`CI_PIPELINE.md` 提及按 flavor 拆）。面试时坦诚「当前单 module，规划按 core/feature/data 拆分」即可。
- **索引**：`ANDROID_ADVANCED_CORE.md`、`CI_PIPELINE.md`。

---

## 5. Jetpack 全家桶（必考）

### 5.1 Jetpack Compose
- **考点**：重组原理（`@Composable` 非函数调用、`Composer`/`SlotTable`）、`remember`/`derivedStateOf`/`rememberSaveable`、状态提升、`SideEffect`/`DisposableEffect`/`LaunchedEffect`、`produceState`、`collectAsStateWithLifecycle`、`Modifier` 顺序、自定义 `Layout`、动画 `Animatable`/`InfiniteTransition`、`graphicsLayer`。
- **本项目实战**：高频 state 下沉（`COMPOSE_RECOMPOSITION.md`）；`derivedStateOf` 派低频布尔避免每帧重组；无限动画收敛叶子（`COMPOSE_ANIMATION.md`）；手势拖动吸边（`COMPOSE_GESTURE_LAYOUT.md`）。
- **索引**：`COMPOSE_RECOMPOSITION.md`、`COMPOSE_ANIMATION.md`、`COMPOSE_GESTURE_LAYOUT.md`、`COMPOSE_THEME_MATERIAL3.md`。

### 5.2 Navigation Compose
- **考点**：`NavHost`/`composable`、参数传递、`popUpTo`/`launchSingleTop`、`navigate` 去重、深层链接、`NavType`、类型安全路由（2.8.0+）。
- **本项目实战**：`Screen` 密封路由 + 字符串 route；底部 `popUpTo(Home){saveState}+launchSingleTop` 但刻意不开 `restoreState`；**类型安全路由是 2.8.0+ 升级蓝图**（当前 2.7.7，`NAVIGATION_DEEPLINK.md`）；Activity 级 VM 跨页共享实现训练会话挂起续跳。
- **索引**：`NAVIGATION_ARCH.md`、`NAVIGATION_DEEPLINK.md`。

### 5.3 Room
- **考点**：`@Entity`/`@Dao`/`@Query`、协程支持、`Flow` 查询、迁移（手写 `Migration` vs `AutoMigration` vs `fallbackToDestructiveMigration`）、`exportSchema`、索引、`@Relation`/`@Embedded`、事务。
- **本项目实战**：`AppDatabase v1 + fallbackToDestructiveMigration`（尚无 Migration）；`ROOM_MIGRATION.md` 给出 v1→v2 手写 Migration 完整样例（给 `jump_records` 加 `mode` 列带 DEFAULT）+ `room-testing` 迁移测试。
- **索引**：`ROOM_MIGRATION.md`、`DATASTORE_MULTICHILD.md`。

### 5.4 DataStore / WorkManager / CameraX / Lifecycle
- **考点**：`DataStore` 事务性、`Preferences` vs `Proto`；`WorkManager` 约束/加急/Periodic 不精确；`CameraX` `ImageAnalysis`/`Preview`/`Strategy`；`Lifecycle` 状态机/`RepeatOnLifecycle`。
- **本项目实战**：积分隔离（`DATASTORE_MULTICHILD.md`）；提醒调度（`WORKMANAGER_REMINDER.md`）；帧节流（`CAMERAX_THROTTLE.md`）；`collectAsStateWithLifecycle`（`COROUTINE_FLOW_VM.md`）。
- **索引**：对应四份文档。

---

## 6. 并发与线程（与协程重叠，单列为高频深挖）

- **考点**：线程池参数、`Executors`、为什么子线程不能更新 UI、`HandlerThread`、`IntentService`（已废弃）、`CoroutineDispatcher` 映射（`Dispatchers.IO/Main/Default`）、`limitedParallelism`、`Channel` 与 `Mutex`、`并发集合`。
- **本项目实战**：相机分析用 `executor`（`ContextCompat.getMainExecutor` 或 `Executors.newSingleThreadExecutor`）；模型加载 `withContext(Dispatchers.IO)`（`CameraTrainingScreen.kt:279`）；TTS 全局仲裁用 `VoiceSpeaker` 单出口 + 静默窗口（`TTS_ARBITRATION.md`）。
- **索引**：`CAMERAX_THROTTLE.md`、`TTS_ARBITRATION.md`、`COROUTINE_EXCEPTION_TIMEOUT.md`。

---

## 7. 存储（中频）

- **考点**：`SharedPreferences` 缺陷（主线程、全量读、ANR）、`DataStore` 优势、`Room` 选型、`MMKV`、`文件/ ContentValues`、加密（`EncryptedSharedPreferences`/`SQLCipher`）。
- **本项目实战**：仅用 `Room` + `DataStore`（无 `SharedPreferences`）；积分/奖品/设置走 `PreferencesRepository`（`DATASTORE_MULTICHILD.md`、`DATASTORE_KOTLIN_SERIALIZATION.md`）。
- **索引**：`DATASTORE_MULTICHILD.md`、`DATASTORE_KOTLIN_SERIALIZATION.md`、`ROOM_MIGRATION.md`。

---

## 8. 工程化与质量（高阶加分）

### 8.1 Gradle / 构建
- **考点**：`Kotlin DSL`、`productFlavors`、`buildTypes`、`applicationVariants` 重命名、`dependencyResolutionManagement`、增量编译、`configuration cache`、`R8`/`ProGuard` 区别（`proguard-android.txt` vs `-optimize.txt`）。
- **本项目实战**：三渠道 `official/huawei/xiaomi`（`FLAVOR_ADVANCED.md`）；APK 自动命名（`TECH_STACK.md` §14）；`proguard-android.txt` 改坏 MediaPipe 的坑（`R8_RELEASE.md`）。
- **索引**：`FLAVOR_ADVANCED.md`、`R8_RELEASE.md`、`TECH_STACK.md`。

### 8.2 测试 / CI
- **考点**：测试金字塔、JVM 单测（`JUnit5`/`turbine`/`mockk`/`coroutines-test`）、`Robolectric`、`Espresso`、Room 迁移测试、`Macrobenchmark`、CI 门禁（lint/测试/构建）。
- **本项目实战**：现有 3 个纯逻辑单测（`DateUtilsTest`/`PoseCounterTest`/`RewardsTest`）；VM/UI 测试为缺口（`TESTING.md`）；CI 为待实施蓝图（`CI_PIPELINE.md`）。
- **索引**：`TESTING.md`、`CI_PIPELINE.md`、`BASELINE_PROFILE.md`。

---

## 9. 发布与合规（儿童类 App 重灾区，易加分）

- **考点**：App 备案、软著、隐私政策公网 URL、儿童个人信息保护（`个人信息保护法`/`儿童个人信息网络保护规定`）、权限最小化、摄像头不上传声明、无广告/无 SDK 埋点承诺、签名一致性、64 位强制、备案号填写。
- **本项目实战**：`PrivacyScreen` 10 章政策（含儿童保护、摄像头不上传、无广告/统计 SDK）；CAMERA `required=false` + 三步法动态申请；POST_NOTIFICATIONS 版本门控；首启 `privacy_accepted` 同意框；⚠️ App 备案/软著/隐私公网 URL 待补（`STORE_PUBLISHING.md`、`PERMISSION_PRIVACY.md`）。
- **索引**：`STORE_PUBLISHING.md`、`PERMISSION_PRIVACY.md`。

---

## 10. 跨平台与前沿（了解即可，展示广度）

- **考点**：`KMP`/`Compose Multiplatform`、`Flutter` 与原生桥接、`React Native`/`Hermes`、`端侧 AI`（MediaPipe/TFLite/ONNX）、`Baseline Profile`。
- **本项目实战**：`MediaPipe tasks-vision` 官方预置 `pose_landmarker_lite.task`（`MEDIAPIPE_POSE.md`）；自定义模型训练为进阶参考；`Baseline Profile` 待实施（`BASELINE_PROFILE.md`）；你另有 Flutter 项目 MicroTrip（简历可提跨端经验）。
- **索引**：`MEDIAPIPE_POSE.md`、`BASELINE_PROFILE.md`、`ANDROID_ADVANCED_CORE.md` §跨平台。

---

## 11. 高频自测题（30 道，闭卷自答）

1. Binder 一次跨进程调用几次内存拷贝？为什么不用共享内存？
2. `Looper.loop()` 是死循环，为什么主线程不会卡死/退出？
3. 同步屏障（`Barrier`）是什么？`Choreographer` 怎么用它保证绘制优先？
4. `Activity` 启动从 `startActivity` 到 `onResume` 跨了哪几个进程？
5. 事件分发 `onInterceptTouchEvent` 返回 true/false 各意味什么？滑动冲突怎么解？
6. `requestLayout` 和 `invalidate` 区别？会触发哪些遍历？
7. 为什么 Compose 每帧写 state 会导致整页重组？怎么破？
8. `derivedStateOf` 解决什么问题？和 `remember` 区别？
9. `StateFlow` 和 `LiveData` 区别？`SharedFlow` 适合什么场景？
10. `stateIn(WhileSubscribed(5000))` 三个参数分别干嘛？
11. `viewModelScope` 为什么能自动取消协程？
12. `launch` 里抛异常和 `async` 里抛异常，暴露时机有何不同？
13. `supervisorScope` 和 `coroutineScope` 异常传播差异？举例。
14. 协作式取消是什么？为什么 `Thread.stop()` 不能用？
15. `withTimeout` vs `withTimeoutOrNull` 区别？本项目哪里该用？
16. `Dispatchers.Main.immediate` 有什么用？
17. `Flow` 是冷流还是热流？`collect` 取消会发生什么？
18. Room 迁移有几种方式？`fallbackToDestructiveMigration` 风险？
19. `AutoMigration` 的限制？什么时候必须手写 `Migration`？
20. `DataStore` 相比 `SharedPreferences` 解决了哪些痛点？底层是什么？
21. `DataStore` 的 `edit { }` 是事务的吗？并发写会怎样？
22. `CameraX ImageAnalysis` 的 `STRATEGY_KEEP_ONLY_LATEST` 是什么？
23. 为什么分析帧要节流？本项目节流到多少 fps？依据？
24. `R8` 和 `ProGuard` 关系？`proguard-android.txt` 和 `-optimize.txt` 区别？
25. release 包为什么 logcat 不可靠？本项目怎么诊断致命状态？
26. `Baseline Profile` 是什么？怎么生成？`profileinstaller` 干嘛的？
27. `WorkManager` 的 `PeriodicWorkRequest` 准时不？不准怎么桥接？
28. 厂商 ROM（华为/小米）对后台提醒的限制？怎么提升到达率？
29. `Navigation` 类型安全路由怎么实现？当前项目为什么没用？
30. 儿童类 App 上架必须准备哪些合规材料？摄像头数据能上传吗？

> 答案索引散落在本提纲各节与 25 份文档；建议每题先口述再查文档补全。

---

## 12. 源码走查清单（面试前过一遍）

- [ ] 顺着 `AppContainer` 看依赖装配（`SOURCE_TOUR.md` §四步）
- [ ] 走查一次跳绳生命周期：`LaunchedEffect` → `vm.start` → 相机绑定 → 计数 → 暂停/挂起 → 落库（`SOURCE_TOUR.md` §时序图）
- [ ] 在 IDE 里 `Ctrl+B` 跳 `CameraTrainingScreen.kt:185-190` 看骨架绘制阶段读
- [ ] 看 `TrainingViewModel.kt:60-126` 的 `_x`/`stateIn` 模式
- [ ] 看 `PreferencesRepository.kt` 动态 key 隔离与批量 flush
- [ ] 看 `VoiceSpeaker.kt` 优先级仲裁与 `lastFeedbackTs` 共用
- [ ] 看 `ReminderScheduler`/`ReminderWorker` 提醒调度
- [ ] 看 `build.gradle.kts` 多渠道与命名
- [ ] 跑一次 `./gradlew testDebugUnitTest` 看现有 3 个单测
- [ ] 跑一次 `tools/build_apk.sh huawei release` 出一包

---

*文档随面试复习推进维护；如发现某知识点本项目尚无实战佐证，可在对应专题文档补"待实施"蓝图，并在面试中坦诚说明——这比编造更可信。*
