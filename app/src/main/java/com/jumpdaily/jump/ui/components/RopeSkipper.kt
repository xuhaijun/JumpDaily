package com.jumpdaily.jump.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos

/**
 * 启动页 / 加载页用：白线稿小人跳绳循环动效（与启动图标同风格）。
 * 小人随节奏上下跳动，跳绳在「头顶 → 脚下」之间循环绕过，周期约 700ms，欢快连续。
 */
@Composable
fun RopeSkipper(modifier: Modifier = Modifier, sizeDp: Dp = 150.dp) {
    val transition = rememberInfiniteTransition(label = "rope-skipper")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Restart),
        label = "skip-phase"
    )
    // phase2：0（落地、绳在头顶）→ 1（最高点、绳在脚下），驱动跳动与绕绳同步
    val phase2 = (1f - cos(t * 2f * PI.toFloat())) / 2f
    val jumpAmp = 0.10f // 跳动幅度（相对画布短边）

    Canvas(modifier.size(sizeDp)) {
        val u = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f
        val sw = u * 0.045f
        val offsetY = -u * jumpAmp * phase2

        translate(0f, offsetY) {
            val rHead = u * 0.12f
            val headC = Offset(cx, cy - u * 0.16f)
            val bodyTop = headC.y + rHead
            val bodyBot = headC.y + u * 0.30f
            val shoulderY = headC.y + u * 0.15f
            val leftHand = Offset(headC.x - u * 0.22f, shoulderY)
            val rightHand = Offset(headC.x + u * 0.22f, shoulderY)
            val leftFoot = Offset(headC.x - u * 0.12f, bodyBot + u * 0.22f)
            val rightFoot = Offset(headC.x + u * 0.12f, bodyBot + u * 0.20f)

            // 跳绳：左手经身前绕回右手；phase2=0 在头顶，=1 在脚下
            val overY = headC.y - rHead - u * 0.06f
            val underY = bodyBot + u * 0.30f
            val ropeY = overY + (underY - overY) * phase2
            val rope = Path().apply {
                moveTo(leftHand.x, leftHand.y)
                quadraticTo(cx, ropeY, rightHand.x, rightHand.y)
            }
            drawPath(rope, Color.White, style = Stroke(width = sw, cap = StrokeCap.Round))

            // 头
            drawCircle(Color.White, radius = rHead, center = headC)
            // 身体
            drawLine(Color.White, Offset(headC.x, bodyTop), Offset(headC.x, bodyBot), strokeWidth = sw, cap = StrokeCap.Round)
            // 手臂（举向两侧握绳端）
            drawLine(Color.White, Offset(headC.x, shoulderY), leftHand, strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(headC.x, shoulderY), rightHand, strokeWidth = sw, cap = StrokeCap.Round)
            // 腿（腾空小跑姿势）
            drawLine(Color.White, Offset(headC.x, bodyBot), leftFoot, strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(headC.x, bodyBot), rightFoot, strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}
