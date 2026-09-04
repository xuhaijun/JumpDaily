package com.jumpdaily.jump.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jumpdaily.jump.ui.theme.InkSoft
import com.jumpdaily.jump.ui.theme.Mint
import com.jumpdaily.jump.ui.theme.PinkSecondary
import com.jumpdaily.jump.ui.theme.SunYellow
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jumpdaily.jump.ui.components.PermissionDeniedDialog
import com.jumpdaily.jump.ui.components.PermissionRationaleDialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jumpdaily.jump.camera.PoseJumpDetector
import com.jumpdaily.jump.camera.PoseModelProvider
import com.jumpdaily.jump.data.model.CountMode
import com.jumpdaily.jump.di.AppContainer
import com.jumpdaily.jump.sensor.JumpListener
import com.jumpdaily.jump.ui.components.ConfettiBurst
import com.jumpdaily.jump.ui.components.CuteButton
import com.jumpdaily.jump.ui.components.DanmakuOverlay
import com.jumpdaily.jump.ui.components.FeedbackOverlay
import com.jumpdaily.jump.ui.components.FloatingRewards
import com.jumpdaily.jump.ui.components.JumpMascot
import com.jumpdaily.jump.ui.components.MascotState
import com.jumpdaily.jump.ui.components.PopCount
import com.jumpdaily.jump.ui.components.PoseOverlay
import com.jumpdaily.jump.ui.viewmodel.SessionViewModel
import com.jumpdaily.jump.ui.viewmodel.TrainingResult
import com.jumpdaily.jump.ui.viewmodel.TrainingViewModel
import com.jumpdaily.jump.util.formatDuration
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 摄像头姿态估计训练页。
 *
 * 用 CameraX 打开后置摄像头预览，逐帧把图像交给 [PoseJumpDetector]（MediaPipe PoseLandmarker）
 * 做人体关键点检测，髋中心起伏触发计数；计数 / 节奏 / 反馈逻辑复用 [TrainingViewModel]，
 * 因此与传感器训练页共享同一套结果保存与庆祝流程。
 *
 * 注意：依赖 mediapipe_tasks_vision 与摄像头权限；首次使用需联网下载 pose 模型或把
 * `pose_landmarker.task` 放入 assets。本页在真机验证。
 */
