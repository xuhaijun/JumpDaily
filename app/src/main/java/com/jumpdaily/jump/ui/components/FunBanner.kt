package com.jumpdaily.jump.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 花哨的鼓励横幅：主色底 + 内嵌彩带喷洒 + emoji，用于统计页 / 成就页点击卡片的即时反馈。
 * 文案变化（key(text)）时彩带会重新喷洒一次。
 */
@Composable
fun FunBanner(text: String, emoji: String = "🎉", modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            // 彩带覆盖在横幅内（每次文案变化重新来一次）
            key(text) {
                ConfettiBurst(active = true, onDone = {})
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(emoji, fontSize = 18.sp)
                Text(
                    text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary, textAlign = TextAlign.Center
                )
            }
        }
    }
}
