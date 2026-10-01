package com.example.tgclient.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.tgclient.MainActivity
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduledMessageUiTest {
    @Test fun failedRequestKeepsSchedulingDialogOpen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val calls = AtomicInteger()
            scenario.onActivity { activity ->
                activity.setContent {
                    ChatwaveTheme {
                        ScheduleMessageDialog(onDismiss = {}, onSchedule = {
                            calls.incrementAndGet()
                            throw IllegalStateException("Server rejected schedule")
                        })
                    }
                }
            }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            assertTrue(device.wait(Until.hasObject(By.text("Schedule")), 10000))
            device.findObject(By.text("Schedule")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Server rejected schedule")), 10000))
            assertEquals(1, calls.get())
            assertTrue(device.hasObject(By.text("Schedule message")))
        }
    }

    @Test fun successfulRequestUsesFutureTimestampAndClosesDialog() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val calls = AtomicInteger()
            scenario.onActivity { activity ->
                activity.setContent {
                    var shown by remember { mutableStateOf(true) }
                    ChatwaveTheme {
                        if (shown) ScheduleMessageDialog(onDismiss = { shown = false }, onSchedule = { sendAt ->
                            assertTrue(sendAt > System.currentTimeMillis() / 1000 + 3000)
                            calls.incrementAndGet()
                        }) else Text("Schedule accepted", Modifier.padding(64.dp))
                    }
                }
            }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            assertTrue(device.wait(Until.hasObject(By.text("Schedule")), 10000))
            device.findObject(By.text("Schedule")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Schedule accepted")), 10000))
            assertEquals(1, calls.get())
        }
    }
}
