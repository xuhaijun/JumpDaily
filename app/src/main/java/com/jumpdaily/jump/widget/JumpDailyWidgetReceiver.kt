package com.jumpdaily.jump.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 小组件广播接收器：系统通过它派发 APPWIDGET_UPDATE 等事件给 [JumpDailyWidget]。
 * 在 AndroidManifest 中以 <receiver> 注册，名称固定（不被混淆，系统按 manifest 名实例化）。
 */
class JumpDailyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = JumpDailyWidget()
}
