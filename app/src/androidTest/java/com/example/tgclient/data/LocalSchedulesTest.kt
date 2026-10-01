package com.example.tgclient.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LocalSchedulesTest {
    @Test fun persistentClaimPreventsDuplicateAndCancellationRemovesTask() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val account = "schedule_test"
        val store = LocalSchedules(context, account)
        store.clear()
        try {
            val at = System.currentTimeMillis() / 1000 + 3600
            val fields = JSONObject().put("chat_id", 123).put("input_message_content", JSONObject())
            val id = store.add(fields, "test", at, 86400, null)
            val restored = LocalSchedules(context, account)
            assertEquals(1, restored.list(123).size)
            assertTrue(restored.list(456).isEmpty())
            assertNotNull(restored.claim(id, at))
            assertNull(store.claim(id, at))
            assertTrue(restored.list(123).single().status.startsWith("Paused"))
            restored.accepted(id, at)
            assertEquals(at + 86400, store.list(123).single().sendAt)
            store.cancel(id)
            assertTrue(restored.list(123).isEmpty())
        } finally { store.clear() }
    }
    @Test fun mediaSurvivesCacheRemovalAndAccountsAreIsolated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LocalSchedules(context, "schedule_media_test")
        val other = LocalSchedules(context, "schedule_other_test")
        store.clear(); other.clear()
        val source = java.io.File(context.cacheDir, "scheduled-test.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            val fields = JSONObject().put("chat_id", 123).put("input_message_content", JSONObject()
                .put("photo", JSONObject().put("@type", "inputFileLocal").put("path", source.absolutePath)))
            val at = System.currentTimeMillis() / 1000 + 3600
            val id = store.add(fields, "private-test-caption", at, 0, source.absolutePath)
            source.delete()
            val restored = LocalSchedules(context, "schedule_media_test")
            val copied = java.io.File(restored.get(id)!!.getJSONObject("fields").getJSONObject("input_message_content").getJSONObject("photo").getString("path"))
            assertArrayEquals(byteArrayOf(1, 2, 3), copied.readBytes())
            assertTrue(other.list(123).isEmpty())
            assertFalse(java.io.File(context.noBackupFilesDir, "local-schedules/schedule_media_test/jobs").readText().contains("private-test-caption"))
            assertNotNull(store.claim(id, at))
            store.accepted(id, at)
            assertFalse(copied.exists())
            assertTrue(restored.list(123).isEmpty())
        } finally { store.clear(); other.clear(); source.delete() }
    }

    @Test fun missedDaysAdvanceWithoutBurst() {
        assertEquals(100L + 86400 * 4, nextLocalOccurrence(100, 100 + 86400 * 3 + 400))
        assertEquals(86500L, nextLocalOccurrence(100, 100))
    }
}
