# Navigation 深层链接 / 类型安全路由落地

> 基于本仓库真实导航代码（`AppRoot.kt:59-71` 的 `Screen` 密封类 + `:202-211` 的 `composable` 注册）、`navigation-compose:2.7.7`、当前 `AndroidManifest.xml` 现状，讲清现状、深层链接接入、类型安全路由升级路径。
> 读前建议先看 [`NAVIGATION_ARCH.md`](./NAVIGATION_ARCH.md)（整体导航架构）。

---

## 1. 现状：字符串路由 + 密封类

```kotlin
// AppRoot.kt:59-71 —— 路由用 object 常量，route 是字符串
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object Training : Screen("training")
    object CameraTraining : Screen("camera_training")
    object Stats : Screen("stats")
    object Achievements : Screen("achievements")
    object Children : Screen("children")
    object Settings : Screen("settings")
    object Privacy : Screen("privacy")
    object Profile : Screen("profile")
}

// AppRoot.kt:202-211 —— 注册
composable(Screen.Splash.route) { SplashScreen(nav, container) }
composable(Screen.Home.route) { HomeScreen(nav, session, records, container) }
// ...
```

- **优点**：简单、零额外依赖、编译期 `Screen` 枚举防拼错。
- **缺点**：
  1. **参数靠字符串拼接**——若某页需传 `childId`，只能 `navigate("children/$id")`，取值要手动 `split`/`arguments`，无类型安全。
  2. **无编译期校验**：`nav.navigate("hom")` 拼错只能在运行时发现。
  3. **深层链接需要手写 `navDeepLink` + `arguments`**。

---

## 2. 深层链接（Deep Link）接入

### 2.1 当前 Manifest 现状

`AndroidManifest.xml:41-44` 只有一个 `LAUNCHER` 的 `intent-filter`，**没有** `VIEW` / `data android:scheme` 的深层链接入口。要支持"从短信/推送点开直接跳训练页"，需新增：

```xml
<!-- AndroidManifest.xml <activity ...> 内新增 -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data
        android:scheme="jumpdaily"
        android:host="training"
        android:pathPrefix="/camera" />
</intent-filter>
```

### 2.2 在 NavGraph 声明 `navDeepLink`

```kotlin
// 当前 composable 改为带参数 + 深层链接
composable(
    route = "camera_training/{childId}?auto={auto}",          // 带可选参数
    deepLinks = listOf(navDeepLink {
        uriPattern = "jumpdaily://training/camera/{childId}?auto={auto}"
    })
) { backStackEntry ->
    val childId = backStackEntry.arguments?.getString("childId")?.toLongOrNull()
    val auto = backStackEntry.arguments?.getString("auto") == "1"
    CameraTrainingScreen(nav, session, container, childId, auto)
}
```

- 点击 `jumpdaily://training/camera/3?auto=1` → 系统拉起 App → Nav 自动匹配 → 直接进摄像头训练页并带 `childId=3`、`auto=1`。
- **儿童类 App 注意**：深层链接不要绕过首启隐私同意框（`SplashScreen.kt:49` 的 `privacy_accepted` 门禁仍要先过，或在深层链接落地页补一道同意校验）。

### 2.3 隐式导航（PendingIntent / 推送）

```kotlin
// WorkManager 提醒点击 → 跳训练页（接 WORKMANAGER_REMINDER.md）
val deepLink = Uri.parse("jumpdaily://training/camera/${child.id}?auto=1")
val pending = PendingIntent.getActivity(
    ctx, 0,
    NavDeepLinkBuilder(ctx)
        .setGraph(R.navigation.nav_graph)          // 需抽 nav_graph.xml（见下）
        .setDestination("camera_training/{childId}")
        .setUri(deepLink)
        .createPendingIntent(),
    PendingIntent.FLAG_IMMUTABLE
)
```

---

## 3. 类型安全路由（Type-Safe Routing）——⚠️ 需升级到 2.8.0+

Navigation Compose **2.8.0** 起官方支持类型安全路由：**用 `data object` / `data class` 直接当目的地**，参数自动序列化，告别字符串拼接。

> 本项目当前 `navigation-compose:2.7.7`（`app/build.gradle.kts:154`）——**低于 2.8.0，无法用类型安全路由**。要启用需升级 AGP/Compose 版本。

### 3.1 升级依赖

```kotlin
// app/build.gradle.kts —— 升级到 2.8.x（同时 Compose Compiler/CompilerExtension 配套）
implementation("androidx.navigation:navigation-compose:2.8.0")
// Kotlin Serialization（类型安全路由的 NavType 依赖它序列化 data class）
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
```

