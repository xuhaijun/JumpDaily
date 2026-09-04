package com.jumpdaily.jump

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jumpdaily.jump.ui.navigation.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 主题（含夜间模式）在 AppRoot 内根据偏好统一应用
            // 权限（摄像头 / 通知）改为在对应功能处按需、带说明框申请，避免启动即硬弹系统授权框
            val container = (application as JumpDailyApplication).container
            AppRoot(container = container)
        }
    }
}
