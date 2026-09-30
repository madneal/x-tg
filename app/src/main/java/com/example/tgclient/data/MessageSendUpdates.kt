package com.example.tgclient.data

/** Reconciles TDLib's temporary outgoing message ID with its authoritative update. */
internal fun <T> reconcileOutgoingMessage(
    messages: List<T>,
    oldMessageId: Long,
    replacement: T,
    idOf: (T) -> Long,
): List<T> {
    val replacementId = idOf(replacement)
    return messages
        .filterNot { idOf(it) == oldMessageId || idOf(it) == replacementId }
        .plus(replacement)
}
