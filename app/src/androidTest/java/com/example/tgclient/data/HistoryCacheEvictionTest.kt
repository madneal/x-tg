package com.example.tgclient.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tgclient.TelegramApplication
import com.example.tgclient.model.MessageSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryCacheEvictionTest {
    @Test fun cacheEvictionDoesNotRemoveOrMarkVisibleMessagesAsDeleted() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TelegramApplication
        val manager = app.telegramAccountManager
        val repository = manager.repository(manager.activeAccountId.value)
        val chatId = Long.MIN_VALUE + 123L
        @Suppress("UNCHECKED_CAST")
        val messages = TelegramRepository::class.java.getDeclaredField("_messages").apply { isAccessible = true }
            .get(repository) as MutableStateFlow<Map<Long, List<MessageSummary>>>
        val expected = listOf(MessageSummary(id = 42L, chatId = chatId, senderName = "QA", text = "Cached history"))
        messages.update { it + (chatId to expected) }
        try {
            val update = JSONObject().put("chat_id", chatId).put("message_ids", JSONArray().put(42L))
                .put("from_cache", true).put("is_permanent", false)
            TelegramRepository::class.java.getDeclaredMethod("deletePublishedMessages", JSONObject::class.java)
                .apply { isAccessible = true }.invoke(repository, update)
            assertEquals(expected, messages.value[chatId])
        } finally {
            messages.update { it - chatId }
        }
    }
}
