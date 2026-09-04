package com.jumpdaily.jump.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jumpdaily.jump.audio.SoundPlayer
import com.jumpdaily.jump.data.local.entities.JumpRecord
import com.jumpdaily.jump.data.model.CountMode
import com.jumpdaily.jump.ui.components.ChildAvatar
import com.jumpdaily.jump.ui.components.CuteButton
import com.jumpdaily.jump.ui.components.FunBanner
import com.jumpdaily.jump.ui.components.JumpMascot
import com.jumpdaily.jump.ui.components.MascotState
import com.jumpdaily.jump.ui.components.StatCard
import com.jumpdaily.jump.ui.components.WeeklyBarChart
import com.jumpdaily.jump.ui.navigation.Screen
import com.jumpdaily.jump.ui.theme.Bubble
import com.jumpdaily.jump.ui.theme.InkSoft
import com.jumpdaily.jump.ui.viewmodel.RecordsViewModel
import com.jumpdaily.jump.ui.viewmodel.SessionViewModel
import com.jumpdaily.jump.util.formatDuration
import com.jumpdaily.jump.util.formatMonthDay
import kotlinx.coroutines.delay

/**
 * 「统计」页：运动报告 + 历史记录合并在一个 tab 内，用内部 tab 栏切换。
 *
 * 合并原因：底部两个 tab（统计 / 历史）内容高度重叠 —— 历史页顶部的「累计个数 / 最佳单次 /
 * 打卡天数 / 累计时长 / 消耗热量」与运动报告完全重复。合并后：
 * - 报告页负责【看趋势与目标】：7 天柱状图、本周目标进度、6 张数据卡；
 * - 历史页只保留【逐条记录】：时间、个数、时长、热量与删除，不再重复展示汇总数字。
 */
@Composable
fun StatsScreen(
    records: RecordsViewModel,
    session: SessionViewModel,
    soundPlayer: SoundPlayer,
    nav: NavHostController
) {
    var tab by remember { mutableStateOf(0) }
    val stats by records.stats.collectAsStateWithLifecycle()
    val weekly by records.weekly.collectAsStateWithLifecycle()
    val weeklyGoal by session.weeklyGoal.collectAsStateWithLifecycle()
    val child by session.currentChild.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChildAvatar(child = child, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                "${child?.name ?: "宝贝"} 的运动记录",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            // 连续打卡小徽章：一进页面就能看到坚持天数
            Text(
                "🔥 连续 ${stats.streak} 天",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // 内部 tab 栏：运动报告 / 历史记录
        StatsInnerTabs(selected = tab, onSelect = { tab = it })

        // 内容区占满剩余高度（weight 必须在 ColumnScope 内调用，故包一层 Box），
        // 报告页与历史页各自在内部滚动，互不干扰
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> SportsReport(
                    stats = stats,
                    weekly = weekly,
                    weeklyGoal = weeklyGoal,
                    soundPlayer = soundPlayer
                )
                else -> HistoryList(records = records, session = session, nav = nav)
            }
        }
    }
}

