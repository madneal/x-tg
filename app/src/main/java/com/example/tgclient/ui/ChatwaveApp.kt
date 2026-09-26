package com.example.tgclient.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tgclient.data.TelegramAccountManager
import com.example.tgclient.model.AuthState
import com.example.tgclient.update.AppUpdateManager

@Composable
fun ChatwaveApp(accountManager: TelegramAccountManager, updateManager: AppUpdateManager) {
    val activeAccountId by accountManager.activeAccountId.collectAsStateWithLifecycle()
    val repository = remember(activeAccountId) { accountManager.repository(activeAccountId) }
    val viewModel: ChatwaveViewModel = viewModel(key = "account:$activeAccountId", factory = ChatwaveViewModel.factory(repository))
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var selectedChatId by remember { mutableStateOf<Long?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    ChatwaveTheme(darkTheme = settings.darkTheme) {
        Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                authState !is AuthState.Ready -> AuthScreen(authState, viewModel, accountManager, activeAccountId)
                selectedChatId != null -> ConversationScreen(selectedChatId!!, viewModel) { selectedChatId = null }
                showSettings -> SettingsScreen(viewModel, accountManager, updateManager, activeAccountId, onBack = { showSettings = false }, onAccountChanged = { showSettings = false })
                else -> ChatListScreen(viewModel, onSettingsClick = { showSettings = true }) { selectedChatId = it }
            }
        }
    }
}
