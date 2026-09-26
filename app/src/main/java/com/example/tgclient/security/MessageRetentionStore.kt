package com.example.tgclient.security

import android.content.Context
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

/**
 * Encrypted, account-scoped local copies of messages that have already reached this device.
 * It cannot restore messages that were never delivered by Telegram.
 */
class MessageRetentionStore(context: Context, accountId: String) {
    private val root = File(context.applicationContext.noBackupFilesDir, "retained-messages")
    private val accountSuffix = accountId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    private val archiveFile = File(root, "$accountSuffix.archive")
    private val mediaDirectory = File(root, "$accountSuffix-media")
    private val keyAlias = "chatwave.messages.$accountSuffix"
    private val lock = Any()
    private var loaded = false
    private val records = linkedMapOf<String, JSONObject>()

    fun save(message: MessageSummary) {
        synchronized(lock) {
            loadLocked()
            records[key(message.chatId, message.id)] = toJson(copyMediaIfNeeded(message))
            trimLocked()
            persistLocked()
        }
    }

    fun loadChat(chatId: Long): List<MessageSummary> = synchronized(lock) {
        loadLocked()
        records.values
            .filter { it.optLong("chat_id") == chatId }
            .mapNotNull(::fromJson)
            .sortedBy { it.id }
    }

    fun markDeleted(chatId: Long, messageIds: Set<Long>) {
        if (messageIds.isEmpty()) return
        synchronized(lock) {
            loadLocked()
            messageIds.forEach { messageId ->
                records[key(chatId, messageId)]?.put("is_deleted", true)?.put("deleted_at", System.currentTimeMillis() / 1000L)
            }
            persistLocked()
        }
    }

    /** Copies a TDLib file into the retention directory once its download completes. */
    fun updateMediaPath(fileId: Int, sourcePath: String) {
        if (fileId <= 0 || sourcePath.isBlank()) return
        synchronized(lock) {
            loadLocked()
            var changed = false
            records.values.forEach { record ->
                if (record.optInt("media_file_id") != fileId) return@forEach
                val message = fromJson(record) ?: return@forEach
                val copied = copyMediaIfNeeded(message.copy(mediaPath = sourcePath))
                record.put("media_path", copied.mediaPath ?: JSONObject.NULL)
                changed = true
            }
            if (changed) persistLocked()
        }
    }

    fun clear() {
        synchronized(lock) {
            records.clear()
            loaded = true
            archiveFile.delete()
            mediaDirectory.deleteRecursively()
            runCatching {
                KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(keyAlias)
            }
        }
    }

    private fun key(chatId: Long, messageId: Long) = "$chatId:$messageId"

    private fun loadLocked() {
        if (loaded) return
        loaded = true
        if (!archiveFile.isFile) return
        runCatching {
            val encrypted = archiveFile.readBytes()
            require(encrypted.size > IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(TAG_BITS, encrypted.copyOfRange(0, IV_BYTES)),
            )
            val rootObject = JSONObject(String(cipher.doFinal(encrypted.copyOfRange(IV_BYTES, encrypted.size)), StandardCharsets.UTF_8))
            val array = rootObject.optJSONArray("messages") ?: JSONArray()
            for (index in 0 until array.length()) {
                val record = array.optJSONObject(index) ?: continue
                records[key(record.optLong("chat_id"), record.optLong("id"))] = record
            }
        }.onFailure {
            records.clear()
            archiveFile.delete()
        }
    }

    private fun persistLocked() {
        root.mkdirs()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = JSONObject().put("version", 1).put("messages", JSONArray().apply { records.values.forEach(::put) }).toString()
        val encrypted = cipher.iv + cipher.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        val temporary = File(root, "$accountSuffix.archive.tmp")
        temporary.writeBytes(encrypted)
        if (!temporary.renameTo(archiveFile)) {
            archiveFile.delete()
            check(temporary.renameTo(archiveFile)) { "Unable to persist retained messages" }
        }
    }

    private fun trimLocked() {
        while (records.size > MAX_RECORDS) {
            val oldest = records.entries.minByOrNull { it.value.optLong("date", Long.MAX_VALUE) } ?: break
            records.remove(oldest.key)
        }
    }

    private fun copyMediaIfNeeded(message: MessageSummary): MessageSummary {
        val source = message.mediaPath?.let(::File)?.takeIf { it.isFile } ?: return message
        mediaDirectory.mkdirs()
        val destination = File(mediaDirectory, "${message.chatId}_${message.id}_${message.mediaFileId ?: 0}.bin")
        if (!destination.isFile || destination.length() != source.length()) {
            runCatching { source.copyTo(destination, overwrite = true) }.onFailure { return message }
        }
        return message.copy(mediaPath = destination.absolutePath)
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
        .put("media_name", message.mediaName ?: JSONObject.NULL)
        .put("entities", JSONArray().apply { message.entities.forEach { entity -> put(toJson(entity)) } })
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

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

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
    }
}
