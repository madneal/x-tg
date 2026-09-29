package com.example.tgclient.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                authState == AuthState.Loading -> RestoringSessionScreen()
                authState !is AuthState.Ready -> AuthScreen(authState, viewModel, accountManager, activeAccountId)
                selectedChatId != null -> ConversationScreen(
                    chatId = selectedChatId!!,
                    viewModel = viewModel,
                    onBack = { selectedChatId = null },
                    onOpenChat = { selectedChatId = it },
                )
                showSettings -> SettingsScreen(viewModel, accountManager, updateManager, activeAccountId, onBack = { showSettings = false }, onAccountChanged = { showSettings = false })
                else -> ChatListScreen(viewModel, onSettingsClick = { showSettings = true }) { selectedChatId = it }
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
