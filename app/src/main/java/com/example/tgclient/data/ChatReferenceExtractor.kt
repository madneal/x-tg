package com.example.tgclient.data

import com.example.tgclient.model.ChatReference
import com.example.tgclient.model.MessageEntity
import java.util.LinkedHashMap

/** Extracts public links and Telegram usernames while preserving first-seen order. */
internal fun extractChatReferences(text: String, entities: List<MessageEntity> = emptyList()): List<ChatReference> {
    val unique = LinkedHashMap<String, ChatReference>()
    val candidates = mutableListOf<Pair<Int, ChatReference>>()

    fun add(raw: String, entityKind: ChatReference.Kind? = null, position: Int = Int.MAX_VALUE) {
        val value = raw.trim().trimEnd('.', ',', '!', '?', ';', ':', ')', ']', '}')
        if (value.isBlank()) return
        val username = value.removePrefix("@").takeIf {
            value.startsWith("@") && it.matches(USERNAME)
        }
        val kind = username?.let { ChatReference.Kind.USERNAME } ?: entityKind
        val target = when {
            username != null -> "https://t.me/$username"
            value.startsWith("www.", ignoreCase = true) -> "https://$value"
            value.startsWith("t.me/", ignoreCase = true) || value.startsWith("telegram.me/", ignoreCase = true) -> "https://$value"
            value.matches(NUMERIC_CHAT_ID) -> "tg://openmessage?chat_id=$value"
            else -> value
        }
        candidates += position to ChatReference(username?.let { "@$it" } ?: value, target, kind ?: ChatReference.Kind.LINK)
    }

    LINK_OR_USERNAME.findAll(text).forEach { add(it.value, position = it.range.first) }
    entities.forEach { entity ->
        val start = entity.offset.coerceIn(0, text.length)
        val end = (entity.offset + entity.length).coerceIn(start, text.length)
        if (start >= end) return@forEach
        val display = text.substring(start, end)
        when (entity.type) {
            "textEntityTypeTextUrl" -> entity.argument?.let { add(it, ChatReference.Kind.LINK, start) }
            "textEntityTypeUrl" -> add(display, ChatReference.Kind.LINK, start)
            "textEntityTypeMention" -> add(display, ChatReference.Kind.USERNAME, start)
        }
    }
    candidates.sortedBy { it.first }.forEach { (_, reference) ->
        val canonical = "target:${reference.target.trimEnd('/').lowercase()}"
        unique.putIfAbsent(canonical, reference)
    }
    return unique.values.toList()
}

private val USERNAME = Regex("[A-Za-z][A-Za-z0-9_]{4,31}")
private val NUMERIC_CHAT_ID = Regex("-100\\d{5,20}")
private val LINK_OR_USERNAME = Regex(
    "(?i)(?<![\\w@])(?:https?://|tg://|www\\.|t\\.me/|telegram\\.me/)[^\\s<>()]+|(?<![\\w@])@[A-Za-z][A-Za-z0-9_]{4,31}|(?<![\\w-])-100\\d{5,20}(?!\\w)",
)
