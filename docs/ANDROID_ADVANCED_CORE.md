# Android 高级开发还需掌握的核心技术

> 面向"已能写 App、要向高级/架构师进阶"的开发者。按主题梳理核心技术栈，并标注**本项目对应点**（JumpDaily 哪些地方已经用上、哪些还没涉及），帮你把"理论"落到"自己的代码"。
> 配套文档：Framework 层见 [`R8_RELEASE.md`](./R8_RELEASE.md)（release 坑）、[`CAMERAX_THROTTLE.md`](./CAMERAX_THROTTLE.md)（相机底层）、[`DATASTORE_KOTLIN_SERIALIZATION.md`](./DATASTORE_KOTLIN_SERIALIZATION.md)（存储内部机制）。

---

## 1. Android Framework 源码与系统机制

| 技术点 | 为什么高级必学 | 本项目对应 |
|--------|----------------|------------|
| **Binder / IPC** | 跨进程通信基石（AMS、WMS、Service 都靠它） | 间接用到：`TextToSpeech`/`CameraX`/`MediaStore` 都跨进程；理解"跨进程调用为何慢/为何不能传大对象" |
| **Handler / Looper / MessageQueue** | 主线程消息机制、ANR 根因、Choreographer 帧调度 | 本项目已用协程替代手写 Handler；但"主线程卡顿→掉帧/ANR"原理必须懂 |
| **Activity/Window/View 启动与绘制** | `setContentView`→ViewRootImpl→measure/layout/draw 全流程 | Compose 走 `AndroidComposeView` 桥接，原理仍同（见 [`COMPOSE_RECOMPOSITION.md`](./COMPOSE_RECOMPOSITION.md) 重组=重组+布局+绘制） |
| **AMS / WMS / PMS** | 四大组件调度、任务栈、权限判定 | 返回栈 `finishAffinity`（`AppRoot.kt:133`）、深层链接（`NAVIGATION_DEEPLINK.md`）都绕不开任务栈 |
| **Zygote / ART / 类加载** | 进程孵化、dex 加载、R8/混淆影响 | [`R8_RELEASE.md`](./R8_RELEASE.md) 里 MediaPipe 被 R8 优化改坏即 ART 类加载相关 |

> 进阶路径：从 `ActivityThread.main()` → `Looper.loop()` → `Choreographer` 帧回调读起；配合《Android 开发艺术探索》《深入理解 Android》卷。

---

## 2. 性能优化（高级岗高频考点）

| 维度 | 技术 | 本项目已做 |
|------|------|------------|
| **卡顿 / 掉帧** | 布局层级、过度绘制、主线程耗时、重组范围 | 摄像头页重组优化（`COMPOSE_RECOMPOSITION.md`）、骨架只 Canvas 绘制阶段读 |
| **内存** | 泄漏（Context/Handler/协程）、Bitmap、LargeHeap | 协程作用域用对（`viewModelScope` 自动取消）、`proxy.close()` 防相机缓冲区泄漏（`CAMERAX_THROTTLE.md`） |
| **ANR** | 主线程 IO/锁、Binder 调用过长、广播超时 | ⚠️ 相机绑定 `future.get()` 无超时（`COROUTINE_EXCEPTION_TIMEOUT.md:5.1` 建议补） |
| **启动速度** | 冷启动 topic、ContentProvider 初始化、`App Startup`、Baseline Profile | ⚠️ Baseline Profile 待实施（`BASELINE_PROFILE.md`） |
| **包体积** | R8/混淆、资源精简、ABI 拆分、Android App Bundle | 仅 ARM ABI + 仅中文 + R8（`R8_RELEASE.md`、`STORE_PUBLISHING.md`） |
| **电量 / 发热** | 后台任务、定位/相机/传感器唤醒 | 相机 15fps 节流（`CAMERAX_THROTTLE.md`）、提醒用 WorkManager（`WORKMANAGER_REMINDER.md`） |
| **工具** | Profiler / Systrace / Perfetto / Layout Inspector / LeakCanary | 建议接入 LeakCanary（debug）、Perfetto 抓帧 |

---

## 3. 架构与模块化

| 技术 | 说明 | 本项目对应 |
|------|------|------------|
| **MVVM + 单向数据流** | StateFlow 出口 + 事件入 | 全部 VM（`COROUTINE_FLOW_VM.md`） |
| **Repository 单一数据源** | VM 不直接碰 DB/网络 | `JumpRepository`/`PreferencesRepository`/`ChildrenRepository` |
| **依赖注入（Hilt / Koin / 手动）** | 解耦、可测 | 本项目 `AppContainer` 手动 DI（可平滑迁 Hilt） |
| **模块化 / 组件化** | `:feature`/`:core`/`:data` 多模块、路由解耦 | 当前单模块；上规模必拆（见下"进阶"） |
| **Clean Architecture** | domain/usecase 隔离业务 | 小型 App 未必需要，但 usecase 思想可吸收 |
| **MVI** | 状态归一并不可变 | 本项目是 MVVM，可演进 |

