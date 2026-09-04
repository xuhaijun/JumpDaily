package com.jumpdaily.jump.data.model

/** 计数方式：传感器启发式 / 摄像头姿态估计。 */
enum class CountMode(val key: String) {
    SENSOR("sensor"),
    CAMERA("camera");

    companion object {
        fun fromKey(key: String?) = if (key == CAMERA.key) CAMERA else SENSOR
    }
}
