package com.example.tgclient.notifications

import android.app.NotificationManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NotificationChannelTest {
    @Test
    fun constructingNotificationControllerCreatesRequiredAndroidChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)

        TelegramNotificationController(context, "test-account")

        val channel = manager.getNotificationChannel(TelegramNotificationService.CHANNEL_ID)
        assertNotNull("Notification controller must create its channel before posting", channel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }
}
