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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jumpdaily.jump.data.model.CountMode
import com.jumpdaily.jump.data.repository.DEFAULT_DAILY_GOAL
import com.jumpdaily.jump.data.repository.DEFAULT_WEEKLY_GOAL
import com.jumpdaily.jump.di.AppContainer
import com.jumpdaily.jump.ui.components.PermissionDeniedDialog
import com.jumpdaily.jump.ui.components.PermissionRationaleDialog
import com.jumpdaily.jump.ui.theme.DarkSurfaceVariant
import com.jumpdaily.jump.ui.theme.InkSoft
import com.jumpdaily.jump.ui.theme.PurplePrimary
import com.jumpdaily.jump.ui.viewmodel.SettingsViewModel

/**
 * 开关配色（与「我的」页一致：开=品牌紫填充，关=紫色描边 + 浅底）。
 * 注意：SwitchDefaults.colors 是 @Composable，必须写成函数而不能是顶层 val。
 */
@Composable
private fun settingsSwitchColors(dark: Boolean = false) = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = PurplePrimary,
    checkedBorderColor = PurplePrimary,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = if (dark) DarkSurfaceVariant else Color(0xFFE4DBFB),
    uncheckedBorderColor = PurplePrimary
)

/**
 * 独立「设置」页（替代老设置页；入口在「我的」页）。
 *
 * 从「我的」页迁移过来的设置项：
 * - 训练设置：计数方式 / 计数灵敏度 / 每日目标 / 每周目标；
 * - 每日提醒：开关 + 时间（Android 13+ 带通知权限申请流程）；
 * - 声音与语音：反馈音效 / 语音提示 / 夜间模式 / 中文语音包下载 / eSpeak 离线优先；
 * - 恢复默认设置（确认弹框）。
 *
 * 首页「今日目标」卡的「调整目标」也直达本页。
 */
