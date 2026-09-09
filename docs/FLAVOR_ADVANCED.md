# 多渠道 Flavor 进阶：HMS / 小米推送差异化

> 基于 JumpDaily 真实 flavor 配置（`app/build.gradle.kts` 第 30~53 行）：当前只有 `official/huawei/xiaomi`
> 三个渠道，差异**仅**是 `manifestPlaceholders["CHANNEL_VALUE"]`，运行时读 `CHANNEL` meta-data。
> 本文给出「按渠道接入差异化能力（华为 Push Kit / 小米 Mi Push）」的工程化架构与代码骨架。
> ⚠️ 当前项目**未接入**任何推送 SDK，以下为落地蓝图。

---

## 1. 现状盘点

```kotlin
// app/build.gradle.kts
flavorDimensions += "channel"
productFlavors {
    create("official") { dimension = "channel"; manifestPlaceholders["CHANNEL_VALUE"] = "official" }
    create("huawei")  { dimension = "channel"; manifestPlaceholders["CHANNEL_VALUE"] = "huawei" }
    create("xiaomi")  { dimension = "channel"; manifestPlaceholders["CHANNEL_VALUE"] = "xiaomi" }
}
```

运行时读取：

```kotlin
// 任一 Activity/Application
val channel = packageManager
    .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
    .metaData.getString("CHANNEL")
// 或 BuildConfig.FLAVOR == "huawei" / "xiaomi" / "official"
```

产物命名已带渠道：`JumpDaily_v1.0.0_huawei_release_20260907.apk`（见 build.gradle `applicationVariants`）。

**缺口**：huawei/xiaomi 只是「渠道号不同」，没有任何商店/厂商专属能力。上架华为/小米应用市场后，
要想「杀掉 App 也能收到跳绳提醒」「接各市场统计」「用市场支付/评价」，必须按渠道差异化接入。

---

## 2. 按渠道「按需依赖」——核心机制

Gradle 支持 **flavor 专属 configuration**：`<flavorName>Implementation(...)`，
依赖只编译进对应渠道变体，其它渠道完全不含该 SDK（包体不膨胀、互不污染）。

```kotlin
// app/build.gradle.kts  dependencies 块
// 华为推送：仅 huawei 渠道
huaweiImplementation("com.huawei.hms:push:6.12.0.300")

// 小米推送：仅 xiaomi 渠道
xiaomiImplementation("com.xiaomi.mipush:mipush-core:4.9.0")
xiaomiImplementation("com.xiaomi.mipush:redmipush-core:4.9.0")
```

> 若 IDE 报「无法解析 huaweiImplementation」：flavor 名与 configuration 名必须**完全一致**（小写），
> 且 `flavorDimensions` 已声明。AGP 会自动为每个 flavor 生成 `<name>Implementation`。

---

## 3. 华为渠道：Push Kit 差异化

### 3.1 工程接入

```kotlin
// ① 项目根 build.gradle.kts 加华为 Agg 插件 classpath
buildscript {
    repositories { maven { url = "https://repo.huaweicloud.com/repository/maven/" } }
    dependencies {
        classpath("com.huawei.agconnect:agcp:1.9.1.301")
    }
}

// ② app/build.gradle.kts 顶部
apply(plugin = "com.huawei.agconnect")

// ③ huawei flavor 注入 AppID（占位符，来自 agconnect-services.json）
productFlavors {
    create("huawei") {
        dimension = "channel"
        manifestPlaceholders["CHANNEL_VALUE"] = "huawei"
        manifestPlaceholders["HMS_APP_ID"] = "appid=10xxxxxx"  // 华为开发者联盟分配
    }
}

// ④ 依赖
huaweiImplementation("com.huawei.hms:push:6.12.0.300")
```

`app/src/huawei/` 放华为专属源集（Agg 配置、初始化代码）。
`agconnect-services.json` 放 `app/src/huawei/` 或根（勿提交密钥，已 `.gitignore`）。

### 3.2 Manifest 差异化

华为推送需要一个 `HmsMessageService` 子类接收 token/消息，仅 huawei 渠道需要：

```xml
<!-- app/src/huawei/AndroidManifest.xml（渠道专属 Manifest，AGP 自动合并）-->
<service
    android:name=".push.HmsPushService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.huawei.push.action.MESSAGING_EVENT" />
    </intent-filter>
</service>
<meta-data
    android:name="com.huawei.hms.client.appid"
    android:value="${HMS_APP_ID}" />
```

### 3.3 初始化（代码层按渠道分支）

