package com.example.tgclient.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import com.example.tgclient.BuildConfig
import com.example.tgclient.model.AppSettings
import com.example.tgclient.model.AuthAction
import com.example.tgclient.model.AuthState
import com.example.tgclient.model.ChatFolder
import com.example.tgclient.model.ChatHistoryState
import com.example.tgclient.model.ChatListLoadState
import com.example.tgclient.model.ChatSummary
import com.example.tgclient.model.GroupActivityState
import com.example.tgclient.model.GroupSpeakerStat
import com.example.tgclient.model.MediaType
import com.example.tgclient.model.MessageEntity
import com.example.tgclient.model.MessageSendState
import com.example.tgclient.model.MessageSummary
import com.example.tgclient.model.TelegramUser
import com.example.tgclient.model.TransferState
import com.example.tgclient.model.TdConnectionStatus
import com.example.tgclient.model.VerificationCodeState
import com.example.tgclient.notifications.TelegramNotificationController
import com.example.tgclient.security.DatabaseKeyStore
import com.example.tgclient.security.MessageRetentionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

class TelegramRepository(context: Context, scope: CoroutineScope, val accountId: String = DEFAULT_ACCOUNT_ID) {
    private val appContext = context.applicationContext
    private val repositoryJob = SupervisorJob(scope.coroutineContext[Job])
    private val repositoryScope = CoroutineScope(scope.coroutineContext + repositoryJob)
    private val updateScope = CoroutineScope(repositoryJob + Dispatchers.Default.limitedParallelism(1))
    private val chatPublication = CoalescedAction(updateScope, CHAT_PUBLICATION_COALESCE_MS, ::publishChats)
    private val retentionScope = CoroutineScope(repositoryJob + Dispatchers.IO)
    private val client: TdLibClient?
    private val notificationController = TelegramNotificationController(context, accountId)
    private val messageRetentionStore = MessageRetentionStore(context, accountId)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    private val _openedChats = MutableStateFlow<Map<Long, ChatSummary>>(emptyMap())
    val openedChats: StateFlow<Map<Long, ChatSummary>> = _openedChats.asStateFlow()
    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    private val _chatListLoadState = MutableStateFlow(ChatListLoadState())
    private val _messages = MutableStateFlow<Map<Long, List<MessageSummary>>>(emptyMap())
    private val _chatHistory = MutableStateFlow<Map<Long, ChatHistoryState>>(emptyMap())
    private val _users = MutableStateFlow<Map<Long, TelegramUser>>(emptyMap())
    private val _currentUser = MutableStateFlow<TelegramUser?>(null)
    private val _verification = MutableStateFlow(VerificationCodeState())
    private val _authAction = MutableStateFlow(AuthAction.None)
    private val _authError = MutableStateFlow<String?>(null)
    private val _operationError = MutableStateFlow<String?>(null)
    private val authActionGuard = AtomicReference(AuthAction.None)
    private val authStateVersion = MutableStateFlow(0L)
    private val lastStableAuthState = AtomicReference<AuthState>(AuthState.Loading)
    private val _groupActivity = MutableStateFlow<Map<Long, GroupActivityState>>(emptyMap())
    private val _transfers = MutableStateFlow<Map<Int, TransferState>>(emptyMap())
    private val _connectionStatus = MutableStateFlow(TdConnectionStatus.UNKNOWN)
    private val settingsPreferences = context.getSharedPreferences("chatwave_settings", Context.MODE_PRIVATE)
    private val folderPreferences = context.getSharedPreferences("chatwave_chat_folders_$accountId", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    private val _chatFolders = MutableStateFlow(loadChatFolders())
    private val chatCache = ConcurrentHashMap<Long, JSONObject>()
    private val filePaths = ConcurrentHashMap<Int, String>()
    private val requestedDownloads = ConcurrentHashMap.newKeySet<Int>()
    private val avatarRetryAttempts = ConcurrentHashMap<Int, Int>()
    private val pendingGallerySaves = ConcurrentHashMap<Int, GallerySaveRequest>()
    private val savedGalleryFiles = ConcurrentHashMap.newKeySet<Int>()
    private val requestedUsers = ConcurrentHashMap.newKeySet<Long>()
    private val localPinOverrides = ConcurrentHashMap<Long, Boolean>()
    private val historyCursors = ConcurrentHashMap<Long, HistoryCursor>()
    private val chatsLoadGuard = AtomicBoolean(false)
    private val allMainChatsLoaded = AtomicBoolean(false)
    private val retainedMessageWrites = Channel<RetentionEvent>(Channel.UNLIMITED)
    private lateinit var retentionWriterJob: Job

    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    val chats: StateFlow<List<ChatSummary>> = _chats.asStateFlow()
    val chatListLoadState: StateFlow<ChatListLoadState> = _chatListLoadState.asStateFlow()
    val messages: StateFlow<Map<Long, List<MessageSummary>>> = _messages.asStateFlow()
    val chatHistory: StateFlow<Map<Long, ChatHistoryState>> = _chatHistory.asStateFlow()
    val users: StateFlow<Map<Long, TelegramUser>> = _users.asStateFlow()
    val currentUser: StateFlow<TelegramUser?> = _currentUser.asStateFlow()
    val verification: StateFlow<VerificationCodeState> = _verification.asStateFlow()
    val authAction: StateFlow<AuthAction> = _authAction.asStateFlow()
    val authError: StateFlow<String?> = _authError.asStateFlow()
    val operationError: StateFlow<String?> = _operationError.asStateFlow()
    val groupActivity: StateFlow<Map<Long, GroupActivityState>> = _groupActivity.asStateFlow()
    val transfers: StateFlow<Map<Int, TransferState>> = _transfers.asStateFlow()
    val connectionStatus: StateFlow<TdConnectionStatus> = _connectionStatus.asStateFlow()
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val chatFolders: StateFlow<List<ChatFolder>> = _chatFolders.asStateFlow()

    init {
        retentionWriterJob = retentionScope.launch {
            for (firstEvent in retainedMessageWrites) {
                val batch = mutableListOf(firstEvent)
                delay(RETENTION_WRITE_BATCH_DELAY_MS)
                while (true) {
                    val next = retainedMessageWrites.tryReceive().getOrNull() ?: break
                    batch += next
                }
                try {
                    persistRetentionEvents(batch)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    reportOperationError(error)
                }
            }
        }
        updateScope.launch {
            delay(SESSION_RESTORE_TIMEOUT_MS)
            if (_authState.value == AuthState.Loading) {
                // A slow or unavailable connection must not look like a request to
                // log in again. Keep the encrypted TDLib database and report recovery.
                _authState.value = AuthState.Error("Telegram session is taking too long to restore. Check your connection and restart the app.")
            }
        }
        if (BuildConfig.TELEGRAM_API_ID == 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            client = null
            _authState.value = AuthState.MissingConfiguration
        } else {
            client = runCatching { TdLibClient(context, DatabaseKeyStore(context, accountId), accountId) }
                .onFailure { _authState.value = AuthState.Error("TDLib could not start") }
                .getOrNull()
            // TDLib can burst hundreds of updates after a reconnect. Mapping chats,
            // messages and files on Main made the UI freeze and let the update queue
            // grow faster than it could drain.
            client?.updates?.onEach(::handleUpdate)?.launchIn(updateScope)
            client?.start(
                onAuthorizationState = ::applyAuthorizationState,
                onFailure = { _authState.value = AuthState.Error("Telegram couldn't initialize. Check the connection, then retry session restore.") },
            )
        }
    }

    fun submitPhoneNumber(phoneNumber: String) = runAuthRequest(
        action = AuthAction.SubmitPhone,
        type = "setAuthenticationPhoneNumber",
        fields = JSONObject().apply {
            val request = phoneAuthenticationRequest(phoneNumber)
            put("phone_number", request.phoneNumber)
            put("settings", JSONObject.NULL)
        },
    )
    fun submitEmailAddress(emailAddress: String) = runAuthRequest(
        AuthAction.SubmitEmail,
        "setAuthenticationEmailAddress",
        JSONObject().put("email_address", emailAddress),
    )
    fun submitEmailCode(code: String) = runAuthRequest(
        AuthAction.SubmitEmailCode,
        "checkAuthenticationEmailCode",
        JSONObject().put("code", JSONObject().put("@type", "emailAddressAuthenticationCode").put("code", code)),
    )
    fun submitCode(code: String) = runAuthRequest(AuthAction.SubmitCode, "checkAuthenticationCode", JSONObject().put("code", code))
    fun resendCode() = runAuthRequest(AuthAction.ResendCode, "resendAuthenticationCode")
    /** Replaces the phone number while TDLib is still in the login flow. */
    fun changeAuthenticationPhoneNumber(phoneNumber: String) = submitPhoneNumber(phoneNumber)
    fun submitPassword(password: String) = runAuthRequest(AuthAction.SubmitPassword, "checkAuthenticationPassword", JSONObject().put("password", password))
    fun register(firstName: String, lastName: String) = runAuthRequest(
        AuthAction.Register,
        "registerUser",
        JSONObject().put("first_name", firstName).put("last_name", lastName),
    )

    /**
     * Sends an authorization request once and keeps the action locked until
     * TDLib publishes the next authorization state. This makes a slow network
     * response visible in the UI and prevents duplicate requests from rapid taps.
     */
    private fun runAuthRequest(action: AuthAction, type: String, fields: JSONObject = JSONObject()) {
        if (client == null || !authActionGuard.compareAndSet(AuthAction.None, action)) return
        _authError.value = null
        _authAction.value = action
        val requestStateVersion = authStateVersion.value
        repositoryScope.launch {
            try {
                client.request(type, fields)
                // Usually the state update arrives immediately after the OK
                // response. Keep the guard briefly if the update is delayed,
                // then allow retrying a genuinely lost request.
                withTimeoutOrNull(AUTH_ACTION_TIMEOUT_MS) {
                    authStateVersion.first { it > requestStateVersion }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: TelegramException) {
                showAuthRequestError(error.message)
            } catch (_: Exception) {
                showAuthRequestError("Unable to contact Telegram. Please try again.")
            } finally {
                authActionGuard.compareAndSet(action, AuthAction.None)
                _authAction.value = AuthAction.None
            }
        }
    }

    private fun showAuthRequestError(message: String) {
        // Keep TDLib's current authorization state visible. Replacing a wait-code state with
        // AuthState.Error caused the UI's generic retry button to call
        // setAuthenticationPhoneNumber again, which TDLib rejects as an unexpected request.
        if (lastStableAuthState.get() !is AuthState.Loading) {
            _authError.value = message
        } else {
            _authState.value = AuthState.Error(message)
        }
    }

    /** Data requests must never replace a valid authorization state with a login screen. */
    private fun reportOperationError(error: Throwable) {
        _operationError.value = error.safeMessage()
    }

    private fun launchOperation(operation: suspend () -> Unit) {
        repositoryScope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportOperationError(error)
            }
        }
    }

