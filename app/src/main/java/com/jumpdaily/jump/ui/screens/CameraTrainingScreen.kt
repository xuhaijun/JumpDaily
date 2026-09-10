package com.jumpdaily.jump.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import android.util.Log
import java.util.concurrent.TimeUnit
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import com.jumpdaily.jump.ui.theme.SkyBlue
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
import com.jumpdaily.jump.audio.VoicePriority
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
import kotlinx.coroutines.delay
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
    // 训练 VM 提升为 Activity 级：暂停挂起后离开本页再回来（或从首页浮条进入），会话仍在，
    // 浮条「继续跳绳」依赖这个跨页存活的实例（默认 viewModel() 会绑定本页 backstack，离开即销毁）
    val vm: TrainingViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
        factory = container.trainingFactory
    )
    val child by session.currentChild.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val confetti by vm.confetti.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val goalReached by vm.goalReached.collectAsStateWithLifecycle()
    // ⚠️ 性能约定（2026-09-07）：count / elapsed / cadence / streak / sessionPoints 都是高频 state，
    // 这里**一律不读**——否则计时每秒、积分每跳都会重组整页（含相机预览 AndroidView），卡顿的主因之一。
    // 它们改由下方各自的 HUD 子组件内部 collect，重组范围被限制在组件自身。

    // 最新一帧人体关键点：只交给 PoseOverlay 在「绘制阶段」读取（重绘不重组）。
    // 页面本身只依赖「是否检测到人」这个低频布尔值，避免每帧重组。
    val skeleton = remember { mutableStateOf(emptyList<Pair<Float, Float>>()) }
    var hasPose by remember { mutableStateOf(false) }
    // 计数瞬间的发光脉冲（0~1）：每跳一下影子伙伴亮一下，只在绘制阶段消费，不引发重组
    val pulse = remember { mutableFloatStateOf(0f) }
    // 画面过暗提示（关灯/逆光会让姿态识别失效，及时提醒家长）
    var dimLight by remember { mutableStateOf(false) }

    // 姿态引擎初始化失败（如 MediaPipe 原生库在 x86/x86_64 模拟器缺失）→ 友好降级，不崩溃
    var poseError by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()

    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(ctx).apply {
            // 页面上方叠了大量 Compose 图层（骨架/HUD/弹幕），TextureView(PERFORMANCE) 比
            // 默认 SurfaceView(COMPATIBLE) 更稳，不会出现层级撕裂与叠加闪烁
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    // 是否已向系统申请过（用于区分「首次」与「被永久拒绝」）
    var askedCameraBefore by remember { mutableStateOf(false) }
    // 我们的自定义说明弹框 / 永久拒绝引导弹框
    var showCamRationale by remember { mutableStateOf(false) }
    var showCamDenied by remember { mutableStateOf(false) }

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
                onLandmarks = { pts ->
                    skeleton.value = pts
                    // 只在「有人 ↔ 无人」状态翻转时写 state：每帧都写会让页面每帧重组
                    val detected = pts.isNotEmpty()
                    if (detected != hasPose) hasPose = detected
                },
                onInitError = { t -> scope.launch(Dispatchers.Main) { poseError = t } }
            )
        }
    }

    // 分析帧节流时间戳（跨 bindCamera 调用保留；数组包装以便在 lambda 里改值）
    val frameStamp = remember { longArrayOf(0L) }

    /**
     * 采样画面平均亮度：先缩到 32×24 再算灰度均值，每帧成本 < 0.2ms。
     * 过暗（关灯/逆光）时姿态识别会明显变差，及时提示家长开灯。
     */
    fun reportBrightness(bmp: Bitmap) {
        runCatching {
            val small = Bitmap.createScaledBitmap(bmp, 32, 24, false)
            val px = IntArray(32 * 24)
            small.getPixels(px, 0, 32, 0, 0, 32, 24)
            var sum = 0
            for (v in px) {
                val r = (v shr 16) and 0xFF
                val g = (v shr 8) and 0xFF
                val b = v and 0xFF
                sum += (r * 299 + g * 587 + b * 114) / 1000
            }
            small.recycle()
            val tooDim = (sum / px.size) < BRIGHTNESS_DIM
            // 只在状态翻转时写 state（避免每帧触发重组）
            if (tooDim != dimLight) scope.launch(Dispatchers.Main) { dimLight = tooDim }
        }
    }

    fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                // 加超时保护：相机初始化在极端情况下（相机被占用/系统繁忙）可能长时间挂起，
                // 无超时会在主线程 executor 上无限阻塞 → ANR。Guava ListenableFuture 原生支持 get(timeout, unit)。
                // 详见 docs/COROUTINE_EXCEPTION_TIMEOUT.md「withTimeoutOrNull 补强」一节。
                val cameraProvider = future.get(CAMERA_PROVIDER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
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
                    val now = System.currentTimeMillis()
                    // 跳帧节流：跳绳约 1~3 次/秒，15fps 检测绰绰有余，却能省掉大量
                    // toBitmap/旋转/推理开销——这是摄像头页发热掉帧的主要来源
                    if (now - frameStamp[0] >= FRAME_INTERVAL_MS) {
                        frameStamp[0] = now
                        val bmp = proxy.toBitmap()
                        val rotated = rotateBitmap(bmp, proxy.imageInfo.rotationDegrees)
                        detector?.processFrame(rotated)
                        reportBrightness(rotated)
                    }
                    // 无论是否处理都必须 close，否则相机管线会被阻塞
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
        // 只有「没有进行中的会话」才开新会话：暂停挂起后（running=false 但 paused=true）重入
        // 不能重置，否则从首页浮条点「继续」进来会把辛苦跳的计数清零
        if (!vm.running.value && !vm.paused.value) vm.start(child?.id ?: 0L, CountMode.CAMERA)
    }

    // 后台获取模型路径（可能联网下载），加 15s 超时兜底：极端慢网/断网时 getModelPathSafely
    // 在 IO 调度器上超时返回 null，此处优雅降级（不绑相机、提示手动放模型），避免协程/线程被挂死。
    // 详见 docs/COROUTINE_EXCEPTION_TIMEOUT.md「withTimeoutOrNull 补强」一节。
    LaunchedEffect(Unit) {
        val path = runCatching { PoseModelProvider.getModelPathSafely(ctx) }.getOrNull()
        if (path != null) modelPath = path
        else modelMsg = "姿态模型加载失败或超时：请检查网络，或手动把 pose_landmarker.task 放入 app/src/main/assets/"
    }

    // 权限与模型都就绪后再绑定相机；缺权限时先弹我们的说明框（不直接硬弹系统框）
    LaunchedEffect(hasPermission, modelPath) {
        if (hasPermission && modelPath != null) bindCamera()
        else if (!hasPermission) promptCameraPermission()
    }

    // 看不到人时：用语音温柔引导小朋友站进画面；节流 8 秒，避免反复喊
    // 依赖低频的 hasPose 而非每帧骨架，避免每帧启停协程
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
    LaunchedEffect(hasPose, running, modelPath) {
        if (!hasPose && running && hasPermission && modelPath != null && poseError == null) {
            val now = System.currentTimeMillis()
            // 8s → 15s：原来站偏一点就被反复喊，太吵
            if (now - lastNoPoseSpeakTs > 15000) {
                lastNoPoseSpeakTs = now
                // 轮换取下一句，避免反复同一句；池子只有 4 句，简单取模即可
                lastNoPoseIdx = (lastNoPoseIdx + 1) % noPoseLines.size
                // 优先级最低：若此刻正在播别的（报数/鼓励），这句直接丢弃
                container.voiceSpeaker.speak(noPoseLines[lastNoPoseIdx], VoicePriority.HINT)
            }
        }
    }

    // 「找到你啦」欢迎：从「没人」变「有人」时给一次音效 + 语音，孩子立刻知道可以开跳（节流 20 秒）
    var lastFoundTs by remember { mutableStateOf(0L) }
    LaunchedEffect(hasPose) {
        if (hasPose && running) {
            val now = System.currentTimeMillis()
            if (now - lastFoundTs > 20000) {
                lastFoundTs = now
                container.soundPlayer.star()
                container.voiceSpeaker.speak("找到你啦，我们一起跳吧！", VoicePriority.HINT)
            }
        }
    }

    // ===== 返回拦截（2026-09-07）：跳了一些再点返回，先问「接着跳还是清零」 =====
    // 性能注意：页面不能直接读高频 count（每跳重组整页）。这里用 derivedStateOf 把
    // 「count > 0」派生成低频布尔——只在 0 ↔ 有数 翻转那一刻重组一次页面。
    val countState = vm.count.collectAsStateWithLifecycle()
    val hasAnyCount by remember { derivedStateOf { countState.value > 0 } }
    // 返回弹框期间会先挂起（数字定格），选择后再决定去留
    var showExitPrompt by remember { mutableStateOf(false) }
    // 结果弹框显示时不拦返回（成绩已保存，让它自然退出）；无会话/没跳过也不拦（挂起无害）
    BackHandler(enabled = (running || paused) && hasAnyCount && result == null) {
        vm.pause() // 先挂起并落盘快照，弹框里的个数不再变化，取消时 resume 即可无缝回到训练
        showExitPrompt = true
    }
    if (showExitPrompt) {
        // 弹框内才读 count：此时已 pause，计数冻结，读它不会引发高频重组
        val frozenCount by vm.count.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showExitPrompt = false; child?.id?.let { vm.resume(it) } },
            title = { Text("要休息一下吗？", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "这次已经跳了 $frozenCount 个。\n\n" +
                            "「下次接着跳」：暂停并保留计数，回首页点浮条就能继续；\n" +
                            "「清零退出」：这次的计数全部清空。"
                )
            },
            dismissButton = {
                TextButton(onClick = { showExitPrompt = false; child?.id?.let { vm.resume(it) } }) {
                    Text("继续跳")
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        showExitPrompt = false
                        vm.discard() // 清内存 + 清挂起快照 → 浮条不再显示
                        nav.popBackStack()
                    }) { Text("清零退出") }
                    TextButton(onClick = { showExitPrompt = false; nav.popBackStack() }) {
                        Text("下次接着跳", fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            detector?.stop()
            // 2026-09-05：离开本页从「结束保存」改为「自动暂停挂起」——
            // 会话保存在 Activity 级 VM 里，回首页后浮条可一键「继续跳绳」；
            // 正式落库只发生在训练页内点「结束并保存」（vm.stop()）。
            // pause() 内部有 running 判断：已结束/已暂停时是无害空操作。
            vm.pause()
        }
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

        // 实时骨架叠加层：让孩子在相机画面上看到自己的跳绳姿态（发光「影子伙伴」）。
        // 骨架在绘制阶段读取，每帧只重绘不重组；pulse 让每跳一下影子伙伴亮一下
        PoseOverlay(skeleton = skeleton, pulse = pulse, modifier = Modifier.fillMaxSize())

        // 上浮奖励层 / 积分弹幕：各自包一层子组件，内部 collect，避免计数变化重组整页
        RewardLayer(vm = vm, modifier = Modifier.fillMaxSize())
        DanmakuLayer(vm = vm, modifier = Modifier.fillMaxSize())

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

        // 顶部状态 + 中部主区域 + 底部按钮：合并为同一个 Column 统一排布。
        // 此前顶栏（药丸 + 暗光提示，top 16dp）与中部（top 64dp）是两套独立全屏 Box 各自 padding，
        // 人工错位 + 节奏卡 offset(-10dp) 容易在暗光提示出现时贴撞；合并后顶栏与中部同属一个流，
        // 由 SpaceBetween + 中部 weight(1f) 排版，间距一致、不再重叠。
        Column(
            Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部：模式 + 计时 + 积分（毛玻璃小药丸）+ 暗光提示。抽成子组件，让高频变化只重组它自己
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TopStatusPill(vm)
                // 画面太暗时提示开灯：暗光下姿态识别会明显变差，提前告诉家长比事后数不准更好
                if (dimLight) {
                    Text(
                        "💡 房间有点暗，开灯后跳跳星看得更准哦",
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.42f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            // 中部主区域：根据是否检测到人，二选一展示「引导卡」或「伙伴+计数 HUD」。
            // 关键点：两处共用「同一个」跳跳星；未检测到人时只显示引导卡，避免进入页面瞬间
            // 出现「两个头像 + 多段文字叠在中部」的混乱观感。
            // 中间区域占满剩余高度（weight 必须在 ColumnScope 内生效），并居中；检测到人→训练 HUD，否则→引导卡
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (hasPermission && modelPath != null && poseError == null && !hasPose) {
                    GuideCard()
                } else {
                    // HUD 内部自己 collect 高频 state，重组范围限制在组件内
                    TrainingHud(vm = vm, pulse = pulse)
                }
            }
            // 暂停时给一句安抚提示，让孩子知道「随时可以歇一下」（固定高度占位，避免中部跳动）
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                if (paused) {
                    Text(
                        "⏸ 已暂停，休息一下吧～",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 暂停 / 继续：中途想歇就歇，计时与计数一起停（见 TrainingViewModel.pause）
                CuteButton(
                    text = if (paused) "继续" else "暂停",
                    onClick = { if (paused) child?.id?.let { vm.resume(it) } else vm.pause() },
                    modifier = Modifier.weight(1f),
                    emoji = if (paused) "▶" else "⏸",
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
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
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
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

        // 2026-09-05：返回不再直接「结束保存」。2026-09-07 改为：跳了一些（count>0）时
        // 拦截返回弹「继续跳 / 下次接着跳 / 清零退出」三选框（见上方 BackHandler）；
        // 没跳过（count=0）仍自动挂起（无害空操作，浮条不会出现）。
    }
}

/** 按相机传感器旋转角度校正 Bitmap，保证输入 MediaPipe 的图像方向正确。 */
private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return src
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}

// ============================ 页面子组件（性能：收窄重组范围） ============================

/**
 * 顶部状态药丸：模式 + 本次积分 + 计时。
 *
 * 为什么单独抽组件：积分每跳变、计时每秒变。若在页面作用域读这两个 state，
 * 每次变化都会重组整页（含相机预览 AndroidView）→ 明显卡顿。
 * 放到这里自己 collect，重组范围就只有这颗药丸。
 */
@Composable
private fun TopStatusPill(vm: TrainingViewModel) {
    val points by vm.sessionPoints.collectAsStateWithLifecycle()
    val elapsed by vm.elapsed.collectAsStateWithLifecycle()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
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
            Text("⭐ $points", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SunYellow)
            Text(formatDuration(elapsed), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

/** 节奏档位：把「个/分」翻译成孩子看得懂的反馈。 */
private enum class Pace { NONE, SLOW, GOOD, FAST }

private fun paceOf(cadence: Int): Pace = when {
    cadence <= 0 -> Pace.NONE
    cadence < 50 -> Pace.SLOW
    cadence <= 150 -> Pace.GOOD
    else -> Pace.FAST
}

/**
 * 节奏带：显示当前节奏 + 一句「好不好」的评价（太慢/真棒/太快），
 * 卡片还会跟着节奏轻轻跳动，帮孩子卡拍子。
 */
@Composable
private fun PaceCard(cadence: Int) {
    val pace = paceOf(cadence)
    val hint = when (pace) {
        Pace.NONE -> "⏳ 准备开始" to Color.White.copy(alpha = 0.75f)
        Pace.SLOW -> "🐢 太慢啦，加油！" to SkyBlue
        Pace.GOOD -> "🔥 节奏真棒！" to Mint
        Pace.FAST -> "🐇 太快啦，慢一点" to SunYellow
    }
    // 跟着节奏快慢呼吸跳动（节奏越快跳得越快；没节奏时慢速呼吸）
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
        modifier = Modifier.offset(y = (-10).dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.38f))
    ) {
        Row(
            Modifier
                .scale(beat)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("节奏 $cadence/分", fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold)
            Text(hint.first, fontSize = 13.sp, color = hint.second, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 训练中 HUD：节奏带 + 跳跳星 + 大数字 + 连击 + 目标进度（含里程碑气泡）。
 *
 * count / cadence / streak 等高频 state 全在这里 collect，变化只重组本组件，不波及相机预览。
 * [pulse] 用于每跳一下点亮影子伙伴（写入后由 PoseOverlay 在绘制阶段消费，不触发重组）。
 */
@Composable
private fun TrainingHud(vm: TrainingViewModel, pulse: MutableState<Float>) {
    val count by vm.count.collectAsStateWithLifecycle()
    val cadence by vm.cadence.collectAsStateWithLifecycle()
    val mascot by vm.mascot.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()
    val dailyGoal by vm.dailyGoal.collectAsStateWithLifecycle()

    // 每跳一下给影子伙伴一个短脉冲：0.5 秒左右衰减回 0
    LaunchedEffect(count) {
        if (count > 0) {
            pulse.value = 1f
            while (pulse.value > 0f) {
                delay(50)
                pulse.value = (pulse.value - 0.1f).coerceAtLeast(0f)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        PaceCard(cadence = cadence)

        // 跳跳星半透明：与节奏卡/按钮的半透明风格统一，不挡镜头画面
        JumpMascot(mascot, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.alpha(0.5f))

        // 计数区：大数字 + 右上角连击徽章。
        // 连击 2026-09-07 由「单独占一行」改为叠在数字右上角的徽章——同屏纵向元素少一块。
        Box(contentAlignment = Alignment.Center) {
            // 柔光底改为静态：原为无限呼吸动画，与大数字弹跳语义重复，且常驻动画白耗电
            Box(
                Modifier
                    .size(150.dp, 120.dp)
                    .alpha(0.62f)
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
            // 连跳 5 个以上才亮出来，避免刚开跳就挂个徽章分散注意力
            if (streak >= 5) {
                Text(
                    "🔥 x$streak",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SunYellow,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.38f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        if (dailyGoal > 0) {
            GoalProgressWithMilestone(cur = count, goal = dailyGoal)
        }
    }
}

/**
 * 每日目标进度条 + 里程碑气泡：跨过 25/50/75/100% 时飘出一句鼓励，
 * 把「长目标」切成四段小成就感，孩子更容易坚持。
 */
@Composable
private fun GoalProgressWithMilestone(cur: Int, goal: Int) {
    val frac = (cur.toFloat() / goal).coerceIn(0f, 1f)
    var bubble by remember { mutableStateOf<String?>(null) }
    var reached by remember { mutableStateOf(0) }
    LaunchedEffect(cur) {
        val stage = (frac * 4).toInt().coerceAtMost(4)
        if (stage > reached) {
            reached = stage
            bubble = when (stage) {
                1 -> "🎯 四分之一达成！"
                2 -> "🎯 一半啦，真棒！"
                3 -> "🎯 就快到啦，加油！"
                else -> "🎉 今日目标达成！"
            }
            delay(2400)
            bubble = null
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (bubble != null) {
            val s by animateFloatAsState(if (bubble != null) 1f else 0.8f, tween(220), label = "milestone")
            Text(
                bubble!!,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SunYellow,
                modifier = Modifier.scale(s)
            )
        }
        Text("🎯 今日目标 $cur / $goal", fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
        Box(
            Modifier
                .width(180.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.28f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(listOf(PinkSecondary, Mint)))
            )
        }
    }
}

/** 未检测到人时的引导卡：邀请小朋友站进画面。 */
@Composable
private fun GuideCard() {
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
            JumpMascot(MascotState.IDLE, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.alpha(0.5f))
            Text("👀 站进来一点～", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "让跳跳星看到你，\n一起开心地跳！",
                fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 上浮奖励层：内部自己 collect count，避免计数变化重组整页。
 * every = 5：每满 5 个冒一批（原为每跳一个，画面太满）。
 */
@Composable
private fun RewardLayer(vm: TrainingViewModel, modifier: Modifier = Modifier) {
    val count by vm.count.collectAsStateWithLifecycle()
    FloatingRewards(count = count, every = 5, modifier = modifier)
}

/** 积分弹幕层：内部自己 collect 弹幕事件。 */
@Composable
private fun DanmakuLayer(vm: TrainingViewModel, modifier: Modifier = Modifier) {
    val events by vm.danmaku.collectAsStateWithLifecycle()
    DanmakuOverlay(events = events, onConsumed = vm::consumeDanmaku, modifier = modifier)
}

/** 分析帧最小间隔（ms）：约 15fps。跳绳 1~3 次/秒，精度足够，CPU 占用显著下降。 */
private const val FRAME_INTERVAL_MS = 66L

/** 平均亮度（0~255）低于此值判定为「画面太暗」。 */
private const val BRIGHTNESS_DIM = 42

// 相机 Provider 初始化超时：超过则放弃绑定并走 catch 日志（避免主线程无限挂起 → ANR）
private const val CAMERA_PROVIDER_TIMEOUT_MS = 10_000L
