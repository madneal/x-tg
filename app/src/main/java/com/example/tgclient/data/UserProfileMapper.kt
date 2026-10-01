package com.example.tgclient.data

import com.example.tgclient.model.TelegramUser
import com.example.tgclient.model.UserProfile
import org.json.JSONObject

internal fun activeUsername(user: JSONObject): String? =
    user.optJSONObject("usernames")?.optJSONArray("active_usernames")?.optString(0)?.takeIf { it.isNotBlank() }
        ?: user.optString("username").takeIf { it.isNotBlank() }

internal fun mapUserProfile(user: TelegramUser, full: JSONObject): UserProfile = UserProfile(
    user = user,
    bio = full.optJSONObject("bio")?.optString("text").orEmpty(),
    personalChatId = full.optLong("personal_chat_id").takeIf { it != 0L },
)
