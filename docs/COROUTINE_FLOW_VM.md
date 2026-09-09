# Kotlin 协程 / Flow 在 ViewModel 的最佳实践

> 基于 `app/src/main/java/com/jumpdaily/jump/ui/viewmodel/` 下 5 个真实 ViewModel 总结。
> 本文只讲「本项目怎么用、为什么这么用、踩过哪些坑」，不展开协程/流的入门概念。

---

## 1. 项目现状一览（先看这张表）

| ViewModel | 文件 | 流/协程用法 |
|-----------|------|------------|
| `TrainingViewModel` | `viewmodel/TrainingViewModel.kt` | 14 个 `MutableStateFlow` + `asStateFlow`；`viewModelScope.launch` 写盘；`Job` 定时批量 flush；无 Channel |
| `RecordsViewModel` | `viewmodel/RecordsViewModel.kt` | `flatMapLatest` + `stateIn(WhileSubscribed(5000))`；`map` 派生 `badges`/`weekly`；`statsLoaded` 真实首帧标志 |
| `SessionViewModel` | `viewmodel/SessionViewModel.kt` | `combine` 派生 `currentChild`；`init` 里 `viewModelScope.launch { ... .first() }` 做种子数据 |
| `SettingsViewModel` | `viewmodel/SettingsViewModel.kt` | 每个偏好一个 `stateIn`；写操作 `viewModelScope.launch`；提醒调度 `try/catch` 包裹 |
| `ChildrenViewModel` | `viewmodel/ChildrenViewModel.kt` | `repo.children.stateIn(...)`；增删改用 `viewModelScope.launch` |

依赖：**没有** Hilt/Dagger，全部用手写 `ViewModelProvider.Factory`（`AppContainer` 注入），见 `di/AppContainer.kt`。

---

## 2. 不可变状态出口：私有可变 + 公开只读

项目里统一用「`_xxx` 私有可变 + `xxx` 公开只读」两字段模式（`TrainingViewModel.kt:60-126`）：

```kotlin
private val _count = MutableStateFlow(0)
val count: StateFlow<Int> = _count.asStateFlow()   // 外部只能读，不能乱改
```

**为什么**：VM 内部才能 `emit`/`value=`，UI 层只能 `collectAsStateWithLifecycle()` 订阅。
既防止 UI 误改状态，也让状态来源单一、可测试。

**最佳实践**：
- 公开属性一律返回 `StateFlow`（热流、可重放最新值），不要返回 `MutableStateFlow`。
- 高频变化的状态（计数/计时/连击）也走这个模式——只是在 UI 层下沉到子组件 `collectAsStateWithLifecycle`，避免父页每帧重组（详见 `COMPOSE_RECOMPOSITION.md`）。

---

## 3. cold Flow（仓库）→ hot StateFlow（VM）：`stateIn` 三件套

仓库返回的是「冷流」（`JumpRepository.kt:20-45` 直接透传 Room DAO 的 `Flow`，或 `combine` 聚合多个查询）。
VM 把它转成「热流」对外暴露，用 `stateIn`：

```kotlin
// RecordsViewModel.kt:37-39
val records: StateFlow<List<JumpRecord>> = _childId.flatMapLatest { id ->
    id?.let { repo.records(it) } ?: flowOf(emptyList())
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
)
```

**三个参数的含义（务必记牢）**：
- `scope = viewModelScope`：VM 销毁时上游自动取消。
- `started = SharingStarted.WhileSubscribed(5000)`：**最后一个订阅者离开后还多活 5 秒**，再无订阅才停。5 秒窗口正好覆盖配置变更（旋转屏幕）时的短暂退订，避免每次转屏都重建流、重新查库。
- `initialValue`：**首帧立即有值**，不阻塞 UI。

**`flatMapLatest` 的语义**：`childId` 一变（切换孩子），自动取消上一个孩子的查询、切到新孩子的查询。
配合 `setChild(id)`（`RecordsViewModel.kt:33`）实现「切娃即刷新统计/历史/成就」。

---

## 4. ⚠️ 真实踩坑：`stateIn` 初始值导致的「每次开 App 都弹成就」

`RecordsViewModel.kt:48-58` 专门写了 `statsLoaded` 标志，注释里说清了根因：

