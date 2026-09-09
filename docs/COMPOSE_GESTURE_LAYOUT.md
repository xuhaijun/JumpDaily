# Compose 手势与自定义 Layout

> 基于本仓库真实手势代码（`FloatingJumpBar.kt` 拖动吸边）讲清 Compose 手势体系；自定义 `Layout` 本项目当前未直接使用，作为**技术参考 + 可落地场景**讲清原理与何时用（不伪造成项目现状）。
> 读前建议先看 [`COMPOSE_RECOMPOSITION.md`](./COMPOSE_RECOMPOSITION.md)（手势触发的状态要下沉）。

---

## 1. 手势三层 API

| 层级 | API | 用途 |
|------|-----|------|
| 高级（语义） | `clickable` / `combinedClickable` / `swipeable` / `draggable` / `scrollable` | 直接给交互语义，最省心 |
| 中阶（指针） | `pointerInput { detectTapGestures / detectDragGestures / detectTransformGestures }` | 组合多个手势、精细控制 |
| 底层（原始） | `pointerInput { awaitPointerEventScope { ... } }` | 完全自定义手势仲裁 |

本项目 `FloatingJumpBar` 用的是**中阶** `detectDragGestures`——因为要"拖动 vs 点击"自己区分、还要吸边，高级 `draggable` 不够灵活。

---

## 2. 真实案例：`FloatingJumpBar` 拖动吸边

完整实现见 `FloatingJumpBar.kt:58-213`。核心决策：

### 2.1 拖动 vs 点击的区分（tap slop）

```kotlin
// FloatingJumpBar.kt:131-169
.pointerInput(Unit) {
    var moved = false
    var accumulated = Offset.Zero
    detectDragGestures(
        onDragStart = { dragging = true; moved = false; accumulated = Offset.Zero; pos = Offset(displayX(), pos.y) },
        onDrag = { change, dragAmount ->
            change.consume()                       // ← 消费事件，clickable 不会再误触发点击
            accumulated += dragAmount
            // 位移超过阈值才视为"拖动"，否则当作点击
            if (moved || abs(accumulated.x) > TAP_SLOP_PX || abs(accumulated.y) > TAP_SLOP_PX) {
                moved = true
                pos = Offset(pos.x + dragAmount.x, (pos.y + dragAmount.y).coerceIn(0f, maxY))
            }
        },
        onDragEnd = {
            dragging = false
            if (moved) { /* 吸附到最近边缘 + 回调持久化 */ }
        }
    )
}
```

- `TAP_SLOP_PX = 24f`（`FloatingJumpBar.kt:222`，约 8dp@3x）：低于它算点击，保证点击灵敏。
- `change.consume()`：关键——消费掉 pointer 事件后，内部 `Row` 的 `clickable` 不会在拖动结束时误触发点击。

### 2.2 位置建模：单一绝对偏移，避免双重换算

```kotlin
// FloatingJumpBar.kt:74-78 / 105-111
var pos by remember { mutableStateOf(Offset.Zero) }   // 垂直真源 + 拖动临时 x
var edge by remember { mutableStateOf(initialEdge) }  // 静止时水平由 edge + 实测宽度推导
// 初始化只在容器就绪后换算一次垂直比例（两步走：容器测量→估算定位→实测校正，避免死锁）
LaunchedEffect(containerSize) {
    if (containerSize == IntSize.Zero || initialized) return@LaunchedEffect
    pos = Offset(0f, (initialYRatio * estMaxY).coerceIn(0f, estMaxY))
    initialized = true
}
```

> 设计教训（注释里写明的坑）：**水平位置不能存绝对 x**——挂起胶囊比入口胶囊宽，若存旧宽度算出的 x，形态切换/重进 App 后会被屏幕边缘裁掉一截。所以静止时 x 由 `edge + 实测宽度` 实时推导（`FloatingJumpBar.kt:116-127` 的 `offset { }` lambda）。

### 2.3 静止变淡

```kotlin
// FloatingJumpBar.kt:83-91
LaunchedEffect(dragging, mode) {
    if (dragging) dimmed = false
    else { delay(3000); dimmed = true }              // 3 秒无触摸降到 55%
}
val alpha by animateFloatAsState(if (dimmed) 0.55f else 1f, tween(400), label = "bar-alpha")
```

`delay` 在 `LaunchedEffect` 内，effect 因 `dragging` 变化重启即取消旧 delay——协作取消（见 [`COROUTINE_EXCEPTION_TIMEOUT.md`](./COROUTINE_EXCEPTION_TIMEOUT.md)）。

### 2.4 与重组优化的关系

- `pos`/`dragging`/`dimmed` 是浮条**自身**的局部 state，改动只触发浮条子树重组，**不会**重组整个 AppRoot（`FloatingJumpBar` 是 AppRoot 的覆盖层）。
- 拖动时每帧 `pos = ...` 只重排浮条 Box，符合"高频 state 下沉到子组件"原则。

---

## 3. 其它手势用法速查

