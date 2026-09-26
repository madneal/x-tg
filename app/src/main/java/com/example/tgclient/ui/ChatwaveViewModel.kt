package com.example.tgclient.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tgclient.data.TelegramRepository
import com.example.tgclient.model.AuthState
import com.example.tgclient.model.MessageEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatwaveViewModel(private val repository: TelegramRepository) : ViewModel() {
    val authState = repository.authState
    val chats = repository.chats
    val messages = repository.messages
    val users = repository.users
    val settings = repository.settings
    val chatFolders = repository.chatFolders
    val currentUser = repository.currentUser
    val verification = repository.verification
    val authAction = repository.authAction
    val authError = repository.authError
    val groupActivity = repository.groupActivity

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
    fun openChat(chatId: Long) = viewModelScope.launch { repository.loadMessages(chatId) }
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
    fun downloadFile(fileId: Int) = repository.downloadFile(fileId)
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