```
stats / badges 都是 stateIn 且带初始值（空 Stats / 空列表），
调用方若在首帧之前就去 badges.first() 做「成就基线快照」，会拿到空列表，
随后真实成就到达时会被误判成「新解锁」，导致每次打开 App 都弹成就框。
```

**教训（通用）**：
- `stateIn` 的 `initialValue` 只是为了「不阻塞第一帧」，它**不是真实数据**。
- 凡是「用首帧数据做基线/快照」的逻辑（成就种子化、首帧埋点），必须等一个「真实数据已到达」标志（`statsLoaded`）再动手，不能读 `initialValue`。
- 这也是为什么 VM 把 `statsLoaded: StateFlow<Boolean>` 单独暴露出来（`RecordsViewModel.kt:56-58`），而不是让 UI 自己判断 `badges.isEmpty()`。

---

## 5. 切换孩子：`flatMapLatest` vs `combine`

- **按「触发源」切换上游** → 用 `flatMapLatest`（`RecordsViewModel` 的 `childId` 驱动）。
- **按「多个源合并成一个值」** → 用 `combine`（`SessionViewModel.kt:36` 合并 `childId + children 列表` 出 `currentChild`）：

```kotlin
val currentChild: StateFlow<Child?> = combine(_currentChildId, children) { id, list ->
    list.firstOrNull { it.id == id } ?: list.firstOrNull()
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
```

`combine` 任一源变化都重算，适合「派生只读状态」。

---

## 6. 一次性 UI 事件：本项目用 `StateFlow<T?>`（并接受了它的限制）

训练反馈、彩带、弹幕都走 `StateFlow`（可空）：

```kotlin
private val _feedback = MutableStateFlow<Feedback?>(null)   // TrainingViewModel.kt:79
val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()
private val _confetti = MutableStateFlow(false)             // TrainingViewModel.kt:82
```

UI 侧 `collectAsStateWithLifecycle()` 拿到后消费（展示后由 VM 复位或自然过期）。

**局限（要知道）**：`StateFlow` 只保留「最新一个值」，会**合并**中间值。
如果 1 帧内连发 3 条 feedback，UI 只会看到最后 1 条，前 2 条被覆盖。
本项目训练反馈是**低频**（几秒一条），所以可接受；但高频一次性事件（如 toast 队列、连续报错）用 `StateFlow` 会丢。

**进阶替代方案**（本项目当前没用，按需引入）：
- **`Channel` + `consumeAsFlow`/`receiveAsFlow`**：每个事件都送达，`Channel.BUFFERED` 还能排队。适合「每条都要弹」的场景。
- **`SharedFlow`（`MutableSharedFlow(replay=0, extraBufferCapacity=N)`）**：订阅前丢弃、订阅后可缓冲 N 条，比 `StateFlow` 更适合事件总线。
- 注意：Compose 侧要 `LaunchedEffect` 里 `collect`，且事件消费后**不要**再回写 StateFlow，否则重组会重复触发。

---

## 7. 写操作：`viewModelScope.launch` + 不要每跳落盘

所有写库/写偏好都在 `viewModelScope.launch` 里（`SettingsViewModel.kt:52-88`、`ChildrenViewModel.kt:23-32`）：

```kotlin
fun delete(record: JumpRecord) = viewModelScope.launch { repo.delete(record) }
```

**关键点（性能）**：跳绳「每跳一次」如果直接写 DataStore/Room，I/O 会拖垮帧率。
本项目用**批量 flush**（`TrainingViewModel.kt:110-118`）：

```kotlin
private var pendingPoints = 0            // 内存累计增量
private var flushJob: Job? = null        // 定时落盘 Job
// 每 3 秒把 pendingPoints 落盘一次，而非每跳写盘（见 TrainingViewModel 的 flush 逻辑）
```

`viewModelScope` 会在 VM `onCleared` 时**自动取消所有未完成的 `launch`**，所以退出页面不会泄漏协程。

**错误处理**：
- 大多数写操作没包 try/catch（本地 Room/DataStore 失败概率极低）。
- 唯独 `SettingsViewModel.setReminder`（涉及 WorkManager 调度）包了 try/catch（`SettingsViewModel.kt:54-59`），因为 WorkManager 在某些 ROM 上初始化会抛异常，不能让它把设置页搞崩。
  **最佳实践**：调用外部组件（WorkManager / 系统 API / 网络）的写操作才需要兜底；纯本地存储可信任。

