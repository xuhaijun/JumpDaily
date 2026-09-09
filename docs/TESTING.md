# 测试体系

> 基于 `app/src/test/`（3 个单测）+ `app/build.gradle.kts:179-181`（测试依赖）真实状态。
> 说明现有覆盖、测试金字塔、如何跑、以及补齐 VM/Repository/UI 测试的推荐方案。

---

## 1. 现有测试清单（真实）

全部是 **JVM 本地单测**（`app/src/test/java/...`），无需 Android 设备或 Robolectric：

| 测试类 | 文件 | 测什么 | 依赖 |
|--------|------|--------|------|
| `DateUtilsTest` | `util/DateUtilsTest.kt` | `dayStart` 清零时分秒、`computeStreak` 连续打卡、`formatDuration` 时长格式化 | JUnit4 |
| `PoseCounterTest` | `camera/PoseCounterTest.kt` | 跳绳计数算法：一次完整落地周期计 1、过快抖动被最小间隔过滤、灵敏度阈值 | JUnit4 |
| `RewardsTest` | `data/model/RewardsTest.kt` | 积分/奖品体系不变量：等级边界、奖品 id 唯一、门槛严格递增、解锁/兑换语义 | JUnit4 |

**这 3 个测试守护的是「纯算法 / 纯数据」核心逻辑**：计数正确性、连续打卡、奖品墙三态自洽。
它们不依赖 Android 框架，跑得极快（`app/src/test` 直接 JVM 运行）。

---

## 2. 测试依赖（真实，`build.gradle.kts:179-181`）

```kotlin
testImplementation("junit:junit:4.13.2")
androidTestImplementation("androidx.test.ext:junit:1.2.1")
androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"  // build.gradle.kts:22
```

`androidTest` 依赖（espresso）已引入，但**当前没有任何 `androidTest` 用例**（目录为空）。
**缺失的关键测试库**（建议补）：

```kotlin
testImplementation("app.cash.turbine:turbine:1.2.0")                       // 断言 StateFlow 发射序列
testImplementation("io.mockk:mockk:1.13.11")                             // mock Repository/DataStore
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1") // runTest / TestDispatcher
testImplementation("org.robolectric:robolectric:4.12.2")                  // JVM 跑 Android 代码（可选）
androidTestImplementation("androidx.room:room-testing:2.6.1")            // Room 迁移测试（见 ROOM_MIGRATION.md）
```

---

## 3. 测试金字塔（项目当前位置）

```
        ▲  UI 测试（Espresso / Compose UI test）    ← 0 个（缺，最慢最贵）
       ██│
      ████│  集成测试（VM + 真 Repository / Room in-memory） ← 0 个（缺）
     ██████│
    ████████│  本地单测（纯逻辑 / VM 用 mock）        ← 3 个（已有，最快最便宜）
   ██████████▼
```

健康形态：底部厚、顶部薄。本项目**只有塔底**（3 个纯逻辑单测），中间（VM/Repository）和顶部（UI）是空档。

**为什么纯逻辑单测最值得先写**：计数/连续打卡/奖品门槛这些算法一旦写错，用户体验直接崩；且它们零依赖、秒级运行，性价比最高——本项目已做对。

---

## 4. 怎么跑（命令）

```bash
# 全部 JVM 单测（含 DateUtils/PoseCounter/Rewards）
./gradlew testDebugUnitTest

# 指定模块/变体
./gradlew :app:testHuaweiDebugUnitTest

# 需要真机/模拟器的仪表化测试（当前无用例，但依赖已就绪）
./gradlew connectedDebugAndroidTest

# 跑单个类
./gradlew testDebugUnitTest --tests "com.jumpdaily.jump.camera.PoseCounterTest"
```

> Windows（Git Bash）用 `gradlew.bat`；本项目 `tools/build_apk.sh` 已封装环境。

---

## 5. 补齐 VM 层测试（推荐，当前缺口）

VM 用 `viewModelScope` + `stateIn`，测试要**可观测流 + mock 仓库**。骨架（配合 turbine + mockk + runTest）：

