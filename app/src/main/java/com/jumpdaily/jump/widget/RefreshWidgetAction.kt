package com.jumpdaily.jump.widget

import android.content.Context
import androidx.glance.action.ActionParameters
import androidx.glance.GlanceId
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.GlanceAppWidget

/**
 * 小组件「刷新」按钮回调：主动触发一次 [JumpDailyWidget.update]。
 *
 * 由 Glance 在运行时按类名反射实例化（actionRunCallback<RefreshWidgetAction>），
 * 因此本类被 proguard-rules.pro 的 `-keep class com.jumpdaily.jump.widget.**` 保留原名。
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        JumpDailyWidget().update(context, glanceId)
    }
}
