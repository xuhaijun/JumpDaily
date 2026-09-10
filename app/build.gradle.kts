import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.jumpdaily.jump"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jumpdaily.jump"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 仅保留中文资源，剔除依赖库里的其它语言字符串，显著减小包体积
        resourceConfigurations += setOf("zh", "zh-rCN", "zh-rTW")
        // 渠道号默认值：命令行未指定 flavor 时（如直接 assembleDebug）兜底为 official，
        // 保证 Manifest 里 ${CHANNEL_VALUE} 占位符始终能解析
        manifestPlaceholders["CHANNEL_VALUE"] = "official"
    }

    // ===== 多渠道打包（2026-09-07）=====
    // 维度只有 channel 一个；每个渠道生成独立的 debug/release 变体（共 3×2=6 个组合）。
    // 渠道号通过 Manifest 占位符注入 <meta-data android:name="CHANNEL">，
    // 运行时读取：packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
    //            .metaData.getString("CHANNEL")；也读 BuildConfig.FLAVOR（= flavor 名）。
    // 目前应用无统计 SDK，渠道号先注入占位，接入统计后可直接上报。
    flavorDimensions += "channel"
    productFlavors {
        create("official") {
            dimension = "channel"
            manifestPlaceholders["CHANNEL_VALUE"] = "official"
            // 官方通用包：无任何商店专属定制
        }
        create("huawei") {
            dimension = "channel"
            manifestPlaceholders["CHANNEL_VALUE"] = "huawei"
            // 华为应用市场包：后续可在此加 HMS 专属依赖/配置（resValue 差异化应用名等）
        }
        create("xiaomi") {
            dimension = "channel"
            manifestPlaceholders["CHANNEL_VALUE"] = "xiaomi"
            // 小米应用商店包：后续可加小米推送等商店专属能力
        }
    }

    signingConfigs {
        create("release") {
            // 从项目根 local.properties 读取密钥（该文件已被 .gitignore 忽略）
            val props = Properties()
            val propFile = rootProject.file("local.properties")
            if (propFile.exists()) {
                props.load(propFile.inputStream())
                val storePath = props.getProperty("KEYSTORE_FILE")
                if (!storePath.isNullOrBlank()) {
                    storeFile = rootProject.file(storePath)
                    storePassword = props.getProperty("KEYSTORE_PASSWORD")
                    keyAlias = props.getProperty("KEY_ALIAS")
                    keyPassword = props.getProperty("KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        release {
            // 开启 R8 压缩 + 资源缩减，显著减小包体积（debug 保持关闭以便断点）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                // 注意：不能用 proguard-android-optimize.txt（含代码优化）。
                // R8 的优化（类合并 / allowaccessmodification 等）会破坏 MediaPipe/protobuf 的
                // 静态初始化块（<clinit>），导致 release 下抛 ExceptionInInitializerError、
                // 摄像头姿态引擎初始化失败（debug 不优化故正常）。
                // 改用 proguard-android.txt：仅做压缩+混淆，不优化代码，根治该 release-only Bug。
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
            // 生产包仅打包 ARM 架构原生库（MediaPipe/CameraX 仅 ARM 可用，x86 库对发布无意义）
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
            // 仅当 local.properties 配置了密钥时才应用签名（未配置则产出未签名包，不影响构建验证）
            val rel = signingConfigs.getByName("release")
            if (rel.storeFile != null) signingConfig = rel
        }
    }

    // ===== 产物命名（2026-09-07）=====
    // APK 文件名自动带上版本号 / 渠道 / 构建类型 / 日期，如：
    //   JumpDaily_v1.0.0_huawei_release_20260907.apk
    // 注意：日期只精确到「天」——同一天内文件名稳定，不破坏 Gradle 增量构建；
    // 若加时分秒，每次构建文件名都变，up-to-date 检查会永远失效。
    applicationVariants.all {
        val vName = versionName
        val flavor = flavorName
        val type = buildType.name
        val date = SimpleDateFormat("yyyyMMdd").format(Date())
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "JumpDaily_v${vName}_${flavor}_${type}_$date.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // 与 Kotlin 1.9.24 匹配的 Compose 编译器版本
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ===== 核心 =====
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.2")

    // ===== Jetpack Compose =====
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // 仅用到 Icons.Filled.Add / Delete（均在 core 中），用 core 替代 extended，省下数十 MB 图标资源
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ===== 本地数据库 Room =====
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ===== 偏好设置（当前孩子 / 设置项） =====
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ===== 每日提醒 WorkManager =====
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // ===== Lottie 动画（庆祝 / 吉祥物动态效果）=====
    implementation("com.airbnb.android:lottie-compose:6.5.2")

    // ===== 摄像头姿态估计（MediaPipe Tasks Vision + CameraX）=====
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // ===== 测试 =====
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // ===== 性能：云基线配置（Baseline Profile）支撑，Play 商店可下发，提升冷启动与滚动流畅度 =====
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    // ===== 桌面小组件（Jetpack Glance，纯本地、无外部账号）=====
    // glance 1.1.0 依赖 Compose UI 1.6.x，与本项目 compose-bom 2024.09.00（Compose 1.6.8）匹配；
    // 用 compose = true 的同一套 Compose 编译器（kotlinCompilerExtensionVersion 1.5.14）。
    implementation("androidx.glance:glance:1.1.0")
    implementation("androidx.glance:glance-appwidget:1.1.0")

    // ===== 单元测试：验证「async 异常延迟暴露」fail-fast（coroutineScope + 逐一 await）=====
    // kotlinx-coroutines-test 带来与主线一致的 kotlinx-coroutines-core，runTest 驱动结构化并发测试。
    // 版本对齐 kotlinx-coroutines-bom:1.7.3（镜像源未收录 1.7.6，且与主协程库保持一致避免重复类）
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