```kotlin
class RecordsViewModelTest {
    @Test
    fun `setChild switches records flow`() = runTest {
        val repo = mockk<JumpRepository>()
        every { repo.records(1) } returns flowOf(listOf(recA))
        every { repo.records(2) } returns flowOf(listOf(recB))
        val vm = RecordsViewModel(repo)

        vm.records.test {                       // turbine：收集 StateFlow 发射序列
            assertEquals(emptyList(), awaitItem())  // 初始值
            vm.setChild(1); assertEquals(listOf(recA), awaitItem())
            vm.setChild(2); assertEquals(listOf(recB), awaitItem())
        }
    }

    @Test
    fun `statsLoaded true only after real stats arrive`() = runTest {
        val repo = mockk<JumpRepository>()
        every { repo.stats(any()) } returns flowOf(Stats(totalCount = 10))
        val vm = RecordsViewModel(repo).apply { setChild(7) }
        vm.statsLoaded.test {
            assertEquals(false, awaitItem())        // 初始值（空 Stats）不算
            assertEquals(true, awaitItem())         // 真实数据到达
        }
    }
}
```

**要点**：
- `stateIn(WhileSubscribed(5000))` 在测试里要用 `UnconfinedTestDispatcher()` 或 `runTest` 的 `StandardTestDispatcher` + 手动 `advanceTimeBy`，否则 5000ms 订阅窗口不触发。turbine 的 `.test` 默认用 `UnconfinedTestDispatcher`，最省事。
- `vm.statsLoaded` 这个「真实首帧」标志的存在，正是为了可测（验证「成就不每次弹」）。

---

## 6. 补齐 Repository / Room 测试（推荐）

- **Room 迁移测试**：用 `Room` 的 `RoomDatabase.Builder.setQueryCallback` + `MigrationTestHelper`（`room-testing`），逐版本跑 `migrate` 并断言表结构/数据。详见 `ROOM_MIGRATION.md` 第 4 节。
- **`JumpRepository.stats` 聚合**：用内存 Room（`RuntimeEnvironment`/Robolectric 或 `inMemoryDatabaseBuilder`）插几条记录，断言 `combine` 出的 `Stats.streak/totalCount` 正确。

---

## 7. 补齐 UI 测试（可选，最贵）

- Compose UI test：`androidx.compose.ui:ui-test-junit4` + `createComposeRule()`，断言「首页大按钮点击 → 路由到训练页」「成就弹框出现条件」。
- Espresso（已依赖）：原生 View 断言，本项目纯 Compose 用 Compose UI test 更合适。
- 真实设备/模拟器跑（`connectedDebugAndroidTest`），慢，建议只在 CI 的单独 job 跑。

---

## 8. 覆盖率与 CI 集成

- 开启覆盖率：`build.gradle.kts` 加 `buildTypes.debug.isMinifyEnabled = false` + `testCoverageEnabled = true`，然后 `./gradlew createDebugCoverageReport`。
- CI 里 `./gradlew testDebugUnitTest` 作为合并门禁；未过不让合 main（见 `CI_PIPELINE.md`）。

---

## 9. 速查清单（加测试时照着做）

- [ ] 纯算法 / 数据不变量的逻辑 → 直接写 `app/src/test` JVM 单测（参考 `RewardsTest`）。
- [ ] VM 测试 → turbine 断言流 + mockk mock 仓库 + `runTest`。
- [ ] Room 迁移 → `room-testing` + `MigrationTestHelper`（参考 `ROOM_MIGRATION.md`）。
- [ ] UI 测试 → Compose UI test，只在 CI 单独 job 跑。
- [ ] 跑：`gradlew testDebugUnitTest`；单类：`--tests "包名.类"`。
- [ ] 覆盖率：`testCoverageEnabled=true` + `createDebugCoverageReport`。
- [ ] 不要为「已验证的简单 getter」写测试——把预算花在算法/状态机/迁移上。

---

**接入**：本篇与 `COROUTINE_FLOW_VM.md`（VM 协程/流，测试对象）、`ROOM_MIGRATION.md`（迁移测试）、`CI_PIPELINE.md`（测试即门禁）强相关。