---

## 8. 种子数据放在 VM 的 `init`（仅一次）

`SessionViewModel.kt:55-70` 在 `init` 里启动协程，首启种一个示例孩子、读保存的当前孩子：

```kotlin
init {
    viewModelScope.launch {
        val list = childrenRepo.children.first()   // .first() 取冷流首值并取消
        if (list.isEmpty()) {
            val id = childrenRepo.add("小跳", "🐰", "#FF8FD3", null)
            prefs.setCurrentChildId(id); _currentChildId.value = id; prefs.markSeeded()
        } else { ... }
    }
}
```

**要点**：
- `Flow.first()` 会取第一个发射值然后**自动取消上游**——适合「我只想知道现在的值、不需要持续订阅」的初始化场景。
- `init` 里的 `viewModelScope.launch` 是 fire-and-forget，VM 销毁即取消；种子数据只跑一次（`markSeeded()` 防止重复种）。

---

## 9. 测试 VM 的协程（当前项目缺口 + 推荐写法）

项目目前**只有纯逻辑单测**（`DateUtilsTest` / `PoseCounterTest` / `RewardsTest`），**没有 VM 层测试**。
要给 VM 写测试，推荐引入：

```kotlin
// build.gradle.kts testImplementation
testImplementation("app.cash.turbine:turbine:1.2.0")          // 断言 StateFlow 发射序列
testImplementation("io.mockk:mockk:1.13.11")                  // mock Repository
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")  // runTest / StandardTestDispatcher
```

示例骨架（测 `RecordsViewModel.setChild` 切换是否刷新 `records`）：

```kotlin
@Test
fun `setChild switches records flow`() = runTest {
    val repo = mockk<JumpRepository>()
    every { repo.records(1) } returns flowOf(listOf(rec1))
    every { repo.records(2) } returns flowOf(listOf(rec2))
    val vm = RecordsViewModel(repo)
    vm.test {
        vm.setChild(1); assertEquals(listOf(rec1), awaitItem().records)
        vm.setChild(2); assertEquals(listOf(rec2), awaitItem().records)
    }
}
// 真实写法用 turbine：vm.records.test { ... awaitItem() ... }
```

**要点**：
- 用 `runTest` + `StandardTestDispatcher` 让虚拟时间可控，避免真等 5000ms 的 `WhileSubscribed`。
- `stateIn(WhileSubscribed(5000))` 在测试里要切到 `UnconfinedTestDispatcher` 或手动 `advanceTimeBy`，否则订阅窗口不触发。

---

## 10. 速查清单（写到新 VM 时照着做）

- [ ] 状态出口一律 `_x: MutableStateFlow` + `x: StateFlow`（`asStateFlow()`）。
- [ ] 冷流（仓库）→ 热流：`.stateIn(viewModelScope, WhileSubscribed(5000), initialValue)`。
- [ ] 切换类触发用 `flatMapLatest`；合并类派生用 `combine`。
- [ ] `initialValue` 不是真实数据——做基线/快照前等「真实首帧」标志。
- [ ] 高频一次性事件别用 `StateFlow`（会合并），改用 `Channel` / `SharedFlow`。
- [ ] 写盘放 `viewModelScope.launch`；高频写（积分/计数）走内存累计 + 定时批量 flush。
- [ ] 调系统组件/WorkManager 的写操作包 `try/catch`。
- [ ] 种子数据放 `init { viewModelScope.launch { ...first() } }`，配合「已种」标记防重。
- [ ] 不要手写 `CoroutineScope` 忘记取消——`viewModelScope` 已自动管理。
- [ ] 独立互不影响的多个任务，考虑 `supervisorScope` / `coroutineScope`，避免一个失败连坐其它。

---

**接入**：本篇与 `COMPOSE_RECOMPOSITION.md`（高频状态下沉）、`DATASTORE_MULTICHILD.md`（批量 flush）、`ROOM_MIGRATION.md`（仓库层 Flow）、`TTS_ARBITRATION.md`（VM 调语音）强相关，建议对照读。
