# 导航架构（Jetpack Navigation Compose）

> 基于 `ui/navigation/AppRoot.kt` 真实代码。本文说明路由表、底部导航、ViewModel 跨页共享、返回拦截与已知坑。

---

## 1. 路由表：`Screen` 密封类

所有目的地集中在一个密封类里（`AppRoot.kt:59-71`），路由用字符串常量：

```kotlin
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object Training : Screen("training")
    object Stats : Screen("stats")
    object Achievements : Screen("achievements")
    object Children : Screen("children")
    object Settings : Screen("settings")
    object CameraTraining : Screen("camera_training")
    object Privacy : Screen("privacy")
    object Profile : Screen("profile")
}
```

**结构（10 个目的地）**：
- 底部 4 tab：`Home` / `Stats` / `Achievements` / `Profile`（`AppRoot.kt:78-84`）。
- 二级页（不显示底栏）：`Training` / `CameraTraining` / `Children` / `Settings` / `Privacy`。
- 启动页：`Splash`（首页之前的闪屏，种数据/跳首页）。

> 设计注记：统计与历史**已合并为一个「统计」页**（页内用 tab 栏切报告/历史），空出的位置给了「我的」(`Profile`)。见 `StatsScreen.kt`。

---

## 2. 导航宿主与图

`AppRoot.kt:88` 用 `rememberNavController()`，`NavHost` 的 `startDestination = Screen.Splash.route`，10 个 `composable(...)` 目的地（`AppRoot.kt:197-212`）。

每个 `composable` 直接把 VM / `session` / `container` 作为**组合参数**传给屏幕 Composable，而不是塞进 `route`：

```kotlin
composable(Screen.Home.route) { HomeScreen(nav, session, records, container) }
composable(Screen.Training.route) { TrainingScreen(nav, session, container) }
```

**设计取舍（重要）**：
- ✅ 简单：屏幕直接拿到已构造好的 VM 和 `AppContainer`，不用 `navArguments()` 解析、不用 `SavedStateHandle` 取参。
- ❌ 代价：**无法用 deep link 进某个页**（参数没在 route 里）；进程被系统杀死后，若要从外部链接恢复特定页/状态，当前架构不支持。
- 本项目是单机儿童跳绳 App，无分享链/推送直达页需求，当前取舍合理。

---

## 3. ViewModel 跨页存活：Activity 级作用域

`AppRoot` 在组合顶层用 `viewModel(factory = ...)` 创建三个「全局 VM」（`AppRoot.kt:89-90, 112`），它们默认归属 **Activity**（非某个 navigable destination），因此**跨所有页面共享同一实例**：

| VM | 作用域 | 共享目的 |
|----|--------|----------|
| `SessionViewModel` | Activity | 当前孩子、孩子列表、全局偏好（音效/灵敏度/目标） |
| `RecordsViewModel` | Activity | 统计/历史/成就派生数据（随孩子切换刷新） |
| `TrainingViewModel` | Activity | **训练会话跨页存活**（暂停挂起 → 回首页 → 浮条续跳） |

**`TrainingViewModel` 跨页存活是该架构的关键**（`AppRoot.kt:108-112` 注释详述）：
- 进入训练页（`Training`/`CameraTraining`）时，屏幕 Composable 显式传 **Activity owner** 给 `viewModel()`，从而拿到 AppRoot 创建的那个实例。
- 训练中退出训练页 = 自动暂停挂起（`onDispose` 调 `pause()`），计数/时长留在 VM 内存 + 落盘快照。
- 回首页后 `FloatingJumpBar` 读取 `trainingVm` 的 `paused/count`，显示「已跳 X 个 · 继续」胶囊；点它按 `sessionMode` 回到正确训练页续跳。

> 若训练页误用 `viewModel()` 默认作用域（destination 级），每次进页都新建 VM、会话清零——这是「浮条续跳」功能能成立的前提。

---

## 4. 底部导航：`popUpTo` + `launchSingleTop`

`JumpBottomBar`（`AppRoot.kt:244-270`）点击 tab 的导航配置：

```kotlin
nav.navigate(screen.route) {
    popUpTo(Screen.Home.route) { saveState = true }  // 回 Home 以下并保存各 tab 状态
    launchSingleTop = true                            // 不在栈顶重复压
    // 刻意不启用 restoreState：规避 nav-compose 已知 bug
}
```