---

## 4. Jetpack 全家桶（高级应熟练）

| 组件 | 本项目已用 |
|------|------------|
| Compose（Material3、动画、手势） | ✅ 全量 UI |
| Navigation Compose | ✅（2.7.7，类型安全路由待升级） |
| ViewModel / Lifecycle / LiveData→StateFlow | ✅ |
| Room | ✅（`v1 + fallbackToDestructiveMigration`，迁移见 [`ROOM_MIGRATION.md`](./ROOM_MIGRATION.md)） |
| DataStore (Preferences) | ✅（多孩子隔离 [`DATASTORE_MULTICHILD.md`](./DATASTORE_MULTICHILD.md)） |
| WorkManager | ✅（提醒 [`WORKMANAGER_REMINDER.md`](./WORKMANAGER_REMINDER.md)） |
| CameraX | ✅（节流 [`CAMERAX_THROTTLE.md`](./CAMERAX_THROTTLE.md)） |
| Paging | ❌ 数据量小，暂不需要 |
| Hilt | ❌ 手动 DI |
| Startup | ❌ 初始化少 |
| Benchmark / Macrobenchmark | ⚠️ Baseline Profile 待实施 |

---

## 5. 并发与异步（深入）

- **协程原理**：状态机（`suspend` 编译为 `Continuation`）、`Dispatchers` 调度、_channel、_flow 冷/热。
- **Flow 全家族**：`StateFlow`/`SharedFlow`/`callbackFlow`/`flowOn`/`buffer`/`conflate`/`debounce`。
- **Channel**：`Channel`/`actor`（已废弃→用 `actor` 思路的 `Channel`+协程）。
- **与 RxJava 互操作**：老项目迁移常见（`callbackFlow` 桥接）。
- 本项目实战：VM 协程（[`COROUTINE_FLOW_VM.md`](./COROUTINE_FLOW_VM.md)）、异常/超时（[`COROUTINE_EXCEPTION_TIMEOUT.md`](./COROUTINE_EXCEPTION_TIMEOUT.md)）。

---

## 6. 构建体系与工程化

| 技术 | 本项目对应 |
|------|------------|
| **Gradle KTS / 版本目录（catalog）** | ✅ KTS；可进一步上 `libs.versions.toml` |
| **Flavor / BuildType** | ✅ 三渠道（`FLAVOR_ADVANCED.md`） |
| **R8 / 混淆 / 资源缩减** | ✅（`R8_RELEASE.md`） |
| **签名 / 多渠道出包** | ✅（`tools/build_apk.sh`、[`STORE_PUBLISHING.md`](./STORE_PUBLISHING.md)） |
| **CI/CD** | ⚠️ 待实施（`CI_PIPELINE.md`） |
| **测试体系** | ⚠️ 仅 3 个单测（`TESTING.md`） |
| **Baseline Profile** | ⚠️ 待实施（`BASELINE_PROFILE.md`） |
| **Module 拆分** | ❌ 单模块 |

---

## 7. 存储与数据

- **Room 进阶**：关系（@Relation/@Embedded）、事务（`@Transaction`）、DAO 多返回类型、`RawQuery`、与 Paging 结合、迁移策略（[`ROOM_MIGRATION.md`](./ROOM_MIGRATION.md)）。
- **DataStore 内部机制**：protobuf、冷流重放、类型安全（`DATASTORE_KOTLIN_SERIALIZATION.md`）。
- **SQLite / SQLCipher**（加密库）、**Realm / ObjectBox**（替代 ORM）。
- **文件/媒体**：`MediaStore`、`FileProvider`、`scoped storage`（分区存储，Android 10+）。

---

## 8. 多媒体与硬件（本项目强相关）

| 技术 | 本项目对应 |
|------|------------|
| CameraX（Preview/ImageAnalysis/ImageCapture） | ✅ 跳绳姿态分析 |
| OpenGL / GLES / RenderScript | ❌ 用 Canvas 2D 绘制骨架 |
| MediaPipe / TFLite / ONNX（端侧 AI） | ✅ 姿态估计（`MEDIAPIPE_POSE.md`） |
| Audio（TTS / MediaPlayer / ExoPlayer） | ✅ 语音播报（`TTS_ARBITRATION.md`） |
| 传感器（计步/加速度） | ✅ 传感器计数模式（`CountMode.SENSOR`） |

---

## 9. 安全与合规（儿童类 App 重点）