并在模块启用序列化插件（见 [`DATASTORE_KOTLIN_SERIALIZATION.md`](./DATASTORE_KOTLIN_SERIALIZATION.md) 进阶）。

### 3.2 定义类型安全目的地

```kotlin
// 无参目的地用 data object（编译期单例）
@Serializable data object Home

// 带参目的地用 @Serializable data class，字段即参数
@Serializable
data class CameraTraining(val childId: Long = -1, val auto: Boolean = false)

// 导航图用 KClass 注册
NavHost(navController, startDestination = Splash) {
    composable<Splash> { SplashScreen(nav, container) }
    composable<Home> { HomeScreen(nav, session, records, container) }
    composable<CameraTraining> { backStackEntry ->
        val args = backStackEntry.toRoute<CameraTraining>()   // ← 自动反序列化
        CameraTrainingScreen(nav, session, container, args.childId, args.auto)
    }
    // 深层链接：用 NavType 序列化 data class
    composable<CameraTraining>(
        deepLinks = listOf(navDeepLink<CameraTraining>(basePath = "jumpdaily://training/camera"))
    ) { /* 同上的 args */ }
}
```

- `toRoute<CameraTraining>()` 取代手动 `arguments?.getString(...)`，**类型安全、编译期校验**。
- 深层链接的 `NavType` 由 Kotlin Serialization 自动生成（data class 需 `@Serializable`）。

### 3.3 迁移成本

| 改动 | 范围 |
|------|------|
| `Screen` 密封类 → `@Serializable data object/class` | `AppRoot.kt:59-71` |
| `composable(String)` → `composable<Type>` | `AppRoot.kt:202-211` |
| `nav.navigate("training")` → `nav.navigate(Training)` | 所有调用点（`HomeScreen`/`StatsScreen` 等） |
| 参数传递 `children/$id` → `Children(childId = id)` | 调用方 |
| 抽 `nav_graph.xml` 或纯 Kotlin NavHost | 架构微调 |

> 收益：彻底消除"route 字符串拼错运行时才炸"的隐患；参数类型由编译器保证。代价：升级 Compose/AGP 版本 + 全量回归（尤其 release 混淆，见 [`R8_RELEASE.md`](./R8_RELEASE.md)——`kotlinx.serialization` 需 keep 规则）。

---

## 4. 抽离 `nav_graph.xml`（可选，利于可视化）

类型安全路由可用纯 Kotlin `NavHost`，也可保留 `navigation/nav_graph.xml` 供 Android Studio Navigation Editor 可视化：

```xml
<!-- res/navigation/nav_graph.xml -->
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    app:startDestination="@id/splash">
    <fragment/androidx.navigation.fragment.NavHostFragment .../>  <!-- 旧式 -->
    <!-- Compose 项目通常用 <composable> 在 Kotlin 里，xml 仅作可视化占位 -->
</navigation>
```

本项目是纯 Compose NavHost，建议**保持 Kotlin NavHost**，不强制抽 xml（可视化收益有限、易与代码图不同步）。

---

## 5. 测试

```kotlin
// 深层链接测试（androidTest）
@Test
fun deepLink_opensCameraTraining() {
    val scenario = launchActivity<MainActivity>(
        Intent(Intent.ACTION_VIEW, Uri.parse("jumpdaily://training/camera/3?auto=1"))
    )
    // 断言当前目的地是 CameraTraining 且 childId=3
}

// 类型安全路由单测（JVM）
@Test
fun route_serialization() {
    val json = Json.encodeToString(CameraTraining(3, true))
    val back = Json.decodeFromString<CameraTraining>(json)
    assertEquals(CameraTraining(3, true), back)
}
```

---

## 6. 决策建议（落地优先级）

| 项 | 优先级 | 说明 |
|----|--------|------|
| 深层链接 `intent-filter` + `navDeepLink` | 中 | 推送/分享直达训练页，儿童类 App 体验好；当前无 |
| 升级 2.8.0 + 类型安全路由 | 低-中 | 收益大但需版本升级 + 全量回归，建议作为"下一次大版本"一并做 |
| `nav_graph.xml` 可视化 | 低 | 纯 Compose 可不抽 |

**小结**：当前导航是"密封类 + 字符串 route"的务实方案，参数少、运行稳；**深层链接**可直接补（Manifest + `navDeepLink`，无需升级）；**类型安全路由**是 2.8.0+ 的能力，本项目被 `2.7.7` 卡住，按第 3 节升级路径落地即可，注意序列化依赖与 R8 keep 规则。两处均不伪造成项目现状——当前是无深层链接、无类型安全路由的字符串路由。