/** 内部切换栏：报告 / 历史，选中段用品牌紫实心高亮。 */
@Composable
private fun StatsInnerTabs(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf("📊 运动报告", "📜 历史记录")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, label ->
            val active = selected == index
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
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

/** 运动报告：趋势 + 目标 + 6 张数据卡（点击卡片有鼓励气泡）。 */
@Composable
private fun SportsReport(
    stats: com.jumpdaily.jump.data.model.Stats,
    weekly: List<Pair<String, Int>>,
    weeklyGoal: Int,
    soundPlayer: SoundPlayer
) {
    // 点击统计卡片后的临时鼓励反馈（文案 + 配图 emoji，1.6s 后自动消失）
    var feedback by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(1600)
            feedback = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部临时鼓励横幅：点击卡片时弹出，带彩带的花哨样式，缩放淡入淡出
        AnimatedVisibility(
            visible = feedback != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f)
        ) {
            feedback?.let { (txt, em) -> FunBanner(txt, em) }
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Bubble)
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("最近 7 天", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("本周共 ${weekly.sumOf { it.second }} 个", fontSize = 13.sp, color = InkSoft)
                }
                Spacer(Modifier.height(10.dp))
                if (weekly.sumOf { it.second } == 0) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "还没有跳绳记录哦，今天跳一跳，点亮这 7 根小柱子吧 💪",
                            fontSize = 14.sp, color = InkSoft, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        )
                    }
                } else {
                    WeeklyBarChart(weekly)
                }
            }
        }

        // 本周目标进度（附与上周的对比，让孩子看到自己的进步）
        val weekSum = weekly.sumOf { it.second }
        val weekProgress = if (weeklyGoal > 0) (weekSum.toFloat() / weeklyGoal).coerceIn(0f, 1f) else 0f
        // 上周总个数：date 为当天 0 点时间戳，取 [上周一, 本周一) 区间内的记录求和
        val today0 = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dow = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        val daysSinceMonday = if (dow == java.util.Calendar.SUNDAY) 6 else dow - java.util.Calendar.MONDAY
        val monday = today0 - daysSinceMonday * 86_400_000L
        val lastWeekSum = stats.history
            .filter { it.date >= monday - 7 * 86_400_000L && it.date < monday }
            .sumOf { it.count }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏁 本周目标", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        if (weekSum >= weeklyGoal) "已达成 🎉" else "还差 ${maxOf(0, weeklyGoal - weekSum)} 个",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (weekSum >= weeklyGoal) MaterialTheme.colorScheme.primary else InkSoft
                    )
                }
                LinearProgressIndicator(
                    progress = { weekProgress },
                    modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text("$weekSum / $weeklyGoal 个", fontSize = 13.sp, color = InkSoft)
                if (lastWeekSum > 0) {
                    val diff = weekSum - lastWeekSum
                    Text(
                        if (diff >= 0) "🚀 比上周多跳 $diff 个，越来越厉害啦！"
                        else "💪 比上周少跳 ${-diff} 个，本周加油追回来！",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 6 张数据卡：3 列 × 2 行
        val totalDurationSec = stats.history.sumOf { it.durationSec }
        val totalCalories = stats.history.sumOf { it.calories }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("💯", "${stats.totalCount}", "累计个数", Modifier.weight(1f), valueSize = 20.sp, onClick = {
                soundPlayer.star()
                feedback = "你已经跳了 ${stats.totalCount} 个啦，太厉害了！💪" to "💯"
            })
            StatCard("🏆", "${stats.bestCount}", "最佳单次", Modifier.weight(1f), valueSize = 20.sp, onClick = {
                soundPlayer.star()
                feedback = "单次最高 ${stats.bestCount} 个，挑战更高纪录吧！🏆" to "🏆"
            })
            StatCard("📅", "${stats.activeDays}", "打卡天数", Modifier.weight(1f), valueSize = 20.sp, onClick = {
                soundPlayer.star()
                feedback = "坚持了 ${stats.activeDays} 天，习惯正在养成 🌱" to "🌱"
            })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("🔥", "${stats.streak}", "连续天数", Modifier.weight(1f), valueSize = 20.sp, onClick = {
                soundPlayer.star()
                feedback = "连续 ${stats.streak} 天打卡，别断掉哦 🔥" to "🔥"
            })
            StatCard("⏱️", formatDuration(totalDurationSec), "累计时长", Modifier.weight(1f), valueSize = 20.sp, onClick = {
                soundPlayer.star()
                feedback = "累计跳了 ${formatDuration(totalDurationSec)}，时间都变成肌肉啦 ⏱️" to "⏱️"
            })
            StatCard("🍎", "$totalCalories", "消耗(千卡)", Modifier.weight(1f), valueSize = 20.sp, onClick = {
                soundPlayer.star()
                feedback = "累计消耗 $totalCalories 千卡，脂肪拜拜 👋" to "🍎"
            })
        }

        Text("小提示：每天坚持跳一跳，连续打卡会有惊喜成就哦 🌟", fontSize = 13.sp, color = InkSoft)
    }
}

/**
 * 历史记录：只保留【逐条记录 + 删除】，汇总数字全部交给「运动报告」页，
 * 避免同一个数字在两个 tab 里重复出现。
 */
@Composable
private fun HistoryList(
    records: RecordsViewModel,
    session: SessionViewModel,
    nav: NavHostController
) {
    val list by records.records.collectAsStateWithLifecycle()
    val countMode by session.countMode.collectAsStateWithLifecycle()

    // 待删除的记录：点删除先弹确认框，防止孩子误触丢数据
    var pendingDelete by remember { mutableStateOf<JumpRecord?>(null) }
    pendingDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条记录吗？") },
            text = { Text("${formatMonthDay(rec.date)} 跳了 ${rec.count} 个，删除后就找不回来啦。") },
            confirmButton = {
                TextButton(onClick = {
                    records.deleteRecord(rec)
                    pendingDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    if (list.isEmpty()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            JumpMascot(MascotState.IDLE, Modifier.height(120.dp))
            Spacer(Modifier.height(16.dp))
            Text("还没有跳绳记录哦", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text(
                "和跳跳星一起跳起来，留下第一条记录吧 💛",
                fontSize = 14.sp, color = InkSoft, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(22.dp))
            CuteButton(
                "开始跳绳 🏃",
                onClick = { nav.navigate(if (countMode == CountMode.CAMERA) Screen.CameraTraining.route else Screen.Training.route) },
                modifier = Modifier.fillMaxWidth(0.8f)
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list, key = { it.id }) { rec ->
                HistoryItem(rec, onDelete = { pendingDelete = rec })
            }
        }
    }
}

/** 单条历史记录：日期 + 个数/时长/热量 + 删除。 */
@Composable
private fun HistoryItem(rec: JumpRecord, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🪢", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(formatMonthDay(rec.date), fontSize = 13.sp, color = InkSoft)
                Text(
                    "${rec.count} 个 · ${formatDuration(rec.durationSec)} · ${rec.calories} 千卡",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
