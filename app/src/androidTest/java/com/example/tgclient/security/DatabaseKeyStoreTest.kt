package com.example.tgclient.security

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class DatabaseKeyStoreTest {
    @Test
    fun wrappedDatabaseKeySurvivesPreferenceLossAndRemainsEncryptedAtRest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val accountId = "test_${UUID.randomUUID().toString().replace("-", "")}"
        val store = DatabaseKeyStore(context, accountId)
        val root = File(context.noBackupFilesDir, "tdlib/accounts/$accountId")
        val preferences = context.getSharedPreferences("secure_database_key.$accountId", 0)

        try {
            val firstKey = store.getOrCreateKey()
            val storedValue = preferences.getString("wrapped_key", null)
            assertTrue("Wrapped database key was not persisted", !storedValue.isNullOrBlank())
            assertNotEquals(
                "Database key must not be stored in plaintext or as plain Base64",
                Base64.encodeToString(firstKey, Base64.NO_WRAP),
                storedValue,
            )
            assertArrayEquals(firstKey, DatabaseKeyStore(context, accountId).getOrCreateKey())

            // The encrypted sidecar is intentionally independent from SharedPreferences,
            // which may be interrupted during app process shutdown.
            assertTrue(preferences.edit().remove("wrapped_key").commit())
            assertArrayEquals(firstKey, DatabaseKeyStore(context, accountId).getOrCreateKey())

            // A damaged preference copy must also fall back to the valid encrypted file.
            assertTrue(preferences.edit().putString("wrapped_key", "corrupt").commit())
            assertArrayEquals(firstKey, DatabaseKeyStore(context, accountId).getOrCreateKey())
        } finally {
            store.clear()
            preferences.edit().clear().commit()
            root.deleteRecursively()
        }
    }
}
