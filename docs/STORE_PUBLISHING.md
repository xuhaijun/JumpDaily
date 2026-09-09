# 应用商店发布流程（爱跳绳 / JumpDaily）

> 基于 JumpDaily 真实工程事实编写：包名 `com.jumpdaily.jump`、三渠道 flavor（official/huawei/xiaomi）、
> 仅 ARM 64/32 位、仅中文本地化、R8 开启、儿童运动类（含摄像头）、内置隐私政策
> （`PrivacyScreen.kt`，联系邮箱 `xuhaijun5382@163.com`）。
> 本文是「上架行动手册」：资料清单 → 构建产物 → 各商店步骤 → 注意事项/坑 → 检查清单。
> 标注 ⚠️ 的为本项目**当前尚未完成**、上架前必须补齐的项。

---

## 0. 渠道与 flavor 对应关系（真实）

| flavor | 目标商店/渠道 | Manifest `CHANNEL` | 备注 |
|--------|--------------|--------------------|------|
| `official` | 官网/蒲公英等自有渠道、应用宝等通用市场 | `official` | 通用包 |
| `huawei` | 华为应用市场（AppGallery） | `huawei` | 后续可接入华为 Push Kit（见 FLAVOR_ADVANCED） |
| `xiaomi` | 小米应用商店（GetApps） | `xiaomi` | 后续可接入小米 Mi Push |

> 当前三渠道**仅 CHANNEL 占位不同**，APK 内容一致；接入厂商推送后差异化见 [FLAVOR_ADVANCED.md](./FLAVOR_ADVANCED.md)。

包体事实（`app/build.gradle.kts`）：
- `minSdk = 26`（Android 8.0）、`targetSdk = 34`、`versionCode = 1`、`versionName = "1.0.0"`
- `resourceConfigurations = ["zh","zh-rCN","zh-rTW"]`：仅中文本地化（各商店中文市场足够，但**Google Play 上架需补英文等**）
- `ndk.abiFilters = ["armeabi-v7a","arm64-v8a"]`：仅 ARM（**满足各商店 64 位强制要求** ✅）
- `isMinifyEnabled = true` + `proguard-android.txt`：R8 压缩（非 `-optimize.txt`，避免改坏 MediaPipe，见 R8_RELEASE）

---

## 1. 发布前资料准备（清单）

### 1.1 资质与备案（⚠️ 必办，多数可并行）

| 资料 | 说明 | 状态 |
|------|------|------|
| **App 备案号** | 2023-09 起国内上架**强制**要求：在工信部「ICP/IP 地址/域名备案管理系统」做 App 备案（依附于已有 ICP 备案域名）。上架时须填备案号。 | ⚠️ 待办 |
| **软著（计算机软件著作权）** | 华为/小米/应用宝等**普遍要求**软著或受理通知书；无软著很可能被驳回。 | ⚠️ 待办 |
| **隐私政策（独立网页版）** | 应用内已有（`PrivacyScreen.kt`）；但**商店要求可公网访问的 URL**。需把同正文发到官网/对象存储，拿到 `https://` 链接。 | ⚠️ 待办 |
| **开发者企业/个人账号** | 各商店开发者认证（身份证/营业执照 + 人脸识别/对公打款）。 | ⚠️ 待办 |
| **应用图标/截图/视频** | 见 1.2。 | 部分待补 |

### 1.2 素材清单

- **应用图标**：当前仅自适应 XML `ic_launcher.xml`（无 PNG 兜底）。商店后台多要 **512×512 PNG**、**前景/背景分离**图，需另出 PNG 上传（APK 内已用自适应图标，无需改代码）。
- **截图**：每商店 2~5 张（手机 1080×1920 或 9:16）。建议覆盖：首页、统计/成就、摄像头训练页、达标庆祝、设置/通知。
- **应用简介 + 一句话 slogan**：如「爱跳绳 · 陪孩子快乐运动」。
- **分类**：健康/运动（或教育-工具）。儿童类需勾选「面向儿童/家庭」。
- **关键词**：跳绳、儿童运动、体测、家庭、打卡。
- **演示视频（选填）**：应用宝/华为可用 30s 内 mp4。

