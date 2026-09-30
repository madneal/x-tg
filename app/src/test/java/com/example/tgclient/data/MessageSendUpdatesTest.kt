package com.example.tgclient.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageSendUpdatesTest {
    private data class Message(val id: Long, val text: String)

    @Test
    fun successReplacesTemporaryMessageIdWithoutDuplicate() {
        val before = listOf(Message(-7, "sending"), Message(12, "other"))

        val after = reconcileOutgoingMessage(before, -7, Message(25, "sent"), Message::id)

        assertEquals(listOf(Message(12, "other"), Message(25, "sent")), after)
    }

    @Test
    fun replacementAlsoReconcilesAlreadyObservedCanonicalMessage() {
        val before = listOf(Message(-7, "sending"), Message(25, "stale"), Message(12, "other"))

        val after = reconcileOutgoingMessage(before, -7, Message(25, "authoritative"), Message::id)

        assertEquals(listOf(Message(12, "other"), Message(25, "authoritative")), after)
    }
}
