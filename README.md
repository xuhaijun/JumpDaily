# 爱跳绳 · JumpDaily

一款面向儿童的「天天跳绳」Android 应用（Kotlin + Jetpack Compose），界面卡哇伊、反馈即时，让孩子用了爱不释手。

> 📌 迭代交付与工程变更记录见 [CHANGELOG.md](CHANGELOG.md)。

## ✨ 主要功能

- **多孩子档案**：添加 / 切换 / 删除孩子；每个孩子有独立 emoji 头像与主题色，数据按孩子隔离。
- **传感器实时计数**：加速度计峰值检测落地次数 + 陀螺仪动作规范启发式（检测大幅挥手机=动作不规范）。
- **摄像头姿态估计计数（可选）**：设置里切到「摄像头模式」，用 MediaPipe PoseLandmarker 做人体关键点检测、髋部起伏计数，精度更高；需摄像头权限，首次使用下载/放置 pose 模型。
- **即时正向反馈**（动画 + 音效）：
  - 跳得好 / 每满 10 个 → 彩带爆发 + 上行琶音 + 吉祥物开心 😄
  - 动作不规范 → 纠正提示「站稳小手，绳子甩起来～」+ 柔和双音
  - 节奏偏慢 → 加油鼓励「你可以的！💪」+ 轻快旋律
- **每日提醒**：WorkManager 周期通知，设置里可开关并选择时间（Android 13+ 会申请通知权限）。
- **数据统计**：最近 7 天柱状图、累计个数、最佳单次、打卡天数、连续天数。
- **历史记录**：按天列表，可单条删除。
- **成就墙**：10 个关卡式徽章（首次跳跃 / 百发百中 / 一周达人 …），点亮解锁。
- **跳绳目标 + 达成庆祝**：设置里可设**每日 / 每周目标**；首页与统计页用进度条实时展示距离目标的差距；训练结束时若本次**首次跨过当日目标线**，结果页弹出「🏆 达成今日目标」专属庆祝并播放奖励音效。
- **夜间模式**：设置内一键切换「深空紫蓝」护眼主题，持久化保存、重启保留，孩子晚上跳也不刺眼。
- **卡哇伊 UI + Lottie 动效**：糖果色 / 夜间双主题 Material3、Canvas 手绘吉祥物「跳跳星」、emoji 头像；训练达标用 Lottie 彩带庆祝动画，启动页用 Lottie 旋转星星，几乎无需图片素材。

## 🧱 技术栈

| 模块 | 选型 |
|---|---|
| 语言 / UI | Kotlin 1.9.24 · Jetpack Compose (BOM 2024.09) · Material3 |
| 架构 | MVVM + 单向数据流（`StateFlow` / `collectAsStateWithLifecycle`）· 手动依赖注入 |
| 本地存储 | Room（孩子 / 记录）· DataStore（当前孩子 / 设置） |
| 传感器 / 摄像头 | `SensorManager`（加速度计+陀螺仪）· CameraX + MediaPipe PoseLandmarker（可选姿态计数） |
| 音效 / 动画 | `MediaPlayer` 播放 `res/raw` 卡通音效素材（实时合成兜底）· Lottie 庆祝 / 星星动画 |
| 提醒 | WorkManager 周期任务 + 通知 |
| 构建 | Gradle 8.5 · AGP 8.5.2 |

## 🚀 如何运行

1. 用 **Android Studio（Hedgehog 2023.1+ 或更新）** 打开本目录（`D:\SmallTools\JumpDaily`）。
2. Android Studio 会自动生成 Gradle Wrapper 并下载依赖（首次需联网）。
   - 本机 `maven.google.com` 直连被墙，已在 `settings.gradle.kts` 配置 **阿里云镜像**（`google` / `public` / `gradle-plugin`），可正常解析 AndroidX、Compose、AGP 等依赖。
3. 选择 **Android 8.0+（API 26+）** 真机或模拟器，运行 `app` 模块。
4. 进入首页点「开始跳绳 🏃」，把手机握在手中随跳动，即可实时计数与反馈。