### 1.3 隐私与权限说明（已具备，需核对）

应用权限（`AndroidManifest.xml`）：
- `CAMERA`（摄像头姿态计数，**required=false**，且为可选功能）→ 商店「权限索取说明」须写清「仅本地识别，不上传」。
- `POST_NOTIFICATIONS`（每日提醒）→ 需说明用途（见 [WORKMANAGER_REMINDER.md](./WORKMANAGER_REMINDER.md)）。
- `RECEIVE_BOOT_COMPLETED`（开机重排提醒）。
- `VIBRATE`（触感反馈）、`INTERNET`（首次下载姿态模型）。

> 隐私政策正文已覆盖：最小必要、儿童信息保护、监护人同意、权限说明、第三方 SDK 清单（明确**无广告/无统计上报 SDK**）。
> 这是儿童类应用过审的关键优势——务必保持「不上传任何数据」的真实承诺与文案一致。

---

## 2. 构建产物（一键出包）

使用 `tools/build_apk.sh`（或 `tools/build_apk.bat`）：

```bash
# 三渠道 release 齐出（产物落 dist/YYYYMMDD/）
./tools/build_apk.sh all release

# 或单渠道
./tools/build_apk.sh huawei release
./tools/build_apk.sh xiaomi release
```

产物命名（已自动带版本/渠道/日期）：
`JumpDaily_v1.0.0_huawei_release_20260907.apk` 等。

⚠️ **签名**：`build.gradle.kts` 从 `local.properties` 读 `KEYSTORE_FILE/PASSWORD/ALIAS`；
未配置则产出**未签名包**，商店拒收。上架前必须配置签名（见 4 节注意事项）。
⚠️ **未配置 AAB**：当前只打 APK。华为/小米/应用宝接受 APK；**Google Play 强制 AAB**——
如需上 Play，需加 `bundle` 变体（`android { bundle { ... } }` + `./gradlew :app:bundleRelease`）。

---

## 3. 各商店操作步骤

### 3.1 华为应用市场（AppGallery）

1. 注册「华为开发者联盟」账号 → 实名认证（企业需营业执照）。
2. 创建应用 → 选「应用（APK）」→ 填包名 `com.jumpdaily.jump`、分类「健康运动」、是否面向儿童=是。
3. 上传 `JumpDaily_v1.0.0_huawei_release.apk`；填 **App 备案号**、隐私政策 URL。
4. 上传图标/截图/简介；权限说明勾选并填用途。
5. 提交审核 → 华为安全扫描（**真机安装会弹风控框**，非商店审核，见下 4 节）。
6. 审核通过 → 上架。华为**不强制 64 位单独处理**（已含 arm64-v8a ✅）。

### 3.2 小米应用商店（GetApps）

1. 注册「小米开放平台」开发者 → 实名/企业认证。
2. 创建应用 → 包名一致 → 分类「运动健康」、适龄「儿童」。
3. 上传 `xiaomi` 渠道 APK；软著**必填**（小米对软著审核严）。
4. 隐私政策 URL + 备案号；权限说明。
5. 小米有「自启动/关联启动」限制 → 提醒到达率受控（见 4 节 + FLAVOR_ADVANCED 厂商推送方案）。
6. 提交审核 → 上架。

### 3.3 腾讯应用宝（MyApp）

1. 注册「腾讯开放平台」开发者 → 认证。
2. 创建应用 → 包名一致 → 分类「运动健康」。
3. 上传 `official` 渠道 APK（应用宝属通用市场）。
4. 软著、隐私政策 URL、备案号、截图。
5. 应用宝对「诱导分享/儿童」审核较细，文案避免绝对化承诺。

### 3.4 自有渠道（官网/蒲公英等）

- 用 `official` 渠道签名包，放官网下载页或蒲公英分发（无需软著/备案即可内测，但**正式分发仍建议备案**）。
- 适合灰度/测试用户，不等同商店上架。

### 3.5 Google Play（可选，需额外改造）

