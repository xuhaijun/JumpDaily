# 权限与隐私合规（代码级）

> 配套 `docs/ARCHITECTURE.md` 第 12 节「工程化与质量」，并与 `docs/STORE_PUBLISHING.md`（商店合规）、`docs/DATASTORE_KOTLIN_SERIALIZATION.md`（本地存储）联动。
> 基于真实代码 `AndroidManifest.xml`、`ui/screens/CameraTrainingScreen.kt`、`ui/screens/SettingsScreen.kt`、`ui/screens/PrivacyScreen.kt`、`ui/screens/SplashScreen.kt`、`data/repository/PreferencesRepository.kt` 撰写。

---

## 0. 现状速览（真实）

| 维度 | 项目现状 |
|------|----------|
| 权限清单 | `CAMERA`、`POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED`、`VIBRATE`、`INTERNET`（5 项） |
| 摄像头声明 | `<uses-feature android:required="false">`（不强制相机硬件，无相机设备可装） |
| 运行时请求 | CAMERA：`CameraTrainingScreen` 三步法（check→shouldShow→自定义说明框/去设置框）；POST_NOTIFICATIONS：`SettingsScreen` 版本门控 + 回退关提醒 |
| 首启同意 | `privacy_accepted` 标志（`PreferencesRepository.kt:40`）；`SplashScreen.kt:49` 未同意不进应用 |
| 隐私政策 | `PrivacyScreen.kt` 完整 10 章，联系邮箱 `xuhaijun5382@163.com`，更新日 2026-09-02 |
| 儿童合规 | 隐私政策专设「六、儿童个人信息保护」；摄像头不上传、无广告/无统计 SDK 声明 |
| 数据归属 | 全部本地（Room + DataStore），不上传服务器（隐私政策第四节/八节） |

---

## 1. 权限清单与最小化原则

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />   <!-- :6 -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" /><!-- :7  开机重启后恢复提醒 -->
<uses-permission android:name="android.permission.VIBRATE" />               <!-- :9  计数/完成触感 -->
<uses-permission android:name="android.permission.CAMERA" />               <!-- :11 本地姿态识别 -->
<uses-feature android:name="android.hardware.camera" android:required="false" /> <!-- :13-15 不强制 -->
<uses-permission android:name="android.permission.INTERNET" />             <!-- :17 模型首下/厂商推送预留 -->
<uses-feature ... android:required="false" />                              <!-- :19-21 -->
```

**最小化原则落地**：
- **相机非必需功能**：`<uses-feature camera required=false>` + 摄像头仅在用户切「摄像头模式」才请求权限 → 无相机设备/拒绝授权的用户仍能以「传感器模式」跳绳。这是通过商店儿童类审核的关键。
- **不申请危险权限**：无 `READ_PHONE_STATE`、无定位、无通讯录、无存储（无任何对外读隐私的权限）。
- **INTERNET 说明**：仅用于首次下载姿态模型（之后走缓存）及未来厂商推送预留；项目**不收集/不上传任何用户数据**（隐私政策第八节明确）。若上架时声明 INTERNET 用途为「无数据收集」，须在商店「权限说明」里写清，避免被质疑。

---

## 2. 相机权限运行时请求（三步法）

`CameraTrainingScreen.kt:149-168` 实现了一个**合规且体验好**的权限流：

```kotlin
// 1) 注册 launcher
val permLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted -> askedCameraBefore = true; hasPermission = granted }

// 2) 决策下一步：已授权直接用；首次/可再问→自定义说明框；永久拒绝→去设置框
fun promptCameraPermission() {
    if (ContextCompat.checkSelfPermission(ctx, CAMERA) == PERMISSION_GRANTED) {
        hasPermission = true; return
    }
    val shouldShow = ActivityCompat.shouldShowRequestPermissionRationale(activity, CAMERA)
    if (askedCameraBefore && !shouldShow) showCamDenied = true   // 曾勾「不再询问」
    else showCamRationale = true                                  // 首次或可再问
}

// 3) 永久拒绝 → 引导去系统设置（不卡死）
fun openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", ctx.packageName, null))
    ctx.startActivity(intent)
}
```

**为什么不直接弹系统框？**
- 首次请求前应先用**自定义说明框**告诉家长「为什么需要摄像头、画面不离开本机」（满足儿童/国内合规「先告知」）。本项目用 `showCamRationale` 弹自绘说明，点「继续」才 `permLauncher.launch(CAMERA)`。
- `shouldShowRequestPermissionRationale` 返回 `false` 且已问过 → 说明用户勾了「不再询问」，此时再 `launch` 系统框**永不弹**，必须引导去设置（`openAppSettings`）。本项目对此有专门 `showCamDenied` 框，不会卡死在「点了没反应」。

> 这是 `android-permission-privacy-release` 技能要求的标准范式：先弹「为什么需要」→ 再弹系统框 → 永久拒绝引导设置。本项目已落实。

---

## 3. 通知权限运行时请求（版本门控 + 回退）

`SettingsScreen.kt:116-134` 在开启「每日提醒」时请求通知权限：

```kotlin
val notiLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
    if (granted) vm.setReminder(true, hour, minute)
    else {
        vm.setReminder(false, hour, minute)   // 未授权→回退关开关，避免「开着却不提醒」
        if (!shouldShowRequestPermissionRationale(activity, POST_NOTIFICATIONS))
            showNotiDenied = true              // 永久拒绝→引导设置
    }
}

