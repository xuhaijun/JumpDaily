package com.jumpdaily.jump.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumpdaily.jump.ui.theme.Bubble
import com.jumpdaily.jump.ui.theme.Ink
import com.jumpdaily.jump.ui.theme.InkSoft
import kotlinx.coroutines.launch

/** 首页/统计页的小卡片。 */
@Composable
fun StatCard(
    emoji: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = Bubble,
    /** 值文字字号，默认 22sp；窄卡（如弹框三列）可调小以防折行。 */
    valueSize: TextUnit = 22.sp,
    /** 可选：点击触发可爱交互（弹跳动效 + 回调）。为 null 时卡片不可点击。 */
    onClick: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val bounce = remember { Animatable(1f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .scale(bounce.value)
            .clip(RoundedCornerShape(20.dp))
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        // 点到反馈：轻微缩放一下（弱化版，仅小幅回弹，避免太夸张）
                        scope.launch {
                            bounce.snapTo(1f)
                            bounce.animateTo(1.08f, spring(stiffness = 420f, dampingRatio = 0.62f))
                            bounce.animateTo(1f, spring(stiffness = 380f, dampingRatio = 0.72f))
                        }
                        onClick()
                    }
                } else Modifier
            )
            .padding(16.dp, 14.dp)
    ) {
        // 用固定高度容器包裹 emoji，确保不同字符（⏱️/🔥/🍎）视觉基线一致、三卡片图标严格水平对齐
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 28.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = valueSize,
            color = Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = InkSoft)
    }
}
