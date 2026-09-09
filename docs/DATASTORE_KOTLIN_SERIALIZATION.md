# DataStore 内部机制 与 Kotlin Serialization 实践

> 配套 `docs/ARCHITECTURE.md` 第 12 节「工程化与质量」。
> 本文基于项目真实代码 `data/repository/PreferencesRepository.kt` 与 `app/build.gradle.kts` 撰写，不编造现状。
> 文中标注 ⚠️ 的为「进阶 / 待实施」部分，当前项目尚未采用。

---

## 0. 现状速览（真实）

| 项 | 项目现状 |
|----|----------|
| DataStore 变体 | `androidx.datastore:datastore-preferences:1.1.1`（**Preferences 变体**，底层 protobuf） |
| 存储文件名 | `jump_prefs`（`preferencesDataStore(name = "jump_prefs")`，见 `PreferencesRepository.kt:14`） |
| 数据模型 | 纯 `Preferences`（key-value），**无** `kotlinx.serialization` 依赖，复杂对象手写逐字段映射 |
| 进程内单例 | `private val Context.dataStore by preferencesDataStore(...)` 委托属性，按 Context 复用 |
| 多孩子隔离 | 动态 key：`points_$childId` / `earned_prizes_$childId` / `redeemed_prizes_$childId` |
| 复杂对象 | `SuspendedSession` 是普通 `data class`，落盘时逐字段拆成 5 个 Preferences key（手写序列化） |

---

## 1. DataStore 是什么，为什么取代 SharedPreferences

`DataStore` 是 Jetpack 提供的**异步、一致、可观察**的键值/对象存储，分两种：

- **Preferences DataStore**：key-value，类型安全（`intPreferencesKey` 等），底层 protobuf 文件。本项目用的就是它。
- **Proto DataStore / Typed DataStore**：存一个由 `Serializer` + `@Serializable` 定义的强类型对象（见第 7 节）。

对比 `SharedPreferences`：

| 维度 | SharedPreferences | DataStore(Preferences) |
|------|-------------------|------------------------|
| 读取线程 | 主线程可能 ANR（全量读 XML） | 全程在 `Dispatchers.IO`，返回 `Flow` |
| 写入 | 同步 `apply`/`commit`（`commit` 阻塞主线程） | 事务性 `edit { }`，挂起函数 |
| 一致性 | 多进程/异常易丢数据 | 基于 protobuf 事务文件，原子提交 |
| 观察变化 | 无类型安全监听 | `data.map { }` 返回冷 `Flow`，变更即发 |
| 异常 | 静默失败 | 抛异常，可被 `catch` 处理 |

结论：**凡需要被 Compose 观察、或写操作不应丢的配置，都用 DataStore**，本项目 `PreferencesRepository` 即如此。

---

## 2. 项目落地：初始化与文件位置

```kotlin
// PreferencesRepository.kt:14
private val Context.dataStore by preferencesDataStore(name = "jump_prefs")
```

- 委托属性 `by preferencesDataStore` 保证**同一 `Context` 拿到同一实例**（内部按 name 缓存），避免多实例竞态。
- 实体文件位于：`/data/data/com.jumpdaily.jump/files/datastore/jump_prefs.preferences_pb`
  - `files/datastore/` 是 DataStore 固定目录；`.preferences_pb` 后缀表示该文件是 protobuf 编码的 Preferences 快照。
- 注意：文件在 **App 私有目录**，卸载即删，符合隐私政策「数据不出本机」声明（`PrivacyScreen.kt` 第四节）。

---

## 3. 内部机制（protobuf + 事务 + Flow）

```
写入：edit { tx -> tx[key] = value }
        │
        ▼
  DataStore 读当前快照 → 在你的 lambda 里产出新快照 → 用 protobuf 原子写临时文件
        │
        ▼
  rename 临时文件覆盖正式文件（原子提交，崩溃也不会写一半）

读取：context.dataStore.data  (Flow<Preferences>)
        → 首次读取解析 .preferences_pb 到内存快照
        → 之后每次 edit 提交都会 emit 新快照（含首帧 emit 当前值）
        → 订阅方（collectAsStateWithLifecycle）拿到新值触发重组
```

关键点：

1. **`edit` 的 lambda 是事务性的**：不要在其中做耗时/挂起 IO（DataStore 已在外层用 `Dispatchers.IO`，但你的 lambda 若是另一个挂起调用，会被串行化排队，拉长提交）。本项目 `edit` 块都很薄（只赋值），正确。
2. **`data` 是冷 Flow + 重放**：新订阅者立即收到最新快照（首帧），之后每次变更再 emit。所以 `prefs.darkTheme.collectAsStateWithLifecycle()` 进页面就有正确值，不会闪一下默认。
3. **快照不可变**：`it[key]` 返回的是快照里的副本；在 lambda 外持有 `it` 没有任何意义。

