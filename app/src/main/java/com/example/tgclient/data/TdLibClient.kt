package com.example.tgclient.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Base64
import com.example.tgclient.BuildConfig
import com.example.tgclient.security.DatabaseKeyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    private val connectivityManager = this.context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // TDLib updates are ordered and must not be dropped. A busy group can
    // easily exceed a small SharedFlow buffer, which used to lose messages
    // and even authorization updates while the UI was being recreated.
    private val updateChannel = Channel<JSONObject>(Channel.UNLIMITED)
    val updates: Flow<JSONObject> = updateChannel.receiveAsFlow()
    val rawUpdates: Flow<String>
        get() = client.updates

    private val client: TdKtxClient
    private val networkLock = Any()
    @Volatile private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var lastNetwork: Network? = null
    @Volatile private var lastNetworkType: String? = null
    @Volatile
    private var started = false
    private val parametersState = TdLibParametersState()
    @Volatile
    private var closed = false

    val isStarted: Boolean get() = started

    init {
        val result = TdLibInitializer.init()
        check(result is org.drinkless.tdlib.TdLibInitResult.Success) {
            "Unable to load TDLib native library: $result"
        }
        client = TdKtxClient(30.0, PlatformTdClientEngine())
        scope.launch {
            client.updates.collect { raw ->
                val update = runCatching { JSONObject(raw) }.getOrNull()
                // TDLib may emit one final update while close() is racing with
                // this collector. A suspending send to the closed channel would
                // throw from this IO coroutine and crash the whole process.
                if (update != null) updateChannel.trySend(update)
            }
        }
    }

    @Synchronized
    fun start(onAuthorizationState: (JSONObject) -> Unit, onFailure: (Throwable) -> Unit) {
        if (started || closed) return
        started = true
        scope.launch {
            try {
                // Directory creation and Keystore-backed key persistence can block
                // on first install; keep startup I/O off the UI thread.
                val databaseRoot = if (BuildConfig.TELEGRAM_USE_TEST_DC) "tdlib-test" else "tdlib"
                val accountRoot = if (accountId == DEFAULT_ACCOUNT_ID) {
                    File(context.noBackupFilesDir, databaseRoot)
                } else {
                    File(context.noBackupFilesDir, "$databaseRoot/accounts/$accountId")
                }
                val databaseDirectory = File(accountRoot, "database").apply { mkdirs() }
                val filesDirectory = File(accountRoot, "files").apply { mkdirs() }
                val params = JSONObject()
                    .put("@type", "setTdlibParameters")
                    .put("use_test_dc", BuildConfig.TELEGRAM_USE_TEST_DC)
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
                sendChecked(JSONObject().put("@type", "setLogVerbosityLevel").put("new_verbosity_level", 0))
                // If authorization-state retrieval fails after TDLib accepted
                // its parameters, a retry must resume the existing client. A
                // second setTdlibParameters request is rejected by TDLib and
                // used to turn a transient startup/network error into a stuck
                // session restore.
                parametersState.configureIfNeeded { sendChecked(params) }
                // setNetworkType is available after TDLib parameters have been
                // configured; registering earlier can make the initial request
                // race and be rejected as an out-of-state method.
                monitorDefaultNetwork()
                val authorizationState = sendChecked(JSONObject().put("@type", "getAuthorizationState"))
                onAuthorizationState(authorizationState)
            } catch (cancelled: CancellationException) {
                started = false
                throw cancelled
            } catch (error: Exception) {
                started = false
                onFailure(error)
            }
        }
    }

    private fun monitorDefaultNetwork() {
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                updateNetworkType(network, capabilities)
            }

            override fun onLost(network: Network) {
                updateCurrentDefaultNetwork()
            }

            override fun onUnavailable() {
                updateNetworkType(null, null)
            }
        }
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
            networkCallback = callback
            updateCurrentDefaultNetwork()
        } catch (_: SecurityException) {
            // Keep TDLib's native auto-detection as a fallback on restricted devices.
        } catch (_: RuntimeException) {
            // Callback registration can fail temporarily; TDLib still retries its own connections.
        }
    }

    private fun updateCurrentDefaultNetwork() {
        val network = connectivityManager.activeNetwork
        updateNetworkType(network, network?.let(connectivityManager::getNetworkCapabilities))
    }

    private fun updateNetworkType(network: Network?, capabilities: NetworkCapabilities?) {
        val type = tdLibNetworkType(
            hasInternet = network != null && capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            hasWifiTransport = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            hasCellularTransport = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
        )
        val changed = synchronized(networkLock) {
            if (lastNetwork == network && lastNetworkType == type) false
            else {
                lastNetwork = network
                lastNetworkType = type
                true
            }
        }
        if (changed) {
            send(
                "setNetworkType",
                JSONObject().put("type", JSONObject().put("@type", type)),
            )
        }
    }

    private suspend fun sendChecked(request: JSONObject): JSONObject {
        val response = JSONObject(client.sendJson(request.toString()))
        if (response.optString("@type") == "error") {
            throw TelegramException(response.optInt("code"), response.optString("message", "Telegram request failed"))
        }
        return response
    }

    suspend fun request(type: String, fields: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        withTimeout(REQUEST_TIMEOUT_MS) {
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
    }

    fun send(type: String, fields: JSONObject = JSONObject()) {
        send(JSONObject(fields.toString()).put("@type", type))
    }

    private fun send(request: JSONObject) {
        scope.launch { runCatching { client.sendJson(request.toString()) } }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        started = false
        scope.cancel(CancellationException("TDLib client closed"))
        updateChannel.close()
        networkCallback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        networkCallback = null
        client.release()
    }

    private companion object {
        const val DEFAULT_ACCOUNT_ID = "default"
        const val REQUEST_TIMEOUT_MS = 45_000L
    }
}

class TelegramException(val code: Int, override val message: String) : Exception(message)
