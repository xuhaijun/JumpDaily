# Kotlin 在 Android 开发中的常规用法

> 面向"已会基础、要在 Android 工程里用好 Kotlin"的开发者。结合 JumpDaily 真实代码举例（不要求先读项目，但例子都来自本仓库，便于对照）。
> 协程/流部分请补看 [`COROUTINE_FLOW_VM.md`](./COROUTINE_FLOW_VM.md)、[`COROUTINE_EXCEPTION_TIMEOUT.md`](./COROUTINE_EXCEPTION_TIMEOUT.md)。

---

## 1. 基础语法习惯（相对 Java）

| 特性 | 写法 | 项目示例 |
|------|------|----------|
| 不可变优先 | `val`（默认） | 所有路由常量 `object Home : Screen("home")` |
| 可变 | `var`（谨慎） | 浮条 `var pos by remember { mutableStateOf(Offset.Zero) }` |
| 类型推断 | 多数省略 | `val future = ProcessCameraProvider.getInstance(ctx)` |
| 空安全 | `?` / `!!` / `?.` / `?:` | `child?.id`、`arguments?.getString("id")` |
| 字符串模板 | `"已跳 $count 个"` | `FloatingJumpBar.kt:196` |
| 默认参数 | `fun start(childId: Long, mode: CountMode = CountMode.SENSOR)` | `TrainingViewModel.kt:158` |
| 具名参数 | `setReminder(enabled = true, hour = 8, minute = 0)` | 调用清晰 |

> 铁律：**`!!` 尽量少用**——本项目用 `?.` + Elvis(`?:`) 兜底（如 `child?.id ?: return`）。

---

## 2. 数据类 / 枚举 / 密封类

### 2.1 `data class`——建模主力

```kotlin
// 自动生成 equals/hashCode/toString/copy/componentN
data class JumpRecord(
    val id: Long = 0,
    val childId: Long,
    val count: Int,
    val durationSec: Int,
    val timestamp: Long
)
// copy 改单字段（典型：不可变对象"更新"）
val updated = record.copy(count = record.count + 1)
```

Room Entity（`JumpRecord.kt`、`Child.kt`）就是 `@Entity data class`——Kotlin 数据类与持久化天然契合。

### 2.2 `enum class`——有限离散值

```kotlin
enum class CountMode { SENSOR, CAMERA }          // 计数模式
enum class Edge { LEFT, RIGHT }                   // 浮条吸附边（FloatingJumpBar.kt:216）
enum class Mode { IDLE, SUSPENDED }               // 浮条形态
```

### 2.3 `sealed class`——受限层次（状态机/路由）

```kotlin
// 路由：所有可能 destination 编译期穷举（AppRoot.kt:59）
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    // ...
}
// when 不加 else 即可编译期校验"是否漏处理某个分支"
when (screen) {
    Screen.Home -> {}
    Screen.Training -> {}
    // 漏写某个 → 编译报错
}
```

> `sealed interface` + `data object` 是类型安全路由（2.8.0+）的基石（见 [`NAVIGATION_DEEPLINK.md`](./NAVIGATION_DEEPLINK.md)）。

---

## 3. 扩展函数 / 属性——给已有类"加方法"

```kotlin
// 不修改源码，给 Context 加便捷方法（典型 Android 惯用法）
fun Context.dp2px(dp: Float) = dp * resources.displayMetrics.density

// 给 String 加校验
fun String.isValidEmail() = Patterns.EMAIL_ADDRESS.matcher(this).matches()

// 项目里：audio 模块对模板串做资源映射（Encouragements.kt）
fun String.toAssetPath() = "voice/$this"
```

扩展函数是 Kotlin 在 Android 里"去工具类"的关键——把 `Utils.xxx(ctx, ...)` 变成 `ctx.xxx(...)`。

---

## 4. 作用域函数：`let/run/with/apply/also`

| 函数 | 接收者 | 返回值 | 典型用途 |
|------|--------|--------|----------|
| `let` | `it` | lambda 结果 | 空安全处理 `x?.let { }` |
| `run` | `this` | lambda 结果 | 对象上算值 |
| `with` | `this` | lambda 结果 | 对同一对象多次调用 |
| `apply` | `this` | **对象自身** | 初始化配置（Builder 风） |
| `also` | `it` | **对象自身** | 副作用（日志/验证）后返回原对象 |

```kotlin
// apply：配置即返回（典型 View/Builder）
val preview = Preview.Builder().apply {
    setTargetRotation(Surface.ROTATION_0)
}.build()

// let：空安全 + 链式
child?.let { id -> prefs.points(id).collect { ... } }

// also：初始化副作用
val repo = AppDatabase.get(ctx).also { Log.d("db", "created") }
```

---

## 5. 集合与序列

