package com.jumpdaily.jump.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.Button
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.jumpdaily.jump.JumpDailyApplication
import com.jumpdaily.jump.MainActivity
import com.jumpdaily.jump.data.model.WidgetSnapshot

/**
 * 爱跳绳桌面小组件（Jetpack Glance，纯本地）。
 *
 * 展示当前孩子的「今日个数 / 连续天数 / 累计总数」，并提供：
 * - 「去跳绳」按钮 → 打开 App 主界面（actionStartActivity，传 Intent）；
 * - 「刷新」按钮 → 主动触发一次 [GlanceAppWidget.update]（actionRunCallback）。
 *
 * 数据在 [provideGlance]（本身是挂起函数，处于 Glance 协程上下文）里通过 Application
 * 容器里的 [com.jumpdaily.jump.data.repository.WidgetRepository] 一次性拉取，
 * 整个链路纯本地、无网络、无外部账号。
 */
class JumpDailyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // provideGlance 本身是 suspend，可直接挂起拉取本地快照（无需 runBlocking）。
        // 容器为 lateinit：若小组件在 Application.onCreate 之前被 host 触发（极少见），
        // 访问会抛 UninitializedPropertyAccessException，这里兜底为空快照。
        val snapshot = try {
            val app = context.applicationContext as? JumpDailyApplication
            app?.container?.widgetRepository?.snapshot() ?: WidgetSnapshot.empty()
        } catch (_: Throwable) {
            WidgetSnapshot.empty()
        }
        // 打开主界面用显式 Intent（actionStartActivity 接收 Intent）
        val launchIntent = Intent(context, MainActivity::class.java)
        provideContent { WidgetContent(snapshot, launchIntent) }
    }

    @Composable
    private fun WidgetContent(s: WidgetSnapshot, launchIntent: Intent) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFFF7F7FBuL)))
                .padding(12.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // 顶部：孩子名 + 刷新
                Row(
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.Start
                ) {
                    Text(
                        text = s.childName,
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorProvider(Color(0xFF333333uL))
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Button(text = "刷新", onClick = actionRunCallback<RefreshWidgetAction>())
                }

                Spacer(modifier = GlanceModifier.height(10.dp))

                // 中部：今日个数（大号强调）
                Text(
                    text = "${s.todayCount}",
                    style = TextStyle(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color(0xFF2ECC71uL)),
                        textAlign = TextAlign.Center
                    )
                )
                Text(
                    text = "今日跳绳（个）",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = ColorProvider(Color(0xFF888888uL)),
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = GlanceModifier.height(10.dp))

                // 底部：连续天数 + 累计（同一行）
                Row(
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.Start
                ) {
                    Text(
                        text = "连续 ${s.streak} 天",
                        style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color(0xFF666666uL)))
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = "累计 ${s.totalCount}",
                        style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color(0xFF666666uL)))
                    )
                }
                Spacer(modifier = GlanceModifier.height(10.dp))
                Button(text = "去跳绳 🏃", onClick = actionStartActivity(launchIntent))
            }
        }
    }
}
