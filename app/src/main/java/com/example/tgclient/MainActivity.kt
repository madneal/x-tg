package com.example.tgclient

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.tgclient.ui.ChatwaveApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let the Compose root resize above the IME. The composer must not reserve
        // the keyboard height as internal padding, otherwise a large blank block
        // appears below the text field while typing.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
        setContent {
            val app = application as TelegramApplication
            ChatwaveApp(app.telegramAccountManager, app.appUpdateManager)
        }
    }

    private companion object { const val NOTIFICATION_PERMISSION_REQUEST = 42 }
}
