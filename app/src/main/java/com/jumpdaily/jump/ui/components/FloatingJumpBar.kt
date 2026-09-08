package com.jumpdaily.jump.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumpdaily.jump.ui.theme.PurplePrimary
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 首页「开始跳绳」悬浮入口（2026-09-05）。
 *
 * 两种形态（外层按会话状态切换 [Mode]）：
 * - [Mode.IDLE] 入口胶囊：无进行中会话时显示「▶ 开始跳绳」，品牌紫底白字；
 * - [Mode.SUSPENDED] 挂起胶囊：会话暂停挂起中，显示「⏸ 已跳 X 个 · 时长 | 继续 ▶」，
 *   青绿底与入口态区分，一眼看出「有没跳完的会话」。
 *
 * 交互规则（设计定稿）：
 * - **拖动**：位移超过 [TAP_SLOP_PX] 判定为拖动，松手后水平吸附到最近的左/右边缘；
 * - **点击**：位移未超过阈值视为点击，由胶囊自身 [clickable] 触发 [onClick]
 *   （拖动中 change 被消费，clickable 不会误触发）；
 * - **位置记忆**：由外部持久化（[onPositionSettled] 回调边缘 + 垂直比例），下次进入还原；
 * - **静止变淡**：3 秒无触摸降为 55% 透明度，减少对内容的遮挡，拖动时立即恢复。
 *
 * 实现说明：位置用「单一绝对像素偏移」建模（初始值由 edge/yRatio 换算一次），
 * 拖动直接改偏移、松手吸边时再写回——避免 base+delta 双重换算的精度/状态错位问题。
 */
