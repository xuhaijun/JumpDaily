# ===== 跳了么 · 发布版混淆/压缩规则 =====

# 基础可读性与堆栈定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature
-keepattributes *Annotation*,InnerClasses

# ===== Room =====
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep interface * extends androidx.room.Dao
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.**

# ===== MediaPipe Tasks Vision（摄像头姿态估计，含 native 方法）=====
-keep class com.google.mediapipe.** { *; }
# 关键：MediaPipe 的 createFromOptions 在构建计算图时会按原名调用 com.google.protobuf
# 的方法；若 protobuf 被 R8 混淆改名，release 下会抛 NoSuchMethodError / MediaPipeException，
# 表现为「暂不支持姿态识别」（debug 不混淆故正常）。必须连同成员一起保留，禁止重命名。
-keep class com.google.protobuf.** { *; }
-keep class com.google.devtools.** { *; }
-keepclassmembers class * { native <methods>; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn com.google.devtools.**
-dontwarn org.tensorflow.**
# MediaPipe 传递依赖 auto-value（编译期注解处理器），其引用的 javax.annotation.processing /
# javax.lang.model 在 Android 运行时不存在，仅为编译期使用，统一 dontwarn 即可
-dontwarn com.google.auto.value.**
-dontwarn autovalue.shaded.**
-dontwarn javax.annotation.processing.**
-dontwarn javax.lang.model.**

# ===== CameraX =====
-dontwarn androidx.camera.**
-keep class androidx.camera.** { *; }

# ===== Lottie =====
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ===== Kotlin / Coroutines =====
-keepclassmembers class * { *** Companion; }
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ===== DataStore / WorkManager / 其它 Jetpack =====
-dontwarn androidx.datastore.**
-dontwarn androidx.work.**

# ===== 应用自身：枚举与数据模型（被 DataStore / 导航引用，需保留）=====
-keep enum com.jumpdaily.jump.data.model.CountMode { *; }
-keep class com.jumpdaily.jump.data.model.** { *; }
-keep class com.jumpdaily.jump.ui.viewmodel.TrainingResult { *; }

# ===== Compose =====
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