> 模拟器没有真实加速度传感器，计数不会触发；请用真机体验完整计数。

## ⚠️ 计数说明（重要）

手机传感器方案是**启发式趣味实现**：用加速度计「落地冲击峰值」计次，用陀螺仪「平均转速过高」判定动作不规范。它对孩子握姿敏感，适合家庭趣味场景。本应用已内置**摄像头姿态估计模式**（设置 → 计数方式 → 摄像头）：用 MediaPipe PoseLandmarker 检测髋部起伏计数，更接近竞技级精度。计数灵敏度可在「设置 → 计数灵敏度」中调节（跳得轻调高、跳得重调低）。

## 🎨 自定义建议

- **音效素材**：已落地。卡通音效为 `res/raw/snd_*.wav`（由 `tools/gen_assets.py` 生成，可重新运行定制），`SoundPlayer` 优先播放素材，失败回退 `AudioTrack` 合成。想换成真人录音，直接替换同名 wav / mp3 并改 `SoundPlayer` 的 `R.raw` 引用即可。
- **Lottie 动画**：`res/raw/celebration.json`（达标庆祝）、`star_spin.json`（启动页星星）。用 `LottieRes(resId)` 组件加载，可替换为任意 Lottie JSON。
- **吉祥物**：`JumpMascot` 仍用 Canvas 绘制吉祥物表情（训练页中部），与 Lottie 装饰并存。
- **摄像头模型**：`pose_landmarker.task`（约 5MB）是摄像头姿态计数的核心，按下面任一步骤准备即可：
  - **方式 A（推荐，离线）**：手动下载并放入 `app/src/main/assets/`
    1. 从 MediaPipe 官方下载模型（**推荐 `lite` 版**，约 5MB，家庭使用精度足够）：
       - lite：`https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task`
       - full：`https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task`
       - heavy：`https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/1/pose_landmarker_heavy.task`
    2. 重命名为 `pose_landmarker.task`（代码按此固定文件名加载）
    3. 放到 `app/src/main/assets/`（目录不存在则新建：`app/src/main/assets/`）
  - **方式 B（联网自动下载）**：首次进入摄像头模式时由 `camera/PoseModelProvider` 自动下载到私有目录（需 `INTERNET` 权限且能访问 `storage.googleapis.com`）。⚠️ 模型托管于 Google 服务，**国内网络可能直连失败**，此时请改用方式 A。
  - 计数算法在 `camera/PoseCounter.kt`（纯逻辑、可单测），摄像头模式依赖 `mediapipe_tasks_vision` + CameraX，首次构建需联网拉取。
- **主题色**：`app/.../ui/theme/Color.kt`。

## 📂 目录结构

```
app/src/main/java/com/jumpdaily/jump/
├── data/local/        实体、DAO、Room 数据库
├── data/repository/   Children / Jump / Preferences 仓库
├── data/model/        统计快照与成就目录
├── sensor/            JumpDetector（传感器计数）
├── camera/             PoseCounter（姿态计数算法）· PoseJumpDetector（MediaPipe 封装）· PoseModelProvider（模型加载）
├── audio/             SoundPlayer（素材优先 + 合成兜底）
├── reminder/          ReminderScheduler + ReminderWorker
├── ui/theme/          糖果色主题
├── ui/components/      CuteButton / JumpMascot / LottiePlayer / Confetti / FeedbackOverlay / StatCard / ChildAvatar / WeeklyBarChart
├── ui/viewmodel/      Session / Training / Children / Records / Settings
├── ui/screens/        Home / Training / Children / Stats / History / Achievements / Settings
├── ui/navigation/     AppRoot（导航 + 底部栏）
├── di/                AppContainer（手动装配）
├── JumpDailyApplication.kt
└── MainActivity.kt
```

## 📦 发布签名（Release）

