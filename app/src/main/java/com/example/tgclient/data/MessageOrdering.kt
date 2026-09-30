package com.example.tgclient.data

import com.example.tgclient.model.MessageSummary
import java.util.Comparator

private val messageChronologicalOrder: Comparator<MessageSummary> =
    compareBy<MessageSummary> { it.dateEpochSeconds }
        .thenBy { it.id < 0L }
        .thenBy { it.id }

/** Orders history chronologically while keeping local negative-ID sends after confirmed messages. */
internal fun sortMessagesChronologically(messages: Iterable<MessageSummary>): List<MessageSummary> =
    messages
        .filter { it.id != 0L }
        .associateBy { it.id }
        .values
        .sortedWith(messageChronologicalOrder)

/** Merges small TDLib update batches into an already chronological conversation list. */
internal fun mergeChronologicalMessages(
    existing: List<MessageSummary>,
    incoming: Iterable<MessageSummary>,
): List<MessageSummary> {
    val byId = incoming
        .filter { it.id != 0L }
        .associateBy { it.id }
    if (byId.isEmpty()) return existing
    val history = existing.filterNot { it.id in byId }
    val updates = byId.values
        .sortedWith(messageChronologicalOrder)
    if (history.isEmpty()) return updates

    val merged = ArrayList<MessageSummary>(history.size + updates.size)
    var oldIndex = 0
    var newIndex = 0
    while (oldIndex < history.size && newIndex < updates.size) {
        val old = history[oldIndex]
        val update = updates[newIndex]
        if (messageChronologicalOrder.compare(old, update) <= 0) {
            merged += history[oldIndex++]
        } else {
            merged += updates[newIndex++]
        }
    }
    while (oldIndex < history.size) merged += history[oldIndex++]
    while (newIndex < updates.size) merged += updates[newIndex++]
    return merged
}
