package com.example.tgclient.data

/** Sorts chats using TDLib's order for a particular chat list. */
internal fun <T> sortChatsByPosition(
    chats: Collection<T>,
    chatId: (T) -> Long,
    positionOrder: (T) -> Long?,
): List<T> = chats
    .mapNotNull { chat -> positionOrder(chat)?.takeIf { it != 0L }?.let { order -> chat to order } }
    .sortedWith(compareByDescending<Pair<T, Long>> { it.second }.thenByDescending { chatId(it.first) })
    .map { it.first }
