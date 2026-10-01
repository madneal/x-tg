package com.example.tgclient.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tgclient.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun UserProfileDialog(userId: Long, viewModel: ChatwaveViewModel, onDismiss: () -> Unit, onOpenChat: (Long) -> Unit) {
    var profile by remember(userId) { mutableStateOf<UserProfile?>(null) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    var retry by remember(userId) { mutableIntStateOf(0) }
    var loading by remember(userId) { mutableStateOf(true) }
    var opening by remember(userId) { mutableStateOf(false) }
    val users by viewModel.users.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val user = users[userId] ?: profile?.user
    LaunchedEffect(userId, retry) {
        loading = true
        error = null
        try {
            profile = viewModel.loadUserProfile(userId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Unable to load profile. Check your connection and retry."
        } finally {
            loading = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(user?.displayName ?: "User profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                user?.let {
                    Avatar(it.displayName, size = 80.dp, photoPath = it.avatarPath, photoRevision = it.avatarRevision)
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            it.username?.let { name -> Text("@$name") }
                            it.phoneNumber?.takeIf(String::isNotBlank)?.let { phone -> Text("+$phone") }
                            profile?.bio?.takeIf(String::isNotBlank)?.let { bio -> Text(bio) }
                        }
                    }
                }
                if (loading) CircularProgressIndicator(Modifier.size(24.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (error != null && !loading) TextButton(onClick = { retry++ }) { Text("Retry") }
                profile?.personalChatId?.let { channelId ->
                    OutlinedButton(enabled = !opening, onClick = {
                        opening = true
                        error = null
                        scope.launch {
                            try {
                                val chatId = viewModel.resolveProfileChannel(channelId)
                                if (chatId != null) onOpenChat(chatId)
                                else error = "This channel is unavailable or you do not have access."
                            } finally {
                                opening = false
                            }
                        }
                    }) { Text("View personal channel") }
                }
                Button(enabled = user != null && !opening, onClick = {
                    opening = true
                    error = null
                    scope.launch {
                        try {
                            onOpenChat(viewModel.createPrivateChat(userId))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "Unable to open conversation. Please retry."
                        } finally {
                            opening = false
                        }
                    }
                }) { Text(if (opening) "Opening…" else "Send message") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
