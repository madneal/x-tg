package com.example.tgclient.data

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.tgclient.MainActivity
import com.example.tgclient.TelegramApplication
import com.example.tgclient.model.AuthState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountSwitchLifecycleTest {
    @Test
    fun switchingAccountsPreservesTheOriginalTdlibSessionAndAuthorizationState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as TelegramApplication
        val accountManager = application.telegramAccountManager
        val originalAccountId = accountManager.activeAccountId.value
        val originalRepository = accountManager.repository(originalAccountId)
        var temporaryAccountId: String? = null
        val device = UiDevice.getInstance(instrumentation)

        ActivityScenario.launch(MainActivity::class.java).use {
            assertPhoneLoginVisible(device)
            try {
                val stateBeforeSwitch = runBlocking {
                    withTimeout(20_000) {
                        originalRepository.authState.first {
                            it == AuthState.Ready || it == AuthState.WaitPhoneNumber
                        }
                    }
                }
                temporaryAccountId = accountManager.createAccount()
                assertNotEquals(originalAccountId, temporaryAccountId)
                assertEquals(temporaryAccountId, accountManager.activeAccountId.value)

                accountManager.switchAccount(originalAccountId)
                assertEquals(originalAccountId, accountManager.activeAccountId.value)
                assertSame("Switching accounts recreated the original TDLib session", originalRepository, accountManager.repository(originalAccountId))
                assertEquals("The original account authorization state changed during switching", stateBeforeSwitch, originalRepository.authState.value)
                assertPhoneLoginVisible(device)
            } finally {
                accountManager.switchAccount(originalAccountId)
                temporaryAccountId?.let(accountManager::removeAccount)
            }
        }
    }

    private fun assertPhoneLoginVisible(device: UiDevice) {
        assertTrue(
            "Phone login screen did not appear after account transition",
            device.wait(Until.hasObject(By.textContains("Log in with your Telegram phone number")), 20_000),
        )
        assertTrue(
            "Phone number field is missing",
            device.wait(Until.hasObject(By.textContains("Phone number")), 5_000),
        )
        assertFalse(
            "Notification permission prompt blocked unauthenticated login",
            device.hasObject(By.textContains("Allow Chatwave to send you notifications")),
        )
    }
}
