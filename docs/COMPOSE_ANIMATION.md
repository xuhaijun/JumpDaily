# Compose 动画性能实战

> 基于 JumpDaily 真实动画代码：`LottiePlayer.kt`（Lottie）、`rememberInfiniteTransition`（吉祥物/绳子/奖牌光/节拍）、
> `animateFloatAsState`（按钮/横幅/浮条/目标环）、`Animatable`（彩带/弹幕/奖品/成就弹跳）。
> 本文给出「哪些写法会拖垮帧率、如何收敛」的实战清单，承接 [COMPOSE_RECOMPOSITION.md](./COMPOSE_RECOMPOSITION.md)。

---

## 1. 项目动画用法全景（真实锚点）

| 动画类型 | API | 真实位置 | 驱动方式 |
|----------|-----|----------|----------|
| Lottie 庆祝 | `rememberLottieComposition` + `LottieAnimation` | `LottiePlayer.kt:19-22`、`TrainingScreen.kt:260`（`celebration.json`） | 资源循环 |
| 吉祥物呼吸 | `rememberInfiniteTransition` | `JumpMascot.kt:63`（`idleBob`） | **每帧** |
| 绳子摆动 | `rememberInfiniteTransition` | `RopeSkipper.kt:30-31` | **每帧** |
| 奖牌反光 | `rememberInfiniteTransition` | `HomeScreen.kt:633`（`medal-shine`） | **每帧** |
| 训练节拍 | `rememberInfiniteTransition` | `CameraTrainingScreen.kt:640-641`（`beat`） | **每帧** |
| 按钮按压 | `animateFloatAsState` | `CuteButton.kt:50` | 事件 |
| 横幅缩放/淡入 | `animateFloatAsState` | `FeedbackBanner.kt:52-53` | 事件 |
| 浮条降透明 | `animateFloatAsState` | `FloatingJumpBar.kt:91` | 事件 |
| 目标进度环 | `animateFloatAsState` | `TrainingScreen.kt:317` | 事件 |
| 彩带/弹幕/奖品上浮 | `Animatable` | `Confetti.kt`/`DanmakuOverlay.kt`/`FloatingRewards.kt` | 协程 |
| 成就弹跳+抖动 | `Animatable` | `AchievementsScreen.kt:175-199` | 协程 |

---

## 2. 核心原理：动画为什么会让 Compose 卡

Compose 动画 = **状态变化 → 重组（recomposition）**。
- `rememberInfiniteTransition().animateFloat()` 返回一个**每帧都变的 `State<Float>`**；
  只要在某 Composable 的函数体里读取它，该 Composable（及子树）就**每帧重组**。
- `animateFloatAsState(target)` 只在 `target` 改变时跑一段过渡，期间每帧也重组该 Composable，
  但**只在该 Composable 内**，且是「有限时长」，成本可控。
- `Animatable.animateTo()` 在 `launch` 协程里推进，**读取时机**决定成本：若在 `DrawScope`（Canvas）里读 `value`，
  不触发重组，只重绘——最省。

**结论**：卡顿的根源不是「用了动画」，而是「把每帧变化的动画状态读进了高层/大的 Composable」。

---

## 3. 项目已做对的（保持）

1. **无限动画收敛到叶子组件**：`JumpMascot`、`RopeSkipper`、`HomeScreen` 的奖牌、`CameraTrainingScreen` 的 `beat`
   都是**独立小 Composable**，不在整页/大组件里读 `InfiniteTransition` 的 state。
   → 每帧重组只发生在那一个小组件，影响极小。✅
2. **Canvas 类动画用 `Animatable` + DrawScope 读值**：`Confetti`/`DanmakuOverlay`/`FloatingRewards`
   把 `offsetY/alpha/scale` 等 `Animatable` 在 `drawBehind`/`Canvas` 的 `DrawScope` 里读，
   **只重绘不重组**。✅
3. **事件驱动动画用 `animateFloatAsState`**：按钮/浮条/目标环只在状态翻转时过渡，非每帧常驻。✅

---

## 4. 必须避免的写法（反模式）

### ❌ 反模式 1：把 `rememberInfiniteTransition` 放进 `LazyColumn` 的 item

