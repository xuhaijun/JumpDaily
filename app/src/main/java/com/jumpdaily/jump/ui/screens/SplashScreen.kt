package com.jumpdaily.jump.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jumpdaily.jump.R
import com.jumpdaily.jump.di.AppContainer
import com.jumpdaily.jump.ui.components.JumpMascot
import com.jumpdaily.jump.ui.components.RopeSkipper
import com.jumpdaily.jump.ui.components.MascotState
import com.jumpdaily.jump.ui.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 启动页：展示吉祥物与品牌；首启需同意隐私政策后才进入首页。 */
@Composable
fun SplashScreen(nav: NavHostController, container: AppContainer) {
    val prefs = container.prefsRepository
    val accepted by prefs.privacyAccepted.collectAsStateWithLifecycle(
        initialValue = false,
        lifecycle = LocalLifecycleOwner.current.lifecycle
    )
    var splashDone by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(1000)
        splashDone = true
        // 已同意过则直接进首页；未同意则停在启动页并弹出同意框
        if (accepted) {
            nav.navigate(Screen.Home.route) { popUpTo(Screen.Splash.route) { inclusive = true } }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RopeSkipper(Modifier.size(150.dp))
            Text("爱跳绳", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("天天跳绳，健康成长 💛", fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f))
        }
    }

    // 首启隐私同意框：未同意不进入应用
    if (splashDone && !accepted) {
        AlertDialog(
            onDismissRequest = { /* 必须显式选择，不允许点外部关闭 */ },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { prefs.setPrivacyAccepted(true) }
                    nav.navigate(Screen.Home.route) { popUpTo(Screen.Splash.route) { inclusive = true } }
                }) { Text("同意并继续", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { (ctx as? Activity)?.finishAffinity() }) { Text("不同意") }
            },
            title = { Text("欢迎使用爱跳绳", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "我们非常重视你和孩子的隐私。使用前请阅读并同意《隐私政策》。\n\n" +
                                "本应用的所有数据（孩子昵称、跳绳记录、摄像头画面）均仅保存在你本机，不会上传任何服务器。",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "查看完整《隐私政策》",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { nav.navigate(Screen.Privacy.route) }
                    )
                }
            }
        )
    }
}
