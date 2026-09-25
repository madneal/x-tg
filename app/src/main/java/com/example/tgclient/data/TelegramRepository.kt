package com.example.tgclient.data

import android.content.Context
import com.example.tgclient.BuildConfig
import com.example.tgclient.model.AppSettings
import com.example.tgclient.model.AuthState
import com.example.tgclient.model.ChatSummary
import com.example.tgclient.model.MediaType
import com.example.tgclient.model.MessageSummary
import com.example.tgclient.model.TelegramUser
import com.example.tgclient.model.TransferState
import com.example.tgclient.model.VerificationCodeState
import com.example.tgclient.notifications.TelegramNotificationController
import com.example.tgclient.security.DatabaseKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class TelegramRepository(context: Context, scope: CoroutineScope, val accountId: String = DEFAULT_ACCOUNT_ID) {
    private val repositoryScope = scope
    private val client: TdLibClient?
    private val notificationController = TelegramNotificationController(context)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    private val _messages = MutableStateFlow<Map<Long, List<MessageSummary>>>(emptyMap())
    private val _users = MutableStateFlow<Map<Long, TelegramUser>>(emptyMap())
    private val _currentUser = MutableStateFlow<TelegramUser?>(null)
    private val _verification = MutableStateFlow(VerificationCodeState())
    private val _transfers = MutableStateFlow<Map<Int, TransferState>>(emptyMap())
    private val settingsPreferences = context.getSharedPreferences("chatwave_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    private val chatCache = ConcurrentHashMap<Long, JSONObject>()

    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    val chats: StateFlow<List<ChatSummary>> = _chats.asStateFlow()
    val messages: StateFlow<Map<Long, List<MessageSummary>>> = _messages.asStateFlow()
    val users: StateFlow<Map<Long, TelegramUser>> = _users.asStateFlow()
    val currentUser: StateFlow<TelegramUser?> = _currentUser.asStateFlow()
    val verification: StateFlow<VerificationCodeState> = _verification.asStateFlow()
    val transfers: StateFlow<Map<Int, TransferState>> = _transfers.asStateFlow()
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        if (BuildConfig.TELEGRAM_API_ID == 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            client = null
            _authState.value = AuthState.MissingConfiguration
        } else {
            client = runCatching { TdLibClient(context, DatabaseKeyStore(context, accountId), accountId) }
                .onFailure { _authState.value = AuthState.Error("TDLib could not start") }
                .getOrNull()
            client?.updates?.onEach(::handleUpdate)?.launchIn(scope)
            client?.start()
        }
    }

    fun submitPhoneNumber(phoneNumber: String) = client?.send(
        "setAuthenticationPhoneNumber",
        JSONObject()
            .put("phone_number", phoneNumber)
            .put(
                "settings",
                JSONObject()
                    .put("@type", "phoneNumberAuthenticationSettings")
                    .put("allow_flash_call", false)
                    .put("allow_missed_call", false)
                    .put("is_current_phone_number", false)
                    .put("has_unknown_phone_number", true)
                    .put("allow_sms_retriever_api", false)
                    .put("firebase_authentication_settings", JSONObject.NULL)
                    .put("authentication_tokens", JSONArray()),
            ),
    )
    fun submitEmailAddress(emailAddress: String) = client?.send("setAuthenticationEmailAddress", JSONObject().put("email_address", emailAddress))
    fun submitEmailCode(code: String) = client?.send("checkAuthenticationEmailCode", JSONObject().put("code", JSONObject().put("@type", "emailAddressAuthenticationCode").put("code", code)))
    fun submitCode(code: String) = client?.send("checkAuthenticationCode", JSONObject().put("code", code))
    fun resendCode() = client?.send("resendAuthenticationCode")
    fun submitPassword(password: String) = client?.send("checkAuthenticationPassword", JSONObject().put("password", password))
    fun register(firstName: String, lastName: String) = client?.send("registerUser", JSONObject().put("first_name", firstName).put("last_name", lastName))

    fun logout() {
        _authState.value = AuthState.LoggingOut
        _currentUser.value = null
        client?.send("logOut")
    }

    suspend fun loadChats(limit: Int = 100) {
        runCatching {
            client?.request("getChats", JSONObject().put("chat_list", JSONObject().put("@type", "chatListMain")).put("limit", limit))
        }.onFailure { _authState.value = AuthState.Error(it.safeMessage()) }
    }

    suspend fun loadMessages(chatId: Long, limit: Int = 50) {
        runCatching {
            // Fetch the chat object before its history. This is important for
            // channels because TDLib describes them as chatTypeSupergroup with
            // is_channel=true, rather than using a separate chat type.
            val chat = client?.request("getChat", JSONObject().put("chat_id", chatId))
            if (chat != null && chat.optLong("id") == chatId) {
                chatCache[chatId] = chat
                publishChats()
            }
            val isChannel = chat?.isChannelChat() ?: chatCache[chatId]?.isChannelChat() == true
            client?.request("getChatHistory", JSONObject().put("chat_id", chatId).put("from_message_id", 0).put("offset", 0).put("limit", limit).put("only_local", false))?.let { result ->
                val loaded = result.optJSONArray("messages").toMessageList(chatId, isChannel)
                val merged = mergeMessages(_messages.value[chatId].orEmpty(), loaded)
                _messages.value = _messages.value + (chatId to merged)
            }
        }.onFailure { _authState.value = AuthState.Error(it.safeMessage()) }
    }

    fun sendText(chatId: Long, text: String, replyToMessageId: Long? = null) {
        val content = JSONObject()
            .put("@type", "inputMessageText")
            .put("text", JSONObject().put("@type", "formattedText").put("text", text).put("entities", JSONArray()))
            .put("link_preview_options", if (_settings.value.linkPreviews) JSONObject.NULL else JSONObject().put("@type", "linkPreviewOptions").put("is_disabled", true))
            .put("clear_draft", true)
        client?.send(
            "sendMessage",
            JSONObject()
                .put("chat_id", chatId)
                .put("topic_id", JSONObject.NULL)
                .put("reply_to", replyToMessageId?.let { JSONObject().put("@type", "inputMessageReplyToMessage").put("message_id", it).put("quote", JSONObject.NULL).put("checklist_task_id", 0) } ?: JSONObject.NULL)
                .put("options", JSONObject.NULL)
                .put("reply_markup", JSONObject.NULL)
                .put("input_message_content", content),
        )
    }

    fun sendLocalMedia(chatId: Long, path: String, mimeType: String, caption: String = "") {
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
        client?.send(
            "sendMessage",
            JSONObject().put("chat_id", chatId).put("topic_id", JSONObject.NULL).put("reply_to", JSONObject.NULL).put("options", JSONObject.NULL).put("reply_markup", JSONObject.NULL).put("input_message_content", content),
        )
    }

    fun downloadFile(fileId: Int, priority: Int = 16) = client?.send("downloadFile", JSONObject().put("file_id", fileId).put("priority", priority).put("offset", 0).put("limit", 0).put("synchronous", false))
    fun cancelDownload(fileId: Int) = client?.send("cancelDownloadFile", JSONObject().put("file_id", fileId).put("only_pending", false))

    suspend fun searchMessages(query: String, limit: Int = 50): List<MessageSummary> = runCatching {
        client?.request("searchMessages", JSONObject().put("query", query).put("offset", 0).put("limit", limit).put("min_date", 0).put("max_date", 0).put("chat_list", JSONObject.NULL))?.optJSONArray("messages").toMessageList()
            ?: emptyList()
    }.getOrDefault(emptyList())

    suspend fun searchChatMessages(chatId: Long, query: String, limit: Int = 50): List<MessageSummary> = runCatching {
        client?.request("searchChatMessages", JSONObject().put("chat_id", chatId).put("query", query).put("sender_id", JSONObject.NULL).put("from_message_id", 0).put("offset", 0).put("limit", limit).put("filter", JSONObject.NULL))?.optJSONArray("messages").toMessageList()
            ?: emptyList()
    }.getOrDefault(emptyList())

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
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
            .apply()
    }

    private fun loadSettings() = AppSettings(
        darkTheme = settingsPreferences.getBoolean("darkTheme", false),
        dynamicColors = settingsPreferences.getBoolean("dynamicColors", true),
        notificationsEnabled = settingsPreferences.getBoolean("notificationsEnabled", true),
        showMessagePreview = settingsPreferences.getBoolean("showMessagePreview", true),
        inAppSounds = settingsPreferences.getBoolean("inAppSounds", true),
        vibration = settingsPreferences.getBoolean("vibration", true),
        autoDownloadMedia = settingsPreferences.getBoolean("autoDownloadMedia", true),
        saveToGallery = settingsPreferences.getBoolean("saveToGallery", false),
        useLessData = settingsPreferences.getBoolean("useLessData", false),
        sendByEnter = settingsPreferences.getBoolean("sendByEnter", true),
        linkPreviews = settingsPreferences.getBoolean("linkPreviews", true),
        reduceAnimations = settingsPreferences.getBoolean("reduceAnimations", false),
        language = settingsPreferences.getString("language", "System default") ?: "System default",
    )

    private fun handleUpdate(update: JSONObject) {
        when (update.optString("@type")) {
            "updateAuthorizationState" -> {
                val authorizationState = update.optJSONObject("authorization_state")
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
                val mappedState = AuthStateMapper.fromJson(authorizationState)
                _authState.value = mappedState
                if (mappedState is AuthState.Ready) repositoryScope.launch { loadCurrentUser() }
            }
            "updateNewChat" -> update.optJSONObject("chat")?.let { chatCache[it.optLong("id")] = it; publishChats() }
            "updateChatTitle" -> chatCache[update.optLong("chat_id")]?.put("title", update.optString("title"))?.also { publishChats() }
            "updateChatLastMessage" -> chatCache[update.optLong("chat_id")]?.put("last_message", update.optJSONObject("last_message"))?.also { publishChats() }
            "updateNewMessage" -> update.optJSONObject("message")?.let(::publishMessage)
            "updateMessageContent" -> updateMessageContent(update)
            "updateFile" -> update.optJSONObject("file")?.let(::publishFile)
            "updateUser" -> update.optJSONObject("user")?.let(::updateUser)
            "updateNotification" -> publishNotification(update)
        }
    }

    private fun publishChats() {
        _chats.value = chatCache.values
            .sortedWith(compareByDescending<JSONObject> { it.optBoolean("is_marked_as_unread") }.thenByDescending { it.optJSONObject("last_message")?.optInt("date") ?: 0 })
            .map(::mapChat)
    }

    private fun publishMessage(message: JSONObject) {
        val chatId = message.optLong("chat_id").takeIf { it != 0L } ?: return
        val isChannel = chatCache[chatId]?.isChannelChat() == true
        val mapped = mapMessage(message, chatId, isChannel) ?: return
        val current = _messages.value[chatId].orEmpty()
        _messages.value = _messages.value + (chatId to mergeMessages(current, listOf(mapped)))
    }

    private fun updateMessageContent(update: JSONObject) {
        val chatId = update.optLong("chat_id")
        val messageId = update.optLong("message_id")
        val current = _messages.value[chatId].orEmpty()
        val replacement = current.firstOrNull { it.id == messageId }?.copy(text = contentText(update.optJSONObject("new_content"))) ?: return
        _messages.value = _messages.value + (chatId to current.map { if (it.id == messageId) replacement else it })
    }

    private fun publishFile(file: JSONObject) {
        val local = file.optJSONObject("local") ?: return
        val id = file.optInt("id")
        _transfers.value = _transfers.value + (id to TransferState(id, local.optLong("downloaded_size"), file.optLong("size"), local.optBoolean("is_downloading_completed")))
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
        _users.value = _users.value + (mapped.id to mapped)
        if (_currentUser.value?.id == mapped.id) _currentUser.value = mapped
    }

    private suspend fun loadCurrentUser() {
        runCatching {
            client?.request("getMe")?.let { userJson ->
                updateUser(userJson)
                _currentUser.value = _users.value[userJson.optLong("id")]
            }
        }
    }

    private fun mapUser(user: JSONObject): TelegramUser {
        val displayName = listOf(user.optString("first_name"), user.optString("last_name")).filter { it.isNotBlank() }.joinToString(" ")
        val username = user.optString("username").ifBlank { null }
        val phone = user.optString("phone_number").ifBlank { null }
        return TelegramUser(user.optLong("id"), displayName.ifBlank { username ?: "User" }, username, phone)
    }

    private fun mapChat(chat: JSONObject) = ChatSummary(
        id = chat.optLong("id"),
        title = chat.optString("title", "Chat"),
        subtitle = chat.optJSONObject("last_message")?.let(::messagePreview).orEmpty(),
        unreadCount = chat.optInt("unread_count"),
        isPinned = chat.optJSONArray("positions")?.let { positions -> (0 until positions.length()).any { positions.optJSONObject(it)?.optBoolean("is_pinned") == true } } == true,
        isChannel = chat.isChannelChat(),
        lastMessage = chat.optJSONObject("last_message")?.let { mapMessage(it, chat.optLong("id"), chat.isChannelChat()) },
    )

    private fun mapMessage(message: JSONObject, parentChatId: Long? = null, channelPost: Boolean = false): MessageSummary? {
        val content = message.optJSONObject("content") ?: return null
        val type = content.optString("@type")
        val text = contentText(content)
        // Keep channel posts even when their media type has no text caption
        // (polls, stickers, albums, paid media, and newer TDLib content types).
        if (text.isBlank() && !type.startsWith("message")) return null
        val chatId = message.optLong("chat_id").takeIf { it != 0L } ?: parentChatId ?: return null
        val sender = message.optJSONObject("sender_id")
        val senderName = when (sender?.optString("@type")) {
            "messageSenderUser" -> _users.value[sender.optLong("user_id")]?.displayName ?: "User ${sender.optLong("user_id")}"
            "messageSenderChat" -> chatCache[sender.optLong("chat_id")]?.optString("title") ?: "Chat"
            else -> "Unknown"
        }
        return MessageSummary(message.optLong("id"), chatId, senderName, text.ifBlank { mediaLabel(type) }, message.optInt("date"), message.optBoolean("is_outgoing"), false, mediaType(type), channelPost)
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
        else -> "Message"
    }

    private fun mediaType(type: String) = when (type) {
        "messagePhoto" -> MediaType.PHOTO
        "messageVideo" -> MediaType.VIDEO
        "messageDocument" -> MediaType.DOCUMENT
        "messageAudio" -> MediaType.AUDIO
        "messageVoiceNote" -> MediaType.VOICE
        else -> null
    }

    private fun JSONArray?.toMessageList(chatId: Long, channelPost: Boolean): List<MessageSummary> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { mapMessage(optJSONObject(it) ?: JSONObject(), chatId, channelPost) }

    private fun JSONArray?.toMessageList(): List<MessageSummary> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { mapMessage(optJSONObject(it) ?: JSONObject()) }

    private fun mergeMessages(existing: List<MessageSummary>, incoming: List<MessageSummary>): List<MessageSummary> =
        (existing + incoming).filter { it.id != 0L }.distinctBy { it.id }.sortedBy { it.id }

    private fun JSONObject.isChannelChat(): Boolean =
        optJSONObject("type")?.let { type ->
            type.optString("@type") == "chatTypeSupergroup" && type.optBoolean("is_channel")
        } == true

    private fun Throwable.safeMessage() = (this as? TelegramException)?.message ?: "Telegram request failed"

    private companion object {
        const val DEFAULT_ACCOUNT_ID = "default"
    }
}
