package com.jumpdaily.jump.camera

/**
 * 一帧人体骨架：归一化关键点（x,y ∈ [0,1]，原点左上）与固定连线。
 *
 * 由 [PoseJumpDetector] 从 MediaPipe 结果中提取，供 UI 层直接绘制，
 * 避免界面层直接依赖 MediaPipe 类型。连线索引遵循 BlazePose 33 关键点的官方拓扑。
 */
data class PoseSkeleton(val points: List<Pair<Float, Float>>) {
    companion object {
        /**
         * BlazePose 33 关键点的标准骨架连线（索引对）。
         * 取躯干 / 双臂 / 双腿的主干，足以在屏幕上呈现孩子跳绳的姿态轮廓。
         * 索引对照：11/12 双肩、13/14 双肘、15/16 双腕、23/24 双髋、
         * 25/26 双膝、27/28 双踝、29~32 双脚。
         */
        val CONNECTIONS: List<Pair<Int, Int>> = listOf(
            11 to 12,                // 双肩
            11 to 13, 13 to 15,      // 左臂（肩-肘-腕）
            12 to 14, 14 to 16,      // 右臂
            11 to 23, 12 to 24,      // 肩-髋
            23 to 24,                // 双髋
            23 to 25, 25 to 27,      // 左腿（髋-膝-踝）
            24 to 26, 26 to 28,      // 右腿
            27 to 29, 27 to 31,      // 左脚（踝-跟-趾）
            28 to 30, 28 to 32,      // 右脚
            15 to 17, 15 to 19, 15 to 21, // 左手
            16 to 18, 16 to 20, 16 to 22  // 右手
        )
    }
}
