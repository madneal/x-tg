package com.example.tgclient.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tgclient.model.MediaType
import com.example.tgclient.model.MessageSummary
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(chatId: Long, viewModel: ChatwaveViewModel, onBack: () -> Unit) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val groupActivity by viewModel.groupActivity.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var showChatMenu by remember { mutableStateOf(false) }
    var showActivityDialog by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val chat = chats.firstOrNull { it.id == chatId }
    val title = chat?.title ?: "Chat"
    val chatTypeLabel = when {
        chat?.isChannel == true -> "Channel"
        chat?.isGroup == true -> "Group"
        chat?.isPrivate == true -> "Private chat"
        else -> "Telegram chat"
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val path = uri.copyToCache(context)
        if (path != null) viewModel.sendLocalMedia(chatId, path, context.contentResolver.getType(uri) ?: "application/octet-stream")
    }
    LaunchedEffect(chatId) { viewModel.openChat(chatId) }
    DisposableEffect(chatId) {
        onDispose { viewModel.closeChat(chatId) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(title, size = 38.dp, photoPath = chat?.photoPath)
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            Text(chatTypeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showChatMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Chat actions")
                        }
                        DropdownMenu(expanded = showChatMenu, onDismissRequest = { showChatMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (chat?.isPinned == true) "Unpin chat" else "Pin chat") },
                                onClick = {
                                    showChatMenu = false
                                    viewModel.toggleChatPinned(chatId, chat?.isPinned != true)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (chat?.isMarkedAsUnread == true) "Mark as read" else "Mark as unread") },
                                onClick = {
                                    showChatMenu = false
                                    viewModel.toggleChatMarkedAsUnread(chatId, chat?.isMarkedAsUnread != true)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear history") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                onClick = {
                                    showChatMenu = false
                                    confirmClearHistory = true
                                },
                            )
                            if (chat?.isGroup == true) {
                                DropdownMenuItem(
                                    text = { Text("Member activity (24h)") },
                                    onClick = {
                                        showChatMenu = false
                                        showActivityDialog = true
                                        viewModel.loadGroupActivityStats(chatId)
                                    },
                                )
                            }
                            if (chat?.isGroup == true || chat?.isChannel == true) {
                                DropdownMenuItem(
                                    text = { Text(if (chat.isChannel) "Leave channel" else "Leave group") },
                                    leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                                    onClick = {
                                        showChatMenu = false
                                        confirmLeave = true
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    IconButton(onClick = { mediaPicker.launch("*/*") }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach media", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Message") },
                        modifier = Modifier.weight(1f),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = if (settings.sendByEnter) ImeAction.Send else ImeAction.Default),
                        keyboardActions = KeyboardActions(onSend = {
                            if (settings.sendByEnter && draft.isNotBlank()) {
                                viewModel.sendMessage(chatId, draft.trim())
                                draft = ""
                            }
                        }),
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            IconButton(onClick = { }) { Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    Spacer(Modifier.size(6.dp))
                    IconButton(
                        onClick = { if (draft.isNotBlank()) { viewModel.sendMessage(chatId, draft.trim()); draft = "" } },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(if (draft.isBlank()) Icons.Default.Mic else Icons.Default.Send, contentDescription = if (draft.isBlank()) "Voice message" else "Send", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        },
    ) { padding ->
        val chatMessages = messages[chatId].orEmpty()
        if (chatMessages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Text("No messages yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                itemsIndexed(chatMessages, key = { _, message -> message.id }) { index, message ->
                    val previousMessage = chatMessages.getOrNull(index - 1)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (previousMessage == null || messageDateKey(previousMessage.dateEpochSeconds) != messageDateKey(message.dateEpochSeconds)) {
                            MessageDateDivider(message.dateEpochSeconds)
                        }
                        MessageBubble(
                            message = message,
                            mergeWithPrevious = false,
                            mergeWithNext = false,
                            showSenderAvatar = chat?.isGroup == true,
                            senderAvatarPath = message.senderUserId?.let { users[it]?.avatarPath },
                            onDownloadFile = viewModel::downloadFile,
                        )
                    }
                }
            }
        }
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear chat history?") },
            text = { Text("Messages will be removed from this chat on this device and from Telegram according to your account permissions.") },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") } },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearHistory = false
                        viewModel.deleteChatHistory(chatId)
                    },
                ) { Text("Clear") }
            },
        )
    }

    if (confirmLeave) {
        val leaveLabel = if (chat?.isChannel == true) "channel" else "group"
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave $leaveLabel?") },
            text = { Text("You will stop receiving messages from this $leaveLabel. You can join again with an invite link if one is available.") },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        viewModel.leaveChat(chatId)
                        onBack()
                    },
                ) { Text("Leave") }
            },
        )
    }

    if (showActivityDialog) {
        val activity = groupActivity[chatId]
        AlertDialog(
            onDismissRequest = { showActivityDialog = false },
            title = { Text("Top speakers · 24h") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        activity?.isLoading == true -> {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                        activity?.error != null -> Text(activity.error, color = MaterialTheme.colorScheme.error)
                        activity?.topUsers.isNullOrEmpty() -> Text(
                            "No user messages in the last 24 hours.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> activity?.topUsers.orEmpty().forEachIndexed { index, speaker ->
                            val user = users[speaker.userId]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}", modifier = Modifier.widthIn(min = 22.dp), fontWeight = FontWeight.Bold)
                                Avatar(
                                    title = user?.displayName ?: speaker.displayName,
                                    size = 36.dp,
                                    photoPath = user?.avatarPath,
                                )
                                Spacer(Modifier.size(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(user?.displayName ?: speaker.displayName, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                    Text("${speaker.messageCount} messages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showActivityDialog = false }) { Text("Close") } },
        )
    }
}

private fun android.net.Uri.copyToCache(context: android.content.Context): String? = runCatching {
    val target = File.createTempFile("chatwave_upload_", ".bin", context.cacheDir)
    context.contentResolver.openInputStream(this)?.use { input -> target.outputStream().use(input::copyTo) }
    target.absolutePath
}.getOrNull()

@Composable
private fun MessageBubble(
    message: MessageSummary,
    mergeWithPrevious: Boolean,
    mergeWithNext: Boolean,
    showSenderAvatar: Boolean,
    senderAvatarPath: String?,
    onDownloadFile: (Int) -> Unit,
) {
    val outgoing = message.isOutgoing
    val bubbleColor = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val shape = if (outgoing) {
        RoundedCornerShape(
            topStart = if (mergeWithPrevious) 5.dp else 18.dp,
            topEnd = if (mergeWithPrevious) 5.dp else 18.dp,
            bottomEnd = if (mergeWithNext) 5.dp else 4.dp,
            bottomStart = 18.dp,
        )
    } else {
        RoundedCornerShape(
            topStart = if (mergeWithPrevious) 5.dp else 18.dp,
            topEnd = if (mergeWithPrevious) 5.dp else 18.dp,
            bottomStart = if (mergeWithNext) 5.dp else 4.dp,
            bottomEnd = 18.dp,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!outgoing && showSenderAvatar) {
            Avatar(message.senderName, size = 32.dp, photoPath = senderAvatarPath)
            Spacer(Modifier.size(6.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 330.dp).clip(shape).background(bubbleColor).padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
        ) {
            if (!outgoing && !mergeWithPrevious) Text(message.senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            message.mediaType?.let { type ->
                when (type) {
                    MediaType.PHOTO -> if (message.mediaPath != null) {
                        MediaImage(message.mediaPath)
                    } else {
                        MediaAttachment("Photo", Icons.Default.InsertDriveFile, message.mediaFileId?.let { { onDownloadFile(it) } })
                    }
                    MediaType.VIDEO -> MediaAttachment("Video${message.mediaName?.let { " · $it" }.orEmpty()}", Icons.Default.PlayArrow, message.mediaFileId?.let { { onDownloadFile(it) } })
                    MediaType.DOCUMENT -> MediaAttachment(message.mediaName ?: "Document", Icons.Default.InsertDriveFile, message.mediaFileId?.let { { onDownloadFile(it) } })
                    MediaType.AUDIO -> MediaAttachment(message.mediaName ?: "Audio", Icons.Default.Audiotrack, message.mediaFileId?.let { { onDownloadFile(it) } })
                    MediaType.VOICE -> MediaAttachment("Voice message", Icons.Default.Audiotrack, message.mediaFileId?.let { { onDownloadFile(it) } })
                    MediaType.LOCATION -> MediaAttachment("Location", Icons.Default.LocationOn)
                }
            }
            val mediaPlaceholder = when (message.mediaType) {
                MediaType.PHOTO -> "Photo"
                MediaType.VIDEO -> "Video"
                MediaType.DOCUMENT -> message.mediaName ?: "Document"
                MediaType.AUDIO -> message.mediaName ?: "Audio"
                MediaType.VOICE -> "Voice message"
                MediaType.LOCATION -> "Location"
                null -> null
            }
            if (message.text.isNotBlank() && message.text != mediaPlaceholder) {
                Text(message.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(if (outgoing) Alignment.End else Alignment.Start)) {
                Text(formatMessageTime(message.dateEpochSeconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (outgoing) {
                    Spacer(Modifier.size(2.dp))
                    Icon(if (message.isRead) Icons.Default.DoneAll else Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
private fun MediaImage(path: String) {
    val bitmap = remember(path) { android.graphics.BitmapFactory.decodeFile(path) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().size(220.dp).clip(RoundedCornerShape(12.dp)),
        )
    } else {
        MediaAttachment("Photo", Icons.Default.InsertDriveFile)
    }
}

@Composable
private fun MediaAttachment(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onDownload: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        if (onDownload != null) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDownload) { Text("Download") }
        }
    }
}

private fun formatMessageTime(epochSeconds: Int): String {
    if (epochSeconds <= 0) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds.toLong() * 1000))
}

@Composable
private fun MessageDateDivider(epochSeconds: Int) {
    if (epochSeconds <= 0) return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            text = formatMessageDate(epochSeconds),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun messageDateKey(epochSeconds: Int): LocalDate? =
    if (epochSeconds <= 0) null else Instant.ofEpochSecond(epochSeconds.toLong()).atZone(ZoneId.systemDefault()).toLocalDate()

private fun formatMessageDate(epochSeconds: Int): String {
    val date = messageDateKey(epochSeconds) ?: return ""
    val today = LocalDate.now(ZoneId.systemDefault())
    val locale = Locale.getDefault()
    return when {
        date == today && locale.language.startsWith("zh") -> "今天"
        date == today -> "Today"
        date == today.minusDays(1) && locale.language.startsWith("zh") -> "昨天"
        date == today.minusDays(1) -> "Yesterday"
        locale.language.startsWith("zh") -> date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", locale))
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
}