**已知坑（代码注释已记）**：`launchSingleTop + restoreState` 指向同一目标时，点击可能「无反应」。
本项目**故意不开 `restoreState`**，牺牲「切走再切回保留滚动位置」换稳定性。

**底栏可见性**（`AppRoot.kt:104-106`）：只在 4 个主 tab 路由显示；设置/孩子/隐私等二级页隐藏，避免出现「无高亮 tab」的困惑态。

---

## 5. 首页返回键 = 退出应用

`AppRoot.kt:133-135` 用 `BackHandler` 拦截首页返回：

```kotlin
BackHandler(enabled = currentRoute == Screen.Home.route) {
    activity?.finishAffinity()   // 直接结束任务，而不是弹栈
}
```

**为什么**：早期「返回退出重开后，再按返回关掉一个首页又冒出另一个首页（浮条内容还不一样）」——
根因是栈里出现两个 Home（Splash `navigate` 竞态重复压栈），返回只是逐个弹栈。
现在首页返回直接 `finishAffinity()`，配合 Splash 侧 `launchSingleTop` 双保险。

> 训练页返回则是**三选框**（继续/挂起/清零），见 `CameraTrainingScreen.kt` 的 `BackHandler`，与首页退出是两套逻辑。

---

## 6. 浮条：覆盖在 NavHost 之上

`FloatingJumpBar` 放在 `Box`（NavHost 在下、浮条在上）内（`AppRoot.kt:195-238`），是**跨页面常驻**的悬浮入口。
它的 `onClick` 按会话状态分流到正确训练页（`AppRoot.kt:225-235`）：
- 挂起「继续」→ 回该会话自己的计数方式页（摄像头会话回 `CameraTraining`，传感器回 `Training`）。
- 无会话「开始跳绳」→ 按用户偏好 `countMode` 分流（与首页大按钮一致）。

---

## 7. 跨进程挂起恢复（不是导航目的地，是弹框）

进程被系统杀死后，`TrainingViewModel` 内存状态丢失，但 `pause()` 已落盘快照。
`AppRoot.kt:149-189` 在**首次进首页**时读快照、弹 `AlertDialog` 问「继续跳 / 放弃」：
- 继续 → `trainingVm.restoreSuspended(...)` 还原个数/时长/积分/计数方式。
- 放弃 → `prefsRepository.clearSuspendedSession()` 清空。

这是「恢复会话」而非「导航」，所以用 `LaunchedEffect(currentRoute)` + `mutableStateOf` 控制，不在 NavHost 里。

---

## 8. 当前架构的局限与演进方向

| 现状 | 建议（按需） |
|------|--------------|
| 路由是字符串常量、无参数 | 升级到 Navigation Compose 2.8+ **类型安全路由**（`@Serializable` data class + `NavType`），编译期校验参数 |
| 无嵌套导航图 | 统计页内的「报告/历史」子 tab 可抽成嵌套 `NavGraph`，状态管理更清晰 |
| 无 deep link | 若未来要「点击提醒通知直达训练页」，需把参数放进 route + `<nav-graph>` intent-filter |
| 无 `SavedStateHandle` | 屏幕参数全靠组合入参，进程重建后 UI 层状态（滚动位置等）不恢复 |
| 手动 `viewModel(factory=...)` | 若引入 Hilt，可改为 `hiltViewModel()`，但 Activity 级共享需 `@HiltViewModel` + 自定义 `ViewModelStoreOwner` |

---

## 9. 速查：想改导航去哪

- 加新页面：在 `Screen` 加路由 → `NavHost` 加 `composable` → 底栏（如需）加 `BottomItems`。
- 改底栏显隐：`AppRoot.kt:104` 的 `showBottomBar` 列表。
- 改返回行为：首页在 `AppRoot.kt:133`；训练页在 `CameraTrainingScreen.kt` / `TrainingScreen.kt` 的 `BackHandler`。
- 改「浮条续跳」分流：`AppRoot.kt:225-235`。
- 改 VM 跨页共享：确认 `viewModel(factory=container.xxxFactory)` 在 AppRoot 顶层创建（Activity 级）。

---

**接入**：本篇与 `COMPOSE_RECOMPOSITION.md`（高频状态下沉到子组件）、`DATASTORE_MULTICHILD.md`（挂起快照持久化）、`COROUTINE_FLOW_VM.md`（VM 状态出口）强相关。
