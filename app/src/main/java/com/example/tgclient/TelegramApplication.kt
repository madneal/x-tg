package com.example.tgclient

import android.app.Application
import com.example.tgclient.data.TelegramAccountManager
import com.example.tgclient.update.AppUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

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
        applicationScope.launch(Dispatchers.IO) {
            val uploadCutoff = System.currentTimeMillis() - STALE_UPLOAD_RETENTION_MS
            cacheDir.listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.startsWith(UPLOAD_CACHE_PREFIX) && it.lastModified() < uploadCutoff }
                .forEach(File::delete)
        }
    }

    private companion object {
        const val UPLOAD_CACHE_PREFIX = "chatwave_upload_"
        const val STALE_UPLOAD_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}
