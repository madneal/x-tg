package com.example.tgclient.model

/** A unique Telegram link or username found in a chat's recent messages. */
data class ChatReference(
    val value: String,
    val target: String,
    val kind: Kind,
) {
    enum class Kind { LINK, USERNAME }
}
