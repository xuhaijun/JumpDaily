# Compose 主题与 Material3 体系

> 配套 `docs/ARCHITECTURE.md` 第 12 节「工程化与质量」。
> 基于真实代码 `ui/theme/Color.kt`、`ui/theme/Theme.kt`、`ui/theme/Dimens.kt` 与 `ui/navigation/AppRoot.kt`、`ui/screens/SettingsScreen.kt` 撰写。

---

## 0. 现状速览（真实）

| 项 | 项目现状 |
|----|----------|
| 主题入口 | `JumpDailyTheme(darkTheme: Boolean = isSystemInDarkTheme())`（`Theme.kt:83-93`） |
| 配色方案 | 两套 `lightColorScheme` / `darkColorScheme`（糖果色 + 深空紫蓝） |
| 色板 | `Color.kt`：品牌紫/粉/黄 + 夜间专属底色 |
| 形状 | `Shapes` 圆角加大（extraSmall 12dp → extraLarge 36dp），卡哇伊圆润风 |
| 字体 | `Typography` 加粗标题层级 |
| 夜间切换 | `darkTheme` 来自 `prefsRepository.darkTheme` Flow，在 `AppRoot` 顶层应用 |
| 动态取色 | ❌ **未用** `dynamicColorScheme`（陶土色）；刻意锁定品牌色，保证儿童一眼认得出 |
| 作用域 | 全 App 包在 `JumpDailyTheme { }` 内，组件用 `MaterialTheme.colorScheme.xxx` 取色 |

---

## 1. Material3 角色体系（colorScheme）

Material3 不再用 Material2 的 `primaryVariant` 那套，改为一套**语义角色**，组件自动按角色取色：

| 角色 | 含义 | 本应用 Light 值（节选） |
|------|------|------------------------|
| `primary` | 主品牌色（按钮/选中态） | `PurplePrimary` `0xFF6A3FE8` |
| `onPrimary` | primary 上的文字/图标色 | `Color.White` |
| `primaryContainer` | 主色浅容器（芯片/高亮卡） | `Bubble` `0xFFEDE7FF` |
| `onPrimaryContainer` | 容器内文字 | `Ink` |
| `secondary` | 次品牌色 | `PinkSecondary` 粉 |
| `tertiary` | 强调色（点缀/成就） | `SunYellow` 黄 |
| `background` | 页面底色 | `CreamBg` 奶油粉 |
| `surface` | 卡片/对话框面 | `Color.White` |
| `onSurface` / `onSurfaceVariant` | 面/次文字 | `Ink` / `InkSoft` |
| `outline` | 边框/分割线 | `0xFFE2D9F5` |
| `error` | 错误态 | `ErrorDeep` `0xFFD6336E` |

> **关键设计**：`error` 用的是 `ErrorDeep`（高对比深红）而非装饰用的 `HeartRed`（`Color.kt:15-17` 注释明确区分）。`HeartRed` 只做装饰（如爱心图标），`ErrorDeep` 作按钮/图标底色时白字清晰可读——这是 M3「语义色要满足对比度」的实践。

---

## 2. 项目双色方案（Light / Dark）

```kotlin
// Theme.kt:17-34  浅色
private val LightColors = lightColorScheme(
    primary = PurplePrimary, onPrimary = Color.White,
    primaryContainer = Bubble, secondary = PinkSecondary,
    tertiary = SunYellow, background = CreamBg, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFF1ECFF), onSurfaceVariant = InkSoft,
    outline = Color(0xFFE2D9F5), error = ErrorDeep
)

// Theme.kt:40-58  深色
private val DarkColors = darkColorScheme(
    primary = PurplePrimary,            // 品牌色原样复用，孩子认得出
    background = DarkBg,                 // 深空紫蓝
    surface = DarkSurface, onSurface = DarkOnBg,
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline, error = ErrorDeep
)
```

**深色方案的设计原则**（`Color.kt:19-30` 注释）：
- 底色用「深空紫蓝」`DarkBg=0xFF1A1430`，文字用「浅薰衣草」`DarkOnBg=0xFFF3EEFF`，护眼且保留卡哇伊氛围。
- **品牌三色（紫/粉/黄）原样复用**，切换主题后整体观感一致，避免「换肤后不认识了」。

---

## 3. Shapes 与 Typography 定制

```kotlin
// Theme.kt:60-66  圆角偏大，儿童友好
private val JumpShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

// Theme.kt:68-76  标题统一加粗，正文用 InkSoft 次文字色
private val JumpTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp),
    titleLarge  = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    bodyMedium  = TextStyle(fontSize = 14.sp, color = InkSoft),
    labelLarge  = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)
)
```

