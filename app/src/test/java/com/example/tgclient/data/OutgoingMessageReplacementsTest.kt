package com.example.tgclient.data

import com.example.tgclient.model.MessageSummary
import com.example.tgclient.model.MessageSendState
import org.junit.Assert.*
import org.junit.Test

class OutgoingMessageReplacementsTest {
    private fun message(id: Long, chatId: Long = -100L) = MessageSummary(id, chatId, "Me", text = "same text", isOutgoing = true)

    @Test fun lateHistoryCannotReintroduceReplacedPositiveTemporaryId() {
        val replacements = OutgoingMessageReplacements()
        val temporary = message(1048577)
        val sent = message(2097152)
        replacements.record(-100, temporary.id, sent.id)
        val merged = mergeChronologicalMessages(replacements.filter(listOf(sent)), replacements.filter(listOf(temporary, sent)))
        assertEquals(listOf(sent), merged)
        assertEquals(listOf(temporary.copy(chatId = -200)), replacements.filter(listOf(temporary.copy(chatId = -200))))
    }
    @Test fun intentionallyRepeatedMessagesWithDifferentServerIdsArePreserved() {
        val replacements = OutgoingMessageReplacements()
        val messages = listOf(message(2097152), message(3145728))
        assertEquals(messages, replacements.filter(messages))
    }
    @Test fun retentionExcludesSendingAndFailedButKeepsConfirmedAndLocalMessages() {
        assertTrue(isUnconfirmedOutgoingMessage(message(1048577)))
        assertTrue(isUnconfirmedOutgoingMessage(message(2097152).copy(sendState = MessageSendState.FAILED)))
        assertFalse(isUnconfirmedOutgoingMessage(message(2097152)))
        assertFalse(isUnconfirmedOutgoingMessage(message(1048578)))
        assertFalse(isUnconfirmedOutgoingMessage(message(1048577).copy(isOutgoing = false)))
    }
}
