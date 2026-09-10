# Kotlin 协程异常处理 / 超时取消实战

> 基于本仓库真实协程用法（`TrainingViewModel`、`SettingsViewModel`、`CameraTrainingScreen`、`PoseJumpDetector`、各 Repository），讲清协程异常传播、取消协作、超时控制的原理与反模式。
> 读本文前建议先看 [`COROUTINE_FLOW_VM.md`](./COROUTINE_FLOW_VM.md)（StateFlow / 流切换基础）。

---

## 1. 协程取消的本质：协作式

协程**没有强制杀线程**，取消靠 `Job.cancel()` 抛出一个 `CancellationException`，需要代码**主动配合**才能停下：

- `delay()`、`await()`、`yield()` 等**可挂起**的官方 API 会在取消时自动抛出 `CancellationException`，从而退出挂起循环。
- 纯 CPU 密集的 `while` 循环**不会**因为取消而停，必须在循环体里 `ensureActive()` 或检查 `isActive`。

```kotlin
// TrainingViewModel.kt:195-201 —— 计时器循环，靠 delay() 协作退出
ticker = viewModelScope.launch {
    while (true) {
        delay(1000)                       // ← 可挂起，取消时这里抛 CancellationException 退出
        if (!_running.value) break        // ← 双保险：running 置 false 后下一轮 break
        _elapsed.value += 1
    }
}
```

> 要点：`stop()` 里 `ticker?.cancel()`（`TrainingViewModel.kt:210`）通过取消 Job 让 `delay` 抛异常，循环自然结束。若把 `delay` 去掉、改成 `Thread.sleep`，则取消无效、计时器继续跑。

### 1.1 `ensureActive()` / `isActive` 手动检查

在自定义 `while` 里做推理/计算时，必须手动检查：

```kotlin
// 推荐：CPU 密集循环
while (isActive) {                       // isActive 来自 CoroutineScope
    val result = heavyCompute()
    if (result.done) break
}
// 或在挂起点前
coroutineContext.ensureActive()         // 取消时直接抛 CancellationException
```

`PoseJumpDetector` 的分析回调运行在 `ImageAnalysis` 的分析线程上，Detector 内部生命周期 `stop()` 负责停止，协程层无需额外检查；但落到 VM 的 `onJump` 是在分析线程回调里 `viewModelScope.launch { ... }` 派发到主线程，scope 取消即不再派发新工作。

---

## 2. 作用域与 `viewModelScope`

| 作用域 | 生命周期 | 项目用法 |
|--------|----------|----------|
| `viewModelScope` | 与 ViewModel 同生共死（onCleared 时取消） | 几乎所有 VM：`TrainingViewModel.kt:146-156`、`SettingsViewModel.kt:52`、`ChildrenViewModel.kt:23` |
| `lifecycleScope` | 与 Lifecycle（Activity/Fragment）同生共死 | Composable 内用 `LaunchedEffect` 更合适 |
| `rememberCoroutineScope` | 与 Composable 同生共死 | 事件回调里临时 `launch`，如 `FloatingJumpBar` 的 `onPositionSettled` 写盘 |
| 自定义 `CoroutineScope` | 手动 `cancel()` | `AppContainer.appScope`（应用级，挂提醒初始化） |

**反模式**：在 VM 里 `GlobalScope.launch { ... }`——永不取消，泄漏 + 崩溃风险。本项目没有，但需警惕。

### 2.1 `collect` 冷流在 `viewModelScope` 内自动取消

```kotlin
// TrainingViewModel.kt:147-149 —— 订阅偏好，scope 取消时 collect 自动停
viewModelScope.launch { prefs.soundEnabled.collect { soundPlayer.setEnabled(it) } }
```

`StateFlow`/`DataStore` 的 `data` 是冷流，`collect` 在 `viewModelScope` 内启动，ViewModel 销毁时 `viewModelScope` 取消，collect 协程一并取消，不会泄漏。

---

## 3. 异常传播：`coroutineScope` vs `supervisorScope`

这是最易踩坑的点。

```
coroutineScope {        // 任一子协程抛异常 → 取消整个 scope（含兄弟协程）
    launch { a() }      // a 抛异常
    launch { b() }      // b 也会被取消（即使自己没事）
}

supervisorScope {       // 子协程异常互不牵连（默认不取消父/兄弟）
    launch { a() }      // a 抛异常 → 仅 a 失败
    launch { b() }      // b 继续跑
}
```

