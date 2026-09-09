# Baseline Profile 落地实战

> 基于 JumpDaily 真实构建配置：AGP 8 + R8(`minifyEnabled=true`) + `profileinstaller:1.3.1` 已引入，
> 但**尚未编写** `app/src/main/baseline-prof.txt`，也**没有**宏基准（Macrobenchmark）生成模块。
> 本文给出在本项目落地的完整步骤与代码片段。

---

## 1. 它是什么，解决什么问题

Baseline Profile 是一份「预编译热点清单」（`baseline-prof.txt`，ART 规则格式），随 APK 一起分发，
让 Android Runtime 在**首次启动/首次交互**时就对清单里的类/方法做 AOT 编译，
跳过原本要在运行时靠解释 + JIT 累积的「预热」过程。

收益直接命中本项目两个痛点：

| 场景 | 没 Profile | 有 Profile（实测区间） |
|------|-----------|------------------------|
| 冷启动（点图标到首帧可交互） | 受解释/JIT 拖累，尤其 MediaPipe 初始化（CameraX + PoseLandmarker 一大堆类要加载） | 通常 **快 20%~40%** |
| 列表滚动 / 进训练页首帧 | 前几次滚动掉帧（JIT 还没编热点） | 首屏即流畅 |

**为什么本项目特别值得做**：
- 目标用户是「孩子用的中低端 ARM 手机」，JIT 能力弱、预热更慢 → Baseline Profile 收益比旗舰机更明显。
- `minSdk=26`（A8.0+）：`profileinstaller` 从 API 24 起即可工作，覆盖全量用户。
- 应用已经 `minifyEnabled=true` + 仅 ARM ABI，包体敏感——Profile 是「白送」的免费性能，几乎不增包（规则文本很小）。

---

## 2. 项目现状（准确盘点）

```bash
# 已有：build.gradle.kts 第 183~184 行
implementation("androidx.profileinstaller:profileinstaller:1.3.1")

# 存在但只是构建中间产物（不要手改这些）：
app/build/intermediates/merged_art_profile/*/baseline-prof.txt
app/build/intermediates/r8_art_profile/*/baseline-prof.txt

# 缺失（待落地）：
app/src/main/baseline-prof.txt        # ← 源码里的规则文件，目前不存在
:baselineprofile (Macrobenchmark) 模块  # ← 用于自动生成/验证 Profile 的测试模块，不存在
```

> `BUILD_PROFILES` 里那些 `baseline-prof.txt` 是空壳（AGP 默认只含 `HSPLIT` 头），
> 因为没有任何源码规则可合并。我们要补的就是「真规则」。

---

## 3. 落地步骤

### 3.1 在 `:app` 增加 `benchmark` 构建类型（用真实 release 变体生成）

`app/build.gradle.kts` 的 `android { }` 内：

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        // ↓ Baseline Profile：生成后写回源码目录（src/main/baseline-prof.txt）
        baselineProfile { saveInSrc = true }
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        // ... 原有 signingConfig 不变
    }

    // 专用于生成/测量 Profile 的变体：
    // 跟 release 一样但关掉混淆，让 Macrobenchmark 捕获到「真实方法名」，
    // R8 在打 release 时会用 expandReleaseArtProfileWildcards 把规则映射回混淆后名字。
    create("benchmark") {
        initWith(getByName("release"))
        isMinifyEnabled = false          // 关键：保留可读符号，便于基准测量
        isDebuggable = false             // 必须是 release 级（不可调试），否则 Macrobenchmark 不让跑
        signingConfig = signingConfigs.getByName("release")
    }
}
```

> 渠道（official/huawei/xiaomi）会自动继承 `benchmark` 类型，生成 `huaweiBenchmark` 等变体，无需额外配置。

### 3.2 新增 `:baselineprofile` 模块

`settings.gradle.kts` 追加：

```kotlin
include(":app")
include(":baselineprofile")
```

`baselineprofile/build.gradle.kts`：

```kotlin
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.jumpdaily.jump.baselineprofile"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // 指向被测 App 模块
    targetProjectPath = ":app"
    // 要覆盖的 App 变体（默认取 release/benchmark）
    missingDimensionStrategy("channel", "official")
    experimentalProperties["android.experimental.selfInstrumenting"] = true
}
dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.0")
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
```

`baselineprofile/src/main/java/com/jumpdaily/jump/baselineprofile/BaselineProfileGenerator.kt`：

```kotlin
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun generate() = benchmarkRule.measureRepeated(
        packageName = "com.jumpdaily.jump",
        metrics = listOf(FrameTimingMetric()),
        iterations = 12,
        setupBlock = {
            pressHome()
            startActivityAndWait()     // 冷启到首页
        }
    ) {
        // 走查核心热路径，让 ART 记录这些代码路径为「热点」
        device.waitForIdle()
        // 进统计页滚一滚
        device.findObject(By.text("统计")).click()
        device.waitForIdle()
        // 进训练页（触发 Compose + CameraX + MediaPipe 类加载）
        device.findObject(By.text("训练")).click()
        device.waitForIdle()
        pressHome()
    }
}
```

### 3.3 生成（连真机/模拟器）

```bash
# 一键生成并写回 app/src/main/baseline-prof.txt
./gradlew :app:generateBaselineProfile

