package com.example.tgclient.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPaginationTest {
    private data class Message(val id: Long)

    @Test
    fun emptyLastAllowedPageRemainsExhausted() = runBlocking {
        var requests = 0
        val result = paginateHistory(10L, 50, 50, 2, Message::id) { _, _ ->
            if (requests++ == 0) HistoryPage(listOf(Message(9)), 9)
            else HistoryPage(emptyList(), 0)
        }
        assertFalse(result.hasMore)
        assertEquals(9L, result.oldestMessageId)
    }

    @Test
    fun retriesFromEndOnceWhenTdlibInitiallyReturnsOnlyLatestChannelMessage() = runBlocking {
        val cursors = mutableListOf<Long>()
        var tailRequests = 0
        val result = paginateHistory(
            initialFromMessageId = 0,
            requestedCount = 3,
            pageLimit = 50,
            maxPages = 8,
            idOf = Message::id,
        ) { cursor, _ ->
            cursors += cursor
            if (cursor == 0L && tailRequests++ == 0) {
                HistoryPage(listOf(Message(10)), 10)
            } else if (cursor == 0L) {
                HistoryPage(listOf(Message(10), Message(9), Message(8)), 8)
            } else {
                HistoryPage(emptyList(), 0)
            }
        }

        assertEquals(listOf(0L, 0L), cursors)
        assertEquals(listOf(10L, 9L, 8L), result.messages.map(Message::id))
        assertEquals(8L, result.oldestMessageId)
        assertTrue(result.hasMore)
    }

    @Test
    fun advancesToTheOldestCursorIfTailRetryStillHasNoNewMessages() = runBlocking {
        val cursors = mutableListOf<Long>()
        val result = paginateHistory(
            initialFromMessageId = 0,
            requestedCount = 3,
            pageLimit = 50,
            maxPages = 8,
            idOf = Message::id,
        ) { cursor, _ ->
            cursors += cursor
            when (cursor) {
                0L -> HistoryPage(listOf(Message(10)), 10)
                10L -> HistoryPage(listOf(Message(10), Message(9), Message(8)), 8)
                else -> HistoryPage(emptyList(), 0)
            }
        }

        assertEquals(listOf(0L, 0L, 10L), cursors)
        assertEquals(listOf(10L, 9L, 8L), result.messages.map(Message::id))
        assertEquals(8L, result.oldestMessageId)
        assertTrue(result.hasMore)
    }

    @Test
    fun advancesFromEachShortOverlappingPageUntilItHasEnoughUniqueMessages() = runBlocking {
        val cursors = mutableListOf<Long>()
        val result = paginateHistory(
            initialFromMessageId = 0,
            requestedCount = 4,
            pageLimit = 50,
            maxPages = 8,
            idOf = Message::id,
        ) { cursor, _ ->
            cursors += cursor
            when (cursor) {
                0L -> HistoryPage(listOf(Message(100), Message(99)), 99)
                99L -> HistoryPage(listOf(Message(99), Message(98)), 98)
                98L -> HistoryPage(listOf(Message(98), Message(97)), 97)
                else -> HistoryPage(emptyList(), 0)
            }
        }

        assertEquals(listOf(0L, 0L, 99L, 98L), cursors)
        assertEquals(listOf(100L, 99L, 98L, 97L), result.messages.map(Message::id))
        assertEquals(97L, result.oldestMessageId)
        assertTrue(result.hasMore)
    }

    @Test
    fun stopsWhenTdlibReturnsTheSameCursorWithoutOlderMessages() = runBlocking {
        val cursors = mutableListOf<Long>()
        val result = paginateHistory(
            initialFromMessageId = 0,
            requestedCount = 20,
            pageLimit = 50,
            maxPages = 8,
            idOf = Message::id,
        ) { cursor, _ ->
            cursors += cursor
            HistoryPage(listOf(Message(10)), 10)
        }

        assertEquals(listOf(0L, 0L, 10L), cursors)
        assertEquals(listOf(10L), result.messages.map(Message::id))
        assertFalse(result.hasMore)
    }

    @Test
    fun advancesUsingLastResponseIdEvenWhenItIsALocalNegativeId() = runBlocking {
        val cursors = mutableListOf<Long>()
        val result = paginateHistory(
            initialFromMessageId = 0,
            requestedCount = 3,
            pageLimit = 2,
            maxPages = 8,
            idOf = Message::id,
        ) { cursor, _ ->
            cursors += cursor
            when (cursor) {
                0L -> HistoryPage(listOf(Message(100), Message(-2)), -2)
                -2L -> HistoryPage(listOf(Message(99), Message(98)), 98)
                else -> HistoryPage(emptyList(), 0)
            }
        }

        assertEquals(listOf(0L, 0L, -2L), cursors)
        assertEquals(listOf(100L, -2L, 99L, 98L), result.messages.map(Message::id))
        assertEquals(98L, result.oldestMessageId)
        assertTrue(result.hasMore)
    }
}
