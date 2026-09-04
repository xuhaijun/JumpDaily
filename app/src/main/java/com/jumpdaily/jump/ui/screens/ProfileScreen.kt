package com.jumpdaily.jump.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jumpdaily.jump.data.model.Rewards
import com.jumpdaily.jump.di.AppContainer
import com.jumpdaily.jump.ui.components.ChildAvatar
import com.jumpdaily.jump.ui.navigation.Screen
import com.jumpdaily.jump.ui.theme.InkSoft
import com.jumpdaily.jump.ui.theme.PinkSecondary
import com.jumpdaily.jump.ui.theme.PurplePrimary
import com.jumpdaily.jump.ui.viewmodel.RecordsViewModel
import com.jumpdaily.jump.ui.viewmodel.SessionViewModel
import kotlinx.coroutines.flow.flowOf

/**
 * 「我的」页：孩子主页 + 积分奖品 + 常用入口。
 *
 * 设计取向（儿童产品，家长也会看）：
 * - **没有顶部标题栏**：不再放「我的」标题和右上角设置齿轮，一进来直接是孩子的彩色主页；
 * - 孩子最关心的【积分 / 等级 / 奖品墙】放在最上面，跳完就想来看看赚了多少；
 * - 具体设置项（计数方式 / 灵敏度 / 目标 / 提醒 / 音效 / 语音 / 夜间模式 / 恢复默认）
 *   已迁移到独立「设置」页，通过下方「我的数据」卡的「⚙️ 设置」进入；
 * - 补齐此前只占位未实现的功能：成就墙与运动报告入口、孩子管理、版本信息弹框。
 */
@Composable
fun ProfileScreen(
    nav: NavHostController,
    session: SessionViewModel,
    container: AppContainer,
    records: RecordsViewModel
) {
    val child by session.currentChild.collectAsStateWithLifecycle()
    val stats by records.stats.collectAsStateWithLifecycle()
    val badges by records.badges.collectAsStateWithLifecycle()

    val ctx = LocalContext.current

    // 该孩子的总积分与已兑换奖品（按 childId 隔离存储）
    val childId = child?.id
    val pointsFlow = remember(childId) {
        childId?.let { container.prefsRepository.points(it) } ?: flowOf(0)
    }
    val points by pointsFlow.collectAsStateWithLifecycle(initialValue = 0)
    val earnedFlow = remember(childId) {
        childId?.let { container.prefsRepository.earnedPrizes(it) } ?: flowOf(emptySet())
    }
    val earned by earnedFlow.collectAsStateWithLifecycle(initialValue = emptySet())

    val level = Rewards.levelOf(points)
    val next = Rewards.nextLevel(points)
    val nextPrize = Rewards.nextPrize(points)
    val unlockedCount = Rewards.PRIZES.count { points >= it.needPoints }

    // 缓存大小：进入页面时算一次；清理后重新置 0
    var cacheBytes by remember { mutableLongStateOf(cacheSizeBytes(ctx)) }
    val version = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrDefault("1.0.0") ?: "1.0.0"
    }

    var showVersion by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ===== 孩子主页（彩色头部，替代原「我的」标题栏）=====
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            // 注意必须用 Column：Box 会把「头像行」和「统计行」叠加导致文字重叠
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(PurplePrimary, PinkSecondary)))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChildAvatar(child = child, size = 62.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            child?.name ?: "宝贝",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.22f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text("${level.emoji} ${level.title}", fontSize = 13.sp, color = Color.White)
                        }
                    }
                    // 切换/管理孩子
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable { nav.navigate(Screen.Children.route) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("切换 ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(Modifier.height(16.dp))
                // 三项一眼看懂的成绩
                Row(Modifier.fillMaxWidth()) {
                    HeroStat("累计跳绳", "${stats.totalCount}", Modifier.weight(1f))
                    HeroStat("连续打卡", "${stats.streak} 天", Modifier.weight(1f))
                    HeroStat("解锁成就", "${badges.size}", Modifier.weight(1f))
                }
            }
        }

        // ===== 积分与等级 =====
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐ 我的积分", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        "$points",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { Rewards.levelProgress(points) },
                    modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    if (next != null) "再赚 ${next.minPoints - points} 分升级为 ${next.emoji}${next.title}"
                    else "已经是最高等级啦，你太厉害了！👑",
                    fontSize = 13.sp,
                    color = InkSoft
                )
                Text(
                    "积分规则：每跳 1 个 +1；每满 10 个额外 +${Rewards.BONUS_EVERY_TEN}；" +
                        "连跳 20/50/100 再加分；达成每日目标 +${Rewards.BONUS_DAILY_GOAL}。",
                    fontSize = 12.sp,
                    color = InkSoft
                )
            }
        }

        // ===== 奖品墙（全部奖品，网格展示）=====
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎁 奖品墙", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("已解锁 $unlockedCount/${Rewards.PRIZES.size}", fontSize = 12.sp, color = InkSoft)
                }
                Rewards.PRIZES.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { prize ->
                            val unlocked = points >= prize.needPoints
                            PrizeChip(
                                prize = prize,
                                unlocked = unlocked,
                                claimed = prize.id in earned,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // 最后一行不足 3 个时用空白占位，保证格子等宽
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text(
                    if (nextPrize != null)
                        "下一个奖品：${nextPrize.emoji} ${nextPrize.name}（还差 ${nextPrize.needPoints - points} 分）"
                    else "所有奖品都解锁啦，你就是跳绳小霸王！👑",
                    fontSize = 13.sp,
                    color = InkSoft
                )
            }
        }

        // ===== 我的数据（入口）=====
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(vertical = 6.dp)) {
                ProfileLinkRow("📊 运动报告", "看趋势和打卡") { nav.navigate(Screen.Stats.route) }
                ProfileLinkRow("🏆 成就墙", "已解锁 ${badges.size} 个") { nav.navigate(Screen.Achievements.route) }
                ProfileLinkRow("👦 孩子管理", child?.name ?: "添加或切换宝贝") { nav.navigate(Screen.Children.route) }
                ProfileLinkRow("⚙️ 设置", "目标 / 提醒 / 声音语音") { nav.navigate(Screen.Settings.route) }
            }
        }

        // ===== 其他 =====
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(vertical = 6.dp)) {
                ProfileLinkRow("📄 隐私政策", "") { nav.navigate(Screen.Privacy.route) }
                ProfileLinkRow("🧹 清理缓存", formatBytes(cacheBytes)) {
                    clearCache(ctx)
                    cacheBytes = 0L
                }
                ProfileLinkRow("ℹ️ 版本信息", "v$version") { showVersion = true }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "爱跳绳 · 陪伴宝贝健康成长 💛",
            fontSize = 12.sp,
            color = InkSoft,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
    }

    // ===== 弹框 =====
    if (showVersion) {
        AlertDialog(
            onDismissRequest = { showVersion = false },
            title = { Text("版本信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("应用名称：爱跳绳")
                    Text("版本号：v$version")
                    Text("包名：${ctx.packageName}")
                    Text("离线语音包：${voiceAssetCount(ctx)} 条童音语料")
                    Text("数据全部保存在本机，卸载前记得先查看记录哦。", fontSize = 12.sp, color = InkSoft)
                }
            },
            confirmButton = {
                TextButton(onClick = { showVersion = false }) { Text("知道啦") }
            }
        )
    }
}

