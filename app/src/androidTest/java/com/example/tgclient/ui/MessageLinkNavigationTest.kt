package com.example.tgclient.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tgclient.model.MessageSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageLinkNavigationTest {
    @Test
    fun plainTelegramResolveLinkIsClickableAndResolvesItsUsername() {
        val annotated = messageAnnotatedString(
            MessageSummary(id = 1, chatId = 1, senderName = "Test", text = "tg://resolve?domain=publicchannel"),
        )
        val annotation = annotated.getStringAnnotations("chatwave_link", 0, annotated.length).singleOrNull()

        assertNotNull("A plain tg:// link should be clickable", annotation)
        assertEquals("publicchannel", telegramChatTarget(annotation!!.item))
    }

    @Test
    fun telegramOpenMessageLinkResolvesAChannelNumericId() {
        assertEquals(
            "-1001234567890",
            telegramChatTarget("tg://openmessage?chat_id=-1001234567890"),
        )
    }
}
