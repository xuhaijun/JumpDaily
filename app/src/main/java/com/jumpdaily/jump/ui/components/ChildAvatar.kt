package com.jumpdaily.jump.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumpdaily.jump.data.local.entities.Child
import com.jumpdaily.jump.ui.theme.Ink
import com.jumpdaily.jump.ui.theme.PinkSecondary

/** 用 emoji + 主题色圆形作为孩子头像（无需图片素材）。 */
@Composable
fun ChildAvatar(
    child: Child?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    showName: Boolean = false
) {
    val color = safeColor(child?.themeColor, PinkSecondary)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(child?.avatar ?: "🐰", fontSize = (size.value * 0.5f).sp)
        }
        if (showName) {
            Spacer(Modifier.height(4.dp))
            Text(
                child?.name ?: "未选择",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
        }
    }
}

/** 把 #RRGGBB 字符串安全地转成 Compose Color。 */
fun safeColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        Color(android.graphics.Color.parseColor(hex).toLong() or 0xFF000000L)
    } catch (_: Exception) {
        fallback
    }
}
