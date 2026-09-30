package com.example.tgclient.data

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.tgclient.MainActivity
import com.example.tgclient.TelegramApplication
import com.example.tgclient.model.TdConnectionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkChangeRecoveryTest {
    @Test
    fun tdlibRecoversAfterEmulatorNetworkIsTemporarilyLost() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as TelegramApplication
        val repository = application.telegramAccountManager.repository(
            application.telegramAccountManager.activeAccountId.value,
        )
        val device = UiDevice.getInstance(instrumentation)
        var airplaneModeEnabled = false

        ActivityScenario.launch(MainActivity::class.java).use {
            withTimeout(45_000) {
                repository.connectionStatus.first { it == TdConnectionStatus.READY }
            }
            try {
                device.executeShellCommand("cmd connectivity airplane-mode enable")
                airplaneModeEnabled = true
                val lostConnection = withTimeoutOrNull(20_000) {
                    repository.connectionStatus.first {
                        it != TdConnectionStatus.READY && it != TdConnectionStatus.UNKNOWN
                    }
                }
                assertTrue("TDLib did not report a disconnected state", lostConnection != null)
            } finally {
                if (airplaneModeEnabled) {
                    device.executeShellCommand("cmd connectivity airplane-mode disable")
                }
            }

            withTimeout(60_000) {
                repository.connectionStatus.first { it == TdConnectionStatus.READY }
            }
        }
        Unit
    }
}
