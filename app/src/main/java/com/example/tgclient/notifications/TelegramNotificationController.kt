package com.example.tgclient.notifications

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.tgclient.R

class TelegramNotificationController(context: Context, private val accountId: String) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        // The channel must exist before notify() is called on Android 8+.
        // Do not depend on TelegramNotificationService: notifications can be
        // posted while that optional service is not running.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    TelegramNotificationService.CHANNEL_ID,
                    "Messages",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

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
        // TDLib notification IDs are scoped to a client instance. Give each
        // account a distinct Android notification tag so equal IDs don't replace
        // one another when multiple Telegram accounts are signed in.
        manager.notify(accountId, notificationId, notification)
    }
}