@Composable
fun SettingsScreen(container: AppContainer, nav: NavHostController? = null) {
    val vm: SettingsViewModel = viewModel(factory = container.settingsFactory)
    val ctx = LocalContext.current
    val activity = ctx as? ComponentActivity

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

    var showReset by remember { mutableStateOf(false) }

    // 通知权限（Android 13+ 开启提醒时需要）
    var showNotiRationale by remember { mutableStateOf(false) }
    var showNotiDenied by remember { mutableStateOf(false) }
    val notiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setReminder(true, hour, minute)
        else {
            vm.setReminder(false, hour, minute) // 未授权则回退开关，避免「开着却不提醒」
            if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
                showNotiDenied = true
            }
        }
    }

    /** 拨动「每日提醒」：开启且未授权时，先弹说明框再向系统申请。 */
    fun onReminderToggle(on: Boolean) {
        if (!on) { vm.setReminder(false, hour, minute); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) { vm.setReminder(true, hour, minute); return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            vm.setReminder(true, hour, minute); return
        }
        showNotiRationale = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ===== 头部：返回 + 标题 =====
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (nav != null) {
                Text(
                    "‹",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { nav.popBackStack() }
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text("设置", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }

        // ===== 训练设置 =====
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🏃 训练设置", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("计数方式", fontSize = 14.sp, color = InkSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceChip("📱 传感器", countMode == CountMode.SENSOR, Modifier.weight(1f)) {
                        vm.setCountMode(CountMode.SENSOR)
                    }
                    ChoiceChip("🎥 摄像头", countMode == CountMode.CAMERA, Modifier.weight(1f)) {
                        vm.setCountMode(CountMode.CAMERA)
                    }
                }
                Text(
                    "摄像头用 AI 姿态估计计数更准；传感器不挑光线，戴手上就能跳。",
                    fontSize = 12.sp, color = InkSoft
                )
                Spacer(Modifier.height(2.dp))
                SettingsSliderRow("🎯 计数灵敏度", "%.2f".format(sensitivity), sensitivity, 0.5f..1.5f, 10) {
                    vm.setSensitivity(it)
                }
                Text("孩子跳得轻就调高，跳得重就调低。", fontSize = 12.sp, color = InkSoft)
                Spacer(Modifier.height(2.dp))
                SettingsSliderRow(
                    "🏁 每日目标", "$dailyGoal 个", dailyGoal.toFloat(), 50f..1000f, 95
                ) { vm.setDailyGoal(it.toInt()) }
                SettingsSliderRow(
                    "📅 每周目标", "$weeklyGoal 个",
                    weeklyGoal.toFloat().coerceIn(350f, 7000f), 350f..7000f, 133
                ) { vm.setWeeklyGoal(it.toInt()) }
                Text("调每日目标时每周目标会按「每日 × 7」自动同步。", fontSize = 12.sp, color = InkSoft)
            }
        }

        // ===== 每日提醒 =====
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔔 每日提醒", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(checked = reminderOn, onCheckedChange = { onReminderToggle(it) }, colors = settingsSwitchColors(darkOn))
                }
                if (reminderOn) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showTimePicker(ctx, hour, minute) { h, m -> vm.setReminder(true, h, m) } }
                            .padding(vertical = 8.dp)
                    ) {
                        Text("提醒时间", fontSize = 15.sp, color = InkSoft, modifier = Modifier.weight(1f))
                        Text(
                            String.format(java.util.Locale.US, "%02d:%02d", hour, minute),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text("到点提醒宝贝来跳绳，养成运动好习惯～", fontSize = 12.sp, color = InkSoft)
            }
        }

        // ===== 声音与语音 =====
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔊 声音与语音", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                SettingsSwitchRow("🔔 反馈音效", "跳跃、奖励、成就的童音音效", soundOn) { vm.setSound(it) }
                SettingsSwitchRow("🗣️ 语音提示", "训练中用童音朗读鼓励语", voiceOn) { vm.setVoice(it) }
                SettingsSwitchRow("🌙 夜间模式", "晚上跳也不刺眼", darkOn) { vm.setDarkTheme(it) }
                Spacer(Modifier.height(4.dp))
                // 换手机 / 缺中文语音包时，点这里跳系统下载
                SettingsActionRow("📥 下载中文语音包") { container.voiceSpeaker.requestVoiceDataInstall(ctx) }
                Text("当前语音引擎：${container.voiceSpeaker.engineInfo()}", fontSize = 12.sp, color = InkSoft)
                val espeakReady = container.voiceSpeaker.espeakAvailable()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🌐 离线优先（eSpeak 引擎）", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (espeakReady) "已安装，开启后任意手机音色一致" else "未安装，点下方下载后可用",
                            fontSize = 12.sp, color = InkSoft
                        )
                    }
                    Switch(
                        checked = voiceOffline,
                        enabled = espeakReady,
                        onCheckedChange = { vm.setVoiceOffline(it) },
                        colors = settingsSwitchColors(darkOn)
                    )
                }
                if (!espeakReady) {
                    SettingsActionRow("📥 下载 eSpeak 离线中文引擎", highlight = true) {
                        container.voiceSpeaker.requestEspeakInstall(ctx)
                    }
                }
            }
        }

        // ===== 其他 =====
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(vertical = 6.dp)) {
                SettingsResetRow("♻️ 恢复默认设置") { showReset = true }
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

    // ===== 弹框（放在滚动容器外，避免被裁剪）=====

    // 恢复默认设置
    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("恢复默认设置？") },
            text = { Text("会把计数方式、灵敏度、目标、提醒、音效与语音开关恢复成默认值。\n跳绳记录和积分不会丢失。") },
            confirmButton = {
                TextButton(onClick = {
                    showReset = false
                    vm.setCountMode(CountMode.CAMERA)
                    vm.setSensitivity(1.0f)
                    vm.setDailyGoal(DEFAULT_DAILY_GOAL)
                    vm.setWeeklyGoal(DEFAULT_WEEKLY_GOAL)
                    vm.setReminder(false, 19, 0)
                    vm.setSound(true)
                    vm.setVoice(true)
                    vm.setDarkTheme(false)
                }) { Text("恢复默认") }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text("再想想") }
            }
        )
    }

    // 通知权限说明框（Android 13+ 开启提醒时）
    if (showNotiRationale) {
        PermissionRationaleDialog(
            title = "需要通知权限",
            body = "爱跳绳会在你设定的时间发送「该运动啦」的提醒，帮宝贝养成跳绳好习惯。\n\n" +
                    "通知只在本机触发，你可以随时在系统设置里关闭。",
            onConfirm = {
                showNotiRationale = false
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
            onOpenSettings = {
                showNotiDenied = false
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                )
            },
            onDismiss = { showNotiDenied = false }
        )
    }
}

// ============================ 子组件 ============================

/** 带副标题的开关行。 */
@Composable
private fun SettingsSwitchRow(
    label: String,
    sub: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (sub.isNotEmpty()) Text(sub, fontSize = 11.sp, color = InkSoft)
        }
        Switch(checked = checked, onCheckedChange = onToggle, colors = settingsSwitchColors())
    }
}

/** 可点击的操作行（高亮底色用于「下载」这类主行动）。 */
@Composable
private fun SettingsActionRow(label: String, highlight: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (highlight) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.primaryContainer
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
        Text(
            "›",
            fontSize = 18.sp,
            color = if (highlight) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** 跳转行：左标题 + 右箭头（用于「恢复默认设置」这类整行点击项）。 */
@Composable
private fun SettingsResetRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("›", fontSize = 18.sp, color = InkSoft)
    }
}

/** 带滑块的设置行。 */
@Composable
private fun SettingsSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

/** 二选一胶囊按钮。 */
@Composable
private fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

// ============================ 工具函数 ============================

private fun showTimePicker(ctx: Context, hour: Int, minute: Int, onSet: (Int, Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onSet(h, m) }, hour, minute, true).show()
}