```kotlin
// app/src/main/.../push/ReminderPush.kt
interface ReminderPush {
    fun register(tokenCb: (String) -> Unit)
    fun scheduleDaily(hour: Int, minute: Int)   // 调厂商云端定时任务
}

// huawei 专属实现（放 app/src/huawei/...）
class HmsReminderPush : ReminderPush {
    override fun register(tokenCb: (String) -> Unit) {
        // HuaweiPush.getInstance().getToken / onNewToken
    }
    override fun scheduleDaily(hour: Int, minute: Int) { /* 调 Push Kit 云端定时推送 */ }
}

// 工厂按渠道返回
fun reminderPush(channel: String): ReminderPush? =
    when (channel) {
        "huawei" -> HmsReminderPush()
        "xiaomi" -> MiReminderPush()   // 小米渠道专属，放 app/src/xiaomi/
        else -> null                    // official：WorkManager 兜底
    }
```

`ReminderScheduler`（官方渠道）与 `ReminderPush`（厂商渠道）在 `AppContainer` 里按 `BuildConfig.FLAVOR` 二选一。

---

## 4. 小米渠道：Mi Push 差异化

```kotlin
// app/build.gradle.kts
xiaomiImplementation("com.xiaomi.mipush:mipush-core:4.9.0")
xiaomiImplementation("com.xiaomi.mipush:redmipush-core:4.9.0")

productFlavors {
    create("xiaomi") {
        dimension = "channel"
        manifestPlaceholders["CHANNEL_VALUE"] = "xiaomi"
        manifestPlaceholders["XM_APP_ID"] = "28823xxxxx"
        manifestPlaceholders["XM_APP_KEY"] = "5827xxxxx"
    }
}
```

```xml
<!-- app/src/xiaomi/AndroidManifest.xml -->
<service
    android:name=".push.MiPushReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="com.xiaomi.mipush.RECEIVE_MESSAGE" />
    </intent-filter>
</service>
<meta-data android:name="XM_CHANNEL" android:value="${XM_APP_ID}" />
```

小米 Mi Push 需在 `Application.attachBaseContext` 前 `MiPushClient.registerPush(appId, appKey)`（仅小米渠道调用）。

---

## 5. 资源 / 应用名差异化（轻量）

```kotlin
// 不同渠道换应用名（商店要求/品牌区分）
productFlavors {
    create("huawei") {
        dimension = "channel"
        manifestPlaceholders["CHANNEL_VALUE"] = "huawei"
        resValue("string", "app_name", "爱跳绳（华为版）")
    }
}
// 或放 app/src/huawei/res/values/strings.xml 覆盖 app_name —— 源集优先级高于 resValue
```

图标/开屏图同理：把资源放进 `app/src/<flavor>/res/...`，AGP 自动覆盖 `main` 源集。

---

## 6. 构建与验证

```bash
# 三个渠道各自打 release（既有 tools/build_apk.sh 已支持）
./tools/build_apk.sh huawei release
./tools/build_apk.sh xiaomi release
./tools/build_apk.sh official release

# 验证渠道号已注入
aapt dump badging app/build/outputs/apk/huawei/release/*.apk | grep -i channel
# 验证仅 huawei 包含 HMS 类（xiaomi/official 不应有）
unzip -l app/build/outputs/apk/huawei/release/*.apk | grep -i hms
```

`dist/2026xxxx/` 会同时产出三渠道包（脚本已归档）。

---

## 7. 坑位清单

| 坑 | 现象 | 解决 |
|----|------|------|
| flavor 名 vs configuration 名大小写 | `huaweiImplementation` 报「未解析」 | configuration 名 = flavor 名**小写原样** |
| 华为 `agconnect-services.json` 误提交 | 密钥泄露 | 加 `.gitignore`；**本项目 `.gitignore` 已忽略 `*.json`? 需确认**，建议显式忽略 `agconnect-services.json` |
| 小米 `MiPushClient.registerPush` 时机 | 收不到推送 | 必须在 `Application.attachBaseContext`/极早初始化，且仅 xiaomi 渠道调 |
| 渠道专属 Manifest 合并冲突 | `manifest-merger` 失败 | 渠道 Manifest 放 `app/src/<flavor>/`，不要动 `main` |
| 只测 official 漏掉厂商初始化 | 上架渠道崩溃 | 三渠道都跑一次 `./tools/build_apk.sh <ch> debug` 模拟器验收 |

---

## 8. 状态

- ✅ 三渠道 flavor + `CHANNEL` 占位符已落地，命名/归档已就绪。
- ⚠️ **待实施**：huawei/xiaomi 的厂商推送 SDK（按需依赖 + 源集隔离 + 初始化分支）。
- 与 [WORKMANAGER_REMINDER.md](./WORKMANAGER_REMINDER.md) 联动：厂商推送用于「杀 App 也提醒」的保活，
  WorkManager 作 official 渠道兜底。