- **强制 AAB**（当前项目无）→ 需补 `bundle` 构建。
- **强制多语言**：当前仅中文 → 至少补英文 `strings.xml`，否则拒审/限地区。
- `targetSdk 34` 已满足 Play 要求 ✅；`CAMERA` 需声明 `foregroundServiceType` 等（本项目摄像头为预览非前台服务，OK）。
- 儿童应用走「适合家庭」项目（Designed for Families），政策更严（无广告/无定位）。

---

## 4. 注意事项与坑（重点）

1. **⚠️ App 备案号**：国内商店 2023-09 后强制，无号无法上架。先备案再提审。
2. **⚠️ 软著**：华为/小米/应用宝几乎必查，建议尽早申请（或先用受理通知书试探）。
3. **⚠️ 隐私政策独立 URL**：商店不认应用内页，必须公网可访问；正文与 `PrivacyScreen.kt` 保持一致，**不要写成「会上传」**。
4. **签名一致性**：同一包名在所有商店必须用**同一 keystore**，否则无法更新/认领。妥善备份 `jumpdaily.keystore` 与密码（**勿提交 git**，已被 `.gitignore` 忽略）。
5. **64 位**：已含 `arm64-v8a`，满足强制要求 ✅；勿误加 x86（无必要且可能触发某些商店警告）。
6. **儿童合规**：必须勾选「面向儿童/家庭」，并准备**监护人同意**说明；文案不得含诱导、绝对化疗效（如「包治」「必长高」）。
7. **摄像头权限**：声明 `required=false` + 用途说明「本地处理不上传」，避免被判定过度索权。
8. **无广告/无统计 SDK**：隐私政策已声明，务必保持——若日后接统计/推送，**先更新隐私政策与备案**。
9. **华为真机风控**：`adb install` 华为机会弹「风险提示/风险检查/锁屏密码」三连框（非商店审核），需手动点或脚本处理（见工作记忆 `huawei-adb-install-riskcontrol`）。
10. **版本号递增**：`versionCode` 必须随每次更新 +1，否则商店拒绝覆盖安装。
11. **提醒到达率**（联动）：WorkManager 在国产 ROM 后台限制下可能不响；huawei/xiaomi 渠道建议接入厂商推送保活（见 FLAVOR_ADVANCED）。
12. **资源裁剪**：仅中文已够国内；若上 Play 或海外，需放开 `resourceConfigurations` 并补翻译。

---

## 5. 发布前检查清单（Checklist）

```
[ ] App 备案号已取得并填入各商店
[ ] 软著/受理通知书已备
[ ] 隐私政策已发布到公网 URL，且与应用内正文一致（无「上传」措辞）
[ ] 开发者账号已认证（对应商店）
[ ] 签名 keystore 已配置、已备份，三渠道用同一证书
[ ] 三渠道 release APK 已用 build_apk.sh 产出（dist/）
[ ] 图标 512 PNG、截图、简介、分类、关键词已备
[ ] 包名 com.jumpdaily.jump 各渠道一致
[ ] versionCode 已较上一版 +1
[ ] 权限说明（CAMERA/NOTIFICATIONS/BOOT/VIBRATE/INTERNET）已填用途
[ ] 儿童/家庭分类已勾选，监护人同意说明已备
[ ] 隐私政策「无广告/无统计上报」承诺与代码一致
[ ] （Google Play）已补 AAB 构建 + 英文资源
[ ] 上架后真机验证：安装/摄像头计数/提醒/达标庆祝
```

---

## 6. 状态

- ✅ 工程侧已具备：三渠道 flavor、ARM 64/32 位、R8 压缩、内置完整隐私政策、权限最小化。
- ⚠️ 上架前必补：App 备案号、软著、隐私政策公网 URL、开发者账号认证、签名配置。
- 操作手册类文档；流程随各商店后台变更可能微调，以各平台最新《应用上架规范》为准。
- 联动文档：[FLAVOR_ADVANCED.md](./FLAVOR_ADVANCED.md)（厂商推送差异化）、[WORKMANAGER_REMINDER.md](./WORKMANAGER_REMINDER.md)（提醒到达率）、[R8_RELEASE.md](./R8_RELEASE.md)（签名/混淆）、[TECH_STACK.md](./TECH_STACK.md)（构建全貌）。
