package com.example.tgclient.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tgclient.data.mapUserProfile
import com.example.tgclient.model.MessageSummary
import com.example.tgclient.model.TelegramUser
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileBioLinksTest {
    @Test fun bioRetainsHiddenTextUrlsAndDetectsPlainMentions() {
        val full = JSONObject("""{"bio":{"text":"Site @channelname https://example.org","entities":[{"offset":0,"length":4,"type":{"@type":"textEntityTypeTextUrl","url":"https://example.com"}}]}}""")
        val profile = mapUserProfile(TelegramUser(1, "Test"), full)
        val annotated = messageAnnotatedString(MessageSummary(1, 1, "Test", text = profile.bio, entities = profile.bioEntities))
        val targets = annotated.getStringAnnotations("chatwave_link", 0, annotated.length).map { it.item }
        assertTrue(targets.contains("https://example.com"))
        assertTrue(targets.contains("https://example.org"))
        assertTrue(targets.any { telegramChatTarget(it) == "channelname" })
    }
}
