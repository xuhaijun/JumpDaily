package com.jumpdaily.jump.ui.navigation

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jumpdaily.jump.di.AppContainer
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

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { if (showBottomBar) JumpBottomBar(nav, currentRoute) }
        ) { innerPadding ->
            NavHost(
                navController = nav,
                startDestination = Screen.Splash.route,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                composable(Screen.Splash.route) { SplashScreen(nav, container) }
                composable(Screen.Home.route) { HomeScreen(nav, session, records, container) }
                composable(Screen.Training.route) { TrainingScreen(nav, session, container) }
                composable(Screen.CameraTraining.route) { CameraTrainingScreen(nav, session, container) }
                composable(Screen.Stats.route) { StatsScreen(records, session, container.soundPlayer, nav) }
                composable(Screen.Achievements.route) { AchievementsScreen(nav, records, session, container.soundPlayer) }
                composable(Screen.Children.route) { ChildrenScreen(session, container) }
                composable(Screen.Settings.route) { SettingsScreen(session, container, nav) }
                composable(Screen.Privacy.route) { PrivacyScreen(nav) }
                composable(Screen.Profile.route) { ProfileScreen(nav, session, container, records) }
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
