package com.jumpdaily.jump.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 全局尺寸 token：收敛此前散落在各页的 16/20/22/24/26/28 多种圆角与 16/20dp 边距，
 * 让页面间视觉语言一致。新写的页面统一引用这里，旧页面逐步替换。
 */
object Dimens {
    /** 页面外边距（统计 / 成就 / 训练页统一 20dp）。 */
    val screenPadding = 20.dp

    /** 标准卡片圆角（数据卡、进度卡、药丸等统一 20dp）。 */
    val cardRadius = 20.dp

    /** 大卡片圆角（首页 Hero、结果弹框等 24dp）。 */
    val cardRadiusLarge = 24.dp

    /** 卡片内默认纵向间距（14dp，与统计页/我的页对齐）。 */
    val itemGap = 14.dp

    /** 页头与下方内容的间距。 */
    val headerGap = 10.dp
}
