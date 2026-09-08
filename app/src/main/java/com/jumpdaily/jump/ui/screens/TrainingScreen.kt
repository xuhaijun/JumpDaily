package com.jumpdaily.jump.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Card
import androidx.activity.ComponentActivity
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jumpdaily.jump.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jumpdaily.jump.di.AppContainer
import com.jumpdaily.jump.ui.components.ChildAvatar
import com.jumpdaily.jump.ui.components.ConfettiBurst
import com.jumpdaily.jump.ui.components.CuteButton
import com.jumpdaily.jump.ui.components.DanmakuOverlay
import com.jumpdaily.jump.ui.components.FeedbackOverlay
import com.jumpdaily.jump.ui.components.FloatingRewards
import com.jumpdaily.jump.ui.components.JumpMascot
import com.jumpdaily.jump.ui.components.LottieRes
import com.jumpdaily.jump.ui.components.PopCount
import com.jumpdaily.jump.ui.components.StatCard
import com.jumpdaily.jump.ui.theme.Dimens
import com.jumpdaily.jump.ui.theme.InkSoft
import com.jumpdaily.jump.ui.theme.Mint
import com.jumpdaily.jump.ui.theme.SkyBlue
import com.jumpdaily.jump.ui.viewmodel.SessionViewModel
import com.jumpdaily.jump.ui.viewmodel.TrainingResult
import com.jumpdaily.jump.data.model.CountMode
import com.jumpdaily.jump.ui.viewmodel.TrainingViewModel
import com.jumpdaily.jump.util.formatDuration

@Composable
fun TrainingScreen(nav: NavHostController, session: SessionViewModel, container: AppContainer) {
    // 训练 VM 提升为 Activity 级：与摄像头页共用同一实例，暂停挂起后跨页存活
    val vm: TrainingViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
        factory = container.trainingFactory
    )
    val child by session.currentChild.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val elapsed by vm.elapsed.collectAsStateWithLifecycle()
    val cadence by vm.cadence.collectAsStateWithLifecycle()
    val mascot by vm.mascot.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val confetti by vm.confetti.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val goalReached by vm.goalReached.collectAsStateWithLifecycle()
    val dailyGoal by vm.dailyGoal.collectAsStateWithLifecycle()
    // 积分与弹幕（「+10」/ 连击 / 奖品飘屏）
    val sessionPoints by vm.sessionPoints.collectAsStateWithLifecycle()
    val danmaku by vm.danmaku.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()

    // 返回拦截已移除（2026-09-05）：跳绳中返回 = 自动暂停挂起，见下方 DisposableEffect

    // 会话跨页存活：暂停挂起后（running=false 但 paused=true）重入不能重置会话
    LaunchedEffect(child?.id) {
        val id = child?.id ?: return@LaunchedEffect
        if (!vm.running.value && !vm.paused.value) vm.start(id, CountMode.SENSOR)
    }

    // 离开本页 = 自动暂停挂起（与摄像头页一致）：会话保存在 Activity 级 VM，
    // 回首页后浮条可一键继续；正式落库只发生在点「结束并保存」。
    DisposableEffect(Unit) {
        onDispose { vm.pause() }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().padding(Dimens.screenPadding).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部：孩子 + 实时状态 + 计时
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ChildAvatar(child = child, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(child?.name ?: "宝贝", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    // 实时状态副标题：让孩子一眼知道现在「在跳/暂停/准备」；
                    // 积分挪到下方数据卡片里展示，这里不再重复
                    val stateText = when {
                        paused -> "⏸ 已暂停"
                        running -> "传感器计数中…"
                        else -> "准备开始…"
                    }
                    Text(stateText, fontSize = 12.sp, color = InkSoft)
                }
                Text(formatDuration(elapsed), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            // 中部：吉祥物 + 环形目标进度 + 大数字。weight(1f) 吸收全部剩余高度，
            // 让 SpaceBetween 无可分配空间，矮屏不再挤压、暂停胶囊出现/消失也不会把中部顶动
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                JumpMascot(mascot, color = MaterialTheme.colorScheme.tertiary)
                Box(Modifier.size(208.dp), contentAlignment = Alignment.Center) {
                    val goalDone = dailyGoal > 0 && count >= dailyGoal
                    val prog = if (dailyGoal > 0) (count.toFloat() / dailyGoal).coerceIn(0f, 1f) else 0f
                    // 达成目标瞬间进度环换庆祝色（薄荷绿），给孩子「完成啦」的明确视觉信号
                    GoalRing(progress = prog, reached = goalDone, modifier = Modifier.fillMaxSize())
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        PopCount(count, fontSize = 66.sp, color = MaterialTheme.colorScheme.primary)
                        Text("个", fontSize = 16.sp, color = InkSoft)
                    }
                }
                if (dailyGoal > 0) {
                    Text(
                        if (count >= dailyGoal) "🎉 已达成今日目标！" else "还差 ${dailyGoal - count} 个达成目标",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (count >= dailyGoal) MaterialTheme.colorScheme.primary else InkSoft
                    )
                }
            }

            // 数据小卡片：节奏 / 连跳 / 积分——与首页、统计页同一套 StatCard，视觉语言统一；
            // 替代原来两行干巴巴的小字，数据一眼可读、还有点击弹跳的可爱反馈
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("⚡", "$cadence", "节奏/分", Modifier.weight(1f), color = SkyBlue, valueSize = 20.sp)
                StatCard("🔥", "$streak", "连跳", Modifier.weight(1f), color = Mint, valueSize = 20.sp)
                StatCard("⭐", "$sessionPoints", "本次积分", Modifier.weight(1f), valueSize = 20.sp)
            }

            // 底部：暂停提示胶囊 + 暂停/继续 + 结束。
            // 固定高度槽始终占位：暂停时出现卡片、继续时留空，但不改变底部按钮行的位置，杜绝跳动
            Box(
                Modifier.fillMaxWidth().height(44.dp),
                contentAlignment = Alignment.Center
            ) {
                if (paused) {
                    // 柔和胶囊卡片替代裸文本，暂停状态更醒目也更可爱
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Text(
                            "⏸ 已暂停，休息一下吧～",
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                CuteButton(
                    text = if (paused) "继续" else "暂停",
                    onClick = { if (paused) child?.id?.let { vm.resume(it) } else vm.pause() },
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                    emoji = if (paused) "▶" else "⏸",
                    containerColor = MaterialTheme.colorScheme.secondary
                )
                // 防重复结束：点击后 running 立即变 false，按钮自动置灰；配合 CuteButton 内置 500ms 防抖，
                // 既挡住儿童快速连点，也挡住 stop 耗时窗口内的重复触发。「再跳一次」后 running 恢复，按钮自动可点。
                // 暂停中也要能结束（running || paused），否则暂停后就保存不了成绩
                CuteButton(
                    text = "结束",
                    onClick = { vm.stop() },
                    enabled = running || paused,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    emoji = "🛑",
                    containerColor = MaterialTheme.colorScheme.error
                )
            }
        }

        FeedbackOverlay(feedback, onConsumed = vm::consumeFeedback)
        FloatingRewards(count = count, modifier = Modifier.fillMaxSize())
        // 积分弹幕飘屏（「+10」/ 连击 / 奖品解锁）
        DanmakuOverlay(events = danmaku, onConsumed = vm::consumeDanmaku, modifier = Modifier.fillMaxSize())
        ConfettiBurst(active = confetti, onDone = vm::consumeConfetti)

        // 2026-09-05：返回确认框已移除——跳绳中返回 = 自动暂停挂起（DisposableEffect 里统一处理），
        // 回首页后浮条可一键「继续跳绳」；正式结束走页内「结束并保存」按钮。

        if (result != null) {
            ResultDialog(
                result = result!!,
                goalReached = goalReached,
                onAgain = { vm.clearResult(); child?.id?.let { vm.start(it) } },
                onDone = { vm.clearResult(); nav.popBackStack() }
            )
        }
    }
}

