package ani.saikou.torrserver

import android.util.Log
import ani.saikou.torrserver.models.TorrentFile
import ani.saikou.torrserver.models.TorrentStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

class TorrServerPlaybackController(
    private val apiClient: TorrServerApiClient,
    cacheSizeMb: Int = 64,
    preloadCachePercent: Float = 0.50f
) {
    companion object {
        private const val TAG = "TorrServer"
    }

    private val _stats = MutableStateFlow(TorrentStats(TorrentStats.Stage.STOPPED))
    val stats: StateFlow<TorrentStats> = _stats.asStateFlow()

    private var statsJob: Job? = null
    private var activeHash: String? = null
    private var selectedFileIndex: Int = -1
    private var selectedFileSize: Long = 0

    private val defaultPreloadBytes: Long =
        (cacheSizeMb * 1024L * 1024L * preloadCachePercent).toLong()
    private var requiredPreloadBytes: Long = defaultPreloadBytes

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun getCurrentStats(): TorrentStats = _stats.value


    suspend fun resolveStreamUrl(
        magnetOrUrl: String,
        fileIndex: Int? = null,
        fileName: String? = null
    ): String? {
        return try {
            updateStage(TorrentStats.Stage.RESOLVING_MAGNET, "Adding torrent...")
            Log.d(TAG, "Added torrent with uri: $magnetOrUrl")
            val hash = apiClient.addTorrent(magnetOrUrl)
            activeHash = hash
            Log.d(TAG, "Added torrent with hash: $hash")

            updateStage(TorrentStats.Stage.METADATA_DOWNLOAD, "Downloading metadata...")

            val files = waitForTorrentFiles(hash)
            if (files.isEmpty()) {
                handleError("Failed to get torrent files")
                return null
            }


            Log.d(TAG, "Available files (${files.size}):\n" +
                    files.joinToString("\n") { "  • Index ${it.index}: ${it.path} (${it.size} bytes)" }
            )

            val zeroBasedIndex = fileIndex ?: extractFileIndex(magnetOrUrl)
            val extractedFileName = fileName ?: extractFileName(magnetOrUrl)

            val selectedFile = extractedFileName?.let { name ->
                files.find { it.path.contains(name, ignoreCase = true) }
            }
                ?: zeroBasedIndex?.let { idx -> files.find { it.index == idx + 1 } }
                ?: files.firstOrNull()

            if (selectedFile == null) {
                handleError("No suitable file found in torrent")
                return null
            }

            selectedFileIndex = selectedFile.index
            selectedFileSize = selectedFile.size

            Log.d(
                TAG,
                "Selected file: ${selectedFile.path} (TorrServer index: $selectedFileIndex, size: $selectedFileSize)"
            )

            updateStage(TorrentStats.Stage.HTTP_HANDOFF, "Starting stream...")

            val targetFileName = selectedFile.path.substringAfterLast("/").ifEmpty { selectedFile.path }
            val url = apiClient.getStreamUrl(hash, targetFileName, selectedFile.index)

            startStatsPolling(hash)
            updateStage(TorrentStats.Stage.READY, "Streaming")

            url
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving stream", e)
            handleError(e.message ?: "Unknown error")
            null
        }
    }

    private fun extractFileName(magnetOrUrl: String): String? {
        val queryPart = magnetOrUrl.substringAfter("?", "")
        if (queryPart.isEmpty()) return null

        return queryPart.split("&")
            .firstOrNull { it.startsWith("dn=") }
            ?.substringAfter("dn=")
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }

    private fun extractFileIndex(magnetOrUrl: String): Int? {
        val queryPart = magnetOrUrl.substringAfter("?", "")
        if (queryPart.isEmpty()) return null

        return queryPart.split("&")
            .firstOrNull { it.startsWith("index=") }
            ?.substringAfter("index=")
            ?.toIntOrNull()
    }
    private suspend fun waitForTorrentFiles(hash: String): List<TorrentFile> {
        var files = emptyList<TorrentFile>()
        var attempts = 0
        val maxAttempts = 120

        while (files.isEmpty() && attempts < maxAttempts) {
            delay(500.milliseconds)
            attempts++

            try {
                files = apiClient.getTorrentFiles(hash)
                if (files.isNotEmpty()) {
                    Log.d(TAG, "Got ${files.size} files from torrent metadata")
                    break
                }
            } catch (_: Exception) {
                Log.d(TAG, "Waiting for torrent metadata... attempt $attempts")
            }

            val status = apiClient.getTorrentStatus(hash)
            if (status != null) {
                updateMetadataDownloadStats(status)
            }
        }

        return files
    }

    private fun updateMetadataDownloadStats(status: TorrentStats) {
        val targetPreload = if (status.preloadSize > 0) status.preloadSize else requiredPreloadBytes
        val prebufferRatio = calculatePrebufferProgress(status.preloadedBytes, targetPreload)

        _stats.value = _stats.value.copy(
            stage = TorrentStats.Stage.METADATA_DOWNLOAD,
            prebufferProgress = prebufferRatio,
            hash = status.hash,
            name = status.name,
            progress = status.progress,
            downloadSpeed = status.downloadSpeed,
            uploadSpeed = status.uploadSpeed,
            connectedSeeders = status.connectedSeeders,
            activePeers = status.activePeers,
            totalPeers = status.totalPeers,
            statCode = status.statCode,
            preloadSize = status.preloadSize,
            preloadedBytes = status.preloadedBytes,
            loadedSize = status.loadedSize,
            torrentSize = status.torrentSize
        )
    }

    private fun startStatsPolling(hash: String) {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive && activeHash == hash) {
                try {
                    val currentStage = _stats.value.stage
                    if (currentStage != TorrentStats.Stage.READY &&
                        currentStage != TorrentStats.Stage.HTTP_HANDOFF
                    ) {
                        delay(1000.milliseconds)
                        continue
                    }

                    val torrentStatus = apiClient.getTorrentStatus(hash)
                    if (torrentStatus == null) {
                        Log.d(TAG, "Torrent no longer exists on server, stopping polling")
                        break
                    }

                    val targetPreloadBytes = if (torrentStatus.preloadSize > 0) {
                        torrentStatus.preloadSize
                    } else {
                        requiredPreloadBytes
                    }

                    val startupBufferProgress = calculatePrebufferProgress(
                        torrentStatus.preloadedBytes,
                        targetPreloadBytes
                    )

                    _stats.value = _stats.value.copy(
                        prebufferProgress = startupBufferProgress,
                        hash = torrentStatus.hash,
                        name = torrentStatus.name,
                        progress = torrentStatus.progress,
                        downloadSpeed = torrentStatus.downloadSpeed,
                        uploadSpeed = torrentStatus.uploadSpeed,
                        connectedSeeders = torrentStatus.connectedSeeders,
                        activePeers = torrentStatus.activePeers,
                        totalPeers = torrentStatus.totalPeers,
                        statCode = torrentStatus.statCode,
                        preloadSize = torrentStatus.preloadSize,
                        preloadedBytes = torrentStatus.preloadedBytes,
                        loadedSize = torrentStatus.loadedSize,
                        torrentSize = torrentStatus.torrentSize
                    )

                    Log.d(
                        TAG,
                        "Total: ${(torrentStatus.progress * 100).toInt()}% • " +
                                "${torrentStatus.downloadSpeed / 1024} KB/s • " +
                                "Seeds:${torrentStatus.connectedSeeders} • Peers: ${torrentStatus.totalPeers}"
                    )

                    delay(1000.milliseconds)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Error polling stats", e)
                    delay(2000.milliseconds)
                }
            }
        }
    }

    private fun calculatePrebufferProgress(preloadedBytes: Long, targetPreloadBytes: Long): Float {
        if (targetPreloadBytes <= 0) return 0f
        return (preloadedBytes.toFloat() / targetPreloadBytes.toFloat()).coerceIn(0f, 1f)
    }

    private fun updateStage(stage: TorrentStats.Stage, state: String) {
        _stats.value = _stats.value.copy(stage = stage, state = state)
    }

    private suspend fun handleError(message: String) {
        _stats.value = TorrentStats(TorrentStats.Stage.ERROR, state = message)
        activeHash?.let { apiClient.removeTorrent(it) }
        activeHash = null
    }

    fun releaseStream() {
        statsJob?.cancel()
        statsJob = null

        val hash = activeHash
        activeHash = null
        selectedFileIndex = -1
        selectedFileSize = 0
        requiredPreloadBytes = defaultPreloadBytes
        _stats.value = TorrentStats(TorrentStats.Stage.STOPPED)

        if (hash != null) {
            scope.launch {
                try {
                    apiClient.removeTorrent(hash)
                    Log.d(TAG, "Removed torrent: $hash")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing torrent", e)
                }
            }
        }
    }

    fun cancel() {
        releaseStream()
    }
}