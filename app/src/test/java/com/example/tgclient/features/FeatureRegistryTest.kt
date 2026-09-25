package com.example.tgclient.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureRegistryTest {
    @Test
    fun coreMilestoneIsRepresented() {
        val core = FeatureRegistry.capabilities.filter { it.status == FeatureStatus.IMPLEMENTED }
        assertTrue(core.any { it.id == "auth" })
        assertTrue(core.any { it.id == "chat_list" })
        assertTrue(core.any { it.id == "text_messages" })
    }

    @Test
    fun advancedFeaturesRemainTrackedAsPlanned() {
        assertEquals(FeatureStatus.PLANNED, FeatureRegistry.capabilities.first { it.id == "calls" }.status)
        assertEquals(FeatureStatus.PLANNED, FeatureRegistry.capabilities.first { it.id == "secret_chats" }.status)
    }
}
