package com.jumpdaily.jump.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumpdaily.jump.data.local.entities.Child
import com.jumpdaily.jump.ui.theme.InkSoft

/**
 * 统一页头：头像 + 标题（+ 可选副标题）+ 右侧 trailing 槽。
 * 统计页、成就页共用同一套高度与间距，切换底部 tab 时位置感更稳定。
 *
 * @param child        当前孩子（头像用），可为 null
 * @param title       主标题（如「的运动记录」「的成就墙」），单行省略
 * @param subtitle    副标题（如解锁进度），不传则不显示
 * @param trailing    右侧槽位（如「连续 N 天」徽章、设置入口等）
 */
@Composable
fun PageHeader(
    child: Child?,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChildAvatar(child = child, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = InkSoft)
            }
        }
        trailing?.invoke()
    }
}
