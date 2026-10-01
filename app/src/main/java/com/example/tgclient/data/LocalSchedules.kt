package com.example.tgclient.data

import android.content.Context
import android.util.AtomicFile
import androidx.work.*
import com.example.tgclient.TelegramApplication
import com.example.tgclient.model.AuthState
import com.example.tgclient.security.DatabaseKeyStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Durable device-only outbox. Claim is persisted BEFORE sending: ambiguous attempts never auto-retry. */
class LocalSchedules(private val context: Context, private val account: String) {
    private val directory = File(context.noBackupFilesDir, "local-schedules/$account")
    private val file get() = AtomicFile(File(directory, "jobs"))
    private fun read(): MutableList<JSONObject> {
        if (!file.baseFile.exists()) return mutableListOf()
        val bytes = file.readFully()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val array = JSONArray(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        return (0 until array.length()).map { array.getJSONObject(it) }.toMutableList()
    }
    private fun key() = SecretKeySpec(DatabaseKeyStore(context, account).getOrCreateKey(), "AES")
    private fun write(jobs: List<JSONObject>) {
        check(directory.exists() || directory.mkdirs())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(JSONArray(jobs).toString().toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
    }
    fun add(fields: JSONObject, text: String, at: Long, repeat: Int, media: String?): Long = synchronized(lock) {
        val jobs = read()
        val id = java.util.UUID.randomUUID().mostSignificantBits.or(Long.MIN_VALUE)
        directory.mkdirs()
        val copy = media?.let { source -> File(directory, "media_$id/${File(source).name}").also { it.parentFile!!.mkdirs(); File(source).copyTo(it, overwrite = true) } }
        if (copy != null) {
            val content = fields.getJSONObject("input_message_content")
            listOf("photo", "video", "audio", "document").forEach { field -> content.optJSONObject(field)?.put("path", copy.absolutePath) }
        }
        val job = JSONObject().put("id", id).put("chat", fields.getLong("chat_id")).put("fields", fields)
            .put("text", text.ifBlank { "Media message" }).put("at", at).put("repeat", repeat).put("state", "waiting")
        jobs.add(job); write(jobs); enqueue(job); id
    }
    fun list(chat: Long): List<ScheduledMessage> = synchronized(lock) {
        read().filter { it.getLong("chat") == chat }.map {
            ScheduledMessage(it.getLong("id"), it.getString("text"), it.getLong("at"), it.getInt("repeat"),
                if (it.getString("state") == "waiting") "On this device · waiting" else "Paused: delivery uncertain. Check chat before creating a replacement.")
        }
    }
    fun get(id: Long): JSONObject? = synchronized(lock) { read().find { it.getLong("id") == id } }
    fun claim(id: Long, at: Long): JSONObject? = synchronized(lock) {
        val jobs = read()
        val job = jobs.find { it.getLong("id") == id && it.getLong("at") == at && it.getString("state") == "waiting" } ?: return@synchronized null
        job.put("state", "uncertain"); write(jobs); job
    }
    fun accepted(id: Long, at: Long) = synchronized(lock) {
        val jobs = read()
        val job = jobs.find { it.getLong("id") == id && it.getLong("at") == at } ?: return@synchronized
        if (job.getInt("repeat") == 0) { jobs.remove(job); write(jobs); File(directory, "media_$id").deleteRecursively() }
        else {
            job.put("at", nextLocalOccurrence(at, System.currentTimeMillis() / 1000)).put("state", "waiting")
            write(jobs); enqueue(job)
        }
    }
    fun cancel(id: Long) = synchronized(lock) {
        val jobs = read(); jobs.removeAll { it.getLong("id") == id }; write(jobs)
        WorkManager.getInstance(context).cancelAllWorkByTag("local:$account:$id")
        File(directory, "media_$id").deleteRecursively()
    }
    fun clear() = synchronized(lock) {
        WorkManager.getInstance(context).cancelAllWorkByTag("local:$account")
        directory.deleteRecursively()
    }
    fun restore() = synchronized(lock) { read().filter { it.getString("state") == "waiting" }.forEach(::enqueue) }
    private fun enqueue(job: JSONObject) {
        val id = job.getLong("id"); val at = job.getLong("at")
        val work = OneTimeWorkRequestBuilder<LocalScheduleWorker>()
            .setInputData(workDataOf("account" to account, "id" to id, "at" to at))
            .setInitialDelay((at - System.currentTimeMillis() / 1000).coerceAtLeast(0), TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("local:$account").addTag("local:$account:$id").build()
        WorkManager.getInstance(context).enqueueUniqueWork("local:$account:$id:$at", ExistingWorkPolicy.KEEP, work)
    }
    companion object { private val lock = Any() }
}

internal fun nextLocalOccurrence(previous: Long, now: Long): Long = previous + ((now - previous).coerceAtLeast(0) / 86400 + 1) * 86400

class LocalScheduleWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val account = inputData.getString("account") ?: return Result.failure()
        val id = inputData.getLong("id", 0); val at = inputData.getLong("at", 0)
        val manager = (applicationContext as TelegramApplication).telegramAccountManager
        val store = LocalSchedules(applicationContext, account)
        if (manager.accounts.value.none { it.id == account }) { store.clear(); return Result.success() }
        val job = store.get(id) ?: return Result.success()
        if (job.getLong("at") != at || job.getString("state") != "waiting") return Result.success()
        val repository = manager.repository(account)
        if (withTimeoutOrNull(60_000) { repository.authState.first { it == AuthState.Ready } } == null) return Result.retry()
        if (System.currentTimeMillis() / 1000 < at) return Result.retry()
        val claimed = store.claim(id, at) ?: return Result.success()
        // Once claimed, cancellation, timeout or process death leave a visible paused job. Never resend blindly.
        try {
            repository.sendLocalScheduled(claimed.getJSONObject("fields"))
            store.accepted(id, at)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { return Result.failure() }
        return Result.success()
    }
}
