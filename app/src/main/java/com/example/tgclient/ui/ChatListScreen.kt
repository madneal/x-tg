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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tgclient.model.ChatFolder
import com.example.tgclient.model.ChatSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(viewModel: ChatwaveViewModel, onSettingsClick: () -> Unit, onChatClick: (Long) -> Unit) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val folders by viewModel.chatFolders.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolderId by rememberSaveable { mutableStateOf("all") }
    var showFolderManager by remember { mutableStateOf(false) }
    var chatForLabels by remember { mutableStateOf<ChatSummary?>(null) }
    val selectedFolder = folders.firstOrNull { it.id == selectedFolderId } ?: folders.firstOrNull()
    val accountName = currentUser?.displayName?.takeIf { it.isNotBlank() && it != "User" }
        ?: currentUser?.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
        ?: "Telegram account"
    val accountSubtitle = currentUser?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Telegram account"
    val folderChats = remember(chats, selectedFolder) {
        if (selectedFolder == null || selectedFolder.isAllChats) chats else chats.filter { it.id in selectedFolder.chatIds }
    }
    val filteredChats = remember(folderChats, searchQuery) {
        if (searchQuery.isBlank()) folderChats else folderChats.filter {
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
                        Avatar(title = accountName, size = 38.dp, photoPath = currentUser?.avatarPath)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(accountName, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            Text(accountSubtitle, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) searchQuery = "" }) {
                        Icon(if (searchVisible) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                    IconButton(onClick = { showFolderManager = true }) { Icon(Icons.Default.Folder, contentDescription = "Manage chat folders") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                folders.forEach { folder ->
                    FilterChip(
                        selected = selectedFolder?.id == folder.id,
                        onClick = { selectedFolderId = folder.id },
                        label = { Text(folder.title) },
                        leadingIcon = if (folder.isAllChats) {
                            { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                    )
                }
            }
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
                    Text(
                        when {
                            chats.isEmpty() -> "Your chats will appear here"
                            folderChats.isEmpty() && selectedFolder?.isAllChats == false -> "No chats in this folder"
                            else -> "No chats found"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (folderChats.isEmpty() && selectedFolder?.isAllChats == false) {
                            "Use the chat menu to add conversations to this folder"
                        } else {
                            "Start a conversation to see it here"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredChats, key = { it.id }) { chat ->
                        val labels = folders.filter { !it.isAllChats && chat.id in it.chatIds }
                        ChatRow(chat, labels, onChatClick) { chatForLabels = chat }
                    }
                }
            }
        }
    }

    if (showFolderManager) {
        ChatFolderManagerDialog(
            folders = folders,
            onAdd = viewModel::createChatFolder,
            onRename = viewModel::renameChatFolder,
            onDelete = viewModel::deleteChatFolder,
            onDismiss = { showFolderManager = false },
        )
    }
    chatForLabels?.let { chat ->
        ChatFolderPickerDialog(
            chat = chat,
            folders = folders,
            onToggle = viewModel::setChatFolderMembership,
            onDismiss = { chatForLabels = null },
        )
    }
}

@Composable
private fun ChatRow(chat: ChatSummary, labels: List<ChatFolder>, onChatClick: (Long) -> Unit, onLabelsClick: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().clickable { onChatClick(chat.id) }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(chat.title, photoPath = chat.photoPath)
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
                if (labels.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp)) {
                        labels.take(3).forEach { label ->
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    label.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Chat actions")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Set labels") },
                        onClick = { menuExpanded = false; onLabelsClick() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatFolderManagerDialog(
    folders: List<ChatFolder>,
    onAdd: (String) -> ChatFolder?,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newFolderName by remember { mutableStateOf("") }
    var folderToRename by remember { mutableStateOf<ChatFolder?>(null) }
    var renameName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat folders") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Create labels for groups and conversations, then assign chats from each chat's menu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
                folders.filterNot { it.isAllChats }.forEach { folder ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder.title, style = MaterialTheme.typography.titleSmall)
                            Text("${folder.chatIds.size} chats", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            folderToRename = folder
                            renameName = folder.title
                        }) { Icon(Icons.Default.Edit, contentDescription = "Rename folder") }
                        IconButton(onClick = { onDelete(folder.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete folder", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (folders.none { !it.isAllChats }) {
                    Text("No custom folders yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("New folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (onAdd(newFolderName) != null) newFolderName = ""
                    },
                    enabled = newFolderName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Add folder") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )

    folderToRename?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text("Rename folder") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    singleLine = true,
                    label = { Text("Folder name") },
                )
            },
            dismissButton = { TextButton(onClick = { folderToRename = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(folder.id, renameName)
                        folderToRename = null
                    },
                    enabled = renameName.isNotBlank(),
                ) { Text("Save") }
            },
        )
    }
}

@Composable
private fun ChatFolderPickerDialog(
    chat: ChatSummary,
    folders: List<ChatFolder>,
    onToggle: (String, Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val customFolders = folders.filterNot { it.isAllChats }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Labels for ${chat.title}") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (customFolders.isEmpty()) {
                    Text("Create a folder first from the folder button in the chat list.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    customFolders.forEach { folder ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onToggle(folder.id, chat.id, chat.id !in folder.chatIds) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = chat.id in folder.chatIds,
                                onCheckedChange = { checked -> onToggle(folder.id, chat.id, checked) },
                            )
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(folder.title)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
fun Avatar(title: String, size: Dp = 54.dp, photoPath: String? = null) {
    val colors = listOf(Color(0xFF4F9BD5), Color(0xFF63B98D), Color(0xFFE5A84B), Color(0xFFB47BD5), Color(0xFFE27D73))
    val color = colors[title.hashCode().ushr(1) % colors.size]
    val initials = title.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "C" }
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        val bitmap = remember(photoPath) { photoPath?.let(android.graphics.BitmapFactory::decodeFile) }
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(initials, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun formatTime(epochSeconds: Int): String {
    if (epochSeconds <= 0) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds.toLong() * 1000))
}
