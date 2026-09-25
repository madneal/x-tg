package com.example.tgclient.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Keeps notification channel creation in one place. TDLib updates are owned by
 * the application repository; this service is the extension point for a
 * foreground/background synchronization policy.
 */
class TelegramNotificationService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    companion object { const val CHANNEL_ID = "chatwave_messages" }
}
