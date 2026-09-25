package com.example.tgclient

import android.app.Application
import com.example.tgclient.data.TelegramAccountManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TelegramApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var telegramAccountManager: TelegramAccountManager
        private set

    override fun onCreate() {
        super.onCreate()
        telegramAccountManager = TelegramAccountManager(this, applicationScope)
    }
}
