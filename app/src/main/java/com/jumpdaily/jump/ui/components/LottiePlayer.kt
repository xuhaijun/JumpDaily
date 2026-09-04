package com.jumpdaily.jump.ui.components

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Lottie 动画封装：从 res/raw 加载 JSON 动画并循环播放。
 * 用于训练达标庆祝（celebration.json）等，替代纯 Canvas 手绘，动效更生动、可热替换素材。
 *
 * @param resId  res/raw 下的 Lottie JSON 资源 id
 * @param iterations 循环次数，默认无限循环
 */
@Composable
fun LottieRes(@RawRes resId: Int, modifier: Modifier = Modifier, iterations: Int = Int.MAX_VALUE) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
    LottieAnimation(composition = composition, iterations = iterations, modifier = modifier)
}