- **权限最小化 + 运行时请求**：本项目已做（`PERMISSION_PRIVACY.md`）。
- **隐私政策 + App 备案 + 软著**：上架强制（`STORE_PUBLISHING.md`）。
- **数据加密**：`EncryptedSharedPreferences`/`AndroidKeyStore`（存 token/密钥，本项目 keystore 在 `local.properties`）。
- **儿童隐私（COPPA / 国内儿童个人信息规定）**：摄像头数据不上传、无广告/统计 SDK（隐私政策已声明）。
- **防逆向**：R8 + 资源混淆（`AndResGuard`）+ root/模拟器检测（可选）。

---

## 10. 跨平台与新技术（加分项）

| 方向 | 说明 | 你已有基础 |
|------|------|------------|
| **Kotlin Multiplatform (KMP)** | 业务逻辑跨 Android/iOS/桌面 | Kotlin 已熟；本项目纯 Android |
| **Compose Multiplatform** | UI 跨端 | Compose 已熟 |
| **Flutter / React Native** | 你已有 Flutter 经验（MicroTrip） | ✅ |
| **Jetpack Glance（桌面小组件）** | AppWidget 现代写法 | 可延伸 |
| **Wear OS / TV / Automotive** | 形态适配 | — |

---

## 11. 软技能与进阶路径建议

1. **读源码**：从 Framework 关键类（ActivityThread、Handler、ViewRootImpl、Resources）读起，配合断点调试。
2. **造轮子**：自己实现轻量 Router / DI / 状态管理，理解轮子为何这么设计。
3. **性能驱动**：用 Profiler 定位真实瓶颈，而非"感觉卡"。
4. **系统设计**：能画清"一次跳绳从传感器→计数→UI→落库→积分→语音"的全链路（[`SOURCE_TOUR.md`](./SOURCE_TOUR.md) 有走查）。
5. **可观测性**：日志/埋点/崩溃上报（本项目用文件落盘诊断 `tts_init.txt`/`pose_error.txt`，可升级 Sentry）。
6. **代码质量门禁**：lint 0 warning、单测门禁、CI（[`TESTING.md`](./TESTING.md)、[`CI_PIPELINE.md`](./CI_PIPELINE.md)）。

---

## 12. 本项目"高级知识点"掌握度自检表

| 高级技术 | 状态 | 文档 |
|----------|------|------|
| 协程/流最佳实践 | ✅ 已落地 | [`COROUTINE_FLOW_VM.md`](./COROUTINE_FLOW_VM.md) |
| 协程异常/超时 | ⚠️ 基础有，超时缺 | [`COROUTINE_EXCEPTION_TIMEOUT.md`](./COROUTINE_EXCEPTION_TIMEOUT.md) |
| Compose 重组优化 | ✅ 已落地 | [`COMPOSE_RECOMPOSITION.md`](./COMPOSE_RECOMPOSITION.md) |
| 手势 | ✅ 浮条范例 | [`COMPOSE_GESTURE_LAYOUT.md`](./COMPOSE_GESTURE_LAYOUT.md) |
| 导航架构 | ✅ 已落地 | [`NAVIGATION_ARCH.md`](./NAVIGATION_ARCH.md) |
| 深层链接/类型安全路由 | ⚠️ 待实施 | [`NAVIGATION_DEEPLINK.md`](./NAVIGATION_DEEPLINK.md) |
| 存储内部机制 | ✅ 已落地 | [`DATASTORE_KOTLIN_SERIALIZATION.md`](./DATASTORE_KOTLIN_SERIALIZATION.md) |
| Room 迁移 | ⚠️ 破坏性兜底，手写迁移待补 | [`ROOM_MIGRATION.md`](./ROOM_MIGRATION.md) |
| 端侧 AI (MediaPipe) | ✅ 已落地 | [`MEDIAPIPE_POSE.md`](./MEDIAPIPE_POSE.md) |
| 性能/包体/R8 | ✅ 已落地 | [`R8_RELEASE.md`](./R8_RELEASE.md)、[`BASELINE_PROFILE.md`](./BASELINE_PROFILE.md) |
| 工程化/CI/测试 | ⚠️ 部分待补 | [`CI_PIPELINE.md`](./CI_PIPELINE.md)、[`TESTING.md`](./TESTING.md) |
| Framework 源码深读 | 📚 需系统投入 | 本文第 1 节路径 |

**小结**：Android 高级开发 = Framework 源码理解（Binder/Handler/AMS/WMS/ART）+ 性能工程（卡顿/内存/ANR/启动/包体）+ 架构（MVVM/模块化/DI）+ Jetpack 全家桶 + 并发（协程/Flow）+ 构建与工程化（Gradle/R8/CI/测试）+ 安全合规 + 跨平台视野。JumpDaily 已覆盖其中大部分实战点，少数标 ⚠️ 的（超时兜底、Baseline Profile、类型安全路由、Room 手写迁移、CI/测试体系）正是你下一步可亲手落地的"高级进阶作业"。