fun onReminderToggle(on: Boolean) {
    if (!on) { vm.setReminder(false, ...); return }
    if (Build.VERSION.SDK_INT < TIRAMISU) { vm.setReminder(true, ...); return } // 13 以下无需权限
    if (checkSelfPermission(POST_NOTIFICATIONS) == GRANTED) { vm.setReminder(true, ...); return }
    showNotiRationale = true   // 先弹说明框再申请
}
```

要点：
- **Android 13（TIRAMISU）以下不请求**通知权限（系统无此权限），直接开提醒。
- **拒绝即关开关**：避免「开关开着却永远不提醒」的假成功体验。
- 与 WorkManager 提醒联动：`docs/WORKMANAGER_REMINDER.md` 讲调度，权限是它生效的前提。

---

## 4. 首启隐私同意框（未同意不进应用）

```kotlin
// PreferencesRepository.kt:40  标志位
val PRIVACY_ACCEPTED = booleanPreferencesKey("privacy_accepted")
val privacyAccepted: Flow<Boolean> = context.dataStore.data.map { it[PRIVACY_ACCEPTED] ?: false }

// SplashScreen.kt:49  首启消费
val accepted by prefs.privacyAccepted.collectAsStateWithLifecycle()
// accepted == false → 展示同意框（含隐私政策摘要 + 「查看完整政策」跳 PrivacyScreen）
// 用户点「同意」→ prefs.setPrivacyAccepted(true) → 进入主页
```

- 这是儿童/国内合规的**强制门禁**：未同意不进应用任何功能页。
- 同意框应展示「我们收集什么、摄像头不上传、如何撤回」要点，并提供「查看完整隐私政策」入口（跳 `PrivacyScreen`）。
- `privacy_accepted` 存 DataStore（本地），卸载即清。

---

## 5. 隐私政策正文（10 章，可上架正式版）

`PrivacyScreen.kt:28-71` 是**应用内隐私政策详情页**，十章结构（完全对齐国内监管与儿童合规要求）：

| 章 | 主题 | 关键承诺 |
|----|------|----------|
| 一 | 导言与适用范围 | 依据《个人信息保护法》《儿童个人信息网络保护规定》；监护人代儿童同意 |
| 二 | 收集的信息及用途 | 昵称/运动数据/传感器/摄像头/提醒偏好；**全部本地处理，不上传** |
| 三 | 权限调用说明 | 摄像头/通知/振动，均不强制、可关；拒绝仅影响对应功能 |
| 四 | 存储与安全措施 | 存本机（Room + DataStore），卸载即删 |
| 五 | 共享/转让/披露 | **不向任何第三方共享/转让/披露** |
| 六 | 儿童个人信息保护 | 最小必要；不收集身份证/定位/通讯录/人脸；撤回即删 |
| 七 | 用户权利 | 查阅/更正/删除/撤回同意 |
| 八 | 第三方 SDK 清单 | MediaPipe/CameraX/WorkManager/DataStore/Room 均设备端；**无广告、无统计 SDK** |
| 九 | 联系我们 | 邮箱 `xuhaijun5382@163.com` |
| 十 | 政策更新 | 重大变更重新征求同意 |

**上架必补项（⚠️ 见 `STORE_PUBLISHING.md`）**：应用内政策有了，但**商店要求「可公开访问的隐私政策 URL」**（在商店后台填写）。当前 `PrivacyScreen` 是正文，需另部署一份公网网页并把 URL 填进各商店后台，且两处文本保持一致。

---

## 6. 合规代码级自查清单

- [ ] 所有危险权限都「用时申请」，不在启动即弹（`CAMERA` 在训练页、`POST_NOTIFICATIONS` 在开提醒时）。
- [ ] 永久拒绝有「去设置」引导，不卡死（CameraTrainingScreen / SettingsScreen 均有）。
- [ ] 摄像头 `uses-feature required=false`，无相机设备可安装、可传感器模式使用。
- [ ] 首启隐私同意框未同意不进应用（SplashScreen 门禁）。
- [ ] 隐私政策含「儿童保护」「摄像头不上传」「无广告/无统计 SDK」声明（PrivacyScreen 六/八章）。
- [ ] 联系邮箱真实可用（运营者邮箱）。
- [ ] 商店后台填了**公网隐私政策 URL**（待补）。
- [ ] App 备案号 / 软著（国内上架强制，待补，见 STORE_PUBLISHING）。

---

## 7. 与商店审核的衔接

- **华为/小米/应用宝** 对儿童类应用重点查：隐私政策 URL、摄像头用途说明、是否收集位置/通讯录、是否有广告/SDK 上报。本项目「无广告、无统计、摄像头不上传、本地存储」的声明是**加分项**，但仍需把声明落到「商店后台填写的文案」与「应用内政策」一致。
- **Google Play**（如出海）：需在 Play Console 的「Data safety」表单如实勾选「不收集任何用户数据」；与应用内政策一致，否则下架风险。

> 详细步骤见 `docs/STORE_PUBLISHING.md` 各商店「隐私与权限」填写段。

---

**上手向导：** 加任何新权限 → 先加 `<uses-permission>`（危险权限加 `<uses-feature required=false>` 若适用）→ 在对应页面用 `rememberLauncherForActivityResult` + `shouldShowRequestPermissionRationale` 三步法 → 在 `PrivacyScreen` 第三/八章补说明 → 在商店后台同步文案。改隐私政策正文 → 改 `PrivacyScreen.kt` 的 `sections` + 同步公网 URL 版本 + 更新页首日期。
