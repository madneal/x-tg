package com.example.tgclient.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryMessageAccumulatorTest {
    private data class Message(val id: Long, val text: String)

    @Test
    fun overlappingPagesDoNotCountDuplicatesTowardRequestedHistorySize() {
        val pages = HistoryMessageAccumulator(Message::id)
        pages.addAll(listOf(Message(90, "newest"), Message(89, "older")))
        pages.addAll(listOf(Message(90, "overlap"), Message(89, "overlap"), Message(88, "next page")))

        assertEquals(3, pages.size)
        assertEquals(listOf(90L, 89L, 88L), pages.toList().map(Message::id))
        assertEquals("newest", pages.toList().first().text)
    }

    @Test
    fun keepsLocalNegativeIdsButIgnoresZeroIds() {
        val pages = HistoryMessageAccumulator(Message::id)
        pages.addAll(listOf(Message(0, "invalid"), Message(-1, "invalid"), Message(1, "valid")))

        assertEquals(listOf(Message(-1, "invalid"), Message(1, "valid")), pages.toList())
    }
}
