package com.example.tgclient.data

import com.example.tgclient.model.MessageSummary
import java.util.concurrent.ConcurrentHashMap

/** Keeps late history/cache snapshots from reintroducing IDs already replaced by TDLib. */
internal class OutgoingMessageReplacements {
    private val superseded = ConcurrentHashMap.newKeySet<Pair<Long, Long>>()
    fun record(chatId: Long, oldId: Long, newId: Long) {
        if (oldId != newId) superseded.add(chatId to oldId)
    }
    fun filter(messages: Iterable<MessageSummary>): List<MessageSummary> =
        messages.filterNot { (it.chatId to it.id) in superseded }
}

/** TDLib YetUnsent IDs can be positive. Older archives did not store sending_state.
 * Layout reference: https://github.com/tdlib/td/blob/master/td/telegram/MessageId.h
 * Preserve local/secret-chat messages (type 2) and never deduplicate by text.
 */
internal fun isUnconfirmedOutgoingMessage(message: MessageSummary): Boolean =
    message.sendState != null || (message.isOutgoing && message.id > 0L && (message.id and 3L) == 1L)
