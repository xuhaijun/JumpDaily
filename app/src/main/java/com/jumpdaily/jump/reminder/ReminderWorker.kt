package com.jumpdaily.jump.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.jumpdaily.jump.MainActivity
import com.jumpdaily.jump.R

/** 每日提醒 Worker：弹出一条可爱的通知，点按回到 App。 */
class ReminderWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {

    override fun doWork(): Result {
        showNotification()
        return Result.success()
    }

    private fun showNotification() {
        val channelId = "jump_reminder"
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // minSdk 26（Android 8.0）起通知渠道为强制 API，无需再判 SDK_INT
        val channel = NotificationChannel(
            channelId,
            "跳绳提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "每天提醒宝贝跳绳" }
        nm.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("爱跳绳 · 该运动啦！")
            .setContentText("今天还没跳绳哦，叫上小伙伴一起跳一跳吧 🤾‍♀️")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(1001, notification)
    }
}
