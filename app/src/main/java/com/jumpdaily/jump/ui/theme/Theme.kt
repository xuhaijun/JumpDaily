package com.jumpdaily.jump.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = Bubble,
    onPrimaryContainer = Ink,
    secondary = PinkSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3F3),
    tertiary = SunYellow,
    background = CreamBg,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1ECFF),
    onSurfaceVariant = InkSoft,
    outline = Color(0xFFE2D9F5),
    error = ErrorDeep
)

/**
 * 夜间模式：深空紫蓝底 + 浅薰衣草文字，品牌色（紫/粉/黄）原样复用，
 * 保证切换主题后整体观感一致、孩子一眼认得出「还是跳一跳」。
 */
private val DarkColors = darkColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = PinkSecondary,
    onSecondary = Color.White,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = Color(0xFFFFD6EF),
    tertiary = SunYellow,
    background = DarkBg,
    onBackground = DarkOnBg,
    surface = DarkSurface,
    onSurface = DarkOnBg,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ErrorDeep
)

private val JumpShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

private val JumpTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, color = InkSoft),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)
)

/**
 * 应用主题入口。
 * @param darkTheme 是否启用夜间模式；未显式指定时跟随系统设置。
 */
@Composable
fun JumpDailyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = JumpTypography,
        shapes = JumpShapes,
        content = content
    )
}
