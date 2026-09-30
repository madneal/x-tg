package com.example.tgclient.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AccountStorageTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun removingDefaultAccountPreservesOtherAccountSessions() {
        val root = temporary.newFolder("tdlib")
        val defaultDatabase = File(root, "database/db.sqlite").apply { parentFile!!.mkdirs(); writeText("default") }
        val otherDatabase = File(root, "accounts/second/database/db.sqlite").apply { parentFile!!.mkdirs(); writeText("session") }
        val otherKey = File(root, "accounts/second/.database-key").apply { writeText("wrapped-key") }
        deleteAccountDirectory(root, "default")
        assertFalse(defaultDatabase.exists())
        assertEquals("session", otherDatabase.readText())
        assertEquals("wrapped-key", otherKey.readText())
    }

    @Test fun removingSecondaryAccountPreservesDefaultAndOtherAccounts() {
        val root = temporary.newFolder("tdlib")
        val original = File(root, ".database-key").apply { writeText("default-key") }
        val removed = File(root, "accounts/second/database").apply { mkdirs() }
        val remaining = File(root, "accounts/third/.database-key").apply { parentFile!!.mkdirs(); writeText("third-key") }
        deleteAccountDirectory(root, "second")
        assertFalse(removed.parentFile!!.exists())
        assertEquals("default-key", original.readText())
        assertEquals("third-key", remaining.readText())
    }
}
