package com.example.tgclient.security

import androidx.test.platform.app.InstrumentationRegistry
import com.example.tgclient.model.MediaType
import com.example.tgclient.model.MessageSummary
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

class MessageRetentionStoreTest {
    @Test
    fun neverArchivesPendingOrFailedOutgoingMessages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = MessageRetentionStore(context, "outgoing_${UUID.randomUUID()}")
        val sent = MessageSummary(2097152L, -100L, "Me", text = "hello", isOutgoing = true)
        try {
            store.saveAll(listOf(
                sent.copy(id = 1048577, sendState = com.example.tgclient.model.MessageSendState.SENDING),
                sent.copy(id = 1048585, sendState = com.example.tgclient.model.MessageSendState.FAILED), sent,
            ))
            assertEquals(listOf(sent.id), store.loadChat(-100L).map { it.id })
        } finally { store.clear() }
    }

    @Test
    fun upgradeRemovesLegacySendingPlaceholderButKeepsRepeatedConfirmedMessages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val accountId = "outgoing_migration_${UUID.randomUUID()}"
        val store = MessageRetentionStore(context, accountId)
        val archive = File(context.noBackupFilesDir, "retained-messages/$accountId.archive")
        val records = JSONArray()
        listOf(1048577L, 2097152L, 3145728L).forEach { id ->
            records.put(JSONObject().put("id", id).put("chat_id", -100L).put("sender_name", "Me")
                .put("text", "same text").put("date", 1700000000).put("is_outgoing", true))
        }
        try {
            val encrypt = MessageRetentionStore::class.java.getDeclaredMethod("encryptRecord", JSONObject::class.java).apply { isAccessible = true }
            archive.parentFile?.mkdirs()
            archive.writeBytes(encrypt.invoke(store, JSONObject().put("messages", records)) as ByteArray)
            assertEquals(listOf(2097152L, 3145728L), store.loadChat(-100L).map { it.id })
            store.close()
            assertEquals(listOf(2097152L, 3145728L), store.loadChat(-100L).map { it.id })
        } finally { store.clear(); archive.delete() }
    }

    @Test
    fun migratesExistingEncryptedJsonArchiveWithoutLosingMessages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val accountId = "migration_${UUID.randomUUID().toString().replace("-", "")}"
        val root = File(context.noBackupFilesDir, "retained-messages")
        val legacyArchive = File(root, "$accountId.archive")
        val database = File(root, "$accountId.db")
        val oldStore = MessageRetentionStore(context, accountId)
        val legacyRecord = JSONObject()
            .put("id", 123L)
            .put("chat_id", 456L)
            .put("sender_name", "Earlier sender")
            .put("text", "migrated retained text")
            .put("date", 1700000000)
            .put("is_deleted", true)
        val legacyPayload = JSONObject().put("version", 1).put("messages", JSONArray().put(legacyRecord))

        try {
            val encrypt = MessageRetentionStore::class.java.getDeclaredMethod("encryptRecord", JSONObject::class.java)
            encrypt.isAccessible = true
            legacyArchive.parentFile?.mkdirs()
            legacyArchive.writeBytes(encrypt.invoke(oldStore, legacyPayload) as ByteArray)
            oldStore.close()

            val migratedStore = MessageRetentionStore(context, accountId)
            val migrated = migratedStore.loadChat(456L, limit = 10)
            assertEquals(1, migrated.size)
            assertEquals("migrated retained text", migrated.single().text)
            assertTrue(migrated.single().isDeleted)
            assertTrue(database.isFile)
            assertFalse(legacyArchive.exists())
            migratedStore.clear()
        } finally {
            oldStore.clear()
            legacyArchive.delete()
            database.delete()
        }
    }

    @Test
    fun encryptedHistoryLoadsInBoundedChronologicalPagesAndTracksDeletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val accountId = "retention_${UUID.randomUUID().toString().replace("-", "")}"
        val database = File(context.noBackupFilesDir, "retained-messages/$accountId.db")
        val store = MessageRetentionStore(context, accountId)
        val messages = (1L..400L).map { id ->
            MessageSummary(
                id = id,
                chatId = 777L,
                senderName = "Test user",
                text = "retained private text $id",
                dateEpochSeconds = id.toInt(),
            )
        }

        try {
            store.saveAll(messages)

            val newest = store.loadChat(777L, limit = 30)
            assertEquals((371L..400L).toList(), newest.map(MessageSummary::id))
            assertTrue(store.hasChatBefore(777L, newest.first()))

            store.markDeleted(777L, setOf(365L))
            store.saveAll(listOf(messages[364]))
            val older = store.loadChatBefore(777L, newest.first(), limit = 20)
            assertEquals((351L..370L).toList(), older.map(MessageSummary::id))
            assertTrue(older.single { it.id == 365L }.isDeleted)

            val persisted = database.readBytes().toString(StandardCharsets.ISO_8859_1)
            assertFalse("Message contents must remain encrypted in the SQLite file", persisted.contains("retained private text"))
        } finally {
            store.clear()
        }

        assertFalse("Removing retained messages must delete their local database", database.exists())
    }

    @Test
    fun retainedMediaCopyLivesUntilItsLastMessageReferenceIsRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val accountId = "retention_media_${UUID.randomUUID().toString().replace("-", "")}" 
        val fileId = 43210
        val source = File(context.cacheDir, "$accountId.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val retainedMedia = File(context.noBackupFilesDir, "retained-messages/$accountId-media/$fileId.bin")
        val store = MessageRetentionStore(context, accountId)

        fun photoMessage(id: Long, retainedFileId: Int?, mediaPath: String?) = MessageSummary(
            id = id,
            chatId = 88L,
            senderName = "Test",
            text = "photo",
            dateEpochSeconds = id.toInt(),
            mediaType = MediaType.PHOTO,
            mediaFileId = retainedFileId,
            mediaPath = mediaPath,
        )

        try {
            store.saveAll(listOf(photoMessage(1L, fileId, source.absolutePath), photoMessage(2L, fileId, source.absolutePath)))
            assertTrue("Downloaded media was not copied into encrypted-history storage", retainedMedia.isFile)

            store.saveAll(listOf(photoMessage(1L, null, null)))
            assertTrue("Shared retained media was deleted while a message still referenced it", retainedMedia.isFile)

            store.saveAll(listOf(photoMessage(2L, null, null)))
            assertFalse("Unreferenced retained media was not pruned", retainedMedia.exists())
        } finally {
            store.clear()
            source.delete()
        }
    }
}