```kotlin
// 函数式操作（只读转换，不产生中间态修改）
val topMovers = records
    .filter { it.count > 100 }
    .sortedByDescending { it.count }
    .take(10)
    .map { it.childId }

// groupBy / associate（聚合）
val byChild = records.groupBy { it.childId }
val sumByChild = records.associate { it.childId to it.count }.toMap()

// 序列（大数据链用 asSequence 延迟求值，避免中间 List）
records.asSequence().filter { ... }.map { ... }.toList()

// 区间
for (i in 0 until records.size) { }
(1..7).forEach { day -> }
```

> 本项目 `Rewards.kt` 大量用 `filter`/`maxOf`/`sumOf` 算奖品等级与积分，是 Kotlin 集合 API 的常规用法。

---

## 6. 高阶函数与 Lambda

```kotlin
// 把"行为"当参数传（回调/clICK）
fun onJump(block: (count: Int) -> Unit) { this.block = block }

// 内联（避免 Lambda 装箱开销，高频调用必加 inline）
inline fun measureTime(block: () -> Unit): Long { /* ... */ }

// 带接收者的 Lambda（DSL 核心）
fun buildProfile(block: ProfileBuilder.() -> Unit) { val b = ProfileBuilder(); b.block() }
```

---

## 7. 空安全实战

```kotlin
val len = text?.length ?: 0                  // Elvis 兜底
val name = text!!.length                    // 危险：仅在你100%确定非null时用
val upper = text?.let { it.uppercase() }     // 链式安全调用

// 平台类型（Java 互操作返回值）要显式声明可空
fun getChild(): Child? = dao.findById(1)     // 标记 ? 避免平台类型隐患
```

---

## 8. 伴生对象 / 单例 / 对象声明

```kotlin
object VoicePriority {                        // 真实单例（本项目优先级枚举即 object/enum）
    const val HINT = 0
}
class AppDatabase {
    companion object {                        // 静态工厂 + 兜底
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) { INSTANCE ?: build(ctx).also { INSTANCE = it } }
    }
}
```

> Room 的 `AppDatabase` 单例（`AppDatabase.kt`）用 `companion object` + 双重检查锁，是 Android 标准写法。

---

## 9. 属性委托

```kotlin
// by lazy：首次访问才初始化（重对象）
private val detector by lazy { PoseLandmarker.create(...) }

// by viewModels()：Jetpack 注入（本项目用 AppContainer 手动注入，等价思路）
val vm: TrainingViewModel by viewModels()

// Compose：by mutableStateOf / by remember
var count by remember { mutableStateOf(0) }

// DataStore：by preferencesDataStore（本项目用 PreferencesRepository 封装，非直接委托）
```

---

## 10. 协程基础（与 Android 强相关）

```kotlin
// 挂起函数：可暂停不阻塞线程
suspend fun fetchPoints(): Int = withContext(Dispatchers.IO) { repo.load() }

// 切换线程
withContext(Dispatchers.Main) { updateUI() }      // 回主线程
withContext(Dispatchers.IO) { readFile() }         // 文件/网络

// 在 VM/UI 启动
viewModelScope.launch { val p = fetchPoints(); _points.value = p }
```

> 详尽见 [`COROUTINE_FLOW_VM.md`](./COROUTINE_FLOW_VM.md) + [`COROUTINE_EXCEPTION_TIMEOUT.md`](./COROUTINE_EXCEPTION_TIMEOUT.md)。

---

## 11. 注解与 Android 集成

```kotlin
@Composable fun HomeScreen(...) { }               // Compose UI
@Entity data class Child(...)                      // Room
@Serializable data class CameraTraining(...)       // 类型安全路由（2.8.0+）
@Override fun onInit(status: Int) { }              // 重写
@Inject / @HiltViewModel                          // 依赖注入（本项目用 AppContainer 手动 DI）
```

---

## 12. Kotlin 在 Android 的"不该做"

| 反模式 | 说明 |
|--------|------|
| 滥用 `!!` | 空指针风险；用 `?.`/`?:` |
| 在 UI 线程做 IO | 用 `withContext(Dispatchers.IO)` |
| `var` 满地 | 默认 `val`，可变状态用 `mutableStateOf`/`StateFlow` |
| Java 风格 getter/setter | 用 `val/var` 属性 |
| 大 `when` 不穷举 | 用 `sealed class` + `when` 无 `else`，编译期校验 |
| 工具类 `XXXUtils` | 优先扩展函数 |

**小结**：Kotlin 在 Android 的常规用法 = 不可变优先 + 空安全 + data/sealed/enum 建模 + 扩展函数去工具类 + 作用域函数初始化 + 协程切线程 + 属性委托。JumpDaily 的代码（Room data class、Screen 密封类、AppDatabase 伴生单例、`viewModelScope.launch{ collect }`、扩展函数式资源映射）是这些惯用法的集中体现。