---

## 4. 类型安全的 Key 封装（项目的 Keys object）

```kotlin
// PreferencesRepository.kt:25-44
private object Keys {
    val CURRENT_CHILD_ID = longPreferencesKey("current_child_id")
    val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
    val DAILY_GOAL       = intPreferencesKey("daily_goal")
    val COUNT_MODE       = stringPreferencesKey("count_mode")
    val SEEN_BADGE_IDS   = stringSetPreferencesKey("seen_badge_ids")
    // ... 共 20+ 个
}
```

好处：key 名（字符串）集中在 `Keys`，**调用方永远用强类型 key**，编译期防拼错；value 类型由 key 类型决定（`intPreferencesKey` 必存 `Int`）。

**支持的 key 类型**：`intPreferencesKey` / `longPreferencesKey` / `booleanPreferencesKey` / `floatPreferencesKey` / `stringPreferencesKey` / `stringSetPreferencesKey`（**注意：没有 `doublePreferencesKey`**，需要双精度就自己乘 1000 存 Int 或存 String）。

---

## 5. 多孩子隔离：把 childId 编进 key

积分/奖品按孩子隔离，key 里动态拼 `childId`：

```kotlin
// PreferencesRepository.kt:143-145
private fun pointsKey(childId: Long)   = intPreferencesKey("points_$childId")
private fun prizesKey(childId: Long)   = stringSetPreferencesKey("earned_prizes_$childId")
private fun redeemedKey(childId: Long) = stringSetPreferencesKey("redeemed_prizes_$childId")

// 读取（带默认 0 / emptySet）
fun points(childId: Long): Flow<Int> =
    context.dataStore.data.map { it[pointsKey(childId)] ?: 0 }
```

- **语义区分**（`PreferencesRepository.kt:150-160` 注释已写明）：`earnedPrizes` 只是「解锁庆祝动画只弹一次」的去重集合，**不代表孩子真兑换**；真正兑换用 `redeemedPrizes`。这是 DataStore 当「去重标记位」用、而非当「业务真相」用的典型例子。
- **增减积分用 read-modify-write**：`addPoints` 在 `edit` 内读旧值 + delta + `coerceAtLeast(0)`，保证不会变负（`:163-170`）。

> 进阶讨论：当「按孩子隔离的键越来越多」时，用 `points_$childId` 拼接 key 会很难做「列出某孩子全部配置」或「迁移某孩子」。更规整的做法是 Proto/Typed DataStore 存一个 `ChildPrefs` 对象列表（见第 7 节）。但当前规模下动态 key 足够、可读性最好，**不必提前优化**。

---

## 6. 复杂对象怎么存：SuspendedSession 手写逐字段映射

训练暂停/进程被杀后需要「跨进程挂起续跳」，把会话快照落盘：

```kotlin
// PreferencesRepository.kt:210-216  —— 普通 data class，不是 @Serializable
data class SuspendedSession(
    val childId: Long, val count: Int, val elapsedSec: Int,
    val mode: CountMode, val sessionPoints: Int
)

// 读取：手动逐字段拼回对象（:225-235）
fun suspendedSession(): Flow<SuspendedSession?> = context.dataStore.data.map { p ->
    val child = p[susChildKey] ?: return@map null   // 缺 childId 视为无快照
    val count = p[susCountKey] ?: return@map null
    SuspendedSession(child, count, p[susElapsedKey] ?: 0,
        CountMode.fromKey(p[susModeKey] ?: CountMode.CAMERA.key), p[susPointsKey] ?: 0)
}

// 写入：count<=0 不存，避免「0 个还问继续」（:238-247）
suspend fun setSuspendedSession(...) { if (count <= 0) return; context.dataStore.edit { ... } }
```

**为什么没有用 kotlinx.serialization？** 因为 `CountMode` 是枚举式（`fromKey`），直接拆成 `stringPreferencesKey("suspended_mode")` 存 key 最直观、可单字段改、可人工 `adb` 读。手写映射在字段少（5 个）时反而更透明。详见第 7 节对比。

---

## 7. Kotlin Serialization 进阶（⚠️ 待实施蓝图）

项目**当前未引入 `kotlinx-serialization`**（构建脚本仅 `datastore-preferences:1.1.1`，无 `kotlinx-serialization-json`）。当你想存一个**结构化对象**而非一堆散 key 时，最地道的方式是 **Typed DataStore + `@Serializable`**：

