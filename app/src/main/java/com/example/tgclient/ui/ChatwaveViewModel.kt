package com.example.tgclient.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tgclient.data.TelegramRepository
import com.example.tgclient.model.AuthState
import com.example.tgclient.model.MessageEntity
import com.example.tgclient.model.MessageSummary
import com.example.tgclient.model.MediaType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatwaveViewModel(private val repository: TelegramRepository) : ViewModel() {
    val authState = repository.authState
    val chats = repository.chats
    val openedChats = repository.openedChats
    val chatListLoadState = repository.chatListLoadState
    val messages = repository.messages
    val chatHistory = repository.chatHistory
    val users = repository.users
    val settings = repository.settings
    val chatFolders = repository.chatFolders
    val currentUser = repository.currentUser
    val verification = repository.verification
    val authAction = repository.authAction
    val authError = repository.authError
    val operationError = repository.operationError
    val groupActivity = repository.groupActivity
    val transfers = repository.transfers
    val connectionStatus = repository.connectionStatus

    val ready: StateFlow<Boolean> = authState
        .combine(repository.chats) { auth, _ -> auth is AuthState.Ready }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            authState.collect { state ->
                if (state is AuthState.Ready) repository.loadChats()
            }
        }
    }

    suspend fun loadUserProfile(userId: Long) = repository.loadUserProfile(userId)
    suspend fun resolveProfileLink(target: String) = repository.resolveChatTarget(target)
    suspend fun scheduleMessage(chatId: Long, text: String, entities: List<MessageEntity>, path: String?, mimeType: String?, replyId: Long?, sendAt: Long, repeatPeriod: Int = 0) =
        repository.scheduleMessage(chatId, text, entities, path, mimeType, replyId, sendAt, repeatPeriod)
    suspend fun scheduledMessages(chatId: Long) = repository.scheduledMessages(chatId)
    suspend fun cancelScheduledMessage(chatId: Long, messageId: Long) = repository.cancelScheduledMessage(chatId, messageId)
    suspend fun resolveProfileChannel(chatId: Long) = repository.resolveChatTarget(chatId.toString())
    suspend fun createPrivateChat(userId: Long) = repository.createPrivateChat(userId)

    fun submitPhone(phone: String) = repository.submitPhoneNumber(phone)
    fun submitEmail(email: String) = repository.submitEmailAddress(email)
    fun submitEmailCode(code: String) = repository.submitEmailCode(code)
    fun submitCode(code: String) = repository.submitCode(code)
    fun resendCode() = repository.resendCode()
    fun changeAuthenticationPhoneNumber(phoneNumber: String) = repository.changeAuthenticationPhoneNumber(phoneNumber)
    fun updateProfile(firstName: String, lastName: String, username: String) = viewModelScope.launch {
        repository.updateProfile(firstName, lastName, username)
    }
    fun submitPassword(password: String) = repository.submitPassword(password)
    fun register(firstName: String, lastName: String) = repository.register(firstName, lastName)
    fun logout() = repository.logout()
    fun retrySessionRestore() = repository.retrySessionRestore()
    fun clearOperationError() = repository.clearOperationError()
    fun openChat(chatId: Long) = viewModelScope.launch { repository.loadMessages(chatId) }
    fun resolveChatTarget(target: String, onResolved: (Long) -> Unit, onFailed: () -> Unit = {}) = viewModelScope.launch {
        val chatId = repository.resolveChatTarget(target)
        if (chatId != null) onResolved(chatId) else onFailed()
    }
    fun loadOlderMessages(chatId: Long) = viewModelScope.launch { repository.loadOlderMessages(chatId) }
    fun loadMoreChats() = viewModelScope.launch { repository.loadChats() }
    fun closeChat(chatId: Long) = repository.closeChat(chatId)
    fun loadGroupActivityStats(chatId: Long) = viewModelScope.launch { repository.loadGroupActivityStats(chatId) }
    fun sendMessage(chatId: Long, text: String) = repository.sendText(chatId, text)
    fun sendMessage(chatId: Long, text: String, entities: List<MessageEntity>, replyToMessageId: Long? = null) =
        repository.sendText(chatId, text, entities, replyToMessageId)
    fun editMessage(chatId: Long, messageId: Long, text: String, entities: List<MessageEntity>) =
        repository.editMessageText(chatId, messageId, text, entities)
    fun deleteMessage(chatId: Long, messageId: Long) = repository.deleteMessage(chatId, messageId)
    fun forwardMessageToSaved(chatId: Long, messageId: Long) = repository.forwardMessageToSaved(chatId, messageId)
    fun toggleMessagePinned(chatId: Long, messageId: Long, pinned: Boolean) = repository.toggleMessagePinned(chatId, messageId, pinned)
    fun toggleMessageReaction(chatId: Long, messageId: Long) = repository.toggleMessageReaction(chatId, messageId)
    fun sendLocalMedia(chatId: Long, path: String, mimeType: String, caption: String = "", replyToMessageId: Long? = null) =
        repository.sendLocalMedia(chatId, path, mimeType, caption = caption, replyToMessageId = replyToMessageId)
    fun retryMessage(chatId: Long, message: MessageSummary) = repository.retryMessage(chatId, message)
    fun downloadFile(fileId: Int) = repository.downloadFile(fileId)
    fun cancelDownload(fileId: Int) = repository.cancelDownload(fileId)
    fun saveMediaToGallery(fileId: Int, mediaType: MediaType, fileName: String? = null, localPath: String? = null) =
        repository.saveMediaToGallery(fileId, mediaType, fileName, localPath)
    fun createChatFolder(title: String) = repository.createChatFolder(title)
    fun renameChatFolder(folderId: String, title: String) = repository.renameChatFolder(folderId, title)
    fun deleteChatFolder(folderId: String) = repository.deleteChatFolder(folderId)
    fun setChatFolderMembership(folderId: String, chatId: Long, included: Boolean) = repository.setChatFolderMembership(folderId, chatId, included)
    fun leaveChat(chatId: Long) = repository.leaveChat(chatId)
    fun deleteChatHistory(chatId: Long) = repository.deleteChatHistory(chatId)
    fun toggleChatPinned(chatId: Long, pinned: Boolean) = repository.toggleChatPinned(chatId, pinned)
    fun toggleChatMarkedAsUnread(chatId: Long, markedAsUnread: Boolean) = repository.toggleChatMarkedAsUnread(chatId, markedAsUnread)
    fun updateTheme(enabled: Boolean) = repository.updateSettings { it.copy(darkTheme = enabled) }
    fun updateNotifications(enabled: Boolean) = repository.updateSettings { it.copy(notificationsEnabled = enabled) }
    fun updateMessagePreview(enabled: Boolean) = repository.updateSettings { it.copy(showMessagePreview = enabled) }
    fun updateInAppSounds(enabled: Boolean) = repository.updateSettings { it.copy(inAppSounds = enabled) }
    fun updateVibration(enabled: Boolean) = repository.updateSettings { it.copy(vibration = enabled) }
    fun updateAutoDownload(enabled: Boolean) = repository.updateSettings { it.copy(autoDownloadMedia = enabled) }
    fun updateSaveToGallery(enabled: Boolean) = repository.updateSettings { it.copy(saveToGallery = enabled) }
    fun updateUseLessData(enabled: Boolean) = repository.updateSettings { it.copy(useLessData = enabled) }
    fun updateSendByEnter(enabled: Boolean) = repository.updateSettings { it.copy(sendByEnter = enabled) }
    fun updateLinkPreviews(enabled: Boolean) = repository.updateSettings { it.copy(linkPreviews = enabled) }
    fun updateReduceAnimations(enabled: Boolean) = repository.updateSettings { it.copy(reduceAnimations = enabled) }
    fun updateLanguage(language: String) = repository.updateSettings { it.copy(language = language) }
    fun updateRetainDeletedMessages(enabled: Boolean) = repository.updateSettings { it.copy(retainDeletedMessages = enabled) }
    fun clearRetainedMessages() = repository.clearRetainedMessages()

    companion object {
        fun factory(repository: TelegramRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatwaveViewModel(repository) as T
            }
    }
}