@Composable
fun FloatingJumpBar(
    mode: Mode,
    count: Int,
    elapsedSec: Int,
    initialEdge: Edge,
    initialYRatio: Float,
    onPositionSettled: (Edge, Float) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    // 水平位置不再存绝对 x：静止时由 edge + 实测宽度实时推导（挂起胶囊比入口胶囊宽，
    // 若存绝对 x 会在形态切换/重进 App 后仍用旧宽度定位，导致胶囊被屏幕边缘裁掉一截）。
    // pos.y 为垂直绝对偏移；拖动过程中 x 也临时存在 pos.x 里。
    var initialized by remember { mutableStateOf(false) }
    // 浮条当前吸附边（初始来自持久化；拖动吸边后更新）
    var edge by remember { mutableStateOf(initialEdge) }
    // 拖动中的自由坐标 / 静止时的垂直偏移——垂直位置真源
    var pos by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }

    // 静止变淡：3 秒无触摸降到 55%，拖动时立即恢复
    var dimmed by remember { mutableStateOf(false) }
    LaunchedEffect(dragging, mode) {
        if (dragging) {
            dimmed = false
        } else {
            delay(3000)
            dimmed = true
        }
    }
    val alpha by animateFloatAsState(if (dimmed) 0.55f else 1f, tween(400), label = "bar-alpha")

    val density = LocalDensity.current
    val marginPx = with(density) { 8.dp.toPx() }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { containerSize = it.size }
    ) {
        val barH = if (barSize.height > 0) barSize.height.toFloat() else with(density) { EST_BAR_HEIGHT.dp.toPx() }
        // 水平边界不在外部算：静止吸附 x 由 offset lambda 按 edge 实时推导，拖动中夹取也在那里做
        val maxY = (containerSize.height - barH - marginPx).coerceAtLeast(0f)

        // 垂直位置初始化：容器就绪后按持久化比例换算一次（水平位置无需初始化——由 edge 实时推导）
        LaunchedEffect(containerSize) {
            if (containerSize == IntSize.Zero || initialized) return@LaunchedEffect
            val estMaxY = (containerSize.height - with(density) { EST_BAR_HEIGHT.dp.toPx() } - marginPx).coerceAtLeast(0f)
            pos = Offset(0f, (initialYRatio * estMaxY).coerceIn(0f, estMaxY))
            initialized = true
        }

        if (initialized) {
            Box(
                Modifier
                    .offset {
                        // 水平：拖动中用自由坐标（夹在屏内）；静止时永远吸附到 edge 对应边缘，
                        // 用「当前实测宽度」计算——胶囊从入口态变宽为挂起态时位置自动跟随，不会被裁
                        val w = if (barSize.width > 0) barSize.width.toFloat() else with(density) { EST_BAR_WIDTH.dp.toPx() }
                        val mx = (containerSize.width - w - marginPx).coerceAtLeast(0f)
                        val x = if (dragging) {
                            pos.x.coerceIn(marginPx, marginPx + mx)
                        } else {
                            if (edge == Edge.RIGHT) marginPx + mx else marginPx
                        }
                        IntOffset(x.roundToInt(), pos.y.roundToInt())
                    }
                    .alpha(alpha)
                    .onGloballyPositioned { barSize = it.size }
                    // 点击由内部胶囊的 clickable 处理；这里只管拖动（拖动中 change 被消费，clickable 不误触）
                    .pointerInput(Unit) {
                        var moved = false
                        var accumulated = Offset.Zero
                        // 当前静止时的显示 x（由 edge + 实测宽度推导），拖动起点用它，避免从旧坐标跳变
                        fun displayX(): Float {
                            val w = if (barSize.width > 0) barSize.width.toFloat() else EST_BAR_WIDTH.dp.toPx()
                            val mx = (containerSize.width - w - marginPx).coerceAtLeast(0f)
                            return if (edge == Edge.RIGHT) marginPx + mx else marginPx
                        }
                        detectDragGestures(
                            onDragStart = {
                                dragging = true
                                moved = false
                                accumulated = Offset.Zero
                                pos = Offset(displayX(), pos.y)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulated += dragAmount
                                if (moved || abs(accumulated.x) > TAP_SLOP_PX || abs(accumulated.y) > TAP_SLOP_PX) {
                                    moved = true
                                    // x 显示时再夹取（offset lambda），这里只夹 y 到屏内
                                    pos = Offset(pos.x + dragAmount.x, (pos.y + dragAmount.y).coerceIn(0f, maxY))
                                }
                            },
                            onDragEnd = {
                                dragging = false
                                if (moved) {
                                    // 吸附：胶囊中心落在左半屏贴左、右半屏贴右；垂直位置换算成比例回调持久化
                                    val centerX = pos.x + size.width / 2f
                                    val newEdge = if (centerX > containerSize.width / 2f) Edge.RIGHT else Edge.LEFT
                                    edge = newEdge
                                    // x 由 offset lambda 按 edge 自动吸附，这里只更新 y（夹到实测范围内）
                                    pos = Offset(pos.x, pos.y.coerceIn(0f, maxY))
                                    val yRatio = if (maxY > 0f) (pos.y / maxY).coerceIn(0f, 1f) else 0f
                                    onPositionSettled(newEdge, yRatio)
                                }
                            }
                        )
                    }
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            when (mode) {
                                Mode.IDLE -> PurplePrimary
                                Mode.SUSPENDED -> Color(0xFF0F6E56)
                            }
                        )
                        .clickable(onClick = onClick)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (mode) {
                        Mode.IDLE -> Text(
                            "▶ 开始跳绳",
                            fontSize = 14.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Mode.SUSPENDED -> {
                            val min = elapsedSec / 60
                            val sec = (elapsedSec % 60).toString().padStart(2, '0')
                            Text(
                                "⏸ 已跳 $count 个 · $min:$sec",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "  继续 ▶",
                                fontSize = 13.sp,
                                color = Color(0xFF9FE1CB),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 浮条吸附边。 */
enum class Edge { LEFT, RIGHT }

/** 浮条形态：无会话入口胶囊 / 会话挂起胶囊。 */
enum class Mode { IDLE, SUSPENDED }

/** 拖动位移阈值（px）：低于它不视为拖动，保证点击灵敏。约 8dp@3x。 */
private const val TAP_SLOP_PX = 24f

/** 胶囊首帧估算尺寸（dp）：测量上报前的兜底，只影响首帧定位。 */
private const val EST_BAR_WIDTH = 150
private const val EST_BAR_HEIGHT = 40
