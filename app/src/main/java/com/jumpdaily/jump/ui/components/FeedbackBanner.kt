package com.jumpdaily.jump.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumpdaily.jump.ui.theme.Mint
import com.jumpdaily.jump.ui.theme.SkyBlue
import com.jumpdaily.jump.ui.theme.SunYellow
import kotlinx.coroutines.delay

enum class FeedbackType { REWARD, CORRECTION, CHEER }

data class Feedback(val type: FeedbackType, val text: String)

/**
 * 训练中的实时反馈横幅：跳得好 / 动作不规范 / 加油鼓励。
 * 显示 1.5s 后自动消失并回调 onConsumed。
 *
 * 视觉：半透明深紫底 + 语义描边 + 白字。半透明让摄像头深空紫场景透出来，
 * 与整体主题更协调；描边用糖果色保留「奖励/鼓励/纠正」的语义区分。
 */
@Composable
fun FeedbackOverlay(feedback: Feedback?, onConsumed: () -> Unit) {
    if (feedback == null) return
    var visible by remember { mutableStateOf(true) }
    val scale by animateFloatAsState(if (visible) 1f else 0.8f, tween(250), label = "fb-scale")
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(250), label = "fb-alpha")

    LaunchedEffect(feedback) {
        delay(1500)
        visible = false
        delay(280)
        onConsumed()
    }

    val (emoji, accent) = when (feedback.type) {
        FeedbackType.REWARD -> "🎉" to Mint
        FeedbackType.CHEER -> "💪" to SkyBlue
        FeedbackType.CORRECTION -> "🤔" to SunYellow
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scale)
                .alpha(alpha)
                .clip(RoundedCornerShape(28.dp))
                // 半透明深紫底：深空紫调与整体相机页协调，场景可微微透出
                .background(Color(0xFF3A2A6B).copy(alpha = 0.82f))
                // 语义描边：用糖果色区分奖励/鼓励/纠正，又不破坏整体协调感
                .border(2.5.dp, accent.copy(alpha = 0.9f), RoundedCornerShape(28.dp))
                .padding(30.dp, 24.dp)
        ) {
            Text(emoji, fontSize = 58.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                feedback.text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
