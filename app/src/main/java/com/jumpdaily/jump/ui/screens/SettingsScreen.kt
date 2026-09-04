package com.jumpdaily.jump.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import com.jumpdaily.jump.ui.theme.DarkOutline
import com.jumpdaily.jump.ui.theme.DarkSurfaceVariant
import com.jumpdaily.jump.ui.theme.InkSoft
import com.jumpdaily.jump.ui.theme.PurplePrimary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavHostController
import com.jumpdaily.jump.ui.navigation.Screen
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jumpdaily.jump.data.model.CountMode
import com.jumpdaily.jump.di.AppContainer
import com.jumpdaily.jump.ui.components.PermissionDeniedDialog
import com.jumpdaily.jump.ui.components.PermissionRationaleDialog
import com.jumpdaily.jump.ui.theme.InkSoft
import com.jumpdaily.jump.ui.viewmodel.SessionViewModel
import com.jumpdaily.jump.ui.viewmodel.SettingsViewModel

/**
 * 设置页开关配色：让「关」与「开」互为相反、一眼可辨。
 * - 已开启：轨道用品牌紫 PurplePrimary 填充（实心），thumb 白色。
 * - 未开启：边框用同一个品牌紫 PurplePrimary（与开启态填充色相反——开=填充、关=描边），
 *           轨道用浅薰衣草底色，thumb 白色。这样无论亮/暗色，关闭态都有清晰可见的紫色边框。
 */
private @Composable fun switchColors(dark: Boolean) = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = PurplePrimary,
    checkedBorderColor = PurplePrimary,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = if (dark) DarkSurfaceVariant else Color(0xFFE4DBFB),
    uncheckedBorderColor = PurplePrimary,
)

