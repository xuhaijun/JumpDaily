package com.jumpdaily.jump.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import android.app.Activity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jumpdaily.jump.data.model.CountMode
import com.jumpdaily.jump.di.AppContainer
import com.jumpdaily.jump.ui.components.Edge
import com.jumpdaily.jump.ui.components.FloatingJumpBar
import com.jumpdaily.jump.ui.components.Mode
import com.jumpdaily.jump.ui.theme.JumpDailyTheme
import com.jumpdaily.jump.ui.screens.AchievementsScreen
import com.jumpdaily.jump.ui.screens.SplashScreen
import com.jumpdaily.jump.ui.screens.ChildrenScreen
import com.jumpdaily.jump.ui.screens.HomeScreen
import com.jumpdaily.jump.ui.screens.ProfileScreen
import com.jumpdaily.jump.ui.screens.SettingsScreen
import com.jumpdaily.jump.ui.screens.PrivacyScreen
import com.jumpdaily.jump.ui.screens.StatsScreen
import com.jumpdaily.jump.ui.screens.CameraTrainingScreen
import com.jumpdaily.jump.ui.screens.TrainingScreen
import com.jumpdaily.jump.ui.viewmodel.RecordsViewModel
import com.jumpdaily.jump.ui.viewmodel.SessionViewModel
import com.jumpdaily.jump.ui.viewmodel.TrainingViewModel
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object Training : Screen("training")
    object Stats : Screen("stats")
    object Achievements : Screen("achievements")
    object Children : Screen("children")
    object Settings : Screen("settings")
    object CameraTraining : Screen("camera_training")
    object Privacy : Screen("privacy")
    /** 「我的」：孩子信息 / 积分 / 奖品 / 设置前移 / 隐私 / 缓存 / 版本。 */
    object Profile : Screen("profile")
}

/**
 * 底部 4 个主 tab。
 * 注：统计与历史已合并为一个「统计」页（内部用 tab 栏切换报告/历史），
 * 空出来的位置给了「我的」。
 */
private val BottomItems = listOf(
    Screen.Home to ("首页" to "🏠"),
    Screen.Stats to ("统计" to "📊"),
    Screen.Achievements to ("成就" to "🏆"),
    // 「我的」用小朋友头像 🧒：比灰色剪影 👤 更彩色、更贴合「这是宝贝的主页」的语义
    Screen.Profile to ("我的" to "🧒")
)

