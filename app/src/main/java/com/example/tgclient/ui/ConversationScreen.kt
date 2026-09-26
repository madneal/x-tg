package com.example.tgclient.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tgclient.model.MediaType
import com.example.tgclient.model.MessageEntity
import com.example.tgclient.model.MessageSummary
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(chatId: Long, viewModel: ChatwaveViewModel, onBack: () -> Unit) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val groupActivity by viewModel.groupActivity.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var draft by remember { mutableStateOf(TextFieldValue()) }
    var showChatMenu by remember { mutableStateOf(false) }
    var showActivityDialog by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<MessageSummary?>(null) }
    var confirmDeleteMessage by remember { mutableStateOf<MessageSummary?>(null) }
    var replyTarget by remember { mutableStateOf<MessageSummary?>(null) }
    var editingTarget by remember { mutableStateOf<MessageSummary?>(null) }
    var pendingMedia by remember { mutableStateOf<PendingMedia?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkUrl by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val chatMessages = messages[chatId].orEmpty()
    val showJumpToLatest by remember(chatMessages.size) {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            chatMessages.isNotEmpty() && lastVisibleIndex < chatMessages.lastIndex
        }
    }
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
        if (path != null) {
            pendingMedia = PendingMedia(
                path = path,
                mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream",
            )
            // An attachment is composed before it is sent. Editing and attaching at the same
            // time is ambiguous, so switch back to the normal send composer.
            editingTarget = null
        }
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
                // Scaffold already accounts for the navigation bar. Applying both navigation
                // and IME padding here created a second bottom inset and a large blank area
                // above the keyboard on edge-to-edge Android windows.
                Column(modifier = Modifier.imePadding()) {
                    if (replyTarget != null || editingTarget != null) {
                        val target = editingTarget ?: replyTarget
                        Row(
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 16.dp, end = 8.dp, top = 7.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(if (editingTarget != null) "Editing message" else "Replying to ${target?.senderName.orEmpty()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(target?.text ?: "", maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { replyTarget = null; editingTarget = null; draft = TextFieldValue() }) { Text("Cancel") }
                        }
                    }
                    pendingMedia?.let { media ->
                        PendingMediaPreview(
                            media = media,
                            onRemove = { pendingMedia = null },
                        )
                    }
                    if (draft.text.isNotEmpty()) {
                        RichTextToolbar(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            onFormat = { type, style -> draft = draft.applyFormat(type, style) },
                            onLink = { showLinkDialog = true },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
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
                            if (settings.sendByEnter) sendComposer(
                                viewModel = viewModel,
                                chatId = chatId,
                                draft = draft,
                                pendingMedia = pendingMedia,
                                editingTarget = editingTarget,
                                replyTarget = replyTarget,
                                onSent = {
                                    draft = TextFieldValue()
                                    pendingMedia = null
                                    editingTarget = null
                                    replyTarget = null
                                },
                            )
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
                        onClick = {
                            sendComposer(
                                viewModel = viewModel,
                                chatId = chatId,
                                draft = draft,
                                pendingMedia = pendingMedia,
                                editingTarget = editingTarget,
                                replyTarget = replyTarget,
                                onSent = {
                                    draft = TextFieldValue()
                                    pendingMedia = null
                                    editingTarget = null
                                    replyTarget = null
                                },
                            )
                        },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    ) {
                        val canSend = draft.text.isNotBlank() || pendingMedia != null
                        Icon(if (canSend) Icons.Default.Send else Icons.Default.Mic, contentDescription = if (canSend) "Send" else "Voice message", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            if (chatMessages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No messages yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
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
                                onLongClick = { selectedMessage = it },
                            )
                        }
                    }
                }
            }
            if (showJumpToLatest) {
                SmallFloatingActionButton(
                    onClick = {
                        scrollScope.launch {
                            listState.animateScrollToItem(chatMessages.lastIndex)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Jump to latest message")
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

    selectedMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { selectedMessage = null },
            title = { Text("Message actions") },
            text = {
                Column {
                    Text(messageAnnotatedString(target), style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                    TextButton(
                        onClick = {
                            replyTarget = target
                            editingTarget = null
                            draft = TextFieldValue()
                            selectedMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Reply") }
                    if (target.canEdit) {
                        TextButton(
                            onClick = {
                                editingTarget = target
                                replyTarget = null
                                draft = target.toTextFieldValue()
                                selectedMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Edit") }
                    }
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(target.text))
                            selectedMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Copy text") }
                    if (target.canForward) {
                        TextButton(
                            onClick = {
                                viewModel.forwardMessageToSaved(chatId, target.id)
                                selectedMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Forward to Saved Messages") }
                    }
                    TextButton(
                        onClick = {
                            viewModel.toggleMessageReaction(chatId, target.id)
                            selectedMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("React 👍") }
                    TextButton(
                        onClick = {
                            viewModel.toggleMessagePinned(chatId, target.id, !target.isPinned)
                            selectedMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (target.isPinned) "Unpin message" else "Pin message") }
                    if (target.canDelete) {
                        TextButton(
                            onClick = {
                                confirmDeleteMessage = target
                                selectedMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Delete message", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedMessage = null }) { Text("Close") } },
        )
    }

    confirmDeleteMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDeleteMessage = null },
            title = { Text("Delete message?") },
            text = { Text("This message will be deleted for everyone when Telegram allows it.") },
            dismissButton = { TextButton(onClick = { confirmDeleteMessage = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(chatId, target.id)
                    confirmDeleteMessage = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Add link") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { linkUrl = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        draft = draft.applyLink(linkUrl.trim())
                        linkUrl = ""
                        showLinkDialog = false
                    },
                    enabled = linkUrl.trim().startsWith("http://") || linkUrl.trim().startsWith("https://"),
                ) { Text("Apply") }
            },
        )
    }
}

private fun android.net.Uri.copyToCache(context: android.content.Context): String? = runCatching {
    val target = File.createTempFile("chatwave_upload_", ".bin", context.cacheDir)
    context.contentResolver.openInputStream(this)?.use { input -> target.outputStream().use(input::copyTo) }
    target.absolutePath
}.getOrNull()

private data class PendingMedia(
    val path: String,
    val mimeType: String,
)

@Composable
private fun PendingMediaPreview(media: PendingMedia, onRemove: () -> Unit) {
    val bitmap = remember(media.path) {
        if (media.mimeType.startsWith("image/")) android.graphics.BitmapFactory.decodeFile(media.path) else null
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Selected photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(58.dp).clip(RoundedCornerShape(9.dp)),
                )
            } else {
                Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Ready to send", style = MaterialTheme.typography.labelLarge)
                Text(media.mimeType.substringAfter('/').uppercase(Locale.getDefault()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove attachment")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MessageBubble(
    message: MessageSummary,
    mergeWithPrevious: Boolean,
    mergeWithNext: Boolean,
    showSenderAvatar: Boolean,
    senderAvatarPath: String?,
    onDownloadFile: (Int) -> Unit,
    onLongClick: (MessageSummary) -> Unit,
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
            modifier = Modifier
                .widthIn(max = 330.dp)
                .clip(shape)
                // A normal tap opens the same actions as a long press. This is important for
                // photo messages, where users commonly tap the media itself to reply.
                .combinedClickable(onClick = { onLongClick(message) }, onLongClick = { onLongClick(message) })
                .background(bubbleColor)
                .padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
        ) {
            if (!outgoing && !mergeWithPrevious) Text(message.senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            message.replyToMessageId?.let {
                Text("↪ Reply", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 3.dp))
            }
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
                Text(messageAnnotatedString(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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
private fun RichTextToolbar(
    modifier: Modifier = Modifier,
    onFormat: (String, SpanStyle) -> Unit,
    onLink: () -> Unit,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        FormatButton("B", "textEntityTypeBold", SpanStyle(fontWeight = FontWeight.Bold), onFormat)
        FormatButton("I", "textEntityTypeItalic", SpanStyle(fontStyle = FontStyle.Italic), onFormat)
        FormatButton("U", "textEntityTypeUnderline", SpanStyle(textDecoration = TextDecoration.Underline), onFormat)
        FormatButton("S", "textEntityTypeStrikethrough", SpanStyle(textDecoration = TextDecoration.LineThrough), onFormat)
        FormatButton("Code", "textEntityTypeCode", SpanStyle(fontFamily = FontFamily.Monospace), onFormat)
        TextButton(onClick = onLink, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Link") }
    }
}

@Composable
private fun FormatButton(label: String, type: String, style: SpanStyle, onFormat: (String, SpanStyle) -> Unit) {
    TextButton(onClick = { onFormat(type, style) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
        Text(label, fontWeight = if (type == "textEntityTypeBold") FontWeight.Bold else FontWeight.Normal)
    }
}

private fun TextFieldValue.applyFormat(type: String, style: SpanStyle): TextFieldValue {
    val start = minOf(selection.start, selection.end)
    val end = maxOf(selection.start, selection.end)
    if (start == end) return this
    val builder = AnnotatedString.Builder()
    builder.append(annotatedString)
    builder.addStyle(style, start, end)
    builder.addStringAnnotation("chatwave_entity", type, start, end)
    return copy(annotatedString = builder.toAnnotatedString())
}

private fun TextFieldValue.applyLink(url: String): TextFieldValue {
    val start = minOf(selection.start, selection.end)
    val end = maxOf(selection.start, selection.end)
    if (start == end || url.isBlank()) return this
    val builder = AnnotatedString.Builder()
    builder.append(annotatedString)
    builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = Color(0xFF2A8BE7)), start, end)
    builder.addStringAnnotation("chatwave_entity", "textEntityTypeTextUrl|$url", start, end)
    return copy(annotatedString = builder.toAnnotatedString())
}

private fun sendDraft(
    viewModel: ChatwaveViewModel,
    chatId: Long,
    draft: TextFieldValue,
    editingTarget: MessageSummary?,
    replyTarget: MessageSummary?,
    onSent: () -> Unit,
) {
    if (draft.text.isBlank()) return
    val entities = draft.annotatedString.toMessageEntities()
    if (editingTarget != null) {
        viewModel.editMessage(chatId, editingTarget.id, draft.text, entities)
    } else {
        viewModel.sendMessage(chatId, draft.text, entities, replyTarget?.id)
    }
    onSent()
}

private fun sendComposer(
    viewModel: ChatwaveViewModel,
    chatId: Long,
    draft: TextFieldValue,
    pendingMedia: PendingMedia?,
    editingTarget: MessageSummary?,
    replyTarget: MessageSummary?,
    onSent: () -> Unit,
) {
    if (pendingMedia != null) {
        viewModel.sendLocalMedia(
            chatId = chatId,
            path = pendingMedia.path,
            mimeType = pendingMedia.mimeType,
            caption = draft.text,
            replyToMessageId = replyTarget?.id,
        )
        onSent()
    } else {
        sendDraft(viewModel, chatId, draft, editingTarget, replyTarget, onSent)
    }
}

private fun AnnotatedString.toMessageEntities(): List<MessageEntity> =
    getStringAnnotations("chatwave_entity", 0, length)
        .map { range ->
            val separator = range.item.indexOf('|')
            if (separator >= 0) {
                MessageEntity(range.start, range.end - range.start, range.item.substring(0, separator), range.item.substring(separator + 1))
            } else {
                MessageEntity(range.start, range.end - range.start, range.item)
            }
        }
        .distinctBy { Triple(it.offset, it.length, it.type) }

private fun MessageSummary.toTextFieldValue(): TextFieldValue {
    val builder = AnnotatedString.Builder()
    builder.append(text)
    entities.forEach { entity ->
        val start = entity.offset.coerceIn(0, text.length)
        val end = (entity.offset + entity.length).coerceIn(start, text.length)
        if (start >= end) return@forEach
        entity.style()?.let { builder.addStyle(it, start, end) }
        builder.addStringAnnotation("chatwave_entity", entity.annotationValue(), start, end)
    }
    return TextFieldValue(builder.toAnnotatedString(), TextRange(text.length))
}

private fun messageAnnotatedString(message: MessageSummary): AnnotatedString {
    val builder = AnnotatedString.Builder()
    builder.append(message.text)
    message.entities.forEach { entity ->
        val start = entity.offset.coerceIn(0, message.text.length)
        val end = (entity.offset + entity.length).coerceIn(start, message.text.length)
        if (start >= end) return@forEach
        entity.style()?.let { builder.addStyle(it, start, end) }
    }
    return builder.toAnnotatedString()
}

private fun MessageEntity.style(): SpanStyle? = when (type) {
    "textEntityTypeBold" -> SpanStyle(fontWeight = FontWeight.Bold)
    "textEntityTypeItalic" -> SpanStyle(fontStyle = FontStyle.Italic)
    "textEntityTypeUnderline" -> SpanStyle(textDecoration = TextDecoration.Underline)
    "textEntityTypeStrikethrough" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    "textEntityTypeCode", "textEntityTypePre", "textEntityTypePreCode" -> SpanStyle(fontFamily = FontFamily.Monospace)
    "textEntityTypeTextUrl", "textEntityTypeUrl", "textEntityTypeEmailAddress" -> SpanStyle(textDecoration = TextDecoration.Underline, color = Color(0xFF2A8BE7))
    else -> null
}

private fun MessageEntity.annotationValue(): String =
    if (type == "textEntityTypeTextUrl" && !argument.isNullOrBlank()) "$type|$argument" else type

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
