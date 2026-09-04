package com.jumpdaily.jump.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumpdaily.jump.ui.theme.Ink
import java.util.concurrent.atomic.AtomicLong

/**
 * 圆润、按压缩放的卡哇伊按钮。
 *
 * 内置 500ms 防抖动：儿童快速连点（尤其结果页「完成」/「再跳一次」/「结束」）时，
 * 窗口内的重复点击会被忽略，避免重复导航、重复触发逻辑导致的卡顿或崩溃。
 */
@Composable
fun CuteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "cute-btn-scale")

    // 防抖：记录上次有效点击时间戳，窗口内重复点击直接忽略（不触发重组）
    val lastClickMs = remember { AtomicLong(0L) }
    val throttleMs = 500L

    // 底色越亮越需要深色文字；深色底用白色文字，保证按钮文字始终清晰可读
    val resolvedContent = contentColor ?: if (containerColor.isLight()) Ink else Color.White

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(if (pressed) 4.dp else 8.dp, shape)
            .clip(shape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.4f))
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastClickMs.get() >= throttleMs) {
                                lastClickMs.set(now)
                                onClick()
                            }
                        }
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (emoji != null) Text(emoji, fontSize = 22.sp)
            Text(
                text,
                color = if (enabled) resolvedContent else resolvedContent.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/** 近似判断颜色是否为浅色（用于自动选择按钮文字色：浅底用深紫字、深底用白字）。 */
private fun Color.isLight(): Boolean {
    val lum = 0.299f * red + 0.587f * green + 0.114f * blue
    return lum > 0.5f
}
