package ani.saikou.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import ani.saikou.BuildConfig
import ani.saikou.R
import ani.saikou.client
import ani.saikou.loadData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

object AppUpdater {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val updaterScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var downloadJob: Job? = null
    private val okHttpClient = OkHttpClient()

    private var lastAvailableState: UpdateState.Available? = null
    private var dismissedVersion: String? = null

    fun dismissCurrentUpdate() {
        if (_updateState.value is UpdateState.Available) {
            dismissedVersion = (_updateState.value as UpdateState.Available).version
        }
        _updateState.value = UpdateState.Idle
    }

    fun shouldLaunchUpdate(version: String): Boolean {
        return dismissedVersion != version
    }


    suspend fun check(context: Context, force: Boolean = false) {

        dismissedVersion = null
        _updateState.value = UpdateState.Checking

        val startTime = System.currentTimeMillis()
        val minDelay = 2000L
        val repo = context.getString(R.string.repo)

        try {
            val response = client.get("https://api.github.com/repos/$repo/releases")
            val responseText = response.text

            if (responseText.contains("rate limit exceeded", ignoreCase = true)) {
                throw Exception("GitHub API rate limit exceeded. Please try again later.")
            }

            val jsonElements = ani.saikou.Mapper.json.parseToJsonElement(responseText)
            if (jsonElements !is JsonArray) {
                throw Exception("GitHub API response is not a valid list (possible rate limit or invalid repo)")
            }

            val releases = jsonElements.mapNotNull {
                try {
                    ani.saikou.Mapper.json.decodeFromJsonElement<GithubResponse>(it)
                } catch (e: Exception) {
                    null
                }
            }

            if (releases.isEmpty()) {
                throw Exception("No releases found for repository '$repo'")
            }

            val selectedRelease: GithubResponse = if (BuildConfig.DEBUG) {
                releases.filter { it.prerelease && !it.draft }
                    .maxByOrNull { it.createdAt?.toTimestamp() ?: 0L }
                    ?: throw Exception("No beta releases available")
            } else {
                releases.filter { !it.prerelease && !it.draft }
                    .maxByOrNull { it.createdAt?.toTimestamp() ?: 0L }
                    ?: throw Exception("No stable releases available")
            }

            val changelog = selectedRelease.body ?: ""
            val rawTag = selectedRelease.tagName ?: ""
            val version = rawTag.substringAfter("v", "").ifEmpty { rawTag }
            if (version.isBlank()) throw Exception("Invalid version tag: '$rawTag'")

            val remoteTime = selectedRelease.createdAt?.toTimestamp() ?: 0L

            val downloadUrl = selectedRelease.assets
                ?.firstOrNull {
                    it.browserDownloadURL?.endsWith(".apk", ignoreCase = true) == true
                }
                ?.browserDownloadURL
                ?: "https://github.com/$repo/releases/tag/v$version"

            val isActualUpdate = if (BuildConfig.DEBUG) {
                remoteTime > BuildConfig.BUILD_TIME && BuildConfig.VERSION_NAME != version
            } else {

                compareVersion(version)
            }

            val dontShow = loadData<Boolean>("dont_ask_for_update_$version") ?: false

            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime < minDelay) {
                delay((minDelay - elapsedTime).milliseconds)
            }

            if (isActualUpdate && (!dontShow || force)) {
                val availableState = UpdateState.Available(
                    version = version,
                    changelog = changelog,
                    downloadUrl = downloadUrl
                )
                lastAvailableState = availableState
                _updateState.value = availableState
            } else {
                _updateState.value = UpdateState.Idle
            }
        } catch (e: Exception) {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime < minDelay) {
                delay((minDelay - elapsedTime).milliseconds)
            }

            val errorMessage = if (e.message?.contains("rate limit", ignoreCase = true) == true) {
                "GitHub API rate limit exceeded. Try again later."
            } else {
                e.localizedMessage ?: "Failed to check for updates"
            }

            _updateState.value = UpdateState.Error(errorMessage)
        }
    }

    fun startDownload(context: Context, url: String, version: String) {
        downloadJob?.cancel()
        downloadJob = updaterScope.launch {
            downloadAndInstall(context, url, version)
        }
    }

    private suspend fun downloadAndInstall(
        context: Context,
        url: String,
        version: String
    ) = withContext(Dispatchers.IO) {

        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val finalFile = File(cacheDir, "Saikou_$version.apk")
        val tempFile = File(cacheDir, "Saikou_$version.apk.tmp")

        try {
            if (tempFile.exists()) tempFile.delete()
            if (finalFile.exists()) finalFile.delete()

            _updateState.value = UpdateState.Downloading(
                downloadedBytes = 0L,
                totalBytes = 0L,
                progressPercentage = 0
            )

            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw IOException("Empty response body")
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var downloadedBytes = 0L
                    var lastEmittedPercent = -1
                    var lastEmittedTime = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        ensureActive()

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val percent = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else 0

                        val currentTime = System.currentTimeMillis()
                        if (percent != lastEmittedPercent &&
                            (currentTime - lastEmittedTime >= 100 || percent == 100)
                        ) {
                            lastEmittedPercent = percent
                            lastEmittedTime = currentTime
                            _updateState.value = UpdateState.Downloading(
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                progressPercentage = percent
                            )
                        }
                    }
                    output.flush()
                }
            }

            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            _updateState.value = UpdateState.ReadyToInstall(finalFile)

        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()

            if (e is CancellationException) throw e

            _updateState.value = UpdateState.Error(e.localizedMessage ?: "Download failed")
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _updateState.value = lastAvailableState ?: UpdateState.Idle
    }

    fun installApk(context: Context, file: File) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                setDataAndType(contentUri, "application/vnd.android.package-archive")
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Failed to launch package installer")
        }
    }


    private fun compareVersion(version: String): Boolean {
        fun parse(v: String): List<Int> =
            v.substringBefore('-')
                .substringBefore('+')
                .split('.')
                .take(3)
                .map { it.toIntOrNull() ?: 0 }
                .let { it + List(3 - it.size) { 0 } }

        val remote = parse(version)
        val current = parse(BuildConfig.VERSION_NAME)

        for (i in 0 until 3) {
            if (remote[i] > current[i]) return true
            if (remote[i] < current[i]) return false
        }
        return false
    }

    private fun String.toTimestamp(): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(this)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    @Serializable
    private data class GithubResponse(
        @SerialName("html_url") val htmlUrl: String? = null,
        @SerialName("tag_name") val tagName: String? = null,
        val prerelease: Boolean = false,
        val draft: Boolean = false,
        @SerialName("created_at") val createdAt: String? = null,
        val body: String? = null,
        val assets: List<Asset>? = null
    ) {
        @Serializable
        data class Asset(
            @SerialName("browser_download_url") val browserDownloadURL: String? = null
        )
    }
}