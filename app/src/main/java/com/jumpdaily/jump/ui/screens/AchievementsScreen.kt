package com.jumpdaily.jump.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jumpdaily.jump.audio.SoundPlayer
import com.jumpdaily.jump.data.model.Badge
import com.jumpdaily.jump.data.model.BadgeCatalog
import com.jumpdaily.jump.ui.components.FunBanner
import com.jumpdaily.jump.ui.components.PageHeader
import com.jumpdaily.jump.ui.theme.Dimens
import com.jumpdaily.jump.ui.theme.InkSoft
import com.jumpdaily.jump.ui.viewmodel.RecordsViewModel
import com.jumpdaily.jump.ui.viewmodel.SessionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AchievementsScreen(records: RecordsViewModel, session: SessionViewModel, soundPlayer: SoundPlayer) {
    val badges by records.badges.collectAsStateWithLifecycle()
    val child by session.currentChild.collectAsStateWithLifecycle()
    val unlockedIds = badges.map { it.id }.toSet()

    // 点击成就卡片后的临时反馈（文案 + 配图 emoji，1.6s 后自动消失）
    var feedback by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(1600)
            feedback = null
        }
    }

    // 成就筛选：全部 / 已解锁 / 未解锁
    var filter by remember { mutableStateOf("all") }

    Box(Modifier.fillMaxSize().padding(Dimens.screenPadding)) {
        Column(Modifier.fillMaxSize()) {
            // 统一页头：头像 + 标题 + 解锁进度副标题（与统计页一致）
            PageHeader(
                child = child,
                title = "${child?.name ?: "宝贝"} 的成就墙",
                subtitle = "已解锁 ${unlockedIds.size} / ${BadgeCatalog.ALL.size} 个成就"
            )
            Spacer(Modifier.height(Dimens.headerGap))

            // 「下一枚」目标：第一个未解锁的成就，给孩子一个明确的努力方向
            val nextBadge = BadgeCatalog.ALL.firstOrNull { it.id !in unlockedIds }

            // 成就总进度：已解锁占比，强化「还差几个就集齐」的目标感
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏆 成就进度", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${unlockedIds.size} / ${BadgeCatalog.ALL.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(
                        progress = { unlockedIds.size.toFloat() / BadgeCatalog.ALL.size },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                    )
                    nextBadge?.let {
                        Text(
                            "下一枚：${it.emoji} ${it.title} · ${it.desc}",
                            fontSize = 12.sp, color = InkSoft
                        )
                    }
                }
            }

            // 分段筛选：全部 / 已解锁 / 未解锁，点击切换九宫格展示范围
            Spacer(Modifier.height(Dimens.headerGap))
            AchievementFilter(selected = filter, onSelect = { filter = it })

            val filtered = when (filter) {
                "unlocked" -> BadgeCatalog.ALL.filter { it.id in unlockedIds }
                "locked" -> BadgeCatalog.ALL.filter { it.id !in unlockedIds }
                else -> BadgeCatalog.ALL
            }
            // 九宫格占满剩余高度（weight 必须在 ColumnScope 内生效），切筛选时高度稳定不再跳动
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                // 内边距：上 14dp 给首行角标(向上外溢)留位、右 14dp 给最右列角标(向右外溢)留位、底 16dp 代替原外部下边距
                contentPadding = PaddingValues(top = 14.dp, end = 14.dp, bottom = 16.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(filtered, key = { it.id }) { badge ->
                    val unlocked = badge.id in unlockedIds
                    BadgeCard(
                        badge = badge,
                        unlocked = unlocked,
                        onClick = {
                            if (unlocked) {
                                soundPlayer.star()
                                feedback = "已解锁「${badge.title}」：${badge.desc}" to "🏆"
                            } else {
                                soundPlayer.correction()
                                feedback = "解锁条件：${badge.desc}" to "💡"
                            }
                        }
                    )
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("继续加油，解锁更多成就吧！🌈", fontSize = 13.sp, color = InkSoft)
            }
        }
        // 鼓励横幅改为顶部浮层：不占滚动流位置，弹出/消失不再推挤下方的九宫格
        AnimatedVisibility(
            visible = feedback != null,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f)
        ) {
            feedback?.let { (txt, em) -> FunBanner(txt, em) }
        }
    }
}

/** 成就卡片：已解锁=弹簧弹跳；未解锁=摇头式摇晃（提示「还不能点」）。未解锁卡右上角常驻 🔒 角标。 */
@Composable
private fun BadgeCard(badge: Badge, unlocked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val bounce = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    Box(modifier) {
        Column(
            Modifier
                .scale(bounce.value)
                .rotate(shake.value)
                .clip(RoundedCornerShape(20.dp))
                // 已解锁卡用品牌容器色高亮，未解锁卡保持灰底 + 半透明，层次一眼分明
                .background(if (unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .alpha(if (unlocked) 1f else 0.5f)
                .clickable {
                    scope.launch {
                        if (unlocked) {
                            // 弹簧式弹跳：先放大再回弹，营造「被戳一下」的可爱手感
                            bounce.snapTo(1f)
                            bounce.animateTo(1.18f, spring(stiffness = 700f, dampingRatio = 0.35f))
                            bounce.animateTo(1f, spring(stiffness = 450f, dampingRatio = 0.55f))
                        } else {
                            // 摇头式摇晃：提示这张还没解锁、不能点
                            repeat(3) {
                                shake.animateTo(-9f, spring(stiffness = 2200f, dampingRatio = 0.18f))
                                shake.animateTo(9f, spring(stiffness = 2200f, dampingRatio = 0.18f))
                            }
                            shake.animateTo(0f, spring(stiffness = 2200f, dampingRatio = 0.18f))
                        }
                    }
                    onClick()
                }
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 未解锁卡也显示真实 emoji（靠整体 0.5 透明度表示已上锁），更直观好看
            Text(badge.emoji, fontSize = 34.sp)
            // 标题需 fillMaxWidth + 居中：否则标题比 emoji 宽/换行时会被左对齐，看起来不居中
            Text(badge.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(badge.desc, fontSize = 10.sp, color = InkSoft, textAlign = TextAlign.Center)
        }
        // 常驻角标：未解锁卡右上角挂一个 🔒 圆标，一眼看出「还没解锁」
        if (!unlocked) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(9.dp, (-9).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("🔒", fontSize = 12.sp)
            }
        }
    }
}

/** 成就分段筛选：全部 / 已解锁 / 未解锁。选中段用品牌紫实心高亮，未选段与容器同色仅靠文字区分。 */
@Composable
private fun AchievementFilter(selected: String, onSelect: (String) -> Unit) {
    val tabs = listOf("全部" to "all", "已解锁" to "unlocked", "未解锁" to "locked")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { (label, key) ->
            val active = selected == key
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(key) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