`app/build.gradle.kts` 已配置 `signingConfigs.release`，签名信息从项目根 `local.properties` 读取（该文件已被 `.gitignore` 忽略，**不会提交到仓库**，避免密钥泄露）。仅当 `local.properties` 配置了密钥时 `assembleRelease` 才会真正签名；未配置则跳过，不影响 `assembleDebug`。

1. **生成签名密钥库**（仅一次）：在终端执行
   ```bash
   keytool -genkey -v -keystore app/jumpdaily-release.keystore -alias jumpdaily \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
   按提示填密码与基本信息，生成 `app/jumpdaily-release.keystore`（已被 `.gitignore` 的 `*.keystore` 忽略）。

2. **在项目根 `local.properties` 填入密钥**（切勿提交）：
   ```properties
   KEYSTORE_FILE=app/jumpdaily-release.keystore
   KEYSTORE_PASSWORD=你的密钥库密码
   KEY_ALIAS=jumpdaily
   KEY_PASSWORD=你的密钥别名密码
   ```

3. **构建已签名产物**（需先 Sync 并确认 `local.properties` 已填密钥）：
   ```bash
   # 先停掉可能残留的旧 Gradle daemon，避免缓存/版本错乱
   ./gradlew --stop
   # 已签名 APK → app/build/outputs/apk/release/app-release.apk
   ./gradlew assembleRelease
   # 已签名 AAB → app/build/outputs/bundle/release/app-release.aab（上架 Play 商店用）
   ./gradlew bundleRelease
   ```
   - 若想走 **Android Studio 图形界面**：菜单 **Build → Generate Signed Bundle / APK** → 选 **Android App Bundle** 或 **APK** → 填入上面密钥库路径/密码/alias → 选 release → 完成，产物路径同上。
   - 构建失败报「找不到密钥/密码错误」→ 回头核对 `local.properties` 四项拼写与 `app/jumpdaily-release.keystore` 是否就位。

4. **校验产物已签名**（可选但推荐）：
   ```bash
   # APK：用 Android SDK 的 apksigner（build-tools 下）查 V1/V2 签名
   "%ANDROID_HOME%/build-tools/<版本>/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk
   # AAB：直接看证书指纹（与 keytool -list 显示一致即正确）
   keytool -list -printcerts -jarfile app/build/outputs/bundle/release/app-release.aab
   ```

5. **备份与安全（重要）**：
   - `app/jumpdaily-release.keystore` 是 App 的**唯一签名身份**，丢失后无法更新已发布版本（只能换包名重发）。务必**单独备份**到安全位置（云盘加密/移动硬盘），且**不要只留在本机**。
   - `local.properties` 与 `*.keystore` 已被 `.gitignore` 忽略，不会进 Git 仓库；切勿手动 `git add` 它们。
  - 本项目 keystore 已使用 **32 位随机强密码**（构建阶段已升级，旧弱密码已失效）。若日后仍需更换：PKCS12 格式下 `keypass` 随 `storepass` 同步，**只需改密钥库密码**即可，不要单独用 `keytool -keypasswd`（PKCS12 不支持该命令，会报 `UnsupportedOperationException`）：
    ```bash
    keytool -storepasswd -keystore app/jumpdaily-release.keystore   # 改后 keypass 自动同步
    ```
    改完同步更新 `local.properties` 的 `KEYSTORE_PASSWORD` / `KEY_PASSWORD`。

## ✅ 单元测试

纯 JVM 逻辑（不依赖 Android 运行时）已覆盖，可在 Android Studio 直接右键运行，或用：
```bash
./gradlew testDebugUnitTest
```
- `app/src/test/java/.../camera/PoseCounterTest.kt`：姿态计数滞回算法的「每次完整落地计 1 次 / 过快不计 / 灵敏度降低阈值」三用例。
- `app/src/test/java/.../util/DateUtilsTest.kt`：`dayStart` 清零时分秒、`computeStreak` 连续打卡天数、`formatDuration` 分秒格式化三用例。