    fun clearOperationError() {
        _operationError.value = null
    }

    suspend fun loadUserProfile(userId: Long): com.example.tgclient.model.UserProfile {
        val activeClient = checkNotNull(client) { "Telegram is unavailable" }
        val user = mapUser(activeClient.request("getUser", JSONObject().put("user_id", userId)))
        _users.update { it + (userId to user) }
        val full = activeClient.request("getUserFullInfo", JSONObject().put("user_id", userId))
        return mapUserProfile(user, full)
    }

    suspend fun createPrivateChat(userId: Long): Long {
        val chat = checkNotNull(client).request("createPrivateChat", JSONObject().put("user_id", userId).put("force", false))
        chatCache[chat.optLong("id")] = chat
        publishChats()
        return chat.getLong("id")
    }

    suspend fun updateProfile(firstName: String, lastName: String, username: String) {
        try {
            client?.request("setName", JSONObject().put("first_name", firstName).put("last_name", lastName))
            client?.request("setUsername", JSONObject().put("username", username.removePrefix("@")))
            loadCurrentUser()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            reportOperationError(error)
        }
    }

    /** Resolves a public username or a Telegram numeric chat id for an in-app jump. */
    suspend fun resolveChatTarget(target: String): Long? {
        val cleanTarget = target.trim().removePrefix("@").takeIf { it.isNotBlank() } ?: return null
        val numericId = cleanTarget.toLongOrNull()
        val chat = try {
            if (numericId != null) {
                client?.request("getChat", JSONObject().put("chat_id", numericId))
            } else {
                client?.request("searchPublicChat", JSONObject().put("username", cleanTarget))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return null
        val chatId = chat.optLong("id").takeIf { it != 0L } ?: return null
        chatCache[chatId] = chat
        publishChats()
        return chatId
    }

    /** Removes this TDLib session from the device without deleting the Telegram account. */
    suspend fun removeFromDevice() {
        try {
            withTimeoutOrNull(ACCOUNT_REMOVAL_LOGOUT_TIMEOUT_MS) { client?.request("logOut") }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Device removal must continue even when Telegram can't confirm logout.
        } finally {
            close()
            retainedMessageWrites.cancel()
            retentionWriterJob.cancelAndJoin()
            withContext(Dispatchers.IO) { messageRetentionStore.clear() }
        }
    }

    fun close() {
        client?.close()
        retainedMessageWrites.close()
        retentionWriterJob.invokeOnCompletion {
            messageRetentionStore.close()
            repositoryJob.cancel()
        }
    }

    fun retrySessionRestore() {
        val activeClient = client ?: return
        _authError.value = null
        _authState.value = AuthState.Loading
        if (!activeClient.isStarted) {
            activeClient.start(
                onAuthorizationState = ::applyAuthorizationState,
                onFailure = { _authState.value = AuthState.Error("Telegram couldn't initialize. Check the connection, then retry session restore.") },
            )
            return
        }
        repositoryScope.launch {
            try {
                val authorizationState = activeClient.request("getAuthorizationState")
                applyAuthorizationState(authorizationState)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _authState.value = AuthState.Error(
                    error.safeMessage().ifBlank { "Unable to restore the Telegram session. Check your connection and retry." },
                )
            }
        }
    }

    fun logout() {
        _authState.value = AuthState.LoggingOut
        _currentUser.value = null
        client?.send("logOut")
    }

    fun createChatFolder(title: String): ChatFolder? {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return null
        val folder = ChatFolder("folder_${UUID.randomUUID().toString().replace("-", "").take(12)}", cleanTitle)
        val updated = _chatFolders.value + folder
        _chatFolders.value = updated
        persistChatFolders(updated)
        return folder
    }

    fun renameChatFolder(folderId: String, title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank() || folderId == ALL_CHATS_FOLDER_ID) return
        val updated = _chatFolders.value.map { folder ->
            if (folder.id == folderId) folder.copy(title = cleanTitle) else folder
        }
        _chatFolders.value = updated
        persistChatFolders(updated)
    }

    fun deleteChatFolder(folderId: String) {
        if (folderId == ALL_CHATS_FOLDER_ID) return
        val updated = _chatFolders.value.filterNot { it.id == folderId }
        _chatFolders.value = if (updated.any { it.isAllChats }) updated else listOf(ChatFolder(ALL_CHATS_FOLDER_ID, "All chats", isAllChats = true))
        persistChatFolders(_chatFolders.value)
    }

    fun setChatFolderMembership(folderId: String, chatId: Long, included: Boolean) {
        if (folderId == ALL_CHATS_FOLDER_ID) return
        val updated = _chatFolders.value.map { folder ->
            if (folder.id != folderId) return@map folder
            val chatIds = folder.chatIds.toMutableSet().apply {
                if (included) add(chatId) else remove(chatId)
            }
            folder.copy(chatIds = chatIds)
        }
        _chatFolders.value = updated
        persistChatFolders(updated)
    }

    fun leaveChat(chatId: Long) {
        launchOperation {
            client?.request("leaveChat", JSONObject().put("chat_id", chatId))
            chatCache.remove(chatId)
            historyCursors.remove(chatId)
            _messages.update { it - chatId }
            publishChats()
        }
    }

    fun deleteChatHistory(chatId: Long) {
        launchOperation {
            client?.request(
                "deleteChatHistory",
                JSONObject()
                    .put("chat_id", chatId)
                    .put("remove_from_chat_list", false)
                    .put("revoke", false),
            )
            historyCursors.remove(chatId)
            _messages.update { it + (chatId to emptyList()) }
        }
    }

    fun toggleChatPinned(chatId: Long, pinned: Boolean) {
        launchOperation {
            client?.request(
                "toggleChatIsPinned",
                JSONObject()
                    .put("chat_list", JSONObject().put("@type", "chatListMain"))
                    .put("chat_id", chatId)
                    .put("is_pinned", pinned),
            )
            localPinOverrides[chatId] = pinned
            publishChats()
        }
    }

    fun toggleChatMarkedAsUnread(chatId: Long, markedAsUnread: Boolean) {
        launchOperation {
            client?.request(
                "toggleChatIsMarkedAsUnread",
                JSONObject().put("chat_id", chatId).put("is_marked_as_unread", markedAsUnread),
            )
            chatCache[chatId]?.put("is_marked_as_unread", markedAsUnread)
            publishChats()
        }
    }

    suspend fun loadChats(limit: Int = 100) {
        if (allMainChatsLoaded.get()) {
            _chatListLoadState.update { it.copy(allChatsLoaded = true, isLoading = false) }
            return
        }
        if (!chatsLoadGuard.compareAndSet(false, true)) return
        _chatListLoadState.update { it.copy(isLoading = true, error = null) }
        try {
            client?.request("loadChats", JSONObject().put("chat_list", JSONObject().put("@type", "chatListMain")).put("limit", limit.coerceIn(1, 100)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: TelegramException) {
            if (error.code == 404) {
                allMainChatsLoaded.set(true)
                _chatListLoadState.update { it.copy(allChatsLoaded = true, error = null) }
            } else {
                _chatListLoadState.update { it.copy(error = error.safeMessage()) }
                reportOperationError(error)
            }
        } catch (error: Exception) {
            _chatListLoadState.update { it.copy(error = error.safeMessage()) }
            reportOperationError(error)
        } finally {
            chatsLoadGuard.set(false)
            _chatListLoadState.update { it.copy(isLoading = false) }
        }
    }

    suspend fun loadMessages(chatId: Long, limit: Int = 50) {
        val cursor = beginHistoryLoad(chatId, initial = true) ?: return
        updateChatHistoryState(chatId) { it.copy(isLoadingInitial = true, initialLoadError = null) }
        try {
            // Supergroups and channels only receive their full chat updates
            // after the chat has been opened. Open it before requesting the
            // history so group conversations are hydrated from TDLib rather
            // than relying on the cached last message.
            client?.request("openChat", JSONObject().put("chat_id", chatId))
            // Fetch the chat object before its history. This is important for
            // channels because TDLib describes them as chatTypeSupergroup with
            // is_channel=true, rather than using a separate chat type.
            val chat = client?.request("getChat", JSONObject().put("chat_id", chatId))
            if (chat != null && chat.optLong("id") == chatId) {
                chatCache[chatId] = chat
                _openedChats.update { it + (chatId to mapChat(chat)) }
                publishChats()
            }
            val isChannel = chat?.isChannelChat() ?: chatCache[chatId]?.isChannelChat() == true
            val batch = fetchHistory(chatId, isChannel, limit, cursor.oldestMessageId)
            val loaded = batch.messages
            if (_settings.value.retainDeletedMessages) withContext(Dispatchers.IO) { messageRetentionStore.saveAll(loaded) }
            if (_settings.value.saveToGallery) loaded.forEach(::maybeAutoSaveMedia)
            val retained = if (_settings.value.retainDeletedMessages) {
                withContext(Dispatchers.IO) { messageRetentionStore.loadChat(chatId, limit.coerceIn(1, RETAINED_INITIAL_PAGE_SIZE)) }
            } else {
                emptyList()
            }
            withContext(Dispatchers.Default) {
                _messages.updateTimeline(chatId) { mergeMessages(it, loaded + retained) }
            }
            val merged = _messages.value[chatId].orEmpty()
            val oldest = merged.firstOrNull()
            val hasRetainedOlder = oldest != null && _settings.value.retainDeletedMessages &&
                withContext(Dispatchers.IO) { messageRetentionStore.hasChatBefore(chatId, oldest) }
            val hasMore = batch.hasMore || hasRetainedOlder
            updateHistoryCursor(cursor, batch, initial = true, hasRetainedOlder = hasRetainedOlder)
            updateChatHistoryState(chatId) {
                it.copy(
                    hasLoadedInitial = true,
                    hasOlderMessages = hasMore,
                    initialLoadError = null,
                    olderLoadError = null,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            updateChatHistoryState(chatId) { it.copy(initialLoadError = error.safeMessage()) }
        } finally {
            finishHistoryLoad(cursor)
            updateChatHistoryState(chatId) { it.copy(isLoadingInitial = false) }
        }
    }

    /** Loads the next older page when the user reaches the top of a conversation. */
    suspend fun loadOlderMessages(chatId: Long, limit: Int = 50) {
        val cursor = beginHistoryLoad(chatId, initial = false) ?: return
        updateChatHistoryState(chatId) { it.copy(isLoadingOlder = true, olderLoadError = null) }
        try {
            val isChannel = chatCache[chatId]?.isChannelChat() == true
            val existing = _messages.value[chatId].orEmpty()
            val before = existing.firstOrNull()
            val batch = if (cursor.serverHasMore && cursor.oldestMessageId != 0L) {
                fetchHistory(chatId, isChannel, limit, cursor.oldestMessageId)
            } else {
                HistoryBatch(emptyList(), cursor.oldestMessageId, hasMore = false)
            }
            val loaded = batch.messages
            if (_settings.value.retainDeletedMessages) withContext(Dispatchers.IO) { messageRetentionStore.saveAll(loaded) }
            if (_settings.value.saveToGallery) loaded.forEach(::maybeAutoSaveMedia)
            val retained = if (_settings.value.retainDeletedMessages && before != null) {
                withContext(Dispatchers.IO) { messageRetentionStore.loadChatBefore(chatId, before, limit) }
            } else {
                emptyList()
            }
            withContext(Dispatchers.Default) {
                _messages.updateTimeline(chatId) { mergeMessages(it, loaded + retained) }
            }
            val merged = _messages.value[chatId].orEmpty()
            val oldest = merged.firstOrNull()
            val hasRetainedOlder = oldest != null && _settings.value.retainDeletedMessages &&
                withContext(Dispatchers.IO) { messageRetentionStore.hasChatBefore(chatId, oldest) }
            val hasMore = batch.hasMore || hasRetainedOlder
            updateHistoryCursor(cursor, batch, initial = false, hasRetainedOlder = hasRetainedOlder)
            updateChatHistoryState(chatId) { it.copy(hasOlderMessages = hasMore, olderLoadError = null) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            updateChatHistoryState(chatId) { it.copy(olderLoadError = error.safeMessage()) }
        } finally {
            finishHistoryLoad(cursor)
            updateChatHistoryState(chatId) { it.copy(isLoadingOlder = false) }
        }
    }

    private fun updateChatHistoryState(chatId: Long, transform: (ChatHistoryState) -> ChatHistoryState) {
        _chatHistory.update { states ->
            val current = states[chatId] ?: ChatHistoryState()
            states + (chatId to transform(current))
        }
    }

    fun closeChat(chatId: Long) {
        client?.send("closeChat", JSONObject().put("chat_id", chatId))
    }

    suspend fun loadGroupActivityStats(chatId: Long) {
        if (_groupActivity.value[chatId]?.isLoading == true) return
        _groupActivity.value = _groupActivity.value + (chatId to GroupActivityState(isLoading = true))
        try {
            withContext(Dispatchers.Default) {
                val cutoff = System.currentTimeMillis() / 1000L - ACTIVITY_WINDOW_SECONDS
                val counts = mutableMapOf<Long, Int>()
                var fromMessageId = 0L
                var reachedCutoff = false
                var pages = 0
                while (!reachedCutoff && pages < MAX_ACTIVITY_PAGES) {
                    val result = client?.request(
                        "getChatHistory",
                        JSONObject()
                            .put("chat_id", chatId)
                            .put("from_message_id", fromMessageId)
                            .put("offset", 0)
                            .put("limit", 100)
                            .put("only_local", false),
                    ) ?: break
                    val messages = result.optJSONArray("messages") ?: break
                    if (messages.length() == 0) break
                    for (index in 0 until messages.length()) {
                        val message = messages.optJSONObject(index) ?: continue
                        val messageDate = message.optLong("date")
                        if (messageDate in 1 until cutoff) {
                            reachedCutoff = true
                            break
                        }
                        if (messageDate < cutoff || !message.isCountableActivityMessage()) continue
                        val sender = message.optJSONObject("sender_id") ?: continue
                        if (sender.optString("@type") != "messageSenderUser") continue
                        val userId = sender.optLong("user_id").takeIf { it > 0L } ?: continue
                        requestUser(userId)
                        counts[userId] = (counts[userId] ?: 0) + 1
                    }
                    val lastMessageId = messages.optJSONObject(messages.length() - 1)?.optLong("id") ?: 0L
                    if (lastMessageId <= 0L || lastMessageId == fromMessageId) break
                    fromMessageId = lastMessageId
                    pages += 1
                }
                val topUsers = counts.entries
                    .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
                    .take(5)
                    .map { (userId, count) ->
                        GroupSpeakerStat(
                            userId = userId,
                            displayName = _users.value[userId]?.displayName ?: "User $userId",
                            messageCount = count,
                        )
                    }
                _groupActivity.value = _groupActivity.value + (chatId to GroupActivityState(topUsers = topUsers))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _groupActivity.value = _groupActivity.value + (chatId to GroupActivityState(error = error.safeMessage()))
        }
    }

    fun sendText(chatId: Long, text: String, replyToMessageId: Long? = null) {
        sendText(chatId, text, emptyList(), replyToMessageId)
    }

    fun sendText(chatId: Long, text: String, entities: List<MessageEntity>, replyToMessageId: Long? = null) {
        val content = JSONObject()
            .put("@type", "inputMessageText")
            .put("text", formattedText(text, entities))
            .put("link_preview_options", if (_settings.value.linkPreviews) JSONObject.NULL else JSONObject().put("@type", "linkPreviewOptions").put("is_disabled", true))
            .put("clear_draft", true)
        sendContent(chatId, content, replyToMessageId)
    }

    fun editMessageText(chatId: Long, messageId: Long, text: String, entities: List<MessageEntity>) {
        val content = JSONObject()
            .put("@type", "inputMessageText")
            .put("text", formattedText(text, entities))
            .put("link_preview_options", if (_settings.value.linkPreviews) JSONObject.NULL else JSONObject().put("@type", "linkPreviewOptions").put("is_disabled", true))
            .put("clear_draft", false)
        client?.send(
            "editMessageText",
            JSONObject()
                .put("chat_id", chatId)
                .put("message_id", messageId)
                .put("reply_markup", JSONObject.NULL)
                .put("input_message_content", content),
        )
    }

    fun deleteMessage(chatId: Long, messageId: Long) {
        client?.send(
            "deleteMessages",
            JSONObject()
                .put("chat_id", chatId)
                .put("message_ids", JSONArray().put(messageId))
                .put("revoke", true),
        )
    }

    fun forwardMessageToSaved(chatId: Long, messageId: Long) {
        val savedChatId = _currentUser.value?.id ?: return
        client?.send(
            "forwardMessages",
            JSONObject()
                .put("chat_id", savedChatId)
                .put("message_thread_id", 0)
                .put("from_chat_id", chatId)
                .put("message_ids", JSONArray().put(messageId))
                .put("options", JSONObject.NULL)
                .put("send_copy", false)
                .put("remove_caption", false),
        )
    }

    fun toggleMessagePinned(chatId: Long, messageId: Long, pinned: Boolean) {
        val type = if (pinned) "pinChatMessage" else "unpinChatMessage"
        val fields = JSONObject().put("chat_id", chatId).put("message_id", messageId)
        if (pinned) fields.put("disable_notification", false)
        client?.send(type, fields)
    }

    fun toggleMessageReaction(chatId: Long, messageId: Long, emoji: String = "👍") {
        client?.send(
            "setMessageReaction",
            JSONObject()
                .put("chat_id", chatId)
                .put("message_id", messageId)
                .put("reaction", JSONObject().put("@type", "reactionTypeEmoji").put("emoji", emoji))
                .put("is_big", false)
                .put("update_recent_reactions", true),
        )
    }

    fun retryMessage(chatId: Long, message: MessageSummary) {
        val now = (System.currentTimeMillis() / 1000L).toInt()
        if (message.chatId != chatId || message.sendState != MessageSendState.FAILED ||
            !message.canRetrySend || message.retrySendAtEpochSeconds > now
        ) return
        client?.send(
            "resendMessages",
            JSONObject()
                .put("chat_id", chatId)
                .put("message_ids", JSONArray().put(message.id))
                .put("quote", JSONObject.NULL)
                .put("paid_message_star_count", 0),
        )
    }

    fun sendLocalMedia(chatId: Long, path: String, mimeType: String, caption: String = "", replyToMessageId: Long? = null) {
        val inputFile = JSONObject().put("@type", "inputFileLocal").put("path", path)
        val formattedCaption = JSONObject().put("@type", "formattedText").put("text", caption).put("entities", JSONArray())
        val content = when {
            mimeType.startsWith("image/") -> JSONObject()
                .put("@type", "inputMessagePhoto")
                .put("photo", inputFile)
                .put("thumbnail", JSONObject.NULL)
                .put("added_sticker_file_ids", JSONArray())
                .put("width", 0)
                .put("height", 0)
                .put("caption", formattedCaption)
                .put("show_caption_above_media", false)
                .put("self_destruct_type", JSONObject.NULL)
            mimeType.startsWith("video/") -> JSONObject()
                .put("@type", "inputMessageVideo")
                .put("video", inputFile)
                .put("thumbnail", JSONObject.NULL)
                .put("cover", JSONObject.NULL)
                .put("start_timestamp", 0)
                .put("added_sticker_file_ids", JSONArray())
                .put("duration", 0)
                .put("width", 0)
                .put("height", 0)
                .put("supports_streaming", true)
                .put("caption", formattedCaption)
                .put("show_caption_above_media", false)
                .put("self_destruct_type", JSONObject.NULL)
                .put("has_spoiler", false)
            mimeType.startsWith("audio/") -> JSONObject()
                .put("@type", "inputMessageAudio")
                .put("audio", inputFile)
                .put("album_cover_thumbnail", JSONObject.NULL)
                .put("duration", 0)
                .put("title", "")
                .put("performer", "")
                .put("caption", formattedCaption)
            else -> JSONObject()
                .put("@type", "inputMessageDocument")
                .put("document", inputFile)
                .put("thumbnail", JSONObject.NULL)
                .put("disable_content_type_detection", false)
                .put("caption", formattedCaption)
        }
        sendContent(chatId, content, replyToMessageId)
    }

    /** Sends any supported content and keeps the reply metadata identical for text and media. */
    private fun sendContent(chatId: Long, content: JSONObject, replyToMessageId: Long?) {
        val replyTo = replyToMessageId
            ?.takeIf { it > 0L }
            ?.let {
                JSONObject()
                    .put("@type", "inputMessageReplyToMessage")
                    .put("message_id", it)
                    .put("quote", JSONObject.NULL)
                    .put("checklist_task_id", 0)
            }
            ?: JSONObject.NULL
        client?.send(
            "sendMessage",
            JSONObject()
                .put("chat_id", chatId)
                .put("topic_id", JSONObject.NULL)
                .put("reply_to", replyTo)
                .put("options", JSONObject.NULL)
                .put("reply_markup", JSONObject.NULL)
                .put("input_message_content", content),
        )
    }

    /** Starts a user-requested download even when automatic media downloads are disabled. */
    fun downloadFile(fileId: Int, priority: Int = 16) {
        if (fileId <= 0) return
        requestedDownloads.remove(fileId)
        requestFile(fileId, priority = priority, respectAutoDownload = false)
    }

    /** Saves a downloaded media file to Gallery or Downloads, requesting it first when needed. */
    fun saveMediaToGallery(fileId: Int, mediaType: MediaType, fileName: String? = null, localPath: String? = null) {
        if (fileId <= 0) return
        val request = GallerySaveRequest(mediaType, fileName)
        val path = (localPath ?: filePaths[fileId])?.let(::File)?.takeIf { it.isFile }?.absolutePath
        if (path != null) {
            repositoryScope.launch(Dispatchers.IO) { saveLocalFileToMediaStore(fileId, path, request) }
        } else {
            pendingGallerySaves[fileId] = request
            downloadFile(fileId)
        }
    }

    fun cancelDownload(fileId: Int) = client?.send("cancelDownloadFile", JSONObject().put("file_id", fileId).put("only_pending", false))

    suspend fun searchMessages(query: String, limit: Int = 50): List<MessageSummary> = try {
        val response = client?.request("searchMessages", JSONObject().put("query", query).put("offset", 0).put("limit", limit).put("min_date", 0).put("max_date", 0).put("chat_list", JSONObject.NULL))
        withContext(Dispatchers.Default) { response?.optJSONArray("messages").toMessageList() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        reportOperationError(error)
        emptyList()
    }

    suspend fun searchChatMessages(chatId: Long, query: String, limit: Int = 50): List<MessageSummary> = try {
        val response = client?.request("searchChatMessages", JSONObject().put("chat_id", chatId).put("query", query).put("sender_id", JSONObject.NULL).put("from_message_id", 0).put("offset", 0).put("limit", limit).put("filter", JSONObject.NULL))
        withContext(Dispatchers.Default) { response?.optJSONArray("messages").toMessageList() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        reportOperationError(error)
        emptyList()
    }

    fun clearRetainedMessages() {
        _messages.update { timelines -> timelines.mapValues { (_, messages) -> messages.filterNot { it.isDeleted } } }
        retainedMessageWrites.trySend(RetentionEvent.Clear)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val previous = _settings.value
        val updated = transform(previous)
        _settings.value = updated
        if (!updated.retainDeletedMessages) {
            _messages.update { timelines -> timelines.mapValues { (_, messages) -> messages.filterNot { it.isDeleted } } }
        }
        if (updated.saveToGallery && !previous.saveToGallery) {
            _messages.value.values.flatten().forEach(::maybeAutoSaveMedia)
        }
        settingsPreferences.edit()
            .putBoolean("darkTheme", updated.darkTheme)
            .putBoolean("dynamicColors", updated.dynamicColors)
            .putBoolean("notificationsEnabled", updated.notificationsEnabled)
            .putBoolean("showMessagePreview", updated.showMessagePreview)
            .putBoolean("inAppSounds", updated.inAppSounds)
            .putBoolean("vibration", updated.vibration)
            .putBoolean("autoDownloadMedia", updated.autoDownloadMedia)
            .putBoolean("saveToGallery", updated.saveToGallery)
            .putBoolean("useLessData", updated.useLessData)
            .putBoolean("sendByEnter", updated.sendByEnter)
            .putBoolean("linkPreviews", updated.linkPreviews)
            .putBoolean("reduceAnimations", updated.reduceAnimations)
            .putString("language", updated.language)
            .putBoolean("retainDeletedMessages", updated.retainDeletedMessages)
            .apply()
    }

    private fun loadSettings() = AppSettings(
        darkTheme = settingsPreferences.getBoolean("darkTheme", false),
        dynamicColors = settingsPreferences.getBoolean("dynamicColors", true),
        notificationsEnabled = settingsPreferences.getBoolean("notificationsEnabled", true),
        showMessagePreview = settingsPreferences.getBoolean("showMessagePreview", true),
        inAppSounds = settingsPreferences.getBoolean("inAppSounds", true),
        vibration = settingsPreferences.getBoolean("vibration", true),
        autoDownloadMedia = settingsPreferences.getBoolean("autoDownloadMedia", false),
        saveToGallery = settingsPreferences.getBoolean("saveToGallery", false),
        useLessData = settingsPreferences.getBoolean("useLessData", false),
        sendByEnter = settingsPreferences.getBoolean("sendByEnter", true),
        linkPreviews = settingsPreferences.getBoolean("linkPreviews", true),
        reduceAnimations = settingsPreferences.getBoolean("reduceAnimations", false),
        language = settingsPreferences.getString("language", "System default") ?: "System default",
        retainDeletedMessages = settingsPreferences.getBoolean("retainDeletedMessages", true),
    )

    private fun handleUpdate(update: JSONObject) {
        when (update.optString("@type")) {
            "updateAuthorizationState" -> applyAuthorizationState(update.optJSONObject("authorization_state"))
            "updateConnectionState" -> _connectionStatus.value = tdConnectionStatus(
                update.optJSONObject("state")?.optString("@type").orEmpty(),
            )
            "updateNewChat" -> update.optJSONObject("chat")?.let { chatCache[it.optLong("id")] = it; chatPublication.request() }
            "updateChatTitle" -> chatCache[update.optLong("chat_id")]?.put("title", update.optString("title"))?.also { chatPublication.request() }
            "updateChatPosition" -> updateChatPosition(update)
            "updateChatLastMessage" -> chatCache[update.optLong("chat_id")]?.let { chat ->
                chat.put("last_message", update.optJSONObject("last_message"))
                update.optJSONArray("positions")?.let { chat.put("positions", it) }
                chatPublication.request()
            }
            "updateChatDraftMessage" -> chatCache[update.optLong("chat_id")]?.let { chat ->
                chat.put("draft_message", update.optJSONObject("draft_message"))
                update.optJSONArray("positions")?.let { chat.put("positions", it) }
                chatPublication.request()
            }
            "updateChatReadInbox" -> chatCache[update.optLong("chat_id")]?.let { chat ->
                chat.put("unread_count", update.optInt("unread_count"))
                chat.put("last_read_inbox_message_id", update.optLong("last_read_inbox_message_id"))
                chatPublication.request()
            }
            "updateChatIsMarkedAsUnread" -> chatCache[update.optLong("chat_id")]?.put("is_marked_as_unread", update.optBoolean("is_marked_as_unread"))?.also { chatPublication.request() }
            "updateChatRemovedFromList" -> removeChatFromList(update)
            "updateNewMessage" -> update.optJSONObject("message")?.let(::publishMessage)
            "updateMessageSendSucceeded" -> updateMessageSendSucceeded(update)
            "updateMessageSendFailed" -> updateMessageSendFailed(update)
            "updateMessageContent" -> updateMessageContent(update)
            "updateDeleteMessages" -> deletePublishedMessages(update)
            "updateMessageIsPinned" -> updateMessagePinned(update)
            "updateFile" -> update.optJSONObject("file")?.let(::publishFile)
            "updateUser" -> update.optJSONObject("user")?.let(::updateUser)
            "updateNotification" -> publishNotification(update)
        }
    }

    private fun applyAuthorizationState(authorizationState: JSONObject?) {
        val stateType = authorizationState?.optString("@type")
        _verification.value = if (stateType == "authorizationStateWaitCode") {
            val codeInfo = authorizationState.optJSONObject("code_info")
            VerificationCodeState(
                timeoutSeconds = codeInfo?.optInt("timeout", 0)?.coerceAtLeast(0) ?: 0,
                nextType = codeInfo?.optJSONObject("next_type")?.optString("@type")?.ifBlank { null },
            )
        } else {
            VerificationCodeState()
        }
        authStateVersion.value += 1
        val mappedState = AuthStateMapper.fromJson(authorizationState)
        _authState.value = mappedState
        if (mappedState !is AuthState.Loading && mappedState !is AuthState.LoggingOut && mappedState !is AuthState.Error) {
            lastStableAuthState.set(mappedState)
        }
        _authError.value = null
        if (mappedState is AuthState.Ready) repositoryScope.launch { loadCurrentUser() }
    }

    private fun updateChatPosition(update: JSONObject) {
        val chat = chatCache[update.optLong("chat_id")] ?: return
        val position = update.optJSONObject("position") ?: return
        val list = position.optJSONObject("list") ?: return
        if (list.optString("@type") != "chatListMain") return

        val updatedPositions = JSONArray()
        val positions = chat.optJSONArray("positions")
        if (positions != null) {
            for (index in 0 until positions.length()) {
                val existing = positions.optJSONObject(index) ?: continue
                if (existing.optJSONObject("list")?.optString("@type") != "chatListMain") {
                    updatedPositions.put(existing)
                }
            }
        }
        if (position.optLong("order") != 0L) updatedPositions.put(position)
        chat.put("positions", updatedPositions)
        chatPublication.request()
    }

    private fun removeChatFromList(update: JSONObject) {
        val chat = chatCache[update.optLong("chat_id")] ?: return
        val list = update.optJSONObject("chat_list") ?: return
        if (list.optString("@type") != "chatListMain") return
        val positions = chat.optJSONArray("positions") ?: return
        val updatedPositions = JSONArray()
        for (index in 0 until positions.length()) {
            val position = positions.optJSONObject(index) ?: continue
            if (position.optJSONObject("list")?.optString("@type") != "chatListMain") {
                updatedPositions.put(position)
            }
        }
        chat.put("positions", updatedPositions)
        chatPublication.request()
    }

    private fun publishChats() {
        _openedChats.update { opened -> opened.mapValues { (id, previous) -> chatCache[id]?.let(::mapChat) ?: previous } }

        _chats.value = sortChatsByPosition(
            chats = chatCache.values,
            chatId = { it.optLong("id") },
            positionOrder = { chat ->
                val positions = chat.optJSONArray("positions") ?: return@sortChatsByPosition null
                (0 until positions.length())
                    .mapNotNull { positions.optJSONObject(it) }
                    .firstOrNull { it.optJSONObject("list")?.optString("@type") == "chatListMain" }
                    ?.optLong("order")
            },
        )
            .map(::mapChat)
    }

    private fun publishMessage(message: JSONObject) {
        val chatId = message.optLong("chat_id").takeIf { it != 0L } ?: return
        val isChannel = chatCache[chatId]?.isChannelChat() == true
        val mapped = mapMessage(message, chatId, isChannel) ?: return
        if (_settings.value.retainDeletedMessages) retainedMessageWrites.trySend(RetentionEvent.Save(mapped))
        maybeAutoSaveMedia(mapped)
        _messages.updateTimeline(chatId) { mergeMessages(it, listOf(mapped)) }
    }

    private fun updateMessageSendSucceeded(update: JSONObject) {
        val message = update.optJSONObject("message") ?: return
        val chatId = message.optLong("chat_id").takeIf { it != 0L } ?: return
        val isChannel = chatCache[chatId]?.isChannelChat() == true
        val mapped = mapMessage(message, chatId, isChannel)?.copy(
            sendState = null,
            sendError = null,
            canRetrySend = false,
            retrySendAtEpochSeconds = 0,
        ) ?: return
        val oldMessageId = update.optLong("old_message_id", mapped.id)
        _messages.updateTimeline(chatId) { current ->
            reconcileOutgoingMessage(current, oldMessageId, mapped, MessageSummary::id)
                .let(::sortMessagesChronologically)
        }
    }

    private fun updateMessageSendFailed(update: JSONObject) {
        val message = update.optJSONObject("message") ?: return
        val chatId = message.optLong("chat_id").takeIf { it != 0L } ?: return
        val isChannel = chatCache[chatId]?.isChannelChat() == true
        val sendingState = message.optJSONObject("sending_state")
        val failedState = sendingState?.takeIf { it.optString("@type") == "messageSendingStateFailed" }
        val now = (System.currentTimeMillis() / 1000L).toInt()
        val retryAfter = failedState?.optInt("retry_after", 0)?.coerceAtLeast(0) ?: 0
        val mapped = mapMessage(message, chatId, isChannel)?.copy(
            sendState = MessageSendState.FAILED,
            sendError = update.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() } ?: "Message could not be sent",
            canRetrySend = failedState?.optBoolean("can_retry") == true,
            retrySendAtEpochSeconds = if (retryAfter > 0) now + retryAfter else 0,
        ) ?: return
        val oldMessageId = update.optLong("old_message_id", mapped.id)
        _messages.updateTimeline(chatId) { current ->
            reconcileOutgoingMessage(current, oldMessageId, mapped, MessageSummary::id)
                .let(::sortMessagesChronologically)
        }
    }

    private fun updateMessageContent(update: JSONObject) {
        val chatId = update.optLong("chat_id")
        val messageId = update.optLong("message_id")
        val current = _messages.value[chatId].orEmpty()
        val content = update.optJSONObject("new_content") ?: return
        val type = content.optString("@type")
        val media = mediaInfo(content, type)
        val entities = parseEntities(content.optJSONObject("text") ?: content.optJSONObject("caption"))
        // Photos are small, frequently viewed conversation content and should
        // be available without an extra tap. Larger media still follows the
        // user's automatic-download setting to avoid filling the TDLib cache.
        media?.fileId?.let { fileId ->
            requestFile(fileId, respectAutoDownload = media.type != MediaType.PHOTO)
        }
        val replacement = current.firstOrNull { it.id == messageId }?.copy(
            text = contentText(content).ifBlank { mediaLabel(type) },
            mediaType = media?.type ?: mediaType(type),
            mediaFileId = media?.fileId,
            mediaPath = media?.fileId?.let(filePaths::get),
            mediaFullFileId = media?.fullFileId,
            mediaFullPath = media?.fullFileId?.let(filePaths::get),
            mediaName = media?.name,
            entities = entities,
        ) ?: return
        if (_settings.value.retainDeletedMessages) retainedMessageWrites.trySend(RetentionEvent.Save(replacement))
        maybeAutoSaveMedia(replacement)
        _messages.updateTimeline(chatId) { latest -> latest.map { if (it.id == messageId) replacement else it } }
    }

    private fun deletePublishedMessages(update: JSONObject) {
        // Cache eviction is not a server-side deletion. Removing these from
        // the visible timeline makes older group history appear to disappear.
        if (update.optBoolean("from_cache")) return
        val chatId = update.optLong("chat_id")
        val messageIds = update.optJSONArray("message_ids") ?: return
        val ids = (0 until messageIds.length()).map { messageIds.optLong(it) }.toSet()
        if (_settings.value.retainDeletedMessages) {
            retainedMessageWrites.trySend(RetentionEvent.Deleted(chatId, ids))
            _messages.updateTimeline(chatId) { current -> current.map { message -> if (message.id in ids) message.copy(isDeleted = true) else message } }
        } else {
            _messages.updateTimeline(chatId) { current -> current.filterNot { it.id in ids } }
        }
    }

    private fun updateMessagePinned(update: JSONObject) {
        val chatId = update.optLong("chat_id")
        val messageId = update.optLong("message_id")
        val isPinned = update.optBoolean("is_pinned")
        _messages.updateTimeline(chatId) { current -> current.map { if (it.id == messageId) it.copy(isPinned = isPinned) else it } }
    }

    private fun publishFile(file: JSONObject) {
        val local = file.optJSONObject("local") ?: return
        val remote = file.optJSONObject("remote")
        val id = file.optInt("id")
        if (id <= 0) return
        val path = local.optString("path").takeIf { it.isNotBlank() }
        val isCompleted = local.optBoolean("is_downloading_completed")
        val isActive = local.optBoolean("is_downloading_active")
        val readyPath = path?.let(::readyLocalPath)
        if (readyPath != null && isCompleted) {
            filePaths[id] = readyPath
        } else if (isCompleted) {
            // Never publish a path that still points at a partial or deleted file.
            filePaths.remove(id)
        }
        val downloadedBytes = local.optLong("downloaded_size")
        val transfer = FileTransferSnapshot(
            fileId = id,
            size = file.optLong("size"),
            expectedSize = file.optLong("expected_size"),
            downloadedBytes = downloadedBytes,
            isDownloadingActive = isActive,
            isDownloadingCompleted = isCompleted,
            hasReadyLocalPath = readyPath != null,
            uploadedBytes = remote?.optLong("uploaded_size") ?: 0L,
            isUploadingActive = remote?.optBoolean("is_uploading_active") == true,
        ).toTransferState()
        _transfers.value = _transfers.value + (id to transfer)
        // A failed/cancelled request must release the de-duplication guard. Otherwise a
        // later user/chat update can never request this avatar again.
        if (isCompleted || !isActive) requestedDownloads.remove(id)
        if (isCompleted && readyPath != null) {
            avatarRetryAttempts.remove(id)
        } else if (!isCompleted && !isActive && isAvatarFile(id)) {
            scheduleAvatarRetry(id)
        }
        // File updates can arrive many times per second. Progress is kept in the transfer
        // state, while chat/message paths are published only when the file is complete.
        if (isCompleted && readyPath != null) {
            val revision = fileRevision(readyPath)
            chatPublication.request()
            _currentUser.value?.takeIf { it.avatarFileId == id }?.let { _currentUser.value = it.copy(avatarPath = readyPath, avatarRevision = revision) }
            _users.value = _users.value.mapValues { (_, user) -> if (user.avatarFileId == id) user.copy(avatarPath = readyPath, avatarRevision = revision) else user }
            _messages.update { timelines -> timelines.mapValues { (_, messages) ->
                messages.map { message ->
                    when (id) {
                        message.mediaFileId -> message.copy(mediaPath = readyPath)
                        message.mediaFullFileId -> message.copy(mediaFullPath = readyPath)
                        else -> message
                    }
                }
            } }
        }
        if (_settings.value.retainDeletedMessages && isCompleted && readyPath != null) {
            repositoryScope.launch(Dispatchers.IO) { messageRetentionStore.updateMediaPath(id, readyPath) }
        }
        if (isCompleted && readyPath != null) {
            pendingGallerySaves.remove(id)?.let { request ->
                repositoryScope.launch(Dispatchers.IO) { saveLocalFileToMediaStore(id, readyPath, request) }
            }
            if (_settings.value.saveToGallery) {
                _messages.value.values.flatten()
                    .filter { it.mediaFileId == id || it.mediaFullFileId == id }
                    .forEach(::maybeAutoSaveMedia)
            }
        }
    }

    private fun publishNotification(update: JSONObject) {
        if (!_settings.value.notificationsEnabled) return
        val notification = update.optJSONObject("notification") ?: return
        val content = notification.optJSONObject("content")
        val message = content?.optJSONObject("message")
        val body = message?.let(::mapMessage)?.text
            ?: content?.optString("text")
            ?: notification.optString("subtitle")
        notificationController.show(
            notification.optString("title"),
            if (_settings.value.showMessagePreview) body.orEmpty() else "New message",
            notification.optInt("id"),
            _settings.value.inAppSounds,
            _settings.value.vibration,
        )
    }

    private fun updateUser(user: JSONObject) {
        val mapped = mapUser(user)
        requestedUsers.remove(mapped.id)
        _users.value = _users.value + (mapped.id to mapped)
        if (_currentUser.value?.id == mapped.id) _currentUser.value = mapped
    }

    private fun requestUser(userId: Long) {
        if (userId <= 0L || _users.value.containsKey(userId) || !requestedUsers.add(userId)) return
        repositoryScope.launch {
            try {
                val user = client?.request("getUser", JSONObject().put("user_id", userId))
                if (user != null && user.optLong("id") == userId) updateUser(user)
            } catch (cancelled: CancellationException) {
                requestedUsers.remove(userId)
                throw cancelled
            } catch (_: Exception) {
                requestedUsers.remove(userId)
            }
        }
    }

    private suspend fun loadCurrentUser() {
        try {
            client?.request("getMe")?.let { userJson ->
                updateUser(userJson)
                _currentUser.value = _users.value[userJson.optLong("id")]
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            reportOperationError(error)
        }
    }

    private fun mapUser(user: JSONObject): TelegramUser {
        val firstName = user.optString("first_name")
        val lastName = user.optString("last_name")
        val displayName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
        val username = activeUsername(user)
        val phone = user.optString("phone_number").ifBlank { null }
        val avatarFileId = user.optJSONObject("profile_photo")?.optJSONObject("small")?.optInt("id")?.takeIf { it > 0 }
        avatarFileId?.let { requestFile(it, respectAutoDownload = false) }
        val avatarPath = avatarFileId?.let { filePaths[it]?.let(::readyLocalPath) }
        return TelegramUser(
            id = user.optLong("id"),
            displayName = displayName.ifBlank { username ?: "User" },
            username = username,
            phoneNumber = phone,
            avatarFileId = avatarFileId,
            avatarPath = avatarPath,
            avatarRevision = avatarPath?.let(::fileRevision) ?: 0L,
            firstName = firstName,
            lastName = lastName,
        )
    }

    private fun mapChat(chat: JSONObject): ChatSummary {
        val photoFileId = chat.optJSONObject("photo")?.optJSONObject("small")?.optInt("id")?.takeIf { it > 0 }
        photoFileId?.let { requestFile(it, respectAutoDownload = false) }
        val photoPath = photoFileId?.let { filePaths[it]?.let(::readyLocalPath) }
        return ChatSummary(
            id = chat.optLong("id"),
            title = chat.optString("title", "Chat"),
            subtitle = chat.optJSONObject("last_message")?.let(::messagePreview).orEmpty(),
            unreadCount = chat.optInt("unread_count"),
            isMarkedAsUnread = chat.optBoolean("is_marked_as_unread"),
            isPinned = localPinOverrides[chat.optLong("id")] ?: (chat.optJSONArray("positions")?.let { positions -> (0 until positions.length()).any { positions.optJSONObject(it)?.optBoolean("is_pinned") == true } } == true),
            isChannel = chat.isChannelChat(),
            isGroup = chat.isGroupChat(),
            isPrivate = chat.isPrivateChat(),
            userId = chat.optJSONObject("type")?.optLong("user_id")?.takeIf { it > 0L },
            lastMessage = chat.optJSONObject("last_message")?.let { mapMessage(it, chat.optLong("id"), chat.isChannelChat()) },
            photoPath = photoPath,
            photoRevision = photoPath?.let(::fileRevision) ?: 0L,
        )
    }

    private fun mapMessage(message: JSONObject, parentChatId: Long? = null, channelPost: Boolean = false): MessageSummary? {
        val content = message.optJSONObject("content") ?: return null
        val type = content.optString("@type")
        val textObject = content.optJSONObject("text") ?: content.optJSONObject("caption")
        val text = textObject?.optString("text").orEmpty()
        val entities = parseEntities(textObject)
        val media = mediaInfo(content, type)
        // Keep channel posts even when their media type has no text caption
        // (polls, stickers, albums, paid media, and newer TDLib content types).
        if (text.isBlank() && !type.startsWith("message")) return null
        val chatId = message.optLong("chat_id").takeIf { it != 0L } ?: parentChatId ?: return null
        val sender = message.optJSONObject("sender_id")
        val senderUserId = sender?.takeIf { it.optString("@type") == "messageSenderUser" }
            ?.optLong("user_id")
            ?.takeIf { it > 0L }
        senderUserId?.let(::requestUser)
        val senderName = when (sender?.optString("@type")) {
            "messageSenderUser" -> _users.value[sender.optLong("user_id")]?.displayName ?: "User ${sender.optLong("user_id")}"
            "messageSenderChat" -> chatCache[sender.optLong("chat_id")]?.optString("title") ?: "Chat"
            else -> "Unknown"
        }
        media?.fileId?.let { fileId ->
            requestFile(fileId, respectAutoDownload = media.type != MediaType.PHOTO)
        }
        return MessageSummary(
            id = message.optLong("id"),
            chatId = chatId,
            senderName = senderName,
            senderUserId = senderUserId,
            text = text.ifBlank { mediaLabel(type) },
            dateEpochSeconds = message.optInt("date"),
            isOutgoing = message.optBoolean("is_outgoing"),
            sendState = when (message.optJSONObject("sending_state")?.optString("@type")) {
                "messageSendingStatePending" -> MessageSendState.SENDING
                "messageSendingStateFailed" -> MessageSendState.FAILED
                else -> null
            },
            sendError = message.optJSONObject("sending_state")
                ?.takeIf { it.optString("@type") == "messageSendingStateFailed" }
                ?.let { "Message could not be sent" },
            canRetrySend = message.optJSONObject("sending_state")
                ?.takeIf { it.optString("@type") == "messageSendingStateFailed" }
                ?.optBoolean("can_retry") == true,
            retrySendAtEpochSeconds = message.optJSONObject("sending_state")
                ?.takeIf { it.optString("@type") == "messageSendingStateFailed" }
                ?.optInt("retry_after", 0)?.coerceAtLeast(0)?.let { (System.currentTimeMillis() / 1000L).toInt() + it } ?: 0,
            isRead = false,
            mediaType = media?.type ?: mediaType(type),
            isChannelPost = channelPost,
            mediaFileId = media?.fileId,
            mediaPath = media?.fileId?.let(filePaths::get),
            mediaFullFileId = media?.fullFileId,
            mediaFullPath = media?.fullFileId?.let(filePaths::get),
            mediaName = media?.name,
            entities = entities,
            replyToMessageId = message.optJSONObject("reply_to")?.optLong("message_id")?.takeIf { it > 0L },
            canEdit = message.optBoolean("can_be_edited") || (message.optBoolean("is_outgoing") && type == "messageText"),
            canDelete = message.optBoolean("can_be_deleted_for_all_users") || message.optBoolean("can_be_deleted_only_for_self") || message.optBoolean("is_outgoing"),
            canForward = message.optBoolean("can_be_forwarded", true),
            isPinned = message.optBoolean("is_pinned"),
        )
    }

    private fun formattedText(text: String, entities: List<MessageEntity>): JSONObject =
        JSONObject()
            .put("@type", "formattedText")
            .put("text", text)
            .put("entities", JSONArray().apply { entities.forEach { put(it.toJson()) } })

    private fun MessageEntity.toJson(): JSONObject {
        val entityType = JSONObject().put("@type", type)
        when (type) {
            "textEntityTypeTextUrl" -> entityType.put("url", argument.orEmpty())
            "textEntityTypePreCode" -> entityType.put("language", argument.orEmpty())
            "textEntityTypeCustomEmoji" -> entityType.put("custom_emoji_id", argument.orEmpty())
        }
        return JSONObject().put("@type", "textEntity").put("offset", offset).put("length", length).put("type", entityType)
    }

    private fun parseEntities(textObject: JSONObject?): List<MessageEntity> {
        val entities = textObject?.optJSONArray("entities") ?: return emptyList()
        return (0 until entities.length()).mapNotNull { index ->
            val entity = entities.optJSONObject(index) ?: return@mapNotNull null
            val type = entity.optJSONObject("type") ?: return@mapNotNull null
            MessageEntity(
                offset = entity.optInt("offset"),
                length = entity.optInt("length"),
                type = type.optString("@type"),
                argument = type.optString("url").ifBlank {
                    type.optString("language").ifBlank {
                        type.optString("custom_emoji_id").ifBlank { null }
                    }
                },
            )
        }
    }

    private fun contentText(content: JSONObject?): String {
        val textObject = content?.optJSONObject("text") ?: content?.optJSONObject("caption")
        return textObject?.optString("text").orEmpty()
    }

    private fun messagePreview(message: JSONObject) = mapMessage(message)?.text.orEmpty()

    private fun mediaLabel(type: String) = when (type) {
        "messagePhoto" -> "Photo"
        "messageVideo" -> "Video"
        "messageDocument" -> "Document"
        "messageAudio" -> "Audio"
        "messageVoiceNote" -> "Voice message"
        "messageLocation" -> "Location"
        "messageSticker" -> "Sticker"
        "messageAnimation" -> "Animation"
        else -> "Message"
    }

    private fun mediaType(type: String) = when (type) {
        "messagePhoto" -> MediaType.PHOTO
        "messageVideo" -> MediaType.VIDEO
        "messageDocument" -> MediaType.DOCUMENT
        "messageAudio" -> MediaType.AUDIO
        "messageVoiceNote" -> MediaType.VOICE
        "messageLocation" -> MediaType.LOCATION
        else -> null
    }

    private data class MediaInfo(
        val type: MediaType,
        val fileId: Int?,
        val name: String? = null,
        val fullFileId: Int? = null,
    )

    private data class GallerySaveRequest(val mediaType: MediaType, val fileName: String?)

    private fun mediaInfo(content: JSONObject, type: String): MediaInfo? = when (type) {
        "messagePhoto" -> content.optJSONObject("photo")?.optJSONArray("sizes")?.let { sizes ->
            val candidates = (0 until sizes.length()).mapNotNull { index ->
                val size = sizes.optJSONObject(index) ?: return@mapNotNull null
                val fileId = size.optJSONObject("photo")?.optInt("id") ?: return@mapNotNull null
                PhotoSizeCandidate(fileId, size.optInt("width"), size.optInt("height"))
            }
            val selection = selectPhotoFiles(candidates)
            MediaInfo(MediaType.PHOTO, selection.previewFileId, fullFileId = selection.fullFileId)
        }
        "messageVideo" -> MediaInfo(MediaType.VIDEO, content.optJSONObject("video")?.optJSONObject("video")?.optInt("id")?.takeIf { it > 0 })
        "messageDocument" -> MediaInfo(
            MediaType.DOCUMENT,
            content.optJSONObject("document")?.optJSONObject("document")?.optInt("id")?.takeIf { it > 0 },
            content.optJSONObject("document")?.optString("file_name")?.ifBlank { null },
        )
        "messageAudio" -> MediaInfo(
            MediaType.AUDIO,
            content.optJSONObject("audio")?.optJSONObject("audio")?.optInt("id")?.takeIf { it > 0 },
            content.optJSONObject("audio")?.optString("file_name")?.ifBlank { null },
        )
        "messageVoiceNote" -> MediaInfo(MediaType.VOICE, content.optJSONObject("voice_note")?.optJSONObject("voice")?.optInt("id")?.takeIf { it > 0 })
        "messageLocation" -> MediaInfo(MediaType.LOCATION, null)
        else -> null
    }

    private fun requestFile(fileId: Int, priority: Int = 4, respectAutoDownload: Boolean = true) {
        if (fileId <= 0) return
        val cachedPath = filePaths[fileId]
        if (cachedPath != null && readyLocalPath(cachedPath) != null) return
        if (cachedPath != null) filePaths.remove(fileId, cachedPath)
        if (!requestedDownloads.add(fileId)) return
        if (respectAutoDownload && !_settings.value.autoDownloadMedia) {
            requestedDownloads.remove(fileId)
            return
        }
        if (client == null) {
            requestedDownloads.remove(fileId)
            return
        }
        updateScope.launch {
            try {
                // Already cached files may be returned immediately without a new
                // updateFile event. Consume the response too, otherwise avatars
                // remain blank after a process restart despite existing on disk.
                val file = client.request("downloadFile", JSONObject().put("file_id", fileId).put("priority", priority).put("offset", 0).put("limit", 0).put("synchronous", false))
                if (file.optString("@type") == "file") publishFile(file)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                requestedDownloads.remove(fileId)
                if (isAvatarFile(fileId)) scheduleAvatarRetry(fileId)
            }
        }
        // TDLib normally emits updateFile, but also release the guard if the client is
        // disconnected before that update can arrive. A later map/update can retry safely.
        repositoryScope.launch {
            delay(FILE_REQUEST_GUARD_MS)
            requestedDownloads.remove(fileId)
        }
    }

    private fun isAvatarFile(fileId: Int): Boolean =
        _currentUser.value?.avatarFileId == fileId ||
            _users.value.values.any { it.avatarFileId == fileId } ||
            chatCache.values.any { chat ->
                chat.optJSONObject("photo")?.optJSONObject("small")?.optInt("id") == fileId
            }

    private fun scheduleAvatarRetry(fileId: Int) {
        val attempt = (avatarRetryAttempts.merge(fileId, 1, Int::plus) ?: 1)
        if (attempt > MAX_AVATAR_RETRIES) return
        repositoryScope.launch {
            delay(AVATAR_RETRY_DELAY_MS * attempt)
            requestFile(fileId, priority = 8, respectAutoDownload = false)
        }
    }

    private fun readyLocalPath(path: String): String? =
        File(path).takeIf { it.isFile && it.length() > 0L }?.absolutePath

    private fun fileRevision(path: String): Long {
        val file = File(path)
        return (file.lastModified() * 31L) xor file.length() xor path.hashCode().toLong()
    }

    private fun maybeAutoSaveMedia(message: MessageSummary) {
        if (!_settings.value.saveToGallery) return
        val mediaType = message.mediaType?.takeIf { it == MediaType.PHOTO || it == MediaType.VIDEO } ?: return
        val fileId = message.mediaFullFileId ?: message.mediaFileId ?: return
        saveMediaToGallery(fileId, mediaType, message.mediaName, message.mediaFullPath ?: message.mediaPath)
    }

    private fun saveLocalFileToMediaStore(fileId: Int, path: String, request: GallerySaveRequest) {
        if (!savedGalleryFiles.add(fileId)) return
        val (collection, relativePath, mimeType, defaultExtension) = when (request.mediaType) {
            MediaType.PHOTO -> Quadruple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Pictures/Chatwave", "image/jpeg", "jpg")
            MediaType.VIDEO -> Quadruple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/Chatwave", "video/mp4", "mp4")
            MediaType.DOCUMENT -> Quadruple(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "Download/Chatwave", "application/octet-stream", "bin")
            MediaType.AUDIO, MediaType.VOICE -> Quadruple(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "Download/Chatwave", "audio/mpeg", "mp3")
            MediaType.LOCATION -> {
                savedGalleryFiles.remove(fileId)
                return
            }
        }
        val displayName = request.fileName?.takeIf { it.isNotBlank() }?.replace(Regex("[\\r\\n/]"), "_")
            ?: "chatwave_${fileId}.$defaultExtension"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = appContext.contentResolver
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull()
        if (uri == null) {
            savedGalleryFiles.remove(fileId)
            return
        }
        runCatching {
            resolver.openOutputStream(uri, "w")?.use { output ->
                File(path).inputStream().use { input -> input.copyTo(output) }
            } ?: error("Unable to open media destination")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }.onFailure {
            resolver.delete(uri, null, null)
            savedGalleryFiles.remove(fileId)
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private data class HistoryCursor(
        var oldestMessageId: Long = 0L,
        var hasMore: Boolean = true,
        var serverHasMore: Boolean = true,
        var hasRetainedOlder: Boolean = false,
        var initialLoaded: Boolean = false,
        var loading: Boolean = false,
    )

    private data class HistoryBatch(
        val messages: List<MessageSummary>,
        val oldestMessageId: Long,
        val hasMore: Boolean,
    )

    private fun beginHistoryLoad(chatId: Long, initial: Boolean): HistoryCursor? {
        val cursor = historyCursors.computeIfAbsent(chatId) { HistoryCursor() }
        synchronized(cursor) {
            if (cursor.loading) return null
            if (!initial && (!cursor.initialLoaded || (!cursor.serverHasMore && !cursor.hasRetainedOlder))) return null
            cursor.loading = true
            if (initial) {
                cursor.oldestMessageId = 0L
                cursor.hasMore = true
                cursor.serverHasMore = true
                cursor.hasRetainedOlder = false
                cursor.initialLoaded = false
            }
        }
        return cursor
    }

    private fun finishHistoryLoad(cursor: HistoryCursor) {
        synchronized(cursor) { cursor.loading = false }
    }

    private fun updateHistoryCursor(
        cursor: HistoryCursor,
        batch: HistoryBatch,
        initial: Boolean,
        hasRetainedOlder: Boolean,
    ) {
        synchronized(cursor) {
            if (batch.oldestMessageId != 0L) cursor.oldestMessageId = batch.oldestMessageId
            cursor.serverHasMore = batch.hasMore
            cursor.hasRetainedOlder = hasRetainedOlder
            cursor.hasMore = batch.hasMore || hasRetainedOlder
            if (initial) cursor.initialLoaded = true
        }
    }

    private suspend fun fetchHistory(
        chatId: Long,
        channelPost: Boolean,
        limit: Int,
        initialFromMessageId: Long,
    ): HistoryBatch = withContext(Dispatchers.Default) {
        val result = paginateHistory(
            initialFromMessageId = initialFromMessageId,
            requestedCount = limit,
            pageLimit = limit,
            maxPages = MAX_HISTORY_PAGES,
            idOf = MessageSummary::id,
        ) { fromMessageId, pageLimit ->
            val response = client?.request(
                "getChatHistory",
                JSONObject()
                    .put("chat_id", chatId)
                    .put("from_message_id", fromMessageId)
                    .put("offset", 0)
                    .put("limit", pageLimit)
                    .put("only_local", false),
            ) ?: return@paginateHistory HistoryPage(emptyList(), 0L)
            val messages = response.optJSONArray("messages") ?: return@paginateHistory HistoryPage(emptyList(), 0L)
            val rawMessages = (0 until messages.length()).mapNotNull(messages::optJSONObject)
            // TDLib returns reverse chronological pages. Its documented cursor
            // is the last message ID in the response, which may be negative for
            // a local message that has not received a server ID yet.
            val oldestMessageId = rawMessages.lastOrNull()?.optLong("id") ?: 0L
            HistoryPage(rawMessages.mapNotNull { mapMessage(it, chatId, channelPost) }, oldestMessageId)
        }
        HistoryBatch(result.messages, result.oldestMessageId, result.hasMore)
    }

    private fun JSONArray?.toMessageList(chatId: Long, channelPost: Boolean): List<MessageSummary> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { mapMessage(optJSONObject(it) ?: JSONObject(), chatId, channelPost) }

    private fun JSONArray?.toMessageList(): List<MessageSummary> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { mapMessage(optJSONObject(it) ?: JSONObject()) }

    private fun mergeMessages(existing: List<MessageSummary>, incoming: List<MessageSummary>): List<MessageSummary> =
        mergeChronologicalMessages(existing, incoming)

    private suspend fun persistRetentionEvents(events: List<RetentionEvent>) {
        val pendingMessages = mutableListOf<MessageSummary>()
        suspend fun flushMessages() {
            if (pendingMessages.isEmpty()) return
            messageRetentionStore.saveAll(pendingMessages.toList())
            pendingMessages.clear()
        }
        events.forEach { event ->
            when (event) {
                is RetentionEvent.Save -> pendingMessages += event.message
                is RetentionEvent.Deleted -> {
                    flushMessages()
                    messageRetentionStore.markDeleted(event.chatId, event.messageIds)
                }
                RetentionEvent.Clear -> {
                    flushMessages()
                    messageRetentionStore.clear()
                }
            }
        }
        flushMessages()
    }

    private sealed interface RetentionEvent {
        data class Save(val message: MessageSummary) : RetentionEvent
        data class Deleted(val chatId: Long, val messageIds: Set<Long>) : RetentionEvent
        data object Clear : RetentionEvent
    }

    private fun JSONObject.isChannelChat(): Boolean =
        optJSONObject("type")?.let { type ->
            type.optString("@type") == "chatTypeSupergroup" && type.optBoolean("is_channel")
        } == true

    private fun JSONObject.isGroupChat(): Boolean {
        val type = optJSONObject("type") ?: return false
        return when (type.optString("@type")) {
            "chatTypeBasicGroup" -> true
            "chatTypeSupergroup" -> !type.optBoolean("is_channel")
            else -> false
        }
    }

    private fun JSONObject.isPrivateChat(): Boolean =
        optJSONObject("type")?.optString("@type") == "chatTypePrivate"

    private fun JSONObject.isCountableActivityMessage(): Boolean {
        val type = optJSONObject("content")?.optString("@type") ?: return false
        return type.startsWith("message") && !type.startsWith("messageChat") && type !in NON_ACTIVITY_MESSAGE_TYPES
    }

    private fun loadChatFolders(): List<ChatFolder> {
        val custom = folderPreferences.getString(FOLDER_IDS_KEY, null)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.mapNotNull { id ->
                val title = folderPreferences.getString("$FOLDER_NAME_PREFIX$id", null)?.trim().orEmpty()
                if (title.isBlank()) null else ChatFolder(id, title, parseChatIds(folderPreferences.getString("$FOLDER_CHATS_PREFIX$id", null)))
            }
            .orEmpty()
        return listOf(ChatFolder(ALL_CHATS_FOLDER_ID, "All chats", isAllChats = true)) + custom
    }

    private fun persistChatFolders(folders: List<ChatFolder>) {
        val custom = folders.filterNot { it.isAllChats || it.id == ALL_CHATS_FOLDER_ID }
        val editor = folderPreferences.edit().putString(FOLDER_IDS_KEY, custom.joinToString(",") { it.id })
        custom.forEach { folder ->
            editor.putString("$FOLDER_NAME_PREFIX${folder.id}", folder.title)
            editor.putString("$FOLDER_CHATS_PREFIX${folder.id}", folder.chatIds.joinToString(","))
        }
        editor.apply()
    }

    private fun parseChatIds(value: String?): Set<Long> = value
        ?.split(',')
        ?.mapNotNull { it.trim().toLongOrNull() }
        ?.toSet()
        .orEmpty()

    private fun Throwable.safeMessage() = (this as? TelegramException)?.message ?: "Telegram request failed"

    private companion object {
        const val DEFAULT_ACCOUNT_ID = "default"
        const val ALL_CHATS_FOLDER_ID = "all"
        const val FOLDER_IDS_KEY = "folder_ids"
        const val FOLDER_NAME_PREFIX = "folder_name."
        const val FOLDER_CHATS_PREFIX = "folder_chats."
        const val AUTH_ACTION_TIMEOUT_MS = 15_000L
        const val SESSION_RESTORE_TIMEOUT_MS = 60_000L
        const val ACCOUNT_REMOVAL_LOGOUT_TIMEOUT_MS = 5_000L
        const val RETENTION_WRITE_BATCH_DELAY_MS = 180L
        const val RETAINED_INITIAL_PAGE_SIZE = 100
        const val FILE_REQUEST_GUARD_MS = 15_000L
        const val AVATAR_RETRY_DELAY_MS = 600L
        const val MAX_AVATAR_RETRIES = 3
        const val MAX_HISTORY_PAGES = 8
        const val CHAT_PUBLICATION_COALESCE_MS = 16L
        const val ACTIVITY_WINDOW_SECONDS = 24 * 60 * 60L
        const val MAX_ACTIVITY_PAGES = 200
        val NON_ACTIVITY_MESSAGE_TYPES = setOf(
            "messageCall",
            "messagePinMessage",
            "messageScreenshotTaken",
            "messageVideoChatStarted",
            "messageVideoChatEnded",
        )
    }
}