// ============================ 子组件 ============================

/** 头部彩色卡里的小成绩格。 */
@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

/** 奖品小格子：已解锁彩色实心，未解锁灰化加锁。 */
@Composable
private fun PrizeChip(
    prize: Rewards.Prize,
    unlocked: Boolean,
    claimed: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (unlocked) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(if (unlocked) prize.emoji else "🔒", fontSize = 22.sp)
        Text(
            prize.name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (unlocked) MaterialTheme.colorScheme.onPrimaryContainer else InkSoft,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            if (unlocked) "${prize.needPoints}分" else "${prize.needPoints}分",
            fontSize = 9.sp,
            color = if (unlocked) MaterialTheme.colorScheme.onPrimaryContainer else InkSoft
        )
        if (unlocked && claimed) {
            Text("已兑换", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 跳转行：左标题 + 右副标题 + 箭头。 */
@Composable
private fun ProfileLinkRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(value, fontSize = 13.sp, color = InkSoft)
            Spacer(Modifier.width(6.dp))
        }
        Text("›", fontSize = 18.sp, color = InkSoft)
    }
}

// ============================ 工具函数 ============================

/** 递归统计应用缓存目录大小（字节）。 */
private fun cacheSizeBytes(ctx: Context): Long =
    runCatching {
        ctx.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)

/** 清空应用缓存目录（codeCache 一并清），不影响跳绳记录等数据库数据。 */
private fun clearCache(ctx: Context) {
    runCatching { ctx.cacheDir.deleteRecursively() }
    runCatching { ctx.codeCacheDir.deleteRecursively() }
}

/** 统计 assets/voice 里的离线语音包条数（用于「版本信息」展示）。 */
private fun voiceAssetCount(ctx: Context): Int =
    runCatching { ctx.assets.list("voice")?.size ?: 0 }.getOrDefault(0)

/** 字节数转人类可读文本。 */
private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}