每个 item 都跑一个无限动画 → 列表里 N 个 item **每帧全量重组** → 滚动直接掉到 20fps 以下。
本项目列表（统计/历史）目前没有无限动画 item，但**加新动效时务必警惕**：
吉祥物呼吸、绳子摆动不要进 `LazyColumn` item。

### ❌ 反模式 2：用 `Modifier.alpha` / `Modifier.scale` 做高频动画

`Modifier.alpha(animValue)` 会改变**绘制层属性**，触发重组 + 可能 layout。
高频（`Animatable` 每帧）动画应改用 `graphicsLayer`：

```kotlin
// 慢（重组 + 重布局）
Box(Modifier.alpha(alphaState.value).scale(scaleState.value))

// 快（只走 RenderNode，跳过重组/布局）
Box(Modifier.graphicsLayer {
    alpha = alphaState.value
    scaleX = scaleState.value; scaleY = scaleState.value
})
```

`FloatingRewards.kt` 的 `offsetY/scale` 已用 `Animatable` 在 `DrawScope` 里读，**等价最优**，无需改。

### ❌ 反模式 3：在摄像头训练页（高频重组区）再加无限动画

`CameraTrainingScreen` 已是高频重组重灾区（每秒多次计数/姿态）。`beat` 无限动画被**正确隔离**在小组件，
但若有人把它挪到 `CameraTrainingScreen` 顶层函数体读取 → 整页每帧重组 + 相机预览卡死。
→ 严守 [COMPOSE_RECOMPOSITION.md](./COMPOSE_RECOMPOSITION.md) 的「高频 state 下沉」原则。

---

## 5. Lottie 性能要点（本项目真实用法）

`LottiePlayer.kt`：

```kotlin
val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
LottieAnimation(composition = composition, iterations = iterations, modifier = modifier)
```

- `rememberLottieComposition` **自带缓存**：同一 `resId` 跨重组不重复解析 JSON（✅ 已正确）。
- `iterations = Int.MAX_VALUE` → `celebration.json` 无限循环，仅训练**达标结果页**展示（❌ 不在训练进行中常驻），合理。
- 性能开关（按需加）：
  - `renderMode = RenderMode.HARDWARE`（默认，GPU 合成，省 CPU）。
  - `enableMergePathsForKitKatAndAbove(true)`：含合并路径的动画更顺。
  - `clipToCompositionBounds = true`：避免越界绘制。
- 大 Lottie（>200KB JSON / 多图层）会吃内存；本项目仅 `celebration.json` 一个，安全。
- **不要**把 Lottie 放进高频重组父组件或在 Canvas 上层叠过多 Lottie（本项目未犯）。

---

## 6. 诊断帧率

- **Layout Inspector → Recomposition counts**：看哪个 Composable 重组次数异常高（无限动画若没隔离，这里会爆）。
- **Benchmark + Macrobenchmark 的 `FrameTimingMetric`**：进统计页/训练页滚动，看 `frameOverrunMs` / `P90`。
- **开发者选项 → 显示刷新频率 / GPU 渲染条形图**：真机肉眼确认是否稳 60fps。

---

## 7. 一页速查（改动画时照做）

| 想做 | 用 | 注意 |
|------|----|------|
| 持续呼吸/旋转/反光 | `rememberInfiniteTransition` | **只在叶子小组件读**，绝进 LazyColumn item / 摄像头页顶层 |
| 点击/状态翻转的过渡 | `animateFloatAsState` | 目标变化才动，有限时长，安全 |
| 弹幕/彩带/上浮 | `Animatable` + `DrawScope` 读 | 只在绘制阶段读，不重组 |
| 透明度/缩放高频变化 | `Modifier.graphicsLayer { alpha/scale }` | 别用 `Modifier.alpha/scale` |
| 达标庆祝彩带动画 | Lottie（`LottieRes`） | 仅结果页，迭代缓存已就绪 |

---

## 8. 状态

- ✅ 项目动画用法整体健康：无限动画已隔离到叶子、Canvas 动画走 DrawScope、Lottie 缓存正确。
- ⚠️ 待警惕（非当前 bug）：新动效不要在 `LazyColumn` item 或摄像头训练页顶层引入无限动画；
  高频 transform 优先 `graphicsLayer`。
- 与 [COMPOSE_RECOMPOSITION.md](./COMPOSE_RECOMPOSITION.md) 是同一主题的两面：重组优化管「状态」，本篇管「动画」。
