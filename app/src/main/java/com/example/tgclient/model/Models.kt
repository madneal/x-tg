package com.example.tgclient.model

data class TelegramUser(
    val id: Long,
    val displayName: String,
    val username: String? = null,
    val phoneNumber: String? = null,
    val avatarFileId: Int? = null,
    val avatarPath: String? = null,
    val firstName: String = "",
    val lastName: String = "",
)

data class AccountSummary(
    val id: String,
    val label: String,
)

data class VerificationCodeState(
    val timeoutSeconds: Int = 0,
    val nextType: String? = null,
)

/** A single in-flight authorization request. Used to prevent duplicate taps. */
enum class AuthAction {
    None,
    SubmitPhone,
    SubmitCode,
    ResendCode,
    SubmitPassword,
    Register,
    SubmitEmail,
    SubmitEmailCode,
}

data class ChatSummary(
    val id: Long,
    val title: String,
    val subtitle: String = "",
    val unreadCount: Int = 0,
    val isMarkedAsUnread: Boolean = false,
    val isPinned: Boolean = false,
    val isChannel: Boolean = false,
    val isGroup: Boolean = false,
    val isPrivate: Boolean = false,
    val lastMessage: MessageSummary? = null,
    val photoPath: String? = null,
)

/** A local chat folder, similar to Telegram's chat-folder tabs. */
data class ChatFolder(
    val id: String,
    val title: String,
    val chatIds: Set<Long> = emptySet(),
    val isAllChats: Boolean = false,
)

data class MessageSummary(
    val id: Long,
    val chatId: Long,
    val senderName: String,
    val senderUserId: Long? = null,
    val text: String,
    val dateEpochSeconds: Int = 0,
    val isOutgoing: Boolean = false,
    val isRead: Boolean = false,
    val mediaType: MediaType? = null,
    val isChannelPost: Boolean = false,
    val mediaFileId: Int? = null,
    val mediaPath: String? = null,
    val mediaName: String? = null,
)

enum class MediaType { PHOTO, VIDEO, DOCUMENT, AUDIO, VOICE, LOCATION }

sealed interface AuthState {
    data object Loading : AuthState
    data object MissingConfiguration : AuthState
    data object WaitPhoneNumber : AuthState
    data object WaitEmailAddress : AuthState
    data object WaitEmailCode : AuthState
    data object WaitCode : AuthState
    data object WaitPassword : AuthState
    data object WaitRegistration : AuthState
    data object Ready : AuthState
    data object LoggingOut : AuthState
    data class Error(val message: String) : AuthState
}

data class AppSettings(
    val darkTheme: Boolean = false,
    val dynamicColors: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val showMessagePreview: Boolean = true,
    val inAppSounds: Boolean = true,
    val vibration: Boolean = true,
    val autoDownloadMedia: Boolean = true,
    val saveToGallery: Boolean = false,
    val useLessData: Boolean = false,
    val sendByEnter: Boolean = true,
    val linkPreviews: Boolean = true,
    val reduceAnimations: Boolean = false,
    val language: String = "System default",
)

data class TransferState(
    val fileId: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val isCompleted: Boolean = false,
    val error: String? = null,
)
