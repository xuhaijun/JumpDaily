// 根构建脚本：仅声明插件版本，具体配置在 app 模块
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Room 注解处理：从 kapt 迁移到 KSP（Room 2.6.x 官方支持）。
    // kapt 在本机 JDK 21 + AGP 8.5 下 clean 构建会触发 Room 的 processingEnv 为 null 的 NPE，
    // 且此前一直靠缓存的 kapt 产物掩盖；KSP 不依赖 javac 内部 API，JDK 21 下稳定、构建更快。
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
