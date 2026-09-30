package com.example.tgclient.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatListOrderingTest {
    private data class Chat(val id: Long, val order: Long?)

    @Test
    fun sortsByTdlibPositionThenChatIdAndOmitsChatsOutsideTheList() {
        val chats = listOf(
            Chat(id = 1, order = 10),
            Chat(id = 8, order = 20),
            Chat(id = 4, order = null),
            Chat(id = 9, order = 10),
        )

        val result = sortChatsByPosition(chats, Chat::id, Chat::order)

        assertEquals(listOf(8L, 9L, 1L), result.map(Chat::id))
    }
}
