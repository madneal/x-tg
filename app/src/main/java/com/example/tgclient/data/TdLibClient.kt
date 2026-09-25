package com.example.tgclient.data

import android.content.Context
import android.util.Base64
import com.example.tgclient.BuildConfig
import com.example.tgclient.security.DatabaseKeyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.PlatformTdClientEngine
import org.drinkless.tdlib.TdKtxClient
import org.drinkless.tdlib.TdLibInitializer
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** JSON boundary around TDLib's native Android client. */
class TdLibClient(
    context: Context,
    private val databaseKeyStore: DatabaseKeyStore,
    private val accountId: String = DEFAULT_ACCOUNT_ID,
) : Closeable {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _updates = MutableSharedFlow<JSONObject>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val updates: SharedFlow<JSONObject> = _updates.asSharedFlow()
    val rawUpdates: Flow<String>
        get() = client.updates

    private val client: TdKtxClient
    private var started = false

    init {
        val result = TdLibInitializer.init()
        check(result is org.drinkless.tdlib.TdLibInitResult.Success) {
            "Unable to load TDLib native library: $result"
        }
        client = TdKtxClient(30.0, PlatformTdClientEngine())
        scope.launch {
            client.updates.collect { raw ->
                runCatching { JSONObject(raw) }.onSuccess(_updates::tryEmit)
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        val accountRoot = if (accountId == DEFAULT_ACCOUNT_ID) {
            File(context.noBackupFilesDir, "tdlib")
        } else {
            File(context.noBackupFilesDir, "tdlib/accounts/$accountId")
        }
        val databaseDirectory = File(accountRoot, "database").apply { mkdirs() }
        val filesDirectory = File(accountRoot, "files").apply { mkdirs() }
        val params = JSONObject()
            .put("@type", "setTdlibParameters")
            .put("use_test_dc", false)
            .put("database_directory", databaseDirectory.absolutePath)
            .put("files_directory", filesDirectory.absolutePath)
            .put("database_encryption_key", Base64.encodeToString(databaseKeyStore.getOrCreateKey(), Base64.NO_WRAP))
            .put("use_file_database", true)
            .put("use_chat_info_database", true)
            .put("use_message_database", true)
            .put("use_secret_chats", true)
            .put("api_id", BuildConfig.TELEGRAM_API_ID)
            .put("api_hash", BuildConfig.TELEGRAM_API_HASH)
            .put("system_language_code", "en-US")
            .put("device_model", "Android")
            .put("system_version", android.os.Build.VERSION.RELEASE)
            .put("application_version", "Chatwave/${BuildConfig.VERSION_NAME}")
        scope.launch {
            runCatching {
                client.sendJson(JSONObject().put("@type", "setLogVerbosityLevel").put("new_verbosity_level", 0).toString())
                client.sendJson(params.toString())
                client.sendJson(JSONObject().put("@type", "getAuthorizationState").toString())
            }
        }
    }

    suspend fun request(type: String, fields: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val request = JSONObject(fields.toString()).put("@type", type).put("@extra", UUID.randomUUID().toString())
            scope.launch {
                runCatching { client.sendJson(request.toString()) }
                    .onSuccess { raw ->
                        if (!continuation.isActive) return@onSuccess
                        runCatching { JSONObject(raw) }
                            .onSuccess { result ->
                                if (result.optString("@type") == "error") continuation.resumeWithException(TelegramException(result.optInt("code"), result.optString("message", "Telegram request failed")))
                                else continuation.resume(result)
                            }
                            .onFailure(continuation::resumeWithException)
                    }
                    .onFailure(continuation::resumeWithException)
            }
            continuation.invokeOnCancellation { /* TDLib safely ignores a late response. */ }
        }
    }

    fun send(type: String, fields: JSONObject = JSONObject()) {
        send(JSONObject(fields.toString()).put("@type", type))
    }

    private fun send(request: JSONObject) {
        scope.launch { runCatching { client.sendJson(request.toString()) } }
    }

    override fun close() {
        if (!started) return
        started = false
        scope.cancel(CancellationException("TDLib client closed"))
        client.release()
    }

    private companion object {
        const val DEFAULT_ACCOUNT_ID = "default"
    }
}

class TelegramException(val code: Int, override val message: String) : Exception(message)