```kotlin
// 1) 加依赖（app/build.gradle.kts）
// implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
// implementation("androidx.datastore:datastore:1.1.1")   // 注意是 datastore 而非 datastore-preferences

// 2) 定义可序列化对象
@Serializable
data class SuspendedSession(
    val childId: Long, val count: Int, val elapsedSec: Int,
    val mode: String, val sessionPoints: Int   // CountMode 存其 key 字符串
)

// 3) 写 Serializer（处理版本兼容）
object SuspendedSessionSerializer : Serializer<SuspendedSession?> {
    override val defaultValue: SuspendedSession? = null
    override suspend fun readFrom(input: InputStream): SuspendedSession? =
        try { Json.decodeFromString<SuspendedSession?>(input.readBytes().decodeToString()) }
        catch (e: Exception) { null }   // 旧版本/坏数据 → 兜底 null，绝不崩首屏
    override suspend fun writeTo(t: SuspendedSession?, output: OutputStream) =
        output.write(Json.encodeToString(t).toByteArray())
}

// 4) 建 Typed DataStore
val Context.suspendedSessionStore by dataStore("suspended_session.json", SuspendedSessionSerializer)
```

**何时选 Typed DataStore 而非 Preferences：**
- 对象字段多、且整体读写（如「某孩子的完整偏好」）→ Typed 更省 key、易版本迁移。
- 字段互相独立、需单字段改/人工排查 → 继续用 Preferences（当前项目主流做法）。

**Kotlin Serialization 在别处的价值**：`CountMode`、`Reward` 等若需网络同步/导出 JSON，给它们加 `@Serializable` 即可 `Json.encodeToString`，无需手写映射。本项目目前纯本地、无需此能力。

> 风险警示（呼应 `R8_RELEASE.md`）：若引入 `kotlinx-serialization`，release 混淆要 keep `@Serializable` 类与 `kotlinx.serialization` 内部；否则运行时 `SerializationException`。当前项目因未用，故无此坑。

---

## 8. 文件落盘诊断（真机排查配置）

配置不对时直接看 DataStore 实体文件：

```bash
# 拉取（需 debug 或已 root/有权限）
adb exec-out run-as com.jumpdaily.jump cat files/datastore/jump_prefs.preferences_pb > /tmp/jump_prefs.pb
# protobuf 二进制不可直读；可用 protoc 或简单 strings
strings /tmp/jump_prefs.pb | head

# 或看某 key 的语义：在代码里加临时读取并打印（参考 pose_error.txt / tts_init.txt 的落盘诊断套路）
```

注意：`preferences_pb` 是 protobuf，**不是明文**；想肉眼验证某个开关是否落盘，最稳的是在 App 内读 `prefs.xxx.first()` 打到 `tts_init.txt` 类诊断文件。

---

## 9. 坑与最佳实践清单

1. **不要在 `edit { }` 里调另一个挂起且慢的 `edit`**（会死锁排队）。本项目无此问题。
2. **`data` 是冷 Flow**：不 collect 就不会读文件；UI 用 `collectAsStateWithLifecycle` 自动管生命周期。
3. **默认值写在 `map { it[key] ?: default }`**：DataStore 没有「默认值声明」，缺 key 返回 `null`，必须由调用方兜底（本项目每个读取都带了 `?: default`）。
4. **key 改名 = 数据丢失**：如浮条位置 `float_bar_edge_v2`/`float_bar_y_v2`（`:188-189`）改名即等效「重置」，旧 key 不读即弃——这是有意的一次性迁移手段，但常规改动要兼容旧 key。
5. **多进程不共享**：DataStore 跨进程不保证实时一致；本 App 单进程，无此虑。
6. **minSdk 26 支持**：DataStore 1.1.x 要求 minSdk 21+，本项目 26 满足。

---

## 10. 与 Room 的取舍（速查）

| 存什么 | 用 DataStore | 用 Room |
|--------|--------------|---------|
| 全局开关/设置/当前孩子 id | ✅ `PreferencesRepository` | |
| 积分/已兑换奖品（按孩子 key） | ✅ 简单 KV 足够 | 也可，但过度设计 |
| 跳绳历史记录（结构化、可查询/统计） | | ✅ `JumpRecord` 表 |
| 孩子档案 | | ✅ `Child` 表 |

详见 `docs/ROOM_MIGRATION.md` 与 `docs/DATASTORE_MULTICHILD.md`。

---

**上手向导：** 改任何设置项 → 先在 `PreferencesRepository.kt` 的 `Keys` 加 key + 加 `Flow` 读取 + 加 `suspend setXxx`；UI 端 `collectAsStateWithLifecycle` 消费即可，无需手动保存。
