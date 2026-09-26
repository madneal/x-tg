package com.example.tgclient

import android.app.Application
import com.example.tgclient.data.TelegramAccountManager
import com.example.tgclient.update.AppUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TelegramApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var appUpdateManager: AppUpdateManager
        private set
    lateinit var telegramAccountManager: TelegramAccountManager
        private set

    override fun onCreate() {
        super.onCreate()
        appUpdateManager = AppUpdateManager(this, applicationScope)
        telegramAccountManager = TelegramAccountManager(this, applicationScope)
    }
}
