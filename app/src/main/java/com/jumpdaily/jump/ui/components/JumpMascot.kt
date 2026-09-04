package com.jumpdaily.jump.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jumpdaily.jump.ui.theme.Ink
import com.jumpdaily.jump.ui.theme.PinkSecondary
import com.jumpdaily.jump.ui.theme.PurplePrimary

/** 吉祥物「跳跳星」的表情/动作状态。 */
enum class MascotState { IDLE, JUMPING, HAPPY, SAD, CHEER }

/**
 * 用 Canvas 绘制的卡哇伊吉祥物，根据状态改变弹跳与表情。
 */
@Composable
fun JumpMascot(
    state: MascotState,
    modifier: Modifier = Modifier,
    color: Color = PurplePrimary
) {
    val bounce by animateFloatAsState(
        targetValue = when (state) {
            MascotState.JUMPING -> -20f
            MascotState.HAPPY, MascotState.CHEER -> -18f
            else -> 0f
        },
        animationSpec = if (state == MascotState.JUMPING) {
            infiniteRepeatable(tween(420), repeatMode = RepeatMode.Reverse)
        } else {
            tween(300)
        },
        label = "mascot-bounce"
    )
    val blink by animateFloatAsState(
        targetValue = if (state == MascotState.IDLE) 1f else 0.25f,
        label = "mascot-blink"
    )
    // 待机时轻微上下浮动，让吉祥物始终「活」着、更可爱
    val idleBob by rememberInfiniteTransition(label = "mascot-idle").animateFloat(
        initialValue = 0f, targetValue = -7f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "mascot-idle-bob"
    )

    Canvas(modifier.size(150.dp).offset(y = (bounce + idleBob).dp)) {
        val c = center
        val r = size.minDimension / 2.2f

        // 身体
        drawCircle(color, radius = r, center = c)
        // 腮红
        drawCircle(PinkSecondary.copy(alpha = 0.85f), radius = r * 0.18f,
            center = Offset(c.x - r * 0.55f, c.y + r * 0.28f))
        drawCircle(PinkSecondary.copy(alpha = 0.85f), radius = r * 0.18f,
            center = Offset(c.x + r * 0.55f, c.y + r * 0.28f))

        // 眼睛
        val eyeY = c.y - r * 0.08f
        val eyeDx = r * 0.38f
        if (state == MascotState.SAD) {
            drawCircle(Ink, radius = r * 0.1f, center = Offset(c.x - eyeDx, eyeY))
            drawCircle(Ink, radius = r * 0.1f, center = Offset(c.x + eyeDx, eyeY))
        } else {
            drawCircle(Ink, radius = r * 0.13f * blink, center = Offset(c.x - eyeDx, eyeY))
            drawCircle(Ink, radius = r * 0.13f * blink, center = Offset(c.x + eyeDx, eyeY))
            drawCircle(Color.White, radius = r * 0.05f * blink,
                center = Offset(c.x - eyeDx + r * 0.05f, eyeY - r * 0.05f))
            drawCircle(Color.White, radius = r * 0.05f * blink,
                center = Offset(c.x + eyeDx + r * 0.05f, eyeY - r * 0.05f))
        }

        // 嘴巴
        val mouthY = c.y + r * 0.32f
        when (state) {
            MascotState.HAPPY, MascotState.CHEER -> drawArc(
                Ink, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(c.x - r * 0.25f, mouthY - r * 0.12f),
                size = Size(r * 0.5f, r * 0.5f),
                style = Stroke(width = r * 0.07f)
            )
            MascotState.SAD -> drawArc(
                Ink, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(c.x - r * 0.25f, mouthY),
                size = Size(r * 0.5f, r * 0.5f),
                style = Stroke(width = r * 0.07f)
            )
            else -> drawCircle(Ink, radius = r * 0.08f, center = Offset(c.x, mouthY))
        }
    }
}
