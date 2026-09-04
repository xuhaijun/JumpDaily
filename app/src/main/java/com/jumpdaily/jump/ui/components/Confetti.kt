package com.jumpdaily.jump.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.jumpdaily.jump.ui.theme.HeartRed
import com.jumpdaily.jump.ui.theme.Mint
import com.jumpdaily.jump.ui.theme.PinkSecondary
import com.jumpdaily.jump.ui.theme.PurplePrimary
import com.jumpdaily.jump.ui.theme.SkyBlue
import com.jumpdaily.jump.ui.theme.SunYellow
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiPiece(
    val x: Float, val y: Float, val color: Color,
    val rot: Float, val size: Float, val speed: Float
)

/** 胜利彩带爆发动画。active 为 true 时播放一次，结束回调 onDone。 */
@Composable
fun ConfettiBurst(active: Boolean, onDone: () -> Unit, modifier: Modifier = Modifier) {
    if (!active) return
    val colors = listOf(PinkSecondary, SunYellow, Mint, SkyBlue, PurplePrimary, HeartRed)
    var pieces by remember { mutableStateOf(emptyList<ConfettiPiece>()) }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val rnd = Random(System.currentTimeMillis())
        pieces = List(90) {
            ConfettiPiece(
                x = rnd.nextFloat(),
                y = -0.1f - rnd.nextFloat() * 0.4f,
                color = colors[rnd.nextInt(colors.size)],
                rot = rnd.nextFloat() * 360f,
                size = 8f + rnd.nextFloat() * 12f,
                speed = 0.6f + rnd.nextFloat() * 0.9f
            )
        }
        val anim = Animatable(0f)
        anim.animateTo(1f, animationSpec = tween(1400)) { progress = value }
        onDone()
    }

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        for (p in pieces) {
            val py = (p.y + p.speed * progress) * h
            val px = p.x * w + sin((progress + p.x) * 6.28f) * 36f
            rotate(p.rot + progress * 360f) {
                drawRect(p.color, topLeft = Offset(px, py), size = Size(p.size, p.size * 0.55f))
            }
        }
    }
}
