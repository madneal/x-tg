package com.example.tgclient.data

import com.example.tgclient.model.MessageSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageOrderingTest {
    @Test
    fun localNegativeIdDoesNotJumpAheadOfConfirmedMessages() {
        val first = MessageSummary(id = 50, chatId = 1, senderName = "A", text = "first", dateEpochSeconds = 10)
        val second = MessageSummary(id = 51, chatId = 1, senderName = "A", text = "second", dateEpochSeconds = 11)
        val sending = MessageSummary(id = -1, chatId = 1, senderName = "Me", text = "sending", dateEpochSeconds = 11)

        assertEquals(listOf(first, second, sending), sortMessagesChronologically(listOf(sending, second, first)))
    }

    @Test
    fun canonicalIdsStillFollowMessageTimeAndDuplicateIdsCollapse() {
        val duplicateOld = MessageSummary(id = 20, chatId = 1, senderName = "A", text = "old", dateEpochSeconds = 12)
        val duplicateNew = duplicateOld.copy(text = "updated")
        val earlier = MessageSummary(id = 30, chatId = 1, senderName = "A", text = "earlier", dateEpochSeconds = 9)

        assertEquals(listOf(earlier, duplicateNew), sortMessagesChronologically(listOf(duplicateOld, earlier, duplicateNew)))
    }

    @Test
    fun incrementalUpdatesMergeIntoSortedHistoryAndReplaceOverlappingIds() {
        val first = MessageSummary(id = 1, chatId = 1, senderName = "A", text = "first", dateEpochSeconds = 1)
        val second = MessageSummary(id = 2, chatId = 1, senderName = "A", text = "old", dateEpochSeconds = 2)
        val third = MessageSummary(id = 3, chatId = 1, senderName = "A", text = "third", dateEpochSeconds = 3)
        val updatedSecond = second.copy(text = "updated")
        val middle = MessageSummary(id = 4, chatId = 1, senderName = "B", text = "middle", dateEpochSeconds = 2)

        assertEquals(
            listOf(first, updatedSecond, middle, third),
            mergeChronologicalMessages(listOf(first, second, third), listOf(middle, second, updatedSecond)),
        )
    }

    @Test
    fun incrementalUpdateMovesAnExistingMessageWhenItsSortDateChanges() {
        val first = MessageSummary(id = 1, chatId = 1, senderName = "A", text = "first", dateEpochSeconds = 1)
        val moved = MessageSummary(id = 2, chatId = 1, senderName = "A", text = "moved", dateEpochSeconds = 4)
        val third = MessageSummary(id = 3, chatId = 1, senderName = "A", text = "third", dateEpochSeconds = 3)

        assertEquals(
            listOf(first, third, moved),
            mergeChronologicalMessages(listOf(first, moved.copy(dateEpochSeconds = 2), third), listOf(moved)),
        )
    }
}