- `viewModelScope.launch { ... }` 的**根**是 `SupervisorJob`（ViewModel 实现保证一个子失败不影响其它子），所以多个 `viewModelScope.launch` 相互独立——这正是本项目大量并行 `launch` 订阅各偏好的原因（`TrainingViewModel.kt:146-156` 五个独立订阅互不干扰）。
- 但在**单个** `launch` 内部用 `coroutineScope { }` 拉起多个子任务，任一子失败会连坐兄弟：需要隔离时用 `supervisorScope`。

### 3.1 `launch` vs `async` 的异常处理差异

```kotlin
// launch：异常立即抛出，若没包 try/catch 且未设 handler → 走 scope 的 handler / 崩溃
viewModelScope.launch { riskyOp() }            // riskyOp 抛 → 此处"未捕获"异常

// async：异常被存进 Deferred，只有 await() 时才抛出（延迟暴露）
viewModelScope.launch {
    val d = async { riskyOp() }
    // ... 如果永远不 await，异常被静默吞掉（直到 Deferred 被 GC 才可能 warning）
    val r = d.await()                          // ← 异常在这里才抛
}
```

> 规则：用 `async` 必须 `await()`，且 `await()` 调用点要包 `try/catch`；否则异常延迟到不可控位置。

---

## 4. 真实异常捕获现状

本项目**已用 `try/catch` 的点**（正确范例）：

```kotlin
// SettingsViewModel.kt:52-60 —— 提醒调度失败不应让开关/页面崩溃
fun setReminder(enabled: Boolean, hour: Int, minute: Int) = viewModelScope.launch {
    prefs.setReminder(enabled, hour, minute)
    try {
        if (enabled) scheduler.schedule(hour, minute) else scheduler.cancel()
    } catch (e: Exception) {
        e.printStackTrace()                      // 极端情况 WorkManager 初始化异常，吞掉不影响主流程
    }
}

// CameraTrainingScreen.kt:222-268 —— 相机绑定整体包 try/catch
val future = ProcessCameraProvider.getInstance(ctx)
future.addListener({
    try {
        val cameraProvider = future.get()      // 可能抛 ExecutionException/InterruptedException
        // ... 构建 Preview/ImageAnalysis 并 bindToLifecycle
    } catch (e: Exception) {
        Log.e("CameraTraining", "bind camera failed", e)
    }
}, ContextCompat.getMainExecutor(ctx))
```

`PoseJumpDetector.kt:51-82` 在 `processFrame`/`toBitmap` 周围也做了 `try/catch`，避免单帧异常打断整条分析管线（符合"分析线程不能因一帧崩"的原则）。

### 4.1 ⚠️ 不要吞 `CancellationException`

```kotlin
// 错误：把取消也当普通异常吞了，导致协程无法真正停止
try { doWork() }
catch (e: Exception) { e.printStackTrace() }   // ← 若 e 是 CancellationException，应 rethrow

// 正确：只处理业务异常，放行取消
try { doWork() }
catch (e: CancellationException) { throw e }   // 或干脆不 catch CancellationException
catch (e: Exception) { handle(e) }
```

> 本项目 `catch (_: Throwable)`（`PoseJumpDetector.kt:82`、`ChildAvatar.kt:63`）用于"单帧/单资源加载失败忽略"，可接受；但要清楚它**也会吞掉取消**，仅在"失败可忽略且循环会自然结束"的场景安全。

---

## 5. 超时控制：`withTimeout` / `withTimeoutOrNull`

两个 API：超时抛 `TimeoutCancellationException`（也是 `CancellationException` 子类）。

```kotlin
// 超时即失败（抛异常）
withTimeout(3000) { fetchConfig() }

// 超时返回 null（不抛，推荐用）
val cfg = withTimeoutOrNull(3000) { fetchConfig() }
if (cfg == null) { /* 超时降级 */ }
```

### 5.1 ⚠️ 本项目缺超时——建议补的两处

| 位置 | 风险 | 建议 |
|------|------|------|
| `ProcessCameraProvider.getInstance(ctx).get()`（`CameraTrainingScreen.kt:226`） | 极端机型相机服务卡死，`future.get()` 永久阻塞主线程 executor | 改用 `withTimeoutOrNull` + 失败提示 |
| `PoseModelProvider` 模型下载/拷贝（`MEDIAPIPE_POSE.md` 三级加载） | 网络慢/断网时 `assets→cache` 拷贝或下载无上界 | ✅ **已落地**：`PoseModelProvider.getModelPathSafely`（IO 调度器 + `withTimeoutOrNull(15_000)`）超时/失败返回 `null`，`CameraTrainingScreen` 降级提示（见下方改动） |