```kotlin
// 点击
Modifier.clickable { onClick() }
Modifier.combinedClickable(onLongClick = { ... }, onClick = { ... })

// 轻扫（页面切换/删除）
Modifier.swipeable(state = swipeState, anchors = mapOf(0f to LEFT, 300f to RIGHT), thresholds = ...)

// 缩放旋转（图片预览）
.pointerInput(Unit) {
    detectTransformGestures { centroid, pan, zoom, rotation ->
        // 更新变换矩阵 state
    }
}

// 长按
.pointerInput(Unit) { detectTapGestures(onLongPress = { ... }) }

// 多点/完全自定义
.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            // 自己分发/消费
        }
    }
}
```

---

## 4. 自定义 `Layout`（技术参考 + 可落地场景）

### 4.1 原理：`measure` + `layout` 两阶段

Compose 布局走**单次向下测量、向上返回尺寸**的约束传播模型（与 View 体系不同，没有"measure 两次"的强制，但 `Layout` 内可多次 `measure`）。

```kotlin
@Composable
fun MyRow(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        // 1) 测量每个孩子（可传不同约束）
        val placeables = measurables.map { it.measure(constraints) }
        // 2) 决定自身尺寸
        val width = placeables.maxOf { it.width }
        val height = placeables.sumOf { it.height }
        // 3) 摆放
        layout(width, height) {
            var y = 0
            placeables.forEach { it.placeRelative(0, y); y += it.height }
        }
    }
}
```

### 4.2 什么时候该用自定义 `Layout`

| 场景 | 用标准还是自定义 |
|------|------------------|
| 简单行/列/层叠 | `Row`/`Column`/`Box` 足够 |
| 瀑布流 / 流式换行 | `FlowRow`/`FlowColumn`（Compose 1.4+ 官方） |
| 重叠但需精确坐标控制（如骨架叠加） | `Box` + `Modifier.offset` / `Alignment` 足够（本项目 `CameraTrainingScreen` 用 `Box` 叠预览+骨架） |
| 真正非常规几何（环形菜单、雷达图、自绘排版） | **自定义 `Layout`** |
| 依赖"先量一个孩子再决定另一个"的测量顺序 | 自定义 `Layout`（或 `SubcomposeLayout`） |

### 4.3 `SubcomposeLayout`：先组合一部分再决定其余

用于"根据一个子项尺寸决定另一个子项如何组合"（如 ViewPager 指示器、粘性头部）。代价是**多一次组合 pass**，高频场景慎用（承接 [`COMPOSE_RECOMPOSITION.md`](./COMPOSE_RECOMPOSITION.md) 的性能主题）。

### 4.4 ⚠️ 本项目当前未用自定义 `Layout`

- 浮条定位用 `Box` + `Modifier.offset { IntOffset(...) }`（lambda 形式，每帧重算不触发重组，见 `FloatingJumpBar.kt:116`）。
- 骨架叠加、弹幕、礼物动效用标准 `Box`/`Row`/`Column` + `Canvas` 绘制。
- **落地建议**：若出现"流式子项换行 + 精确测量依赖"，优先用官方 `FlowRow`；确实需要自定义几何时再上 `Layout`，并注意把测量约束（`Constraints`）传给子项、避免无限约束导致崩溃。

---

## 5. `Modifier.offset` 的两种写法（性能差异）

```kotlin
// ① lambda 版：每帧重算，不触发重组（浮条用这个）
.offset { IntOffset(x.roundToInt(), pos.y.roundToInt()) }

// ② 值版：值变化会触发重组（高频场景避免）
.offset(8.dp)                       // dp 版
.offset { IntOffset(...) }          // 等价 lambda，但写法不同
```

> 高频移动（拖拽、跟随手指）一律用 **lambda 版** `offset { }`，让布局阶段就地计算，避免 state 变更引起的重组。

---

## 6. 手势与动画的组合（承接 [`COMPOSE_ANIMATION.md`](./COMPOSE_ANIMATION.md)）

- 拖动中用 `Animatable`/`Offset` 实时跟随（浮条直接写 `pos`，未用动画插值，追求零延迟跟手）。
- 松手吸边可用 `animateTo` 做缓动（本项目为即时吸附，若要平滑可改 `animateDecay`/`animateTo`）。
- 手势触发的一次性动画（弹幕上升 `DanmakuOverlay.kt:92-95`、礼物飞出 `FloatingRewards.kt:88-90`）用 `Animatable.animateTo`，放在 `LaunchedEffect` 内，注意**收敛到叶子节点**避免大范围重组。

---

## 7. 反模式速查

| 反模式 | 后果 | 正解 |
|--------|------|------|
| 拖动里改父页 state | 整页重组卡顿 | 拖动 state 下沉到浮条自身 |
| 拖动不 `change.consume()` | 误触发 clickable | 消费事件 |
| `offset(x.dp)` 高频变化 | 重组 | lambda 版 `offset { }` |
| 水平位置存绝对 x（宽度会变） | 形态切换被裁切 | 由 edge + 实测宽度推导 |
| 自定义 `Layout` 传无限约束 | 崩溃 | 给子项明确 `Constraints` |
| `SubcomposeLayout` 高频用 | 额外组合 pass 卡 | 仅在确实需要时 |

**小结**：手势体系记住"高级→中阶→底层"三档按需取用；浮条是 `detectDragGestures` + `change.consume()` + 阈值判定 + lambda `offset` 的标准范本；自定义 `Layout` 本项目未用，按第 4.4 节场景判断再上。