- `Shapes` 各档位都比 M3 默认（4/8/12/16/28）更圆，契合糖果 UI。
- `Typography` 用 `TextStyle` 直赋值（非 `defaultTypography.copy`），简单可控；注意**只覆盖了用到的档位**，其余档位回退 M3 默认。

---

## 4. 主题入口与夜间模式切换

```kotlin
// Theme.kt:82-93
@Composable
fun JumpDailyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),   // 默认跟随系统
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = JumpTypography, shapes = JumpShapes, content = content
    )
}
```

**顶层应用**（整个 App 只调一次，所有页面共享）：

```kotlin
// AppRoot.kt:93  从 DataStore 读用户偏好（而非只跟随系统）
val darkTheme by container.prefsRepository.darkTheme.collectAsStateWithLifecycle()

// AppRoot.kt:100
JumpDailyTheme(darkTheme = darkTheme) {
    // ... 全部导航/页面
}
```

**用户切换**（设置页）：

```kotlin
// SettingsScreen.kt:108
val darkOn by vm.darkTheme.collectAsStateWithLifecycle()
// 开关 → vm.setDarkTheme(enabled) → prefsRepository.setDarkTheme → DataStore 落盘
// 下次重组 AppRoot 读到新值，整树切色
```

> 切换是**全树重算 colorScheme**，M3 组件自动跟新色；自绘 Canvas（如吉祥物、骨架）要自己读 `MaterialTheme.colorScheme` 或传参，不能硬编码颜色（呼应 `COMPOSE_RECOMPOSITION.md` 的「绘制阶段读」）。

---

## 5. 为什么不用 dynamicColorScheme（动态取色）

Android 12+ 的 `dynamicColorScheme(context)` 会从壁纸取「陶土色」生成整套配色。本项目**刻意不用**，原因：

1. **儿童品牌一致性**：紫色主品牌是产品辨识核心，壁纸取色会让 App 每次看起来不一样，孩子困惑。
2. **跨设备一致**：低版本（本项目 minSdk 26，远低于 12）无动态取色，锁品牌色反而让全版本观感统一。
3. **夜间模式已满足「换肤」需求**：用户要的是亮/暗切换，不是跟随壁纸。

> 若未来要做「家长自定义主题色」，正确做法是：把 `LightColors`/`DarkColors` 的 `primary` 改成从 `DataStore` 读的 `Color`，在 `AppRoot` 组合 `lightColorScheme(primary = userPrimary, ...)` 后再传给 `MaterialTheme`——而不是引入 dynamic color。

---

## 6. MaterialTheme 作用域与取色规范

```kotlin
// 正确：在 JumpDailyTheme { } 内部取色
Text("完成！", color = MaterialTheme.colorScheme.tertiary)

// 错误：在主题作用域外用 colorScheme（会取到默认 M3 色，不是糖果色）
```

- 任何组件都应通过 `MaterialTheme.colorScheme.xxx` / `MaterialTheme.typography.xxx` / `MaterialTheme.shapes.xxx` 取，**不要 import 写死的 `PurplePrimary`** 当组件颜色（除非是主题之外的装饰语义色如 `HeartRed`）。
- 这样主题切换时组件自动跟新，无需每个页面手写暗色分支。

---

## 7. Dimens：尺寸集中管理

`Dimens.kt` 存放跨页面复用的尺寸常量（间距/圆角/图标尺寸等），避免 magic number 散落。新增尺寸优先放这里，再在组件引用。

---

## 8. 暗色适配自查清单

- [ ] 所有文字用 `onBackground`/`onSurface`/`onSurfaceVariant`，不用写死黑/灰（暗色下黑字看不见）。
- [ ] 卡片底色用 `surface`/`surfaceVariant`，不用 `Color.White`（暗色下刺眼）。
- [ ] Canvas 自绘读 `MaterialTheme.colorScheme`（或传参），不硬编码。
- [ ] 图片/插画在暗色下对比度足够（本项目吉祥物 `跳跳星` 用描边，暗底可见）。
- [ ] Lottie 资源（`celebration.json`）底色透明，不依赖浅色背景。

---

**上手向导：** 加一个新颜色 → 先放 `Color.kt`（区分「品牌色」还是「暗色专属底色」）；要让它参与主题 → 加进 `LightColors`/`DarkColors` 对应角色；组件里用 `MaterialTheme.colorScheme.xxx` 取。改夜间配色只动 `DarkColors` 与 `Color.kt` 的 `Dark*` 系列，无需碰业务页面。
