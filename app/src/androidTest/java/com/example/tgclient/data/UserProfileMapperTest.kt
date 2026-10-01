package com.example.tgclient.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tgclient.model.TelegramUser
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserProfileMapperTest {
    @Test fun readsActiveUsernamesAndLegacyFallback() {
        assertEquals("alice", activeUsername(JSONObject("""{"usernames":{"active_usernames":["alice","other"]}}""")))
        assertEquals("legacy", activeUsername(JSONObject("""{"username":"legacy"}""")))
        assertNull(activeUsername(JSONObject("""{"usernames":{"active_usernames":[]}}""")))
    }

    @Test fun preservesNegativePersonalChannelIdAndFormattedBio() {
        val profile = mapUserProfile(TelegramUser(42L, "Alice"), JSONObject("""{"bio":{"text":"Hello"},"personal_chat_id":-1001234567890}"""))
        assertEquals(-1001234567890L, profile.personalChatId)
        assertEquals("Hello", profile.bio)
    }

    @Test fun absentChannelAndBioDoNotCreateAnInvalidNavigationTarget() {
        val profile = mapUserProfile(TelegramUser(42L, "Alice"), JSONObject("""{"personal_chat_id":0}"""))
        assertNull(profile.personalChatId)
        assertEquals("", profile.bio)
    }
}
