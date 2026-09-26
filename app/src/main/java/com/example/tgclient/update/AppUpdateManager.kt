package com.example.tgclient.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.tgclient.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: AppUpdateInfo) : UpdateState
    data class Downloading(val info: AppUpdateInfo, val downloadedBytes: Long, val totalBytes: Long) : UpdateState
    data class Ready(val info: AppUpdateInfo, val apk: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

data class AppUpdateInfo(
    val versionName: String,
    val tagName: String,
    val assetName: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val notes: String,
)

/** Checks and downloads signed releases from the project's public GitHub releases. */
class AppUpdateManager(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private var operation: Job? = null

    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun checkForUpdates() {
        if (operation?.isActive == true) return
        operation = scope.launch {
            _state.value = UpdateState.Checking
            runCatching { withContext(Dispatchers.IO) { fetchLatestRelease() } }
                .onSuccess { info ->
                    _state.value = if (compareVersions(info.versionName, BuildConfig.VERSION_NAME) > 0) {
                        UpdateState.Available(info)
                    } else {
                        UpdateState.UpToDate
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.value = UpdateState.Error(error.message ?: "Unable to check for updates")
                }
        }
    }

    fun downloadUpdate() {
        val info = when (val current = _state.value) {
            is UpdateState.Available -> current.info
            is UpdateState.Error -> return
            else -> return
        }
        if (operation?.isActive == true) return
        operation = scope.launch {
            runCatching { withContext(Dispatchers.IO) { download(info) } }
                .onSuccess { apk -> _state.value = UpdateState.Ready(info, apk) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.value = UpdateState.Error(error.message ?: "Unable to download update")
                }
        }
    }

    fun install(context: Context) {
        val ready = _state.value as? UpdateState.Ready ?: return
        if (!ready.apk.isFile) {
            _state.value = UpdateState.Error("Downloaded update is no longer available")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permissionIntent)
            return
        }
        val apkUri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", ready.apk)
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(installIntent)
    }

    private fun fetchLatestRelease(): AppUpdateInfo {
        val connection = openConnection(LATEST_RELEASE_URL)
        return try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(body)
            val assets = release.optJSONArray("assets")
            val selectedAsset = selectApkAsset(assets)
                ?: throw IllegalStateException("Release APK for this device is unavailable")
            val tagName = release.optString("tag_name").ifBlank { throw IllegalStateException("Release tag is missing") }
            AppUpdateInfo(
                versionName = tagName.removePrefix("v"),
                tagName = tagName,
                assetName = selectedAsset.first,
                downloadUrl = selectedAsset.second,
                releaseUrl = release.optString("html_url"),
                notes = release.optString("body"),
            )
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun download(info: AppUpdateInfo): File {
        val updateDirectory = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val target = File(updateDirectory, "chatwave-${info.versionName}-${info.assetName}.apk")
        if (target.isFile && target.length() > 0L) return target
        val partial = File(updateDirectory, "${target.name}.part")
        val connection = openConnection(info.downloadUrl)
        try {
            val total = connection.contentLengthLong
            var downloaded = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        _state.value = UpdateState.Downloading(info, downloaded, total)
                    }
                }
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            return target
        } catch (cancelled: CancellationException) {
            partial.delete()
            throw cancelled
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Chatwave/${BuildConfig.VERSION_NAME}")
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            throw IllegalStateException("Update server returned HTTP ${connection.responseCode}")
        }
        return connection
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = versionParts(left)
        val rightParts = versionParts(right)
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val comparison = (leftParts.getOrElse(index) { 0 }).compareTo(rightParts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun versionParts(value: String): List<Int> =
        Regex("\\d+").findAll(value).map { it.value.toIntOrNull() ?: 0 }.toList()

    /**
     * Select the smallest release asset that can run on this device. Older
     * releases may still contain a universal APK, so keep it as a fallback
     * while new releases publish ABI-specific artifacts only.
     */
    private fun selectApkAsset(assets: org.json.JSONArray?): Pair<String, String>? {
        if (assets == null) return null
        val available = buildList {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                if (name.endsWith(".apk", ignoreCase = true) && url != null) add(name to url)
            }
        }
        val supportedAbis = Build.SUPPORTED_ABIS
        val matching = supportedAbis.firstNotNullOfOrNull { abi ->
            available.firstOrNull { (name, _) -> name.contains("-$abi-") }
        }
        return matching ?: available.firstOrNull { (name, _) -> name == UNIVERSAL_APK_ASSET_NAME }
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/madneal/x-tg/releases/latest"
        const val UNIVERSAL_APK_ASSET_NAME = "app-universal-release.apk"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val BUFFER_SIZE = 64 * 1024
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
