package com.jumpdaily.jump.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jumpdaily.jump.di.AppContainer
import com.jumpdaily.jump.ui.components.ChildAvatar
import com.jumpdaily.jump.ui.components.ConfettiBurst
import com.jumpdaily.jump.ui.components.CuteButton
import com.jumpdaily.jump.ui.components.FloatingRewards
import com.jumpdaily.jump.ui.components.JumpMascot
import com.jumpdaily.jump.ui.components.MascotState
import com.jumpdaily.jump.ui.components.StatCard
import com.jumpdaily.jump.data.model.Badge
import com.jumpdaily.jump.data.model.BadgeCatalog
import com.jumpdaily.jump.data.model.CountMode
import com.jumpdaily.jump.ui.navigation.Screen
import com.jumpdaily.jump.ui.theme.InkSoft
import com.jumpdaily.jump.ui.viewmodel.RecordsViewModel
import com.jumpdaily.jump.ui.viewmodel.SessionViewModel
import com.jumpdaily.jump.util.formatDuration
import com.jumpdaily.jump.util.formatMonthDay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HomeScreen(nav: NavHostController, session: SessionViewModel, records: RecordsViewModel, container: AppContainer) {
    val child by session.currentChild.collectAsStateWithLifecycle()
    val stats by records.stats.collectAsStateWithLifecycle()
    val dailyGoal by session.dailyGoal.collectAsStateWithLifecycle()
    val countMode by session.countMode.collectAsStateWithLifecycle()
    val weekly by records.weekly.collectAsStateWithLifecycle()
    val badges by records.badges.collectAsStateWithLifecycle()
    val celebrationSeeded by container.prefsRepository.celebrationSeeded.collectAsStateWithLifecycle(initialValue = false)
    val streakMedalSeen by container.prefsRepository.lastStreakMedal.collectAsStateWithLifecycle(initialValue = 0)
    /** 统计/成就数据是否已完成「真实首帧」加载（成就基线快照必须先等它为 true）。 */
    val statsLoaded by records.statsLoaded.collectAsStateWithLifecycle(initialValue = false)
    val unlockedIds = badges.map { it.id }.toSet()

    val sound = container.soundPlayer
    var mascotState by remember { mutableStateOf(MascotState.IDLE) }
    var bubble by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 最近训练记录（records 按 date DESC 排序，第一条即最近一次）→ 用于「上次成绩」摘要卡
    val recentRecords by records.records.collectAsStateWithLifecycle()

    // 该孩子的总积分（按 childId 隔离存储）：放在首页数据卡，孩子跳完最想看的就是它
    val childId = child?.id
    val pointsFlow = remember(childId) {
        childId?.let { container.prefsRepository.points(it) } ?: flowOf(0)
    }
    val points by pointsFlow.collectAsStateWithLifecycle(initialValue = 0)

    /**
     * 历史最长连跳：所有记录里 maxStreak 的最大值。
     * 用它替代原先的「今日个数 / 连续天数 / 最佳单次」——那三项已由上方目标 Hero
     * 与打卡卡完整表达，再摆一遍只是重复。
     */
    val bestStreak = remember(stats.history) {
        stats.history.maxOfOrNull { it.maxStreak } ?: 0
    }

    /**
     * 首次基线种子化：把「当前已解锁的成就」标记为已看，这样老用户（升级后已有历史成就）
     * 不会一打开就弹出一堆历史成就；之后真正新增的成就才会触发庆祝。
     *
     * 关键修复：必须先等 [RecordsViewModel.statsLoaded] 为 true（该孩子的真实统计首帧到达）
     * 再快照。之前直接 `records.badges.first()` 会抢在真实数据之前拿到 stateIn 的初始空
     * 列表，于是基线被记成空集 —— 真实成就到达时全被误判成「新解锁」，每次打开 App 都弹窗。
     */
    LaunchedEffect(child?.id) {
        if (child?.id == null || celebrationSeeded) return@LaunchedEffect
        records.statsLoaded.first { it } // 挂起，直到真实统计首帧到达
        val snapshot = records.badges.value.map { it.id }.toSet()
        container.prefsRepository.setSeenBadgeIds(snapshot)
        // 把当前连续打卡里程碑也设为基线，避免老用户/已连续多天者一打开就弹大奖章
        val currentStreak = records.stats.value.streak
        container.prefsRepository.setLastStreakMedal((currentStreak / 7) * 7)
        container.prefsRepository.setCelebrationSeeded()
    }

    /**
     * 会话基线：数据加载完成后记录一次「刚进本页时已解锁」的快照。
     * 只有本次会话中【新解锁】的成就才庆祝 —— 即便上面的持久化基线因时序抖动失效，
     * 也不会把历史成就误判成新增，彻底杜绝「每次打开 App 都弹」。
     *
     * 一致性校验（防回归关键）：badges 是 stats.map 的 stateIn，初始值可能是【空列表】。
     * 若 statsLoaded 一变 true 就直接快照 composition 里的 unlockedIds，可能在 badges
     * 还没追上真实数据时把基线记成空集 → 历史成就全被误判成「新解锁」→ 开屏弹框。
     * 因此先等 badges 与「用当前 stats 现算的成就集合」完全一致，再记录基线。
     */
    var sessionBaseline by remember(child?.id) { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(statsLoaded) {
        if (!statsLoaded) return@LaunchedEffect
        records.badges.first { badges ->
            badges.map { it.id }.toSet() ==
                BadgeCatalog.evaluate(records.stats.value).map { it.id }.toSet()
        }
        if (sessionBaseline == null) sessionBaseline = records.badges.value.map { it.id }.toSet()
    }
    /** 本次会话内真正新解锁的成就 id（用于庆祝弹框）。 */
    val newlyUnlockedIds = sessionBaseline?.let { base -> unlockedIds - base } ?: emptySet()
    val newBadges = badges.filter { it.id in newlyUnlockedIds }

    /** 新成就解锁（本次会话内真正新增）时弹一次全屏庆祝；关闭后把已看过的徽章持久化。 */
    var showCelebrate by remember { mutableStateOf(false) }
    LaunchedEffect(newlyUnlockedIds) {
        if (newlyUnlockedIds.isNotEmpty()) {
            showCelebrate = true
            sound.badge() // 成就解锁专属旋律
        }
    }

    /**
     * 连续打卡里程碑（7 的倍数）庆祝：仅当本次跨过的里程碑高于「已庆祝过的最高里程碑」时弹，
     * 每个里程碑只庆祝一次；播放专属号角旋律。
     */
    val streakMilestone = (stats.streak / 7) * 7
    /**
     * 进入本页时的连续打卡里程碑快照：只有本次会话中【新跨过】的里程碑才庆祝，
     * 与成就弹框同理，避免持久化基线时序抖动导致一打开就弹大奖章。
     */
    var streakBaseline by remember(child?.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(statsLoaded, streakMilestone) {
        if (statsLoaded && streakBaseline == null) streakBaseline = streakMilestone
    }
    var showStreakMedal by remember { mutableStateOf(false) }
    LaunchedEffect(streakMilestone) {
        val base = streakBaseline
        if (base != null && celebrationSeeded && streakMilestone >= 7 &&
            streakMilestone > base && streakMilestone > streakMedalSeen
        ) {
            showStreakMedal = true
            sound.fanfare() // 连续打卡大奖章专属号角
        }
    }

    /** 戳一下卡片：播放卡通音效 + 吉祥物欢呼 + 冒出逗比气泡，并朗读卡片文字，1.6s 后恢复。 */
    fun poke(line: String, soundEffect: () -> Unit) {
        soundEffect()
        mascotState = MascotState.CHEER
        bubble = line
        container.voiceSpeaker.speak(ttsReady(line)) // 点一下就把卡片里的文字念出来
        scope.launch {
            delay(1600)
            mascotState = MascotState.IDLE
            bubble = null
        }
    }

    /** 戳吉祥物：它蹦起来 + 欢呼 + 喊话，并朗读右侧文字。 */
    fun pokeMascot() {
        sound.cheer()
        mascotState = MascotState.JUMPING
        bubble = "跳跳星陪你一起跳！🤸"
        container.voiceSpeaker.speak(ttsReady(bubble ?: "今天也来跳一跳吧，我陪你一起数"))
        scope.launch {
            delay(1200)
            mascotState = MascotState.IDLE
            bubble = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            // 区块间距 16→14dp：与统计页/我的页统一，顺带给首屏再挤出 10dp 左右
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 顶部问候 + 切换孩子（紧凑版）：首屏高度很贵，头像 60→46dp、问候 22→20sp，
            // 把省下的空间让给「今日目标 Hero + 开始跳绳」这一核心动作区
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ChildAvatar(child = child, size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${greetingNow()}，${child?.name ?: "宝贝"}！",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${weekdayNow()} · 今天也要元气满满哦 🌈",
                        fontSize = 12.sp,
                        color = InkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { nav.navigate(Screen.Children.route) }) {
                    Text("切换 ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            // Hero：今日目标 + 大数字 + 进度条 + 跳跳星同框。
            // 2026-09-07 布局重构：原先「吉祥物气泡卡」单独占 182dp（首屏 27%），目标卡另占 150dp+，
            // 两者合并成一张 Hero 后省下约 150dp，吉祥物缩小为卡内装饰（戳一下照样蹦跳 + 喊话）
            DailyGoalHero(
                todayCount = stats.todayCount,
                bestCount = stats.bestCount,
                dailyGoal = dailyGoal,
                mascotState = mascotState,
                bubble = bubble,
                onPokeMascot = ::pokeMascot,
                onAdjust = { nav.navigate(Screen.Settings.route) }
            )

            // 开始跳绳：紧跟 Hero，首屏 0 滚动即可开跳。
            // （原先排在卡片流最末尾，360×780 机型需下滑约 200dp 才能点到）
            // 达标彩蛋：今天目标已达成时换庆祝文案，鼓励孩子「再来一波」
            val goalReached = stats.todayCount > 0 && stats.todayCount >= dailyGoal.coerceAtLeast(600)
            CuteButton(
                if (goalReached) "已达标！再挑战一波 🎉" else "开始跳绳 🏃",
                onClick = { nav.navigate(if (countMode == CountMode.CAMERA) Screen.CameraTraining.route else Screen.Training.route) },
                modifier = Modifier.fillMaxWidth()
            )

            // 本周打卡日历（连续打卡可视化）：情感激励区，排在核心动作之后，仍落在首屏可见范围内
            WeeklyCheckIn(weekly = weekly, streak = stats.streak)

            // 数据三卡：刻意避开 Hero 已表达的「今日个数 / 连续天数 / 最佳单次」，
            // 换成三项不重复的信息（最长连跳 / 打卡天数 / 我的积分），点击仍有音效 + 气泡 + 朗读
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    "🔥", "$bestStreak", "最长连跳", Modifier.weight(1f),
                    onClick = {
                        poke("一口气连跳 $bestStreak 个，太稳啦！🔥") { sound.cheer() }
                    }
                )
                StatCard(
                    "📅", "${stats.activeDays}", "打卡天数", Modifier.weight(1f),
                    onClick = {
                        poke("已经打卡 ${stats.activeDays} 天啦，好习惯正在长出来 🌱") { sound.reward() }
                    }
                )
                StatCard(
                    "⭐", "$points", "我的积分", Modifier.weight(1f),
                    onClick = {
                        poke("你已经攒了 $points 分，可以去我的页换奖品啦 🎁") { sound.star() }
                    }
                )
            }

            // 上次成绩摘要：有训练记录才显示，点击查看完整统计报告
            recentRecords.firstOrNull()?.let { last ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { nav.navigate(Screen.Stats.route) },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🪢", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        // 标题与数据行之间留 2.dp 间距，避免两行文字贴得太近
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "上次成绩 · ${formatMonthDay(last.date)}",
                                fontSize = 13.sp,
                                color = InkSoft
                            )
                            Text(
                                "${last.count} 个 · ${formatDuration(last.durationSec)} · ${last.calories} 千卡",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text("查看 ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

        }

        // 新成就解锁全屏庆祝（仅弹一次）
        if (showCelebrate && newBadges.isNotEmpty()) {
            BadgeCelebration(
                newBadges = newBadges,
                onClose = {
                    scope.launch { container.prefsRepository.setSeenBadgeIds(unlockedIds) }
                    showCelebrate = false
                }
            )
        }

        // 连续打卡里程碑（如 7 天）大奖章庆祝（每个里程碑仅弹一次）
        if (showStreakMedal && streakMilestone >= 7) {
            StreakMedalCelebration(
                days = streakMilestone,
                onClose = {
                    scope.launch { container.prefsRepository.setLastStreakMedal(streakMilestone) }
                    showStreakMedal = false
                }
            )
        }
    }
}

/** 本周打卡日历：7 天连续可视化，已打卡填色 + ✓，今天加高亮环。点击某天查看当日个数。 */
@Composable
private fun WeeklyCheckIn(weekly: List<Pair<String, Int>>, streak: Int) {
    // 点击某一天：卡片底部显示当天具体个数（再点一次取消）
    var picked by remember { mutableStateOf<String?>(null) }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📅 本周打卡", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("连续 $streak 天 🔥", fontSize = 13.sp, color = InkSoft)
            }
            Text("本周已跳 ${weekly.sumOf { it.second }} 个，继续加油 💪", fontSize = 13.sp, color = InkSoft)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                weekly.forEach { (label, cnt) ->
                    val done = cnt > 0
                    val isToday = label == "今天"
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(
                                    if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                                .then(
                                    when {
                                        // 选中日加实线主色环（比今天的高亮环更细，避免混淆）
                                        picked == label -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        isToday -> Modifier.border(2.5.dp, MaterialTheme.colorScheme.tertiary, CircleShape)
                                        else -> Modifier
                                    }
                                )
                                .clickable { picked = if (picked == label) null else label },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (done) "✓" else "·",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (done) MaterialTheme.colorScheme.onPrimary else InkSoft
                            )
                        }
                        Text(label, fontSize = 11.sp, color = InkSoft)
                    }
                }
            }
            // 点击反馈：显示被点中那天的具体个数
            picked?.let { label ->
                val cnt = weekly.firstOrNull { it.first == label }?.second ?: 0
                Text(
                    if (cnt > 0) "📍 $label 跳了 $cnt 个，真棒！"
                    else "📍 $label 还没有记录，跳一跳吧",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 今日目标 Hero（2026-09-07 重构）：把「目标进度 + 今日个数 + 吉祥物」合并成一张首屏主卡。
 *
 * 布局顺序遵循「孩子最想先看什么」：
 * 1. 标题行：今日目标 + 三颗星进度 + 还差多少个；
 * 2. 主区：超大今日个数（一眼看到成果），右侧跳跳星当装饰（戳一下会蹦跳 + 喊话）；
 * 3. 进度条；
 * 4. 底部行：默认显示达标引导，戳吉祥物/数据卡时临时显示气泡文案，右侧是「调整目标」。
 *
 * @param mascotState 吉祥物状态（首页统一驱动：戳卡片/吉祥物都会让它欢呼）
 * @param bubble 临时气泡文案，为 null 时底部行显示默认引导语
 */
@Composable
private fun DailyGoalHero(
    todayCount: Int,
    bestCount: Int,
    dailyGoal: Int,
    mascotState: MascotState,
    bubble: String?,
    onPokeMascot: () -> Unit,
    onAdjust: () -> Unit
) {
    // 目标值兜底：DataStore 首帧未到达时 dailyGoal 可能为 0，这里保证进度条不崩且不为 0 目标
    val target = dailyGoal.coerceAtLeast(600)
    val reached = todayCount >= target
    val progress = (todayCount.toFloat() / target).coerceIn(0f, 1f)
    val stars = when {
        progress >= 1f -> 3
        progress >= 0.66f -> 2
        progress >= 0.33f -> 1
        else -> 0
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 标题行：目标 + 星级 + 差额（星级用 13sp，比原 18sp 更省高度）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🎯 今日目标", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(6.dp))
                repeat(3) { i ->
                    Text("⭐", fontSize = 13.sp, modifier = Modifier.alpha(if (i < stars) 1f else 0.3f))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (reached) "已达成 🏆" else "还差 ${target - todayCount} 个",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 主区：左「今日大数字」，右「跳跳星装饰」
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "$todayCount",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "/ $target 个",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (bestCount > 0) "最佳纪录 $bestCount 个" else "迈出第一步吧 💪",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                // 跳跳星：Hero 内的情感锚点，戳一下蹦跳 + 喊话（喊话显示在下方气泡行）
                JumpMascot(
                    state = mascotState,
                    color = MaterialTheme.colorScheme.tertiary,
                    mascotSize = 78.dp,
                    // 关闭默认涟漪：吉祥物本身会蹦跳，再叠一层波纹反而显得脏
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onPokeMascot() }
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)),
                // 达成后进度条换成金色调，与「已达成 🏆」文案呼应
                color = if (reached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // 底部行：气泡（戳吉祥物/数据卡时）与默认引导复用同一行，避免额外占高度
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    bubble ?: if (bestCount > 0) "今天跳满 $target 个就达标啦，继续挑战 💪"
                    else "今天跳满 $target 个就达标啦，加油 💪",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "调整目标 ›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onAdjust() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** 新成就解锁全屏庆祝：彩带 + 奖励上浮 + 祝贺卡，点「太棒了」收下。 */
@Composable
private fun BadgeCelebration(newBadges: List<Badge>, onClose: () -> Unit) {
    var dismissed by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        ConfettiBurst(active = !dismissed, onDone = {})
        FloatingRewards(count = newBadges.size, modifier = Modifier.fillMaxSize())
        Card(
            Modifier.padding(28.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🏆", fontSize = 56.sp)
                // 标题水平居中：整行占满卡片宽度，文字在卡片中线居中
                Text(
                    "解锁新成就啦！",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                // 解锁项列表左对齐：整列占满卡片宽度，每项从左侧起排，长标题自动换行也不居中散开
                // 左侧额外缩进 32.dp（比上一版 24.dp 再调大），与卡片左边留出更明显间距，视觉更透气好看
                Column(
                    Modifier.fillMaxWidth().padding(start = 32.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    newBadges.take(3).forEach { b ->
                        Text(
                            "${b.emoji} ${b.title}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (newBadges.size > 3) Text(
                        "…等 ${newBadges.size} 个成就",
                        fontSize = 13.sp,
                        color = InkSoft,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                CuteButton("太棒了 🎉", onClick = {
                    dismissed = true
                    onClose()
                }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** 连续打卡里程碑大奖章庆祝：旋转金光 + 奖章弹入 + 专属号角（旋律已在触发处播放）。 */
@Composable
private fun StreakMedalCelebration(days: Int, onClose: () -> Unit) {
    var dismissed by remember { mutableStateOf(false) }
    val medalScale by animateFloatAsState(
        targetValue = if (dismissed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 220f),
        label = "medal-scale"
    )
    val shine by rememberInfiniteTransition(label = "medal-shine").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "medal-shine-rot"
    )
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        ConfettiBurst(active = !dismissed, onDone = {})
        FloatingRewards(count = 14, modifier = Modifier.fillMaxSize())
        Card(
            Modifier.padding(28.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 旋转金光环 + 奖章弹入
                Box(contentAlignment = Alignment.Center) {
                    Text("✨", fontSize = 120.sp, modifier = Modifier.rotate(shine).alpha(0.5f))
                    Text("🏅", fontSize = 96.sp, modifier = Modifier.scale(medalScale))
                }
                Text("连续打卡 $days 天！", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("坚持大奖章 🏆 解锁啦", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Text("你真是自律的小超人，继续加油哦！💪", fontSize = 14.sp, color = InkSoft)
                CuteButton("收下奖章 🎉", onClick = {
                    dismissed = true
                    onClose()
                }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** 把卡片文案清洗成适合 TTS 朗读的纯文本：去掉 emoji/符号与多余空白。 */
private fun ttsReady(s: String): String =
    s.replace(Regex("[\\uD800-\\uDFFF\\u2600-\\u27BF\\u2B00-\\u2BFF\\uFE00-\\uFE0F\\u200D]"), "")
        .replace(Regex("\\s+"), " ").trim()

/** 按时段问候（早上好 / 下午好 …）。 */
private fun greetingNow(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 6 -> "凌晨好"
        h < 11 -> "早上好"
        h < 14 -> "中午好"
        h < 18 -> "下午好"
        else -> "晚上好"
    }
}

/** 今天星期几，如「星期二」。 */
private fun weekdayNow(): String {
    val names = arrayOf("日", "一", "二", "三", "四", "五", "六")
    val wd = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
    return "星期${names[wd]}"
}
