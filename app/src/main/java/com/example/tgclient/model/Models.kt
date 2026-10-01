package com.example.tgclient.model

data class TelegramUser(
    val id: Long,
    val displayName: String,
    val username: String? = null,
    val phoneNumber: String? = null,
    val avatarFileId: Int? = null,
    val avatarPath: String? = null,
    /** Changes whenever the local avatar file is replaced, so Compose can invalidate its bitmap cache. */
    val avatarRevision: Long = 0L,
    val isPremium: Boolean = false,
    val firstName: String = "",
    val lastName: String = "",
)

data class UserProfile(val user: TelegramUser, val bio: String, val personalChatId: Long?, val bioEntities: List<MessageEntity> = emptyList())

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
    val userId: Long? = null,
    val lastMessage: MessageSummary? = null,
    val photoPath: String? = null,
    /** Changes whenever the local chat photo file is replaced, so Compose can invalidate its bitmap cache. */
    val photoRevision: Long = 0L,
)

/** A local chat folder, similar to Telegram's chat-folder tabs. */
data class ChatFolder(
    val id: String,
    val title: String,
    val chatIds: Set<Long> = emptySet(),
    val isAllChats: Boolean = false,
)

data class ChatHistoryState(
    val isLoadingInitial: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasLoadedInitial: Boolean = false,
    val hasOlderMessages: Boolean = true,
    val initialLoadError: String? = null,
    val olderLoadError: String? = null,
)

data class ChatListLoadState(
    val isLoading: Boolean = false,
    val allChatsLoaded: Boolean = false,
    val error: String? = null,
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
    val mediaFullFileId: Int? = null,
    val mediaFullPath: String? = null,
    val mediaName: String? = null,
    val entities: List<MessageEntity> = emptyList(),
    val replyToMessageId: Long? = null,
    val canEdit: Boolean = false,
    val canDelete: Boolean = false,
    val canForward: Boolean = true,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val sendState: MessageSendState? = null,
    val sendError: String? = null,
    val canRetrySend: Boolean = false,
    val retrySendAtEpochSeconds: Int = 0,
)

enum class MessageSendState { SENDING, FAILED }

enum class TdConnectionStatus {
    UNKNOWN,
    WAITING_FOR_NETWORK,
    CONNECTING_TO_PROXY,
    CONNECTING,
    UPDATING,
    READY,
}

/** A TDLib text entity, kept separate from TDLib JSON for immutable UI state. */
data class MessageEntity(
    val offset: Int,
    val length: Int,
    val type: String,
    val argument: String? = null,
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
    val retainDeletedMessages: Boolean = true,
)

data class TransferState(
    val fileId: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val isActive: Boolean = false,
    val isUploading: Boolean = false,
    val isCompleted: Boolean = false,
    val error: String? = null,
)

data class GroupSpeakerStat(
    val userId: Long,
    val displayName: String,
    val messageCount: Int,
)

data class GroupActivityState(
    val isLoading: Boolean = false,
    val topUsers: List<GroupSpeakerStat> = emptyList(),
    val error: String? = null,
)
