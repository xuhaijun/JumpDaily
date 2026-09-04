package com.jumpdaily.jump.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumpdaily.jump.ui.theme.PinkSecondary
import com.jumpdaily.jump.ui.theme.SunYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 一条弹幕/飘屏事件。
 *
 * @param id   唯一 id，用于并发多条时各自独立动画（同时它也作为随机位置的种子，
 *             保证同一条弹幕在重组时不会「跳位」）
 * @param text 主文案（如「+10」）
 * @param sub  副文案（如「连击奖励」），为空则不显示
 * @param emoji 顶部小图标，为空则不显示
 */
data class DanmakuEvent(
    val id: Long,
    val text: String,
    val sub: String = "",
    val emoji: String = ""
)

/**
 * 弹幕飘屏层：训练途中冒出「+10」「🍭 甜甜奖励」这类即时喝彩。
 *
 * 视觉：从**画面随机位置**冒出（横向 22%~78%、纵向中下部，每次都不一样）
 * → 一边上飘一边横向轻飘 → 放大回弹 → 淡出消失，像烟花/泡泡一样到处开，
 * 比固定居中更抓小朋友眼球，多条同时出现也不会叠在一起。
 * 多条可以并存（各自独立动画），由调用方通过 [onConsumed] 回收。
 */
@Composable
fun DanmakuOverlay(
    events: List<DanmakuEvent>,
    onConsumed: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        events.forEach { ev ->
            key(ev.id) { DanmakuItem(ev) { onConsumed(ev.id) } }
        }
    }
}

@Composable
private fun DanmakuItem(ev: DanmakuEvent, onDone: () -> Unit) {
    val rise = remember { Animatable(0f) }   // 上浮（像素，向上为负）
    val drift = remember { Animatable(0f) }  // 横向轻飘
    val alpha = remember { Animatable(1f) }
    val scale = remember { Animatable(0.5f) }

    // 用 event.id 做随机种子：位置每次不同，但同一条弹幕重组时保持不变（不会抖）
    val r = remember(ev.id) { Random(ev.id) }
    val fracX = remember(ev.id) { 0.22f + r.nextFloat() * 0.56f }  // 横向 22%~78%，留出边距不出屏
    val fracY = remember(ev.id) { 0.52f + r.nextFloat() * 0.30f }  // 纵向 52%~82%，从画面中下部冒出
    val riseTarget = remember(ev.id) { -(150f + r.nextFloat() * 130f) }  // 上飘 150~280dp
    val driftTarget = remember(ev.id) { (r.nextFloat() - 0.5f) * 90f }   // 横飘 ±45dp
    val rotation = remember(ev.id) { (r.nextFloat() - 0.5f) * 22f }      // 轻微倾斜 ±11°，更活泼

    LaunchedEffect(Unit) {
        launch { rise.animateTo(riseTarget, tween(1500)) }
        launch { drift.animateTo(driftTarget, tween(1500)) }
        launch { alpha.animateTo(0f, tween(1100, delayMillis = 300)) }
        launch {
            // 先「蹦」出来再回落，比匀速放大更有弹性、更抓小孩眼球
            scale.animateTo(1.18f, spring(stiffness = 600f, dampingRatio = 0.4f))
            scale.animateTo(1f, tween(300))
        }
        delay(1550)
        onDone()
    }

    Box(
        Modifier.fillMaxSize(),
        // BiasAlignment：把内容中心放到「剩余空间」的百分比位置，
        // 无需测量自身宽高就能精准落在随机点上（-1=最左/最上，+1=最右/最下）
        contentAlignment = BiasAlignment(
            horizontalBias = (fracX - 0.5f) * 2f,
            verticalBias = (fracY - 0.5f) * 2f
        )
    ) {
        Column(
            Modifier
                .offset { IntOffset(drift.value.roundToInt(), rise.value.roundToInt()) }
                .rotate(rotation)
                .scale(scale.value)
                .alpha(alpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (ev.emoji.isNotEmpty()) Text(ev.emoji, fontSize = 30.sp)
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.horizontalGradient(listOf(SunYellow, PinkSecondary)))
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text(ev.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            if (ev.sub.isNotEmpty()) {
                Text(
                    ev.sub,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
