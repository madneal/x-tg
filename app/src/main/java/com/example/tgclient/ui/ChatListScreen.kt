package com.example.tgclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tgclient.model.ChatSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(viewModel: ChatwaveViewModel, onSettingsClick: () -> Unit, onChatClick: (Long) -> Unit) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredChats = remember(chats, searchQuery) {
        if (searchQuery.isBlank()) chats else chats.filter {
            it.title.contains(searchQuery, ignoreCase = true) || it.subtitle.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.shadow(1.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(title = "Chatwave", size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Chatwave", fontWeight = FontWeight.SemiBold)
                            Text("Telegram messenger", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) searchQuery = "" }) {
                        Icon(if (searchVisible) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                    IconButton(onClick = { }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            if (searchVisible) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search chats") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
            if (filteredChats.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(title = "Chatwave", size = 72.dp)
                    Spacer(Modifier.size(16.dp))
                    Text(if (chats.isEmpty()) "Your chats will appear here" else "No chats found", style = MaterialTheme.typography.titleMedium)
                    Text("Start a conversation to see it here", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredChats, key = { it.id }) { chat -> ChatRow(chat, onChatClick) }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatSummary, onChatClick: (Long) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().clickable { onChatClick(chat.id) }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(chat.title)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chat.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(chat.lastMessage?.dateEpochSeconds?.let(::formatTime).orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chat.subtitle.ifBlank { "No messages yet" }, maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (chat.isPinned) Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    if (chat.unreadCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(chat.unreadCount.coerceAtMost(99).toString()) }
                    }
                }
            }
        }
    }
}

@Composable
fun Avatar(title: String, size: Dp = 54.dp) {
    val colors = listOf(Color(0xFF4F9BD5), Color(0xFF63B98D), Color(0xFFE5A84B), Color(0xFFB47BD5), Color(0xFFE27D73))
    val color = colors[title.hashCode().ushr(1) % colors.size]
    val initials = title.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "C" }
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

private fun formatTime(epochSeconds: Int): String {
    if (epochSeconds <= 0) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds.toLong() * 1000))
}