@Composable
fun CameraTrainingScreen(nav: NavHostController, session: SessionViewModel, container: AppContainer) {
    val vm: TrainingViewModel = viewModel(factory = container.trainingFactory)
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
    // 积分与弹幕（「+10」/ 奖品飘屏）
    val sessionPoints by vm.sessionPoints.collectAsStateWithLifecycle()
    val danmaku by vm.danmaku.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()

    // 最新一帧人体关键点（供骨架叠加层绘制）；写在独立 state 上，
    // 仅 PoseOverlay 会因每帧更新而重组，避免大数字等 HUD 频繁重组。
    val skeleton = remember { mutableStateOf(emptyList<Pair<Float, Float>>()) }

    // 姿态引擎初始化失败（如 MediaPipe 原生库在 x86/x86_64 模拟器缺失）→ 友好降级，不崩溃
    var poseError by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()

    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(ctx) }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    // 是否已向系统申请过（用于区分「首次」与「被永久拒绝」）
    var askedCameraBefore by remember { mutableStateOf(false) }
    // 我们的自定义说明弹框 / 永久拒绝引导弹框
    var showCamRationale by remember { mutableStateOf(false) }
    var showCamDenied by remember { mutableStateOf(false) }
    // 返回拦截确认框：跳的个数超过阈值、且尚未「结束」时，系统返回先弹确认，避免误丢成绩
    var showBackConfirm by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askedCameraBefore = true
        hasPermission = granted
    }

    /** 决定下一步：已授权直接用；首次/可再次询问→弹我们的说明框；永久拒绝→弹去设置框。 */
    fun promptCameraPermission() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
            return
        }
        val shouldShow = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        if (askedCameraBefore && !shouldShow) showCamDenied = true   // 曾被勾选「不再询问」
        else showCamRationale = true                                  // 首次或仍可再次询问
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
        ctx.startActivity(intent)
    }

    // 模型路径在 IO 协程获取（首次可能联网下载，避免阻塞主线程）
    var modelPath by remember { mutableStateOf<String?>(null) }
    var modelMsg by remember { mutableStateOf("正在加载姿态模型…") }

    // 姿态检测器：模型路径就绪后才创建；回调转发到 TrainingViewModel 的统一计数/反馈逻辑
    val detector = remember(modelPath) {
        modelPath?.let { path ->
            PoseJumpDetector(
                ctx,
                path,
                object : JumpListener {
                    override fun onJump() = vm.onPoseJump()
                    override fun onCadence(cadence: Int) = vm.onPoseCadence(cadence)
                    override fun onFormIssue() = Unit // 摄像头模式暂不判动作规范
                },
                onLandmarks = { skeleton.value = it },
                onInitError = { t -> scope.launch(Dispatchers.Main) { poseError = t } }
            )
        }
    }

    fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                val cameraProvider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    // setTargetResolution 已弃用，改用 ResolutionSelector：目标 640x480，允许回退到更接近的可用分辨率
                    .setResolutionSelector(
                        androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                androidx.camera.core.resolutionselector.ResolutionStrategy(
                                    android.util.Size(640, 480),
                                    androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
                                )
                            )
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val bmp = proxy.toBitmap()
                    val rotated = rotateBitmap(bmp, proxy.imageInfo.rotationDegrees)
                    detector?.processFrame(rotated)
                    proxy.close()
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
                detector?.create()
                detector?.start()
            } catch (e: Exception) {
                Log.e("CameraTraining", "bind camera failed", e)
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    LaunchedEffect(child?.id) {
        if (!vm.running.value) vm.start(child?.id ?: 0L, CountMode.CAMERA)
    }

    // 后台获取模型路径（可能联网下载），结果写入 modelPath / modelMsg
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { PoseModelProvider.getModelPath(ctx) }
                .onSuccess { modelPath = it }
                .onFailure { modelMsg = "姿态模型加载失败：请检查网络，或手动把 pose_landmarker.task 放入 app/src/main/assets/" }
        }
    }

    // 权限与模型都就绪后再绑定相机；缺权限时先弹我们的说明框（不直接硬弹系统框）
    LaunchedEffect(hasPermission, modelPath) {
        if (hasPermission && modelPath != null) bindCamera()
        else if (!hasPermission) promptCameraPermission()
    }

    // 看不到人时（骨架为空）：用语音温柔引导小朋友站进画面；节流 8 秒，避免每帧都喊
    val noPoseLines = remember {
        listOf(
            "站进来一点，让跳跳星看到你，一起开心地跳！",
            "跳跳星找不到你啦，往中间站一站～",
            "把小脸露出来，我们准备开始跳咯！",
            "再靠近镜头一点点，跳跳星就能陪你跳啦！"
        )
    }
    var lastNoPoseIdx by remember { mutableStateOf(-1) }
    var lastNoPoseSpeakTs by remember { mutableStateOf(0L) }
    LaunchedEffect(skeleton.value.isEmpty()) {
        if (skeleton.value.isEmpty() && running && hasPermission && modelPath != null && poseError == null) {
            val now = System.currentTimeMillis()
            if (now - lastNoPoseSpeakTs > 8000) {
                lastNoPoseSpeakTs = now
                // 轮换取下一句，避免反复同一句；池子只有 4 句，简单取模即可
                lastNoPoseIdx = (lastNoPoseIdx + 1) % noPoseLines.size
                container.voiceSpeaker.speak(noPoseLines[lastNoPoseIdx])
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            detector?.stop()
            if (vm.running.value) vm.stop()
        }
    }

    // 系统返回拦截：只要已经跳过（count > 0）且还没正式「结束」，就先弹框问是否保存本次记录，
    // 避免误触返回把辛苦跳的成绩弄丢；确认则复用 vm.stop() 的保存流程，取消则继续跳。
    BackHandler(enabled = count > 0 && result == null) {
        showBackConfirm = true
    }

    // 深空紫渐变背景：相机预览全屏覆盖时不影响；无预览（授权/加载/降级）时深紫衬白字更协调好看
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3A2A6B), Color(0xFF1A1430))))) {
        if (hasPermission) {
            AndroidView(
                factory = { previewView.apply { scaleX = -1f } }, // 前置自拍镜像，孩子看到的是「镜子里的自己」
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("需要摄像头权限才能用姿态计数哦～", color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))
                CuteButton("授予权限", onClick = { promptCameraPermission() })
            }
        }

        // 模型未就绪（加载中 / 失败）：覆盖提示
        if (hasPermission && modelPath == null) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(modelMsg, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        // 实时骨架叠加层：让孩子在相机画面上看到自己的跳绳姿态（发光「影子伙伴」）
        PoseOverlay(skeleton = skeleton, modifier = Modifier.fillMaxSize())

        // 上浮奖励层：每次计数冒出 ⭐✨💖 等奖励向上飘（在 HUD 之下，避免遮挡大数字）
        FloatingRewards(count = count, modifier = Modifier.fillMaxSize())

        // 积分弹幕飘屏（「+10」/ 连击 / 奖品解锁）：叠在最上层，给孩子即时喝彩
        DanmakuOverlay(events = danmaku, onConsumed = vm::consumeDanmaku, modifier = Modifier.fillMaxSize())

        // 姿态引擎初始化失败（多为混淆导致 native 调用失败 / 个别机型不支持）：提示但不崩溃，预览仍可用
        if (poseError != null) {
            val e = poseError!!
            val debug = buildString {
                append("[调试] ")
                var cur: Throwable? = e
                var d = 0
                while (cur != null) {
                    if (d > 0) append("  ↳ ")
                    append(cur.javaClass.name)
                    append(": ")
                    append(cur.message ?: "null")
                    append("\n")
                    cur = cur.cause
                    d++
                    if (d > 6) break
                }
            }
            Box(Modifier.fillMaxSize().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
                Card(
                    Modifier.padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        "⚠️ 这个设备暂时用不了摄像头姿态计数\n（姿态引擎初始化失败，已自动降级）\n你可以先用手动计数模式，\n或者换一台设备再试～\n\n$debug",
                        Modifier.padding(14.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // 顶部：模式 + 计时，做成毛玻璃小药丸，浮在预览上更清爽
        Box(
            Modifier.fillMaxSize().padding(top = 16.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.32f))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🤳 照镜子模式", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 本次积分：每跳一下都在涨，给孩子「一直在赚」的即时感
                    Text("⭐ $sessionPoints", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SunYellow)
                    Text(formatDuration(elapsed), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // 中部主区域：根据是否检测到人，二选一展示「引导卡」或「伙伴+计数 HUD」。
        // 关键点：两处共用「同一个」跳跳星；未检测到人时只显示引导卡，避免进入页面瞬间
        // 出现「两个头像 + 多段文字叠在中部」的混乱观感。
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 64.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 中间区域（占满剩余高度并居中）：检测到人→伙伴+计数；未检测到人→引导卡
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (hasPermission && modelPath != null && poseError == null && skeleton.value.isEmpty()) {
                    // 引导卡：邀请小朋友站进画面，只用一个跳跳星，不再与下方 HUD 重复
                    Card(
                        Modifier.padding(8.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f))
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 半透明：与页面其他 HUD 元素保持一致的轻盈感，不遮挡画面
                            JumpMascot(MascotState.IDLE, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.alpha(0.5f))
                            Text("👀 站进来一点～", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                "让跳跳星看到你，\n一起开心地跳！",
                                fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // 训练中 HUD：节奏 + 吉祥物 + 计数 + 目标进度（儿童向、强反馈）
                    // 节奏卡片置于跳跳星上方，二者之间留出 12.dp 间距，视觉层级更顺
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 节奏卡片：随当前节奏快慢轻轻跳动，帮孩子卡拍子；放在跳跳星上方。
                        // 用半透明卡片承载，「节奏卡片」更聚焦；与下方跳跳星留出 12.dp 间距。
                        val beat by rememberInfiniteTransition(label = "beat")
                            .animateFloat(
                                1f, 1.18f,
                                infiniteRepeatable(
                                    tween((if (cadence > 0) (60000 / cadence).coerceIn(220, 900) else 700)),
                                    RepeatMode.Reverse
                                ),
                                label = "beat-a"
                            )
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.38f))
                        ) {
                            Row(
                                Modifier.scale(beat).padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔥", fontSize = 16.sp)
                                Text("节奏 $cadence/分", fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold)
                            }
                        }

                        // 跳跳星半透明：与节奏卡/按钮的半透明风格统一，不挡镜头画面
                        JumpMascot(mascot, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.alpha(0.5f))

                        // 计数药丸：糖果渐变 + 柔光呼吸，每跳一下更醒目
                        Box(contentAlignment = Alignment.Center) {
                            val glowA by rememberInfiniteTransition(label = "count-glow")
                                .animateFloat(0.45f, 0.85f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "count-glow-a")
                            Box(
                                Modifier.size(150.dp, 120.dp).alpha(glowA)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(SunYellow.copy(alpha = 0.9f), Color.Transparent),
                                            radius = 240f
                                        )
                                    )
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PopCount(count, fontSize = 72.sp, color = Color.White)
                                Text("个", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                            }
                        }

                        // 连击提示：连跳一段时间才显示，让「连击加分」这件事被看见
                        if (streak >= 5) {
                            Text(
                                "🔥 连击 x$streak",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SunYellow
                            )
                        }

                        // 每日目标进度条：让小朋友看见离目标还有多远
                        if (dailyGoal > 0) {
                            GoalProgressBar(cur = count, goal = dailyGoal)
                        }

                        Text("和影子伙伴一起跳！", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
            // 暂停时给一句安抚提示，让孩子知道「随时可以歇一下」
            if (paused) {
                Text(
                    "⏸ 已暂停，休息一下吧～",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 暂停 / 继续：中途想歇就歇，计时与计数一起停（见 TrainingViewModel.pause）
                CuteButton(
                    text = if (paused) "继续" else "暂停",
                    onClick = { if (paused) child?.id?.let { vm.resume(it) } else vm.pause() },
                    modifier = Modifier.weight(1f),
                    emoji = if (paused) "▶" else "⏸",
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f)
                )
                // 防重复结束：点击后 running 立即变 false，按钮自动置灰；配合 CuteButton 内置 500ms 防抖，
                // 既挡住儿童快速连点，也挡住 stop 耗时窗口内的重复触发。「再跳一次」后 running 恢复，按钮自动可点。
                // 暂停中也要能结束（enabled 用 running || paused），否则暂停后就没法保存成绩了
                CuteButton(
                    text = "结束",
                    onClick = { vm.stop() },
                    enabled = running || paused,
                    modifier = Modifier.weight(1f),
                    emoji = "🛑",
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.72f)
                )
            }
        }

        FeedbackOverlay(feedback, onConsumed = vm::consumeFeedback)
        ConfettiBurst(active = confetti, onDone = vm::consumeConfetti)

        if (result != null) {
            ResultDialog(
                result = result!!,
                goalReached = goalReached,
                onAgain = { vm.clearResult(); child?.id?.let { vm.start(it, CountMode.CAMERA) } },
                onDone = { vm.clearResult(); nav.popBackStack() }
            )
        }

        // 摄像头权限说明框（首次 / 可再次询问时）
        if (showCamRationale) {
            PermissionRationaleDialog(
                title = "需要摄像头权限",
                body = "爱跳绳会用前置摄像头识别你的跳绳动作，在画面上叠加「影子」陪你一起跳。\n\n" +
                        "画面只在你这台手机上实时处理，不会上传、不会保存，请放心授权～",
                onConfirm = { showCamRationale = false; askedCameraBefore = true; permLauncher.launch(Manifest.permission.CAMERA) },
                onDismiss = { showCamRationale = false }
            )
        }

        // 摄像头权限被永久拒绝：引导去系统设置手动开启
        if (showCamDenied) {
            PermissionDeniedDialog(
                title = "摄像头权限被关闭",
                body = "你之前选择了「不再询问」，系统不再弹出授权框。\n\n" +
                        "请到「设置 → 应用 → 爱跳绳 → 权限」中手动开启摄像头权限，才能使用姿态计数。",
                onOpenSettings = { showCamDenied = false; openAppSettings() },
                onDismiss = { showCamDenied = false }
            )
        }

        // 返回拦截确认框：跳了很多个还想走时，先确认是否结束并保存，避免成绩丢失
        if (showBackConfirm) {
            Dialog(onDismissRequest = { showBackConfirm = false }) {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("结束并保存？", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "已经跳了 $count 个啦！要不要结束这次训练，把成绩保存下来呢？\n（不保存的话，这一次的成果可就白跳咯～）",
                            fontSize = 14.sp, color = InkSoft
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CuteButton(
                                "继续跳",
                                onClick = { showBackConfirm = false },
                                modifier = Modifier.weight(1f),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            CuteButton(
                                "结束并保存",
                                onClick = { showBackConfirm = false; vm.stop() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 每日目标进度条：糖果渐变填充，让小朋友直观看到「还差多少个就达成今日目标」，
 * 给跳绳过程一个清晰的小目标，增强成就感与坚持欲。
 */
@Composable
private fun GoalProgressBar(cur: Int, goal: Int) {
    val frac = (cur.toFloat() / goal).coerceIn(0f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("🎯 今日目标 $cur / $goal", fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
        Box(
            Modifier.width(180.dp).height(12.dp).clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.28f))
        ) {
            Box(
                Modifier.fillMaxWidth(frac).height(12.dp).clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(listOf(PinkSecondary, Mint)))
            )
        }
    }
}

/** 按相机传感器旋转角度校正 Bitmap，保证输入 MediaPipe 的图像方向正确。 */
private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return src
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}
