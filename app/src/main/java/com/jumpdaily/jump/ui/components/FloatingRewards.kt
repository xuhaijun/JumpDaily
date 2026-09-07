package com.jumpdaily.jump.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumpdaily.jump.ui.theme.HeartRed
import com.jumpdaily.jump.ui.theme.Mint
import com.jumpdaily.jump.ui.theme.PinkSecondary
import com.jumpdaily.jump.ui.theme.PurplePrimary
import com.jumpdaily.jump.ui.theme.SkyBlue
import com.jumpdaily.jump.ui.theme.SunYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private val REWARD_EMOJIS = listOf("⭐", "✨", "💖", "🌟", "🏅", "🎈", "👏")
private val REWARD_COLORS = listOf(PinkSecondary, SunYellow, Mint, SkyBlue, PurplePrimary, HeartRed)

private data class FloatReward(val id: Int, val emoji: String, val color: Color, val spread: Float)

/**
 * 上浮奖励层：每当 [count] 增加，从画面下方偏中央处冒出一个奖励元素，
 * 向上飘升、放大并淡出（Duolingo / Apple Fitness 式正向反馈）。
 * 适合叠在相机预览或训练主界面之上，给小朋友即时成就感。
 */
@Composable
fun FloatingRewards(count: Int, modifier: Modifier = Modifier, every: Int = 1) {
    var items by remember { mutableStateOf(emptyList<FloatReward>()) }
    var prev by remember { mutableStateOf(count) }
    var nextId by remember { mutableStateOf(0) }
    val rnd = remember { Random(System.currentTimeMillis()) }

    LaunchedEffect(count) {
        val step = every.coerceAtLeast(1)
        // 每满 [every] 个才冒一批（2026-09-07）：原来每跳一个就冒，
        // 按 2 个/秒算屏幕上常驻 2~3 个 emoji 在飞，画面太满、抢大数字的注意力。
        // 一次冒 2 个，降频后仍有「一直在庆祝」的观感。
        if (count > prev && count / step > prev / step) {
            val batch = List(2) {
                FloatReward(
                    id = nextId++,
                    emoji = REWARD_EMOJIS[rnd.nextInt(REWARD_EMOJIS.size)],
                    color = REWARD_COLORS[rnd.nextInt(REWARD_COLORS.size)],
                    spread = (rnd.nextFloat() - 0.5f) * 220f // 横向散布 ±110dp
                )
            }
            items = (items + batch).takeLast(12) // 限制并发数量，避免堆积
        }
        prev = count
    }

    Box(modifier.fillMaxSize()) {
        items.forEach { item ->
            key(item.id) {
                FloatingItem(item) { items = items - item }
            }
        }
    }
}

@Composable
private fun FloatingItem(item: FloatReward, onDone: () -> Unit) {
    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val scale = remember { Animatable(0.6f) }

    LaunchedEffect(Unit) {
        launch { offsetY.animateTo(-240f, tween(1200)) }
        launch { alpha.animateTo(0f, tween(1200)) }
        launch {
            scale.animateTo(1.25f, tween(450))
            scale.animateTo(1f, tween(400))
        }
        delay(1250)
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .offset(x = item.spread.dp, y = offsetY.value.dp)
            .scale(scale.value)
            .alpha(alpha.value),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(item.emoji, fontSize = 32.sp)
    }
}

/**
 * 带「弹跳」动效的大数字：每次 [count] 变化瞬间放大回弹，
 * 给孩子清晰的计数正反馈（参考 Apple Fitness 圆环跳动）。
 */
@Composable
fun PopCount(
    count: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 72.sp,
    color: Color = Color.White,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (count > 0) {
            scale.snapTo(1.3f)
            scale.animateTo(1f, spring(stiffness = 350f, dampingRatio = 0.55f))
        }
    }
    Text(
        "$count",
        fontSize = fontSize,
        color = color,
        fontWeight = fontWeight,
        modifier = modifier.scale(scale.value)
    )
}