# 或分别：先 collect（benchmark 变体跑宏基准），再写回 src
./gradlew :app:collectBaselineProfile
```

生成后 `app/src/main/baseline-prof.txt` 会出现类似：

```
HSPLIT
HSP
Lcom/jumpdaily/jump/ui/screens/HomeScreenKt;
Lcom/jumpdaily/jump/ui/components/JumpMascotKt;
Lcom/jumpdaily/jump/camera/PoseJumpDetector;
Lcom/google/mediapipe/tasks/vision/poselandmarker/PoseLandmarker;
```
（AGP 8 会自动合并宏基准抓到的规则 + 你在源码里手写的规则。）

### 3.4 打包生效

打 release：

```bash
./tools/build_apk.sh official release   # 走既有一键脚本
```

- R8 在 `minifyReleaseWithR8` 阶段把 `baseline-prof.txt` 的**通配规则**展开、嵌入 APK 的 `assets/dexopt/` 与 `META-INF/profiles/`。
- 首次安装后，`profileinstaller` 在后台把规则写入系统 ART 目录（Android 9+ 由 Play 商店/系统下发；低版本由 profileinstaller 自行落盘）。
- 验证：`adb shell cmd package dump-profiles com.jumpdaily.jump` 后看 `pm art` 是否加载了 profile。

---

## 4. 进阶：DexLayout（进一步压冷启）

AGP 8 在 `minifyEnabled=true` 时默认开启 **DexLayout**——按 Baseline Profile 把热点类排到主 `classes.dex` 前部、冷路径拆到后续 dex，
减少冷启时的 dex 内存映射与 IO。无需额外配置，只要 Profile 写对了就会自动受益。

若想显式控制，可在 `gradle.properties` 确认没有关闭：

```
# 保留默认开启即可，不要设成 false
# android.enableDexingArtifactTransform.desugaring=false
```

---

## 5. 验证与回归

- **冷启对比**：`MacrobenchmarkRule` 的 `StartupTimingMetric` 跑 `officialBenchmark` 变体，
  生成前后各测一次，看 `timeToInitialDisplay` / `timeToFullDisplay`。
- **CI 门禁**：把 `generateBaselineProfile` 放进 `tools/build_apk.sh` 的 `release` 前一步，
  保证每次发版都带最新 Profile（本项目的 `tools/` 一键脚本可直接加）。
- **手改规则**：可在 `app/src/main/baseline-prof.txt` 手写高频类（如 `JumpMascotKt`、`RopeSkipperKt`、
  `PoseJumpDetector`），它们每帧都在跑，值得进 Profile。

---

## 6. 坑位清单

| 坑 | 现象 | 解决 |
|----|------|------|
| `benchmark` 变体 `isDebuggable=true` | Macrobenchmark 直接失败：「 must not be debuggable」 | 设 `false`（见 3.1） |
| 用 `generateBaselineProfile` 但 `minifyEnabled=true` 的 release | 规则命中率低（混淆后名字对不上） | 生成务必走 `benchmark` 变体（混淆关），release 再由 R8 展开通配 |
| 只生成不改 `saveInSrc` | 每次构建重新生成、不进版本库 | `baselineProfile { saveInSrc = true }` |
| 模拟器无 GPU | 帧指标不准 | 用真机测（`-Pandroid.testInstrumentationRunner` 指定），或接受近似 |
| `profileinstaller` 版本与 AGP 不匹配 | 编译报错 | 跟着 AGP 8 选 `1.3.x`（当前已 1.3.1，匹配） |

---

## 7. 状态

- ✅ 已引入 `profileinstaller` 依赖。
- ⚠️ **待实施**：`app/src/main/baseline-prof.txt` + `:baselineprofile` 模块 + `benchmark` 构建类型（按第 3 节）。
- 落地后建议把生成步骤并入 `tools/build_apk.sh`，保证发版自带 Profile。
