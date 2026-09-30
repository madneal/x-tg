package com.example.tgclient.security

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.tgclient.model.MediaType
import com.example.tgclient.model.MessageEntity
import com.example.tgclient.model.MessageSummary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted, account-scoped copies of messages delivered to this device. */
class MessageRetentionStore(context: Context, accountId: String) {
    private val appContext = context.applicationContext
    private val root = File(appContext.noBackupFilesDir, "retained-messages")
    private val accountSuffix = accountId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    private val databaseFile = File(root, "$accountSuffix.db")
    private val legacyArchiveFile = File(root, "$accountSuffix.archive")
    private val mediaDirectory = File(root, "$accountSuffix-media")
    private val keyAlias = "chatwave.messages.$accountSuffix"
    private val lock = Any()
    private var database: SQLiteDatabase? = null

    /** Upserts one batch in a single SQLite transaction; payloads are encrypted individually. */
    fun saveAll(messages: Iterable<MessageSummary>) = synchronized(lock) {
        val values = messages.asSequence().filter { it.chatId != 0L && it.id > 0L }.toList()
        if (values.isEmpty()) return@synchronized
        val db = databaseLocked()
        val obsoleteFileIds = mutableSetOf<Int>()
        db.beginTransaction()
        try {
            values.forEach { message -> putMessage(db, copyMediaIfNeeded(message), obsoleteFileIds) }
            obsoleteFileIds += trimLocked(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        pruneOrphanedMediaLocked(db, obsoleteFileIds)
    }

    /** Returns only the newest retained records, keeping large histories out of the UI heap. */
    fun loadChat(chatId: Long, limit: Int = DEFAULT_PAGE_SIZE): List<MessageSummary> = synchronized(lock) {
        readMessages(
            databaseLocked(),
            "chat_id = ?",
            arrayOf(chatId.toString()),
            limit,
        )
    }

    /** Returns a bounded older page before the current oldest visible message. */
    fun loadChatBefore(chatId: Long, before: MessageSummary, limit: Int): List<MessageSummary> = synchronized(lock) {
        readMessages(
            databaseLocked(),
            "chat_id = ? AND (date_epoch < ? OR (date_epoch = ? AND message_id < ?))",
            arrayOf(chatId.toString(), before.dateEpochSeconds.toString(), before.dateEpochSeconds.toString(), before.id.toString()),
            limit,
        )
    }

    fun hasChatBefore(chatId: Long, before: MessageSummary): Boolean = synchronized(lock) {
        databaseLocked().rawQuery(
            "SELECT 1 FROM messages WHERE chat_id = ? AND (date_epoch < ? OR (date_epoch = ? AND message_id < ?)) LIMIT 1",
            arrayOf(chatId.toString(), before.dateEpochSeconds.toString(), before.dateEpochSeconds.toString(), before.id.toString()),
        ).use { it.moveToFirst() }
    }

    fun markDeleted(chatId: Long, messageIds: Set<Long>) = synchronized(lock) {
        if (messageIds.isEmpty()) return@synchronized
        val db = databaseLocked()
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis() / 1000L
            messageIds.forEach { messageId ->
                val record = readRecord(db, chatId, messageId) ?: return@forEach
                record.put("is_deleted", true).put("deleted_at", now)
                putRecord(db, chatId, messageId, record)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Copies downloaded media into the retention directory and updates only matching rows. */
    fun updateMediaPath(fileId: Int, sourcePath: String) = synchronized(lock) {
        if (fileId <= 0 || sourcePath.isBlank()) return@synchronized
        val db = databaseLocked()
        val matchingRecords = mutableListOf<Triple<Long, Long, ByteArray>>()
        db.rawQuery(
            "SELECT chat_id, message_id, payload FROM messages WHERE preview_file_id = ? OR full_file_id = ?",
            arrayOf(fileId.toString(), fileId.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) matchingRecords += Triple(cursor.getLong(0), cursor.getLong(1), cursor.getBlob(2))
        }
        if (matchingRecords.isEmpty()) return@synchronized
        db.beginTransaction()
        try {
            matchingRecords.forEach { (chatId, messageId, encrypted) ->
                val record = decryptRecord(encrypted)
                if (record.optInt("preview_file_id") == fileId) {
                    val copied = copyMediaPath(sourcePath, chatId, messageId, fileId)
                    record.put("media_path", copied ?: JSONObject.NULL)
                }
                if (record.optInt("full_file_id") == fileId) {
                    val copied = copyMediaPath(sourcePath, chatId, messageId, fileId)
                    record.put("media_full_path", copied ?: JSONObject.NULL)
                }
                putRecord(db, chatId, messageId, record)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clear() = synchronized(lock) {
        database?.close()
        database = null
        databaseFile.delete()
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
        legacyArchiveFile.delete()
        mediaDirectory.deleteRecursively()
        runCatching { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(keyAlias) }
    }

    fun close() = synchronized(lock) {
        database?.close()
        database = null
    }

    private fun databaseLocked(): SQLiteDatabase {
        database?.takeIf { it.isOpen }?.let { return it }
        root.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS messages (" +
                "chat_id INTEGER NOT NULL, message_id INTEGER NOT NULL, date_epoch INTEGER NOT NULL, " +
                "preview_file_id INTEGER NOT NULL DEFAULT 0, full_file_id INTEGER NOT NULL DEFAULT 0, " +
                "payload BLOB NOT NULL, PRIMARY KEY(chat_id, message_id))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS messages_chat_date ON messages(chat_id, date_epoch DESC, message_id DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS messages_preview_file ON messages(preview_file_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS messages_full_file ON messages(full_file_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS retention_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        migrateLegacyArchive(db)
        database = db
        return db
    }

    /** One-time migration from the older single encrypted JSON archive. */
    private fun migrateLegacyArchive(db: SQLiteDatabase) {
        val migrated = db.rawQuery("SELECT 1 FROM retention_meta WHERE key = ?", arrayOf(LEGACY_MIGRATION_KEY)).use { it.moveToFirst() }
        if (migrated) return
        val legacy = if (legacyArchiveFile.isFile) runCatching { decryptArchive(legacyArchiveFile.readBytes()) }.getOrNull() else null
        db.beginTransaction()
        try {
            val records = legacy?.optJSONArray("messages")
            if (records != null) {
                for (index in 0 until records.length()) {
                    val record = records.optJSONObject(index) ?: continue
                    val chatId = record.optLong("chat_id")
                    val messageId = record.optLong("id")
                    if (chatId != 0L && messageId > 0L) putRecord(db, chatId, messageId, record)
                }
            }
            db.execSQL("INSERT OR REPLACE INTO retention_meta(key, value) VALUES(?, ?)", arrayOf(LEGACY_MIGRATION_KEY, "1"))
            trimLocked(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        // Keep the old archive unless migration committed successfully.
        legacyArchiveFile.delete()
    }

    private fun readMessages(db: SQLiteDatabase, where: String, args: Array<String>, limit: Int): List<MessageSummary> {
        val boundedLimit = limit.coerceIn(1, MAX_RECORDS)
        val values = mutableListOf<MessageSummary>()
        db.rawQuery(
            "SELECT payload FROM messages WHERE $where ORDER BY date_epoch DESC, message_id DESC LIMIT ?",
            args + boundedLimit.toString(),
        ).use { cursor ->
            while (cursor.moveToNext()) runCatching { fromJson(decryptRecord(cursor.getBlob(0))) }.getOrNull()?.let(values::add)
        }
        return values.sortedWith(compareBy<MessageSummary> { it.dateEpochSeconds }.thenBy { it.id })
    }

    private fun readRecord(db: SQLiteDatabase, chatId: Long, messageId: Long): JSONObject? =
        db.rawQuery("SELECT payload FROM messages WHERE chat_id = ? AND message_id = ?", arrayOf(chatId.toString(), messageId.toString())).use { cursor ->
            if (cursor.moveToFirst()) decryptRecord(cursor.getBlob(0)) else null
        }

    private fun putMessage(db: SQLiteDatabase, message: MessageSummary, obsoleteFileIds: MutableSet<Int>) {
        val record = toJson(message)
        val existing = readRecord(db, message.chatId, message.id)
        val newFileIds = setOfNotNull(message.mediaFileId, message.mediaFullFileId)
        existing?.let { oldRecord ->
            setOf(oldRecord.optInt("media_file_id"), oldRecord.optInt("media_full_file_id"))
                .filterTo(obsoleteFileIds) { it > 0 && it !in newFileIds }
        }
        if (existing?.optBoolean("is_deleted") == true) {
            // Late history pages or update-content events must never resurrect a locally deleted message.
            record.put("is_deleted", true).put("deleted_at", existing.optLong("deleted_at"))
        }
        putRecord(
            db,
            message.chatId,
            message.id,
            record,
            message.dateEpochSeconds,
            message.mediaFileId ?: 0,
            message.mediaFullFileId ?: 0,
        )
    }

    private fun putRecord(
        db: SQLiteDatabase,
        chatId: Long,
        messageId: Long,
        record: JSONObject,
        dateEpoch: Int = record.optInt("date"),
        previewFileId: Int = record.optInt("media_file_id"),
        fullFileId: Int = record.optInt("media_full_file_id"),
    ) {
        val values = ContentValues().apply {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("date_epoch", dateEpoch)
            put("preview_file_id", previewFileId)
            put("full_file_id", fullFileId)
            put("payload", encryptRecord(record))
        }
        db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun trimLocked(db: SQLiteDatabase): Set<Int> {
        val count = db.rawQuery("SELECT COUNT(*) FROM messages", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val excess = count - MAX_RECORDS
        if (excess <= 0) return emptySet()
        val removedFileIds = mutableSetOf<Int>()
        db.rawQuery(
            "SELECT preview_file_id, full_file_id FROM messages ORDER BY date_epoch ASC, message_id ASC LIMIT ?",
            arrayOf(excess.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getInt(0).takeIf { it > 0 }?.let(removedFileIds::add)
                cursor.getInt(1).takeIf { it > 0 }?.let(removedFileIds::add)
            }
        }
        db.execSQL(
            "DELETE FROM messages WHERE rowid IN (SELECT rowid FROM messages ORDER BY date_epoch ASC, message_id ASC LIMIT ?)",
            arrayOf(excess),
        )
        return removedFileIds
    }

    /** Removes media copies whose TDLib file IDs are no longer referenced by retained messages. */
    private fun pruneOrphanedMediaLocked(db: SQLiteDatabase, candidateFileIds: Set<Int>) {
        candidateFileIds.forEach { fileId ->
            val stillReferenced = db.rawQuery(
                "SELECT 1 FROM messages WHERE preview_file_id = ? OR full_file_id = ? LIMIT 1",
                arrayOf(fileId.toString(), fileId.toString()),
            ).use { it.moveToFirst() }
            if (!stillReferenced) File(mediaDirectory, "$fileId.bin").delete()
        }
    }

    private fun copyMediaIfNeeded(message: MessageSummary): MessageSummary {
        val preview = copyMediaPath(message.mediaPath, message.chatId, message.id, message.mediaFileId ?: 0)
        val full = copyMediaPath(message.mediaFullPath, message.chatId, message.id, message.mediaFullFileId ?: 0)
        return message.copy(mediaPath = preview, mediaFullPath = full)
    }

    private fun copyMediaPath(path: String?, chatId: Long, messageId: Long, fileId: Int): String? {
        val source = path?.let(::File)?.takeIf { it.isFile } ?: return path
        mediaDirectory.mkdirs()
        // TDLib file IDs are stable across forwarded messages. Share one retained copy
        // instead of duplicating the same photo/video for every message that references it.
        val destinationName = if (fileId > 0) "$fileId.bin" else "${chatId}_${messageId}.bin"
        val destination = File(mediaDirectory, destinationName)
        if (source.absolutePath == destination.absolutePath) return destination.absolutePath
        if (!destination.isFile || destination.length() != source.length()) {
            runCatching { source.copyTo(destination, overwrite = true) }.onFailure { return path }
        }
        return destination.absolutePath
    }

    private fun encryptRecord(record: JSONObject): ByteArray = crypt(Cipher.ENCRYPT_MODE, record.toString().toByteArray(StandardCharsets.UTF_8))

    private fun decryptRecord(encrypted: ByteArray): JSONObject = JSONObject(String(crypt(Cipher.DECRYPT_MODE, encrypted), StandardCharsets.UTF_8))

    private fun decryptArchive(encrypted: ByteArray): JSONObject = decryptRecord(encrypted)

    private fun crypt(mode: Int, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        return if (mode == Cipher.ENCRYPT_MODE) {
            cipher.init(mode, secretKey())
            cipher.iv + cipher.doFinal(data)
        } else {
            require(data.size > IV_BYTES) { "Corrupt retained message data" }
            cipher.init(mode, secretKey(), GCMParameterSpec(TAG_BITS, data.copyOfRange(0, IV_BYTES)))
            cipher.doFinal(data.copyOfRange(IV_BYTES, data.size))
        }
    }

    private fun toJson(message: MessageSummary) = JSONObject()
        .put("id", message.id)
        .put("chat_id", message.chatId)
        .put("sender_name", message.senderName)
        .put("sender_user_id", message.senderUserId ?: JSONObject.NULL)
        .put("text", message.text)
        .put("date", message.dateEpochSeconds)
        .put("is_outgoing", message.isOutgoing)
        .put("is_read", message.isRead)
        .put("media_type", message.mediaType?.name ?: JSONObject.NULL)
        .put("is_channel_post", message.isChannelPost)
        .put("media_file_id", message.mediaFileId ?: JSONObject.NULL)
        .put("media_path", message.mediaPath ?: JSONObject.NULL)
        .put("media_full_file_id", message.mediaFullFileId ?: JSONObject.NULL)
        .put("media_full_path", message.mediaFullPath ?: JSONObject.NULL)
        .put("media_name", message.mediaName ?: JSONObject.NULL)
        .put("entities", JSONArray().apply { message.entities.forEach { put(toJson(it)) } })
        .put("reply_to", message.replyToMessageId ?: JSONObject.NULL)
        .put("can_edit", message.canEdit)
        .put("can_delete", message.canDelete)
        .put("can_forward", message.canForward)
        .put("is_pinned", message.isPinned)
        .put("is_deleted", message.isDeleted)

    private fun toJson(entity: MessageEntity) = JSONObject()
        .put("offset", entity.offset)
        .put("length", entity.length)
        .put("type", entity.type)
        .put("argument", entity.argument ?: JSONObject.NULL)

    private fun fromJson(record: JSONObject): MessageSummary? = runCatching {
        val entities = record.optJSONArray("entities") ?: JSONArray()
        MessageSummary(
            id = record.optLong("id"),
            chatId = record.optLong("chat_id"),
            senderName = record.optString("sender_name", "Unknown"),
            senderUserId = record.optLong("sender_user_id").takeIf { it > 0L },
            text = record.optString("text"),
            dateEpochSeconds = record.optInt("date"),
            isOutgoing = record.optBoolean("is_outgoing"),
            isRead = record.optBoolean("is_read"),
            mediaType = record.nullableString("media_type")?.let { runCatching { MediaType.valueOf(it) }.getOrNull() },
            isChannelPost = record.optBoolean("is_channel_post"),
            mediaFileId = record.optInt("media_file_id").takeIf { it > 0 },
            mediaPath = record.nullableString("media_path"),
            mediaFullFileId = record.optInt("media_full_file_id").takeIf { it > 0 },
            mediaFullPath = record.nullableString("media_full_path"),
            mediaName = record.nullableString("media_name"),
            entities = (0 until entities.length()).mapNotNull { index ->
                val entity = entities.optJSONObject(index) ?: return@mapNotNull null
                MessageEntity(entity.optInt("offset"), entity.optInt("length"), entity.optString("type"), entity.nullableString("argument"))
            },
            replyToMessageId = record.optLong("reply_to").takeIf { it > 0L },
            canEdit = record.optBoolean("can_edit"),
            canDelete = record.optBoolean("can_delete"),
            canForward = record.optBoolean("can_forward", true),
            isPinned = record.optBoolean("is_pinned"),
            isDeleted = record.optBoolean("is_deleted"),
        )
    }.getOrNull()?.takeIf { it.id > 0L && it.chatId != 0L }

    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val MAX_RECORDS = 20_000
        const val DEFAULT_PAGE_SIZE = 200
        const val LEGACY_MIGRATION_KEY = "legacy_archive_migrated"
    }
}
