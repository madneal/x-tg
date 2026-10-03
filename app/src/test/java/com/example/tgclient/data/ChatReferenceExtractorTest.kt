package com.example.tgclient.data

import com.example.tgclient.model.ChatReference
import com.example.tgclient.model.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReferenceExtractorTest {
    @Test fun extractsAndDeduplicatesLinksUsernamesAndIdsInFirstSeenOrder() {
        val result = extractChatReferences(
            "Visit https://Example.com/a, @ChannelName and t.me/ChannelName. Also -1001234567890.",
        )
        assertEquals(3, result.size)
        assertEquals("https://Example.com/a", result[0].value)
        assertEquals(ChatReference.Kind.USERNAME, result[1].kind)
        assertEquals("@ChannelName", result[1].value)
        assertTrue(result[2].target.startsWith("tg://openmessage?chat_id=-100"))
    }

    @Test fun includesHiddenTextUrlAndMentionEntities() {
        val result = extractChatReferences(
            "Read this and @Alice", listOf(
                MessageEntity(0, 4, "textEntityTypeTextUrl", "https://example.com"),
                MessageEntity(14, 6, "textEntityTypeMention"),
            ),
        )
        assertEquals(listOf("https://example.com", "@Alice"), result.map { it.value })
        assertEquals("https://example.com", result[0].target)
    }
}