> 为什么要补：`future.get()` 在 `ContextCompat.getMainExecutor` 里执行，一旦卡住会**卡主线程**甚至触发 ANR；加 `withTimeoutOrNull` 是低成本的健壮性兜底。

---

## 6. 失败兜底与重试

```kotlin
// 简单退避重试（示例，非本项目代码）
suspend fun <T> retryIO(times: Int, block: suspend () -> T): T {
    repeat(times - 1) { i ->
        try { return block() }
        catch (e: Exception) { delay((100L * (i + 1))) }   // 线性退避
    }
    return block()                                          // 最后一次不再吞
}

// 或用 kotlinx-coroutines 的 retry：
// retry(times = 3, initialDelayMillis = 100, maxDelayMillis = 1000) { shouldRetry(it) }
//     { block() }
```

---

## 7. `CoroutineExceptionHandler`（全局兜底，慎用）

```kotlin
val handler = CoroutineExceptionHandler { ctx, e ->
    Log.e("Global", "uncaught in ${ctx[CoroutineName]}", e)
}
// 只作为"最后一道日志"，不能当正常控制流；且对 supervisorScope 的子无效
viewModelScope.launch(handler) { risky() }
```

> 本项目**没有**用全局 handler，依赖 `try/catch` 精确捕获。建议保持：handler 只做日志/上报，真正的业务降级仍在 `try/catch` 里做。

---

## 8. 与 `flow` 的取消/异常配合

```kotlin
// 流收集中抛异常：默认向上传到 collect 所在协程 → 触发该协程取消
// 用 catch 操作符在流内部兜底（推荐，比在外层 try/catch 更精确）
prefs.points(childId)
    .catch { e -> emit(0) }            // 流级异常转默认值，不崩溃
    .collect { _totalPoints.value = it }

// retryWhen：流恢复后自动重订阅
flow { emit(read()) }
    .retryWhen { cause, attempt -> attempt < 3 && cause is IOException }
```

---

## 9. 反模式速查

| 反模式 | 后果 | 正解 |
|--------|------|------|
| `GlobalScope.launch` | 永不取消，泄漏/崩溃 | `viewModelScope` / `lifecycleScope` |
| `async` 不 `await` | 异常静默 | 必须 `await` 且包 `try/catch` |
| `catch (e: Exception)` 吞 `CancellationException` | 协程停不下来 | 放行取消或单独 `catch (CancellationException)` |
| 在 `launch` 内裸调可能抛的 suspend 不包 `try/catch` | 崩溃 | 关键路径包 `try/catch` |
| 同步 `while` 无 `isActive` | 取消无效 | `while (isActive)` / `ensureActive()` |
| 主线程 executor 里 `future.get()` 无超时 | ANR 风险 | `withTimeoutOrNull` |
| `runBlocking` 在 UI 层 | 卡 UI | 用 `suspend` + 协程 |

---

## 10. 测试要点（接 [`TESTING.md`](./TESTING.md)）

```kotlin
@Test
fun `reminder failure does not crash`() = runTest {
    // 用 StandardTestDispatcher + Turbine 验证异常被吞
    coEvery { reminderScheduler.schedule(any(), any()) } throws RuntimeException()
    viewModel.setReminder(true, 8, 0)
    // 不抛 → 测试通过（验证 SettingsViewModel.kt:54 的兜底）
}
// 取消测试：advanceTime 后 assert 计时器协程 isCancelled
```

**小结**：本项目协程基础扎实（作用域用对、`collect` 自动取消、`try/catch` 关键路径已包），**超时兜底已部分落地**：相机绑定已于 2026-09-10 用 Guava `Future.get(timeout, unit)` 加 10s 超时（见 `CameraTrainingScreen.kt:226`，因绑定在 `addListener` 非协程回调里，用 Future 原生超时等价于 `withTimeoutOrNull` 意图，编译已验证）；**模型下载仍待补**（按第 5.1 节给 `PoseModelProvider` 的联网三级加载加 `withTimeoutOrNull(15_000)` 回退预置）。**「async 异常延迟暴露」认知已落地（2026-09-07）**：`widget/WidgetAggregator.kt` 用 `coroutineScope` + 四个 `async` + 逐一 `await()` 实现 fail-fast，单测 `WidgetAggregatorTest` 固化「单源抛错必须上抛、不可静默吞」；详见 [`GLANCE_WIDGET.md`](./GLANCE_WIDGET.md) §5。
