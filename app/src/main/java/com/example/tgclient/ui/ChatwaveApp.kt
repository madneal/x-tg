package com.example.tgclient.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tgclient.data.TelegramAccountManager
import com.example.tgclient.model.AuthState
import com.example.tgclient.update.AppUpdateManager

@Composable
fun ChatwaveApp(accountManager: TelegramAccountManager, updateManager: AppUpdateManager) {
    val context = LocalContext.current
    val activeAccountId by accountManager.activeAccountId.collectAsStateWithLifecycle()
    val repository = remember(activeAccountId) { accountManager.repository(activeAccountId) }
    val viewModel: ChatwaveViewModel = viewModel(key = "account:$activeAccountId", factory = ChatwaveViewModel.factory(repository))
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember(activeAccountId) { SnackbarHostState() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var selectedChatId by remember { mutableStateOf<Long?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(authState, activeAccountId) {
        if (authState is AuthState.Ready && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val preferences = context.getSharedPreferences("chatwave_permission_state", android.content.Context.MODE_PRIVATE)
            val permissionMissing = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (permissionMissing && !preferences.getBoolean("notification_request_attempted", false)) {
                preferences.edit().putBoolean("notification_request_attempted", true).apply()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(operationError) {
        operationError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearOperationError()
        }
    }

    ChatwaveTheme(darkTheme = settings.darkTheme) {
        Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                when {
                    authState == AuthState.Loading -> RestoringSessionScreen()
                    authState !is AuthState.Ready -> AuthScreen(authState, viewModel, accountManager, activeAccountId)
                    selectedChatId != null -> ConversationScreen(
                        chatId = selectedChatId!!,
                        viewModel = viewModel,
                        onBack = { selectedChatId = null },
                        onOpenChat = { selectedChatId = it },
                    )
                    showSettings -> SettingsScreen(
                        viewModel,
                        accountManager,
                        updateManager,
                        activeAccountId,
                        onBack = { showSettings = false },
                        onAccountChanged = { showSettings = false },
                        onOpenSystemNotificationSettings = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            runCatching { context.startActivity(intent) }
                        },
                    )
                    else -> ChatListScreen(viewModel, onSettingsClick = { showSettings = true }) { selectedChatId = it }
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun RestoringSessionScreen() {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            "Restoring your Telegram session…",
            modifier = Modifier.padding(top = 18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