@Composable
fun AppRoot(container: AppContainer) {
    val nav = rememberNavController()
    val session: SessionViewModel = viewModel(factory = container.sessionFactory)
    val records: RecordsViewModel = viewModel(factory = container.recordsFactory)
    val currentChild by session.currentChild.collectAsStateWithLifecycle()
    // 读取夜间模式偏好，作为主题开关
    val darkTheme by container.prefsRepository.darkTheme.collectAsStateWithLifecycle(
        initialValue = false,
        lifecycle = LocalLifecycleOwner.current.lifecycle
    )

    LaunchedEffect(currentChild?.id) { records.setChild(currentChild?.id) }

        JumpDailyTheme(darkTheme = darkTheme) {
            val navBackStack by nav.currentBackStackEntryAsState()
            val currentRoute = navBackStack?.destination?.route
            // 底部导航栏只在 4 个主 tab 屏显示；设置/孩子/隐私等二级页不显示，避免出现「无高亮 tab」的困惑态
            val showBottomBar = currentRoute in listOf(
                Screen.Home.route, Screen.Stats.route, Screen.Achievements.route, Screen.Profile.route
            )

            // ===== 跳绳浮条（2026-09-05）=====
            // 训练 VM 在 AppRoot 以 Activity 级创建（AppRoot 非导航目的地，viewModel() 默认归属 Activity），
            // 与两个训练页（显式传 Activity owner）共用同一实例，
            // 「暂停挂起 → 回首页 → 浮条继续」的会话才有地方存活
            val trainingVm: TrainingViewModel = viewModel(factory = container.trainingFactory)
            val tRunning by trainingVm.running.collectAsStateWithLifecycle()
            val tPaused by trainingVm.paused.collectAsStateWithLifecycle()
            val tCount by trainingVm.count.collectAsStateWithLifecycle()
            val tElapsed by trainingVm.elapsed.collectAsStateWithLifecycle()
            // 当前会话的计数方式（挂起「继续」回到正确的训练页用）
            val tSessionMode by trainingVm.sessionMode.collectAsStateWithLifecycle()
            // 用户偏好的计数方式（无会话时「开始跳绳」按它分流，与首页大按钮一致）
            val prefCountMode by container.prefsRepository.countMode.collectAsStateWithLifecycle(
                initialValue = CountMode.CAMERA
            )
            // 浮条位置记忆（全局，跨启动还原）
            val barEdge by container.prefsRepository.floatBarEdge().collectAsStateWithLifecycle(initialValue = "R")
            val barY by container.prefsRepository.floatBarY().collectAsStateWithLifecycle(initialValue = 0.62f)
            val coroutine = rememberCoroutineScope()

            // 首页返回键 = 直接退出应用（2026-09-04）：
            // 修复「返回退出重开后，再按返回关掉一个首页又冒出另一个首页（浮条内容还不一样）」——
            // 根因是栈里可能出现两个 Home（Splash navigate 竞态重复压栈），返回只是逐个弹栈。
            // 现在首页上按返回不再弹栈，而是结束任务；配合 Splash 侧 launchSingleTop 双保险。
            val activity = LocalContext.current as? Activity
            BackHandler(enabled = currentRoute == Screen.Home.route) {
                activity?.finishAffinity()
            }

            // 浮条可见性：
            // - 挂起会话（paused=true）：四 tab 常驻，胶囊显示「已跳 X 个 · 时长 | 继续」
            // - 无会话（!running && !paused）：仅首页显示「开始跳绳」入口胶囊
            // - 训练页 / 二级页（设置等）/ 启动页不显示
            val sessionActive = tRunning || tPaused
            val barMode = when {
                tPaused && showBottomBar -> Mode.SUSPENDED
                !sessionActive && currentRoute == Screen.Home.route -> Mode.IDLE
                else -> null
            }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { if (showBottomBar) JumpBottomBar(nav, currentRoute) }
        ) { innerPadding ->
            // Box 作为浮条的覆盖层容器：NavHost 在下、浮条在上（浮条用自身 padding 避开底部导航）
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                NavHost(
                    navController = nav,
                    startDestination = Screen.Splash.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Screen.Splash.route) { SplashScreen(nav, container) }
                    composable(Screen.Home.route) { HomeScreen(nav, session, records, container) }
                    composable(Screen.Training.route) { TrainingScreen(nav, session, container) }
                    composable(Screen.CameraTraining.route) { CameraTrainingScreen(nav, session, container) }
                    composable(Screen.Stats.route) { StatsScreen(records, session, container.soundPlayer, nav) }
                    composable(Screen.Achievements.route) { AchievementsScreen(nav, records, session, container.soundPlayer) }
                    composable(Screen.Children.route) { ChildrenScreen(session, container) }
                    composable(Screen.Settings.route) { SettingsScreen(container, nav) }
                    composable(Screen.Privacy.route) { PrivacyScreen(nav) }
                    composable(Screen.Profile.route) { ProfileScreen(nav, session, container, records) }
                }

                // 悬浮跳绳入口：覆盖在所有页面之上，拖动吸边、位置记忆
                barMode?.let { mode ->
                    FloatingJumpBar(
                        mode = mode,
                        count = tCount,
                        elapsedSec = tElapsed,
                        initialEdge = if (barEdge == "L") Edge.LEFT else Edge.RIGHT,
                        initialYRatio = barY,
                        onPositionSettled = { edge, yRatio ->
                            coroutine.launch { container.prefsRepository.setFloatBarPos(if (edge == Edge.LEFT) "L" else "R", yRatio) }
                        },
                        onClick = {
                            // 按会话状态分流到正确训练页：
                            // - 挂起「继续」→ 回到该会话自己的计数方式页（摄像头会话回摄像头页，传感器回传感器页）
                            // - 无会话「开始跳绳」→ 按用户偏好计数方式分流（与首页大按钮一致）
                            val target = if (tPaused && tSessionMode != null) {
                                if (tSessionMode == CountMode.CAMERA) Screen.CameraTraining.route else Screen.Training.route
                            } else {
                                if (prefCountMode == CountMode.CAMERA) Screen.CameraTraining.route else Screen.Training.route
                            }
                            nav.navigate(target)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun JumpBottomBar(nav: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        BottomItems.forEach { (screen, pair) ->
            val (label, emoji) = pair
            val selected = currentRoute == screen.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) nav.navigate(screen.route) {
                        // 回到 Home 以下并复用栈顶，保证「点首页必回首页」；
                        // 注意：刻意不启用 restoreState，规避 nav-compose 中
                        // launchSingleTop + restoreState 指向同一目标时「点击无反应」的已知问题。
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                    }
                },
                icon = { Text(emoji, fontSize = 22.sp) },
                label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
