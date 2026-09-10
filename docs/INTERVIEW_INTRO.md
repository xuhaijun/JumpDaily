# 项目亮点 · 1 分钟自我介绍模板

> 用途：Android 高级工程师 / 架构师岗面试开场自我介绍。以 **JumpDaily（爱跳绳，儿童跳绳 App）** 为旗舰项目，配套 MicroTrip（Flutter 跨端）、Go 技术书籍作为广度补充。
> 用法：把【方括号】里的内容换成你自己的；正文可直接背诵。讲的时候**慢一点、每个技术点只抛钩子，别展开**——把展开留給面试官追问，你才有发挥空间。
> 配套：深度复习看 [`INTERVIEW_REVIEW.md`](./INTERVIEW_REVIEW.md)；每个技术点对应的真实代码与文档在第四节「面试弹药库」给索引。

---

## 一、1 分钟逐字稿（可直接背）

> 各位面试官好，我是【徐海君】，**【5 年】Android 开发经验**，目前主攻 **Jetpack Compose 方向、目标 Android 高级工程师 / 架构师**岗。
>
> 我最近独立做的一个项目叫 **JumpDaily「爱跳绳」**，是一款面向儿童的跳绳计数 App，Kotlin + Jetpack Compose 全栈自研。它没有接任何第三方计步 SDK，计数逻辑是我自己写的——这是项目里我最想聊的技术点。
>
> 第一，计数有**两套方案**：手机传感器用加速度计「落地冲击峰值」启发式计次；摄像头模式用 **CameraX + MediaPipe PoseLandmarker** 做人体 33 关键点检测、髋部起伏计次。这里我踩过也解决了不少真问题：比如 **MediaPipe 原生库只给 ARM 架构**，x86 模拟器上 `System.loadLibrary` 抛的是 `Error` 而非 `Exception`，原 `catch(Exception)` 兜不住会崩，我是按 ABI 做降级 + `Throwable` 兜底日志解决的。
>
> 第二，性能上我做了**组合层的高频状态隔离**：跳绳每秒计时、每跳积分，如果让页面直接读这些 State，会每帧重组整页、连相机预览都卡。我的做法是高频 state 只在子组件内部 `collectAsStateWithLifecycle` 读，骨架数据只在 Canvas **绘制阶段**读，重组范围被压到最小；相机分析帧还做了 **15fps 节流**，发热掉帧明显好转。
>
> 第三，工程化上我踩了 **R8 release 包把 MediaPipe 初始化改坏**的坑（`<clinit>` 被优化触发 `ExceptionInInitializerError`），最终用 `proguard-android.txt`（只压缩混淆、不优化）+ keep 传递依赖解决；语音播报我做了一套**全局优先级仲裁**，避免多通道同帧撞车把话掐断。
>
> 另外我还有 Flutter 跨端项目 MicroTrip，也写过 Go 语言的技术书。我的优势是**底层问题喜欢追到源码、工程上习惯把坑沉淀成文档**。
>
> 以上就是我的简单介绍，接下来您看从哪个方向聊？

---

## 二、结构拆解（为什么要这么讲）

| 段落 | 时间 | 目的 | 关键钩子 |
|------|------|------|----------|
| **开场** | 5s | 身份 + 方向 + 目标岗 | 年限、主攻 Compose、目标级别 |
| **项目定位** | 10s | 一句话说清项目 + 你的不可替代性 | "没接第三方 SDK，计数自己写" |
| **亮点 1：计数/算法** | 15s | 展示算法 + 跨端/AI 集成能力 | MediaPipe 仅 ARM、`Error` 兜底 |
| **亮点 2：性能** | 15s | 展示 Compose 深度 | 高频 state 隔离、绘制阶段读、节流 |
| **亮点 3：工程化** | 15s | 展示 release/稳定性经验 | R8 改坏 `<clinit>`、语音仲裁 |
| **广度 + 收尾** | 10s | 体现持续学习能力 + 抛球给面试官 | Flutter/Go 书、追源码习惯 |

