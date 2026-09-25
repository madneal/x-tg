package com.example.tgclient.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.tgclient.R

class TelegramNotificationController(context: Context) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(title: String, body: String, notificationId: Int, withSound: Boolean = true, withVibration: Boolean = true) {
        val notification = NotificationCompat.Builder(context, TelegramNotificationService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title.ifBlank { "New message" })
            .setContentText(body.ifBlank { "You have a new message" })
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(!withSound && !withVibration)
            .setVibrate(if (withVibration) longArrayOf(0, 180, 80, 180) else longArrayOf(0))
            .build()
        manager.notify(notificationId, notification)
    }
}
