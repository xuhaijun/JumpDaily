package com.jumpdaily.jump.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumpdaily.jump.ui.theme.Ink
import com.jumpdaily.jump.ui.theme.InkSoft
import com.jumpdaily.jump.ui.theme.PurplePrimary

/**
 * 最近 7 天跳绳柱状图。data 为 (星期标签, 个数)。
 */
@Composable
fun WeeklyBarChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    color: Color = PurplePrimary
) {
    val max = (data.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Row(
        modifier = modifier.fillMaxWidth().height(170.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        for ((label, value) in data) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                Text(value.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(4.dp))
                val frac = value / max.toFloat()
                // 无数据时给一个最小基准高度，避免整片空白显得「没渲染」
                val barHeight = (frac * 100).dp + if (value == 0) 6.dp else 0.dp
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(if (value == max && value > 0) color else color.copy(alpha = 0.4f))
                )
                Spacer(Modifier.height(6.dp))
                Text(label, fontSize = 11.sp, color = InkSoft)
            }
        }
    }
}