> 原则：**每个亮点 = 做了什么 + 用了什么技术 + 解决了什么真问题**。不要堆名词，名词是给面试官"追问入口"的。

---

## 三、精简版

### 30 秒版（电梯陈述）
> 我是【徐海君】，【5 年】Android 经验，主攻 Compose。最近独立做了儿童跳绳 App **JumpDaily**：自研传感器 + MediaPipe 摄像头双计数；重点解决了 Compose 高频状态重组卡顿、MediaPipe 仅 ARM 架构崩溃、R8 release 改坏初始化三个真问题，并把踩坑沉淀成文档。另有 Flutter 与 Go 技术书经验。

### 60 秒版（取逐字稿的"开场 + 亮点 1 + 亮点 2 + 收尾"）
> 我是【徐海君】，【5 年】Android，目标高级 / 架构师。最近独立做 **JumpDaily「爱跳绳」**（Kotlin+Compose，计数自研不接 SDK）。技术上：① 摄像头计数用 CameraX + MediaPipe PoseLandmarker，解决过原生库仅 ARM、模拟器 `Error` 不兜住的崩溃；② 性能上把计时/积分等高频 state 隔离到子组件内部收集、骨架只在 Canvas 绘制阶段读，并做 15fps 分析节流，根除整页重组卡顿。另有 Flutter 与 Go 技术书经验，习惯追源码、沉淀文档。

---

## 四、面试弹药库（每个亮点可深挖的方向 + 本项目索引）

> 面试官大概率从你的钩子追问。下面每条都对应**本项目真实代码 / 文档**，方便你现场举例不虚。

### A. 计数与算法
- **传感器启发式**：`sensor/JumpDetector.kt`，加速度计"落地冲击峰值"计次 + 陀螺仪"转速过高"判不规范。
- **摄像头计数**：`camera/PoseJumpDetector.kt`（LIVE_STREAM、髋中心 y → `PoseCounter`）、`camera/PoseModelProvider.kt`（模型三级加载）。
- **MediaPipe 仅 ARM 坑**：[`R8_RELEASE.md`](./R8_RELEASE.md) + [`MEDIAPIPE_POSE.md`](./MEDIAPIPE_POSE.md)；`catch(Throwable)` 兜 `UnsatisfiedLinkError`。
- **模型加载离线优先**：assets → filesDir 缓存 → 联网下载三级回退；**下载已加 15s 超时兜底**（见 [`COROUTINE_EXCEPTION_TIMEOUT.md`](./COROUTINE_EXCEPTION_TIMEOUT.md)）。

### B. Compose 性能（最有区分度）
- **高频 state 隔离**：[`COMPOSE_RECOMPOSITION.md`](./COMPOSE_RECOMPOSITION.md)；`TrainingViewModel` 每 3 秒批量 flush 落盘、`derivedStateOf` 派生低频布尔。
- **绘制阶段读**：骨架数据 `mutableStateOf` 只在 `PoseOverlay` Canvas 绘制阶段读，不触发重组。
- **相机节流**：[`CAMERAX_THROTTLE.md`](./CAMERAX_THROTTLE.md)；`ImageAnalysis` 66ms 最小间隔、批量 Path 绘制。
- **Baseline Profile**：[`BASELINE_PROFILE.md`](./BASELINE_PROFILE.md) —— ⚠️ **当前待实施**，但你要能讲清原理（profileinstaller + 预热类/方法，冷启快 20%~40%）与为何收益大（MediaPipe 初始化类多）。

