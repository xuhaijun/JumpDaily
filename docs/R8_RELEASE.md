# R8 / ProGuard Release 打包实战坑

> 适用：本项目 `app/build.gradle.kts` 的 `release` 构建 + 真机验收调试经验。
> 文档基于真实构建配置与华为真机踩坑记录，行号见引用。

---

## 1. 第一个大坑：R8 代码优化改坏 MediaPipe/protobuf

**现象**：debug 包摄像头姿态计数正常，打 `release` 包后一进训练页就崩/初始化失败（`ExceptionInInitializerError`），`pose_landmarker` 引擎起不来。

**根因**：早期用的是 `proguard-android-optimize.txt`（含 R8 代码优化：类合并、`allowaccessmodification` 等）。R8 的优化会破坏 MediaPipe / protobuf 的**静态初始化块 `<clinit>`**，导致 release 下类初始化抛异常、引擎初始化失败。debug 不优化故正常。

**修复**（`app/build.gradle.kts` 第 78–86 行）：

```kotlin
proguardFiles(
    // 不能用 proguard-android-optimize.txt（含代码优化）
    // R8 优化会破坏 MediaPipe/protobuf 的 <clinit>，导致 release 下 ExceptionInInitializerError
    getDefaultProguardFile("proguard-android.txt"),  // 仅压缩+混淆，不做优化
    "proguard-rules.pro"
)
```

同时 `proguard-rules.pro` 里 keep 传递依赖（protobuf 等）：

```
-keep class com.google.protobuf.** { *; }
# MediaPipe tasks-vision 相关 keep（按实际 release 报错补充）
```

> **铁律**：本项目 release 用 `proguard-android.txt`，**绝不用 `-optimize.txt`**。

---

## 2. 第二个坑：release 包 logcat 不可靠

**现象**：华为 EMUI 上，三方 App 自己的 `Log.i/e` 在 release 包里**可能被系统吞掉**——肉眼确认 `onInit` 成功了，但 logcat 里 0 条日志。

**根因**：厂商对 release 包的日志有裁剪/限流。靠 logcat 调试致命状态会「假阴性」。

**修复：关键状态一律文件落盘**。写到外部文件目录，再 `adb pull` 看：

```kotlin
// VoiceSpeaker.kt 第 245–252 行
private fun runCachingDiag(line: String) {
    val dir = appContext.getExternalFilesDir(null) ?: return
    val f = File(dir, "tts_init.txt")
    f.appendText("${System.currentTimeMillis()} $line\n")
    f.setReadable(true, false)  // 设为全局可读，便于 adb 远程读取
}
```

本项目已有的诊断文件：
- `tts_init.txt`：TTS 引擎就绪 / 语音选择 / `voice_drop` 丢弃 / `play_*` 命中。
- `pose_error.txt`：姿态引擎初始化失败栈（摄像头页降级时写）。

**验收流程**：

```bash
# 真机跑完关键操作后，拉回本地看
adb pull /sdcard/Android/data/com.jumpdaily.jump/files/tts_init.txt ./
adb pull /sdcard/Android/data/com.jumpdaily.jump/files/pose_error.txt ./
```

> ⚠️ 调试前先确认手机已授权「文件管理」读取该目录；若 pull 报权限错误，用 `adb shell cat` 直接读：
> `adb shell cat /sdcard/Android/data/com.jumpdaily.jump/files/tts_init.txt`

---

## 3. 第三个坑：华为 TTS `OnInitListener` 必须每次 new

**现象**：华为 EMUI + D8 下，把 `OnInitListener` 存成类的属性（`private val onInit = ...`）传给 `TextToSpeech` 构造器，回调**永不触发**——无崩溃、无声，TTS 永远不就绪。

**根因**：D8/R8 + EMUI 的某个交互导致 SAM 属性实例的回调被吞。

**修复**（`VoiceSpeaker.kt` 第 195 行）：每次 `new` 一个实例（内联 lambda 或工厂方法）：

```kotlin
private fun onInit() = TextToSpeech.OnInitListener { status -> ... }
// 用法：tts = TextToSpeech(appContext, onInit())
```

> 同样坑：把 `(Int) -> Unit` 属性当 SAM 传，回调里会 NPE（`$tmp0` 收到 null）。**必须显式写 `OnInitListener`**。

定位靠 `tts_init.txt` 里有没有 `oninit status=` 行；没有 → 说明回调没触发 → 检查是不是用了属性版监听器。

---

## 4. 构建产物配置（release）

`app/build.gradle.kts` 第 73–95 行：

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true       // 开 R8 压缩
        isShrinkResources = true     // 资源缩减
        proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        ndk {
            // 仅 ARM：MediaPipe/CameraX 仅 ARM 可用，x86 库对发布无意义，且大幅减小包体
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        // 仅当 local.properties 配了密钥才签名（未配 → 产出未签名包，不影响构建验证）
        val rel = signingConfigs.getByName("release")
        if (rel.storeFile != null) signingConfig = rel
    }
}
```

**签名从 `local.properties` 读，无硬编码密码**（第 55–71 行）：

```kotlin
signingConfigs {
    create("release") {
        val props = Properties()
        val propFile = rootProject.file("local.properties")
        if (propFile.exists()) {
            props.load(propFile.inputStream())
            storeFile = rootProject.file(props.getProperty("KEYSTORE_FILE"))
            storePassword = props.getProperty("KEYSTORE_PASSWORD")
            keyAlias = props.getProperty("KEY_ALIAS")
            keyPassword = props.getProperty("KEY_PASSWORD")
        }
    }
}
```

> `local.properties`、`*.keystore`、`*.jks` 均已被 `.gitignore` 忽略，**密钥不会进仓库**。

---

## 5. 包体积相关配置（顺带回顾）

- `resourceConfigurations += setOf("zh", "zh-rCN", "zh-rTW")`（第 24 行）：仅中文资源，剔除依赖库其它语言字符串。
- `packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"`（第 133 行）：排除 MediaPipe 许可文件冲突。
- `profileinstaller` 依赖（第 184 行）：支撑 Baseline Profile，Play 商店可下发提升冷启动/滚动流畅度。

---

## 6. Release 验收清单

- [ ] `./gradlew assembleRelease` 通过（或 `tools/build_apk.sh huawei release`）。
- [ ] 产物命名含版本/渠道/日期（见 [ARCHITECTURE.md](ARCHITECTURE.md) 多渠道章节）。
- [ ] 真机安装后：摄像头姿态引擎能初始化（看 `pose_error.txt` 是否空 / `tts_init.txt` 的 `init_ok`）。
- [ ] 真机 TTS 能播（看 `tts_init.txt` 的 `oninit status=SUCCESS` + `play_*` 行）。
- [ ] 华为安装风控若弹框，按 `huawei-adb-install-riskcontrol` 技能自动处理（需用户点「安装」/输锁屏密码）。
- [ ] 如 release 崩，先 `adb pull` 两个诊断文件，别只看 logcat。

---

## 7. 相关文档

- 多渠道/命名见 [ARCHITECTURE.md](ARCHITECTURE.md)
- TTS 全局仲裁见 [TTS_ARBITRATION.md](TTS_ARBITRATION.md)
- CameraX 节流见 [CAMERAX_THROTTLE.md](CAMERAX_THROTTLE.md)
