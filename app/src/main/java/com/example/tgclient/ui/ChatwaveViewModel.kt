package com.example.tgclient.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tgclient.data.TelegramRepository
import com.example.tgclient.model.AuthState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatwaveViewModel(private val repository: TelegramRepository) : ViewModel() {
    val authState = repository.authState
    val chats = repository.chats
    val messages = repository.messages
    val settings = repository.settings
    val currentUser = repository.currentUser
    val verification = repository.verification

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
    fun submitPassword(password: String) = repository.submitPassword(password)
    fun register(firstName: String, lastName: String) = repository.register(firstName, lastName)
    fun logout() = repository.logout()
    fun openChat(chatId: Long) = viewModelScope.launch { repository.loadMessages(chatId) }
    fun sendMessage(chatId: Long, text: String) = repository.sendText(chatId, text)
    fun sendLocalMedia(chatId: Long, path: String, mimeType: String) = repository.sendLocalMedia(chatId, path, mimeType)
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

    companion object {
        fun factory(repository: TelegramRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatwaveViewModel(repository) as T
            }
    }
}
