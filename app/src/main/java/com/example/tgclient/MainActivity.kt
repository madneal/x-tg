package com.example.tgclient

import android.os.Bundle
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
        setContent {
            val app = application as TelegramApplication
            ChatwaveApp(app.telegramAccountManager, app.appUpdateManager)
        }
    }
}
