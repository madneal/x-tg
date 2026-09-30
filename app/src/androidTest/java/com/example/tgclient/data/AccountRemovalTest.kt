package com.example.tgclient.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tgclient.BuildConfig
import com.example.tgclient.TelegramApplication
import com.example.tgclient.model.MessageSummary
import com.example.tgclient.security.MessageRetentionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AccountRemovalTest {
    @Test
    fun removesAccountDatabaseAndWrappedKeyFromDevice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TelegramApplication
        val accountManager = application.telegramAccountManager
        val accountId = accountManager.createAccount()
        val originalAccountId = accountManager.accounts.value.first { it.id != accountId }.id
        val databaseRoot = if (BuildConfig.TELEGRAM_USE_TEST_DC) "tdlib-test" else "tdlib"
        val accountRoot = File(context.noBackupFilesDir, "$databaseRoot/accounts/$accountId")
        val retainedMessagesDatabase = File(context.noBackupFilesDir, "retained-messages/$accountId.db")
        val retentionStore = MessageRetentionStore(context, accountId)
        val keyPreferences = context.getSharedPreferences("secure_database_key.$accountId", 0)

        try {
            withTimeout(20_000) {
                while (!File(accountRoot, ".database-key").isFile) delay(100)
            }
            retentionStore.saveAll(
                listOf(MessageSummary(id = 1L, chatId = 99L, senderName = "Test", text = "retained")),
            )
            retentionStore.close()

            assertTrue("Account was not removed from the account switcher", accountManager.removeAccount(accountId))
            assertNotEquals("Removing the active account did not switch to another account", accountId, accountManager.activeAccountId.value)
            withTimeout(15_000) {
                while (accountRoot.exists() || keyPreferences.contains("wrapped_key")) delay(100)
            }

            assertFalse("Removed TDLib database remains on disk", accountRoot.exists())
            assertFalse("Removed account's local message-retention database remains on disk", retainedMessagesDatabase.exists())
            assertNull("Removed database key remains in preferences", keyPreferences.getString("wrapped_key", null))
            assertTrue("Original account disappeared during removal", accountManager.accounts.value.any { it.id == originalAccountId })
        } finally {
            if (accountManager.accounts.value.any { it.id == accountId }) accountManager.removeAccount(accountId)
        }
    }
}
