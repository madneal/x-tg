package com.example.tgclient.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.tgclient.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthenticationStartupTest {
    @Test
    fun unauthenticatedLaunchShowsPhoneLoginAndDoesNotBlockOnNotifications() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            assertTrue(
                "Phone login screen did not appear",
                device.wait(Until.hasObject(By.textContains("Log in with your Telegram phone number")), 20_000),
            )
            assertTrue("Phone number field is missing", device.hasObject(By.text("Phone number")))
            assertFalse(
                "Notification permission prompt blocked unauthenticated login",
                device.hasObject(By.textContains("Allow Chatwave to send you notifications")),
            )
        }
    }
}