### C. 工程化与稳定性
- **R8/ProGuard**：[`R8_RELEASE.md`](./R8_RELEASE.md)；release 必须用 `proguard-android.txt`（不优化），keep `com.google.protobuf.**`。
- **release 调试**：logcat 在华为 EMUI 被吞 → 关键状态**文件落盘**（`pose_error.txt` / `tts_init.txt`）。
- **语音全局仲裁**：[`TTS_ARBITRATION.md`](./TTS_ARBITRATION.md)；`VoiceSpeaker` 统一入口 + 优先级 + 静默窗口，避免多通道同帧撞车。
- **协程超时**：[`COROUTINE_EXCEPTION_TIMEOUT.md`](./COROUTINE_EXCEPTION_TIMEOUT.md)；相机绑定 `Future.get(10s)`、模型下载 `withTimeoutOrNull(15s)`。

### D. 架构与多模块
- **会话跨页存活**：[`ARCHITECTURE.md`](./ARCHITECTURE.md) 第 7 节；`TrainingViewModel` Activity 级创建 + 浮条续跳。
- **多孩子数据隔离**：[`DATASTORE_MULTICHILD.md`](./DATASTORE_MULTICHILD.md)；DataStore 按 `childId` 动态 key。
- **导航 / 提醒**：`navigation-compose` 基础用法 + WorkManager 提醒（[`WORKMANAGER_REMINDER.md`](./WORKMANAGER_REMINDER.md)）。
- **模块化拆分**：⚠️ **当前待实施蓝图**（单 module；可讲 planned `:core`/`:feature-*` 拆分与收益）。

### E. 广度（被问"还做过什么"时）
- **Flutter 跨端**：MicroTrip（天气/轨迹/步数，和风天气 JWT）。
- **后端 / Go**：企业平台（Go Echo + Kafka + MQTT）、Go 技术书（数据结构/设计模式/分布式）。
- **KMP / Glance / Sentry**：⚠️ **方向②待实施**（见 [`INTERVIEW_REVIEW.md`](./INTERVIEW_REVIEW.md)），可表态"已规划、正在补"。

---

## 五、常见追问与应答要点（不要背，记要点）

| 面试官可能问 | 你要答到位的要点 |
|--------------|------------------|
| "Compose 重组你怎么优化的？" | 高频 state 下放子组件 `collectAsStateWithLifecycle`；骨架只在绘制阶段读；`derivedStateOf` 派生低频布尔；分析帧节流。 |
| "为什么不用第三方计步 SDK？" | 儿童趣味场景 + 自研可控；传感器启发式 + 摄像头双模，可讲精度取舍与降级。 |
| "MediaPipe 在 release 崩怎么查的？" | 先定位 `ExceptionInInitializerError` → R8 优化改坏 `<clinit>` → 换 `proguard-android.txt` + keep；release logcat 不可靠→文件落盘诊断。 |
| "协程你踩过什么坑？" | 取消是协作式（纯 CPU while 要 `isActive`）；`async` 必须 `await`；`withTimeoutOrNull` 包阻塞 IO 的边界（线程不被真打断，靠 connection timeout 回收）。 |
| "你这项目最大的技术债？" | 诚实说：Baseline Profile、模块化、KMP/Glance/Sentry 待实施——但每项你都**知道为什么做、收益是什么**（展示规划力，比假装全做完更加分）。 |
| "为什么想往架构师走？" | 从"解决单点 bug"到"定分层/数据流/稳定性基线"；举例：高频 state 隔离本质是**把重组边界当成架构问题**来设计。 |

---

## 六、反问面试官（收尾抛球，显主动）

准备 2~3 个，按现场选：
1. "团队目前 Compose 的落地程度？有没有 Baseline Profile / 模块化相关的规划？"
2. "客户端和 Framework / 性能团队的协作边界是怎样的？"
3. "这个岗位前 3 个月最希望我解决的是什么问题？"

---

> ⚠️ **诚实原则**：模板里标注"待实施"的项（Baseline Profile / 模块化 / KMP / Glance / Sentry）**不要说成已上线**。面试官更看重你"知道该做、能讲清收益与路径"，这比把蓝图吹成已交付更可信，也经得起追问。
