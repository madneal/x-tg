package com.example.tgclient.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tgclient.TelegramApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountLimitTest {
    @Test
    fun accountSwitcherBoundsConcurrentTdlibClientsAtFour() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = (context.applicationContext as TelegramApplication).telegramAccountManager
        val originalId = manager.activeAccountId.value
        val addedAccounts = mutableListOf<String>()

        try {
            while (manager.canAddAccount()) addedAccounts += manager.createAccount()
            assertEquals(4, manager.accounts.value.size)
            assertFalse(manager.canAddAccount())
            assertTrue(runCatching { manager.createAccount() }.isFailure)
        } finally {
            manager.switchAccount(originalId)
            addedAccounts.forEach(manager::removeAccount)
        }
    }
}
