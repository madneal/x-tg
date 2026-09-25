package com.example.tgclient.features

/** Central parity checklist. Keep capability names stable for release notes and QA. */
enum class FeatureStatus { IMPLEMENTED, PARTIAL, PLANNED }

data class FeatureCapability(
    val id: String,
    val label: String,
    val status: FeatureStatus,
    val tdlibArea: String,
)

object FeatureRegistry {
    val capabilities = listOf(
        FeatureCapability("auth", "Phone, email, code, password login", FeatureStatus.IMPLEMENTED, "authorizationState"),
        FeatureCapability("chat_list", "Chat lists, folders, pinned and unread state", FeatureStatus.IMPLEMENTED, "getChats/updateChat"),
        FeatureCapability("text_messages", "Text, replies, edits, deletion and forwarding", FeatureStatus.IMPLEMENTED, "sendMessage/updateMessage"),
        FeatureCapability("media", "Photo, video, document, audio and voice transfer", FeatureStatus.PARTIAL, "inputMessage* / updateFile"),
        FeatureCapability("search", "Global and chat message search", FeatureStatus.PARTIAL, "searchChatMessages/searchMessages"),
        FeatureCapability("contacts", "Contacts, profiles and group creation", FeatureStatus.PARTIAL, "getContacts/createNewBasicGroupChat"),
        FeatureCapability("notifications", "Grouped local notifications and mute state", FeatureStatus.PARTIAL, "updateNotification"),
        FeatureCapability("settings", "Theme, privacy, sessions and two-step verification", FeatureStatus.PARTIAL, "getOption/account.*"),
        FeatureCapability("channels", "Channels, topics and moderation", FeatureStatus.PLANNED, "supergroup/channel methods"),
        FeatureCapability("stories", "Stories and story reactions", FeatureStatus.PLANNED, "story methods"),
        FeatureCapability("calls", "Voice, video and group calls", FeatureStatus.PLANNED, "call/groupCall methods"),
        FeatureCapability("secret_chats", "End-to-end encrypted secret chats", FeatureStatus.PLANNED, "secret chat methods"),
        FeatureCapability("bots", "Bots, inline bots and mini apps", FeatureStatus.PLANNED, "bot/inlineQuery methods"),
        FeatureCapability("business", "Business accounts, paid media and Stars", FeatureStatus.PLANNED, "business/payment methods"),
    )
}