@Composable
fun ResultDialog(result: TrainingResult, goalReached: Boolean, onAgain: () -> Unit, onDone: () -> Unit) {
    // 单次触发守卫：儿童连点或「点按钮 + 点外部取消」同时发生时，确保「完成」只执行一次，
    // 避免重复 nav.popBackStack() / clearResult() 导致的卡顿或崩溃。
    val finished = remember { mutableStateOf(false) }
    fun finish() {
        if (finished.value) return
        finished.value = true
        onDone()
    }

    // 用全宽 Dialog + 顶部对齐让内容整体上移，不再居中偏下；内部适当紧凑
    Dialog(onDismissRequest = { finish() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().padding(top = 56.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LottieRes(R.raw.celebration, Modifier.size(120.dp))
                    Text("训练完成！", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Text("本次跳了 ${result.count} 个", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    // 把「成绩」翻译成孩子更在意的「奖励」：这一趟赚了多少积分
                    if (result.points > 0) {
                        Text(
                            "获得 ⭐ ${result.points} 积分",
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (goalReached) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text(
                                "🏆 达成今日目标！你太棒啦",
                                Modifier.padding(12.dp),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard("⏱️", formatDuration(result.durationSec), "时长", Modifier.weight(1f).fillMaxHeight(), valueSize = 20.sp)
                        StatCard("🔥", "${result.maxStreak}", "最长连跳", Modifier.weight(1f).fillMaxHeight(), valueSize = 20.sp)
                        StatCard("🍎", "${result.calories}", "千卡", Modifier.weight(1f).fillMaxHeight(), valueSize = 20.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CuteButton(
                            text = "再跳一次", onClick = onAgain,
                            modifier = Modifier.weight(1f).padding(end = 6.dp).height(52.dp),
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                        CuteButton(
                            text = "完成", onClick = { finish() },
                            modifier = Modifier.weight(1f).padding(start = 6.dp).height(52.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 训练页大数字背后的环形目标进度：底环 + 进度弧。
 * [progress] ∈ [0,1]，用动画平滑过渡，给孩子清晰的「离目标还有多远」反馈。
 * [reached] 为 true 时进度弧换庆祝色（薄荷绿），与「🎉 已达成今日目标！」文字呼应。
 */
@Composable
private fun GoalRing(progress: Float, modifier: Modifier = Modifier, reached: Boolean = false) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(500), label = "goal-ring")
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = if (reached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val stroke = 14.dp.toPx()
        val r = (size.minDimension - stroke) / 2f
        val c = center
        drawCircle(
            color = trackColor,
            style = Stroke(stroke),
            radius = r,
            center = c
        )
        if (animated > 0.001f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}