@Composable
fun SettingsScreen(session: SessionViewModel, container: AppContainer, nav: NavHostController? = null) {
    val vm: SettingsViewModel = viewModel(factory = container.settingsFactory)
    val reminderOn by vm.reminderEnabled.collectAsStateWithLifecycle()
    val hour by vm.reminderHour.collectAsStateWithLifecycle()
    val minute by vm.reminderMinute.collectAsStateWithLifecycle()
    val soundOn by vm.soundEnabled.collectAsStateWithLifecycle()
    val voiceOn by vm.voiceEnabled.collectAsStateWithLifecycle()
    val voiceOffline by vm.voiceOffline.collectAsStateWithLifecycle()
    val sensitivity by vm.sensitivity.collectAsStateWithLifecycle()
    val dailyGoal by vm.dailyGoal.collectAsStateWithLifecycle()
    val weeklyGoal by vm.weeklyGoal.collectAsStateWithLifecycle()
    val darkOn by vm.darkTheme.collectAsStateWithLifecycle()
    val countMode by vm.countMode.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity

    // 通知权限授权框状态（Android 13+ 开启提醒时需要）
    var showNotiRationale by remember { mutableStateOf(false) }
    var showNotiDenied by remember { mutableStateOf(false) }
    var askedNotiBefore by remember { mutableStateOf(false) }

    val notiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askedNotiBefore = true
        if (granted) vm.setReminder(true, hour, minute)
        else {
            vm.setReminder(false, hour, minute) // 未授权则回退开关，避免「开着却不提醒」
            if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
                showNotiDenied = true
            }
        }
    }

    /** 用户拨动「每日提醒」开关：开启且未授权时，先弹说明框再向系统申请。 */
    fun onReminderToggle(on: Boolean) {
        if (!on) { vm.setReminder(false, hour, minute); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) { vm.setReminder(true, hour, minute); return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            vm.setReminder(true, hour, minute); return
        }
        showNotiRationale = true
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
        ctx.startActivity(intent)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("设置", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        // 每日提醒
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔔 每日提醒", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(checked = reminderOn, onCheckedChange = { onReminderToggle(it) }, colors = switchColors(darkOn))
                }
                if (reminderOn) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            showTimePicker(ctx, hour, minute) { h, m -> vm.setReminder(true, h, m) }
                        }
                    ) {
                        Text("提醒时间", fontSize = 15.sp, color = InkSoft, modifier = Modifier.weight(1f))
                        Text(String.format("%02d:%02d", hour, minute), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text("到点提醒宝贝来跳绳，养成运动好习惯～", fontSize = 12.sp, color = InkSoft)
            }
        }

        // 反馈音效
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔊 反馈音效", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(checked = soundOn, onCheckedChange = { vm.setSound(it) }, colors = switchColors(darkOn))
                }
                Text("已采用卡通音效素材（res/raw/snd_*.wav），可替换为真人录音", fontSize = 12.sp, color = InkSoft)
            }
        }

        // 语音提示
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🗣️ 语音提示", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(checked = voiceOn, onCheckedChange = { vm.setVoice(it) }, colors = switchColors(darkOn))
                }
                Text("训练时用小朋友的语气朗读鼓励语（如「太棒啦！」「达成今日目标啦」）", fontSize = 12.sp, color = InkSoft)
                // 换手机 / 缺中文语音包时，点这里跳系统下载更自然的中文语音（华为/小米/Google 等各不相同）
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { container.voiceSpeaker.requestVoiceDataInstall(ctx) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📥 下载中文语音包",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text("当前语音引擎：${container.voiceSpeaker.engineInfo()}", fontSize = 12.sp, color = InkSoft)
                // 离线语音包 + 离线引擎：换任何手机音色都一致，不依赖厂商默认 TTS（RHVoice 不支持中文，故用 eSpeak）
                val espeakReady = container.voiceSpeaker.espeakAvailable()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🌐 离线优先（eSpeak 离线引擎）", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (espeakReady) "已安装，开启后任意手机音色一致" else "未安装，点下方下载后可用",
                            fontSize = 12.sp, color = InkSoft
                        )
                    }
                    Switch(checked = voiceOffline, enabled = espeakReady, onCheckedChange = { vm.setVoiceOffline(it) }, colors = switchColors(darkOn))
                }
                if (!espeakReady) {
                    // 跳 F-Droid 下载 eSpeak 离线中文引擎（语音包之一，离线、免费、支持中文）
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { container.voiceSpeaker.requestEspeakInstall(ctx) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📥 下载 eSpeak 离线中文引擎",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }

        // 计数灵敏度
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🎯 计数灵敏度", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Slider(value = sensitivity, onValueChange = { vm.setSensitivity(it) }, valueRange = 0.5f..1.5f, steps = 10)
                Text(
                    "灵敏度越高越容易计数：孩子跳得很轻可调高，跳得很重可调低。当前 ${"%.2f".format(sensitivity)}",
                    fontSize = 12.sp, color = InkSoft
                )
            }
        }

        // 跳绳目标
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🏁 跳绳目标", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("每日目标", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("${dailyGoal} 个", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = dailyGoal.toFloat(),
                    onValueChange = { vm.setDailyGoal(it.toInt()) },
                    valueRange = 50f..1000f, steps = 95
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("每周目标", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("${weeklyGoal} 个", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = weeklyGoal.toFloat().coerceIn(350f, 7000f),
                    onValueChange = { vm.setWeeklyGoal(it.toInt()) },
                    // 上限 7000 = 每日目标上限 1000 × 7，保证每日目标拉满时每周目标也能跟上
                    valueRange = 350f..7000f, steps = 133
                )
                Text(
                    "调每日目标时，每周目标会自动按「每日 × 7」同步；也可以单独微调每周目标。",
                    fontSize = 12.sp, color = InkSoft
                )
                Text("达成目标会有专属庆祝动画和成就哦～", fontSize = 12.sp, color = InkSoft)
            }
        }

        // 夜间模式
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🌙 夜间模式", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = darkOn, onCheckedChange = { vm.setDarkTheme(it) }, colors = switchColors(darkOn))
            }
        }

        // 计数方式
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📷 计数方式", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceChip("📱 传感器", countMode == CountMode.SENSOR) { vm.setCountMode(CountMode.SENSOR) }
                    ChoiceChip("🎥 摄像头", countMode == CountMode.CAMERA) { vm.setCountMode(CountMode.CAMERA) }
                }
                Text("摄像头用 AI 姿态估计计数更准；首次需联网下载模型或把 pose_landmarker.task 放入 assets", fontSize = 12.sp, color = InkSoft)
            }
        }

        Spacer(Modifier.height(8.dp))

        // 隐私政策
        Card(
            Modifier.fillMaxWidth().then(if (nav != null) Modifier.clickable { nav.navigate(Screen.Privacy.route) } else Modifier),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📄 隐私政策", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("›", fontSize = 20.sp, color = InkSoft)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "爱跳绳 v1.0.0 · 陪伴宝贝健康成长 💛",
            fontSize = 12.sp, color = InkSoft,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )

        // 通知权限说明框（Android 13+ 开启提醒时）
        if (showNotiRationale) {
            PermissionRationaleDialog(
                title = "需要通知权限",
                body = "爱跳绳会在你设定的时间发送「该运动啦」的提醒，帮宝贝养成跳绳好习惯。\n\n" +
                        "通知只在本机触发，你可以随时在系统设置里关闭。",
                onConfirm = {
                    showNotiRationale = false
                    askedNotiBefore = true
                    notiLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onDismiss = { showNotiRationale = false; vm.setReminder(false, hour, minute) }
            )
        }

        // 通知权限被永久拒绝：引导去系统设置
        if (showNotiDenied) {
            PermissionDeniedDialog(
                title = "通知权限被关闭",
                body = "你之前选择了「不再询问」，系统不再弹出授权框。\n\n" +
                        "请到「设置 → 应用 → 爱跳绳 → 通知」中手动开启，才能收到跳绳提醒。",
                onOpenSettings = { showNotiDenied = false; openAppSettings() },
                onDismiss = { showNotiDenied = false }
            )
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

private fun showTimePicker(ctx: Context, hour: Int, minute: Int, onSet: (Int, Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onSet(h, m) }, hour, minute, true).show()
}
