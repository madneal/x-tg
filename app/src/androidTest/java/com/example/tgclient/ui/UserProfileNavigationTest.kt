package com.example.tgclient.ui

import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.tgclient.MainActivity
import com.example.tgclient.TelegramApplication
import com.example.tgclient.data.TelegramRepository
import com.example.tgclient.model.ChatSummary
import com.example.tgclient.model.MessageSummary
import com.example.tgclient.model.TelegramUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserProfileNavigationTest {
    @Test fun opensProfileFromGroupSenderName() = checkEntry(group = true)
    @Test fun opensProfileFromPrivateChatHeader() = checkEntry(group = false)

    private fun checkEntry(group: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as TelegramApplication
        val manager = app.telegramAccountManager
        val repository = manager.repository(manager.activeAccountId.value)
        val chatId = -900000000001L
        val userId = 900000000001L
        val chats = state<List<ChatSummary>>(repository, "_chats")
        val messages = state<Map<Long, List<MessageSummary>>>(repository, "_messages")
        val users = state<Map<Long, TelegramUser>>(repository, "_users")
        val chat = ChatSummary(chatId, "Profile QA conversation", isGroup = group, isPrivate = !group, userId = if (group) null else userId)
        chats.update { it + chat }
        users.update { it + (userId to TelegramUser(userId, "Profile QA User", username = "profile_qa")) }
        messages.update { it + (chatId to listOf(MessageSummary(id = 123, chatId = chatId, senderUserId = userId, senderName = "Profile QA User", text = "Profile entry test"))) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent { ChatwaveTheme { ConversationScreen(chatId, ChatwaveViewModel(repository), onBack = {}) } }
                }
                val device = UiDevice.getInstance(instrumentation)
                val entry = if (group) "Profile QA User" else "Profile QA conversation"
                assertTrue("Profile entry not shown", device.wait(Until.hasObject(By.text(entry)), 10000))
                device.findObject(By.text(entry)).click()
                assertTrue("Profile dialog did not open", device.wait(Until.hasObject(By.text("Send message")), 10000))
                assertTrue("Cached username not shown", device.hasObject(By.text("@profile_qa")))
                device.findObject(By.text("Close")).click()
            }
        } finally {
            chats.update { it.filterNot { c -> c.id == chatId } }
            messages.update { it - chatId }
            users.update { it - userId }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> state(repository: TelegramRepository, name: String): MutableStateFlow<T> =
        TelegramRepository::class.java.getDeclaredField(name).apply { isAccessible = true }.get(repository) as MutableStateFlow<T>
}
