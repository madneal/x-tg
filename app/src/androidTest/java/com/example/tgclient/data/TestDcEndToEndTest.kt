package com.example.tgclient.data

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tgclient.BuildConfig
import com.example.tgclient.MainActivity
import com.example.tgclient.TelegramApplication
import com.example.tgclient.model.AuthAction
import com.example.tgclient.model.AuthState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Live TDLib smoke test for Telegram's disposable Test DCs; never runs against production by default. */
@RunWith(AndroidJUnit4::class)
class TestDcEndToEndTest {
    @Test
    fun testDcPhoneLoginSendAndServerSearchWorkEndToEnd() = runBlocking {
        assumeTrue("Enable only with -Ptelegram.useTestDc=true", BuildConfig.TELEGRAM_USE_TEST_DC)
        val arguments = InstrumentationRegistry.getArguments()
        val testPhone = arguments.getString("telegramTestPhone").orEmpty()
        val testCode = arguments.getString("telegramTestCode").orEmpty()
        assumeTrue(
            "Provide a pre-registered Test DC account with telegramTestPhone and telegramTestCode",
            testPhone.isNotBlank() && testCode.isNotBlank(),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TelegramApplication
        val accountManager = application.telegramAccountManager
        val originalAccountId = accountManager.activeAccountId.value
        // A fresh TDLib database avoids reusing a stale code/session from an earlier test run.
        val testAccountId = accountManager.createAccount()
        val repository = accountManager.repository(testAccountId)

        try {
            ActivityScenario.launch(MainActivity::class.java).use {
            var state = awaitAuthState(repository) { it != AuthState.Loading }
            if (state == AuthState.WaitPhoneNumber) {
                // Telegram reserves 99966XYYYY on Test DCs; the confirmation code is X repeated 5 times.
                repository.submitPhoneNumber(testPhone)
                state = awaitAuthState(repository) {
                    it == AuthState.WaitCode || it == AuthState.WaitRegistration || it == AuthState.WaitPassword || it is AuthState.Error
                }
                assertEquals(
                    "Test DC did not accept phone authorization (connection=${repository.connectionStatus.value}, requestError=${repository.authError.value})",
                    AuthState.WaitCode,
                    state,
                )
                repository.authAction.first { it == AuthAction.None }
                repository.submitCode(testCode)
                state = awaitAuthState(repository) {
                    it == AuthState.WaitRegistration || it == AuthState.WaitPassword || it == AuthState.Ready || it is AuthState.Error
                }
            }

            if (state == AuthState.WaitRegistration) {
                repository.authAction.first { it == AuthAction.None }
                repository.register("Chatwave", "QA")
                state = awaitAuthState(repository) { it == AuthState.Ready || it is AuthState.Error }
            }
            assertEquals(
                "Test DC account did not reach an authenticated state (requestError=${repository.authError.value})",
                AuthState.Ready,
                state,
            )

            val user = withTimeout(30_000) { repository.currentUser.first { it != null }!! }
            assertTrue("TDLib did not return the authenticated user", user.id > 0L)
            repository.loadChats()
            assertEquals("Chat history initialization returned an error", null, repository.chatListLoadState.value.error)

            val chatId = withTimeout(30_000) { repository.resolveChatTarget(user.id.toString()) }
                ?: error("Could not resolve the Test DC Saved Messages chat")
            val marker = "Chatwave QA ${System.currentTimeMillis()}"
            repository.sendText(chatId, marker)
            withTimeout(60_000) {
                repository.messages.first { chats ->
                    chats[chatId].orEmpty().any { it.text == marker && it.id > 0L && it.sendState == null }
                }
            }
            val serverResult = repository.searchChatMessages(chatId, marker, limit = 10)
            assertTrue("Telegram Test DC did not return the sent message from server search", serverResult.any { it.text == marker })
            }
        } finally {
            accountManager.switchAccount(originalAccountId)
            accountManager.removeAccount(testAccountId)
        }
    }

    private suspend fun awaitAuthState(repository: TelegramRepository, predicate: (AuthState) -> Boolean): AuthState =
        withTimeout(90_000) {
            while (true) {
                val state = repository.authState.value
                if (predicate(state)) return@withTimeout state
                if (repository.authError.value != null) return@withTimeout AuthState.Error("Telegram authorization request failed")
                delay(50)
            }
            error("Unreachable")
        }
}
