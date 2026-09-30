package com.example.tgclient.data

internal data class HistoryPage<T>(val messages: List<T>, val oldestMessageId: Long)

internal data class HistoryPaginationResult<T>(
    val messages: List<T>,
    val oldestMessageId: Long,
    val hasMore: Boolean,
)

/** Follows the last-message ID cursor from TDLib, tolerating overlaps and short pages. */
internal suspend fun <T> paginateHistory(
    initialFromMessageId: Long,
    requestedCount: Int,
    pageLimit: Int,
    maxPages: Int,
    idOf: (T) -> Long,
    fetchPage: suspend (fromMessageId: Long, limit: Int) -> HistoryPage<T>,
): HistoryPaginationResult<T> {
    val targetCount = requestedCount.coerceAtLeast(1)
    val limit = pageLimit.coerceIn(1, 100)
    val pageCap = maxPages.coerceAtLeast(1)
    val loaded = HistoryMessageAccumulator(idOf)
    var fromMessageId = initialFromMessageId
    var oldestMessageId = 0L
    var pageCount = 0
    var hasMore = true
    var repeatedInitialTailRequest = false

    while (loaded.size < targetCount && pageCount < pageCap) {
        // TDLib may return only the latest message on the first non-local
        // request while it starts preloading older channel history. Repeating
        // the from-end request once lets that preload become visible; moving
        // immediately to the returned ID can otherwise make the cursor appear
        // exhausted after that single message.
        val requestFromMessageId = if (
            initialFromMessageId == 0L &&
            pageCount == 1 &&
            !repeatedInitialTailRequest &&
            loaded.size < targetCount
        ) {
            repeatedInitialTailRequest = true
            initialFromMessageId
        } else {
            fromMessageId
        }
        val page = fetchPage(requestFromMessageId, limit)
        pageCount += 1
        if (page.messages.isEmpty()) {
            hasMore = false
            break
        }

        if (page.oldestMessageId != 0L) oldestMessageId = page.oldestMessageId
        loaded.addAll(page.messages)
        if (page.oldestMessageId == 0L || page.oldestMessageId == requestFromMessageId) {
            hasMore = false
            break
        }
        fromMessageId = page.oldestMessageId
    }

    return HistoryPaginationResult(loaded.toList(), oldestMessageId, hasMore)
}
