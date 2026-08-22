package ani.saikou.torrserver

import android.content.Context
import android.util.Log
import ani.saikou.torrserver.models.ServerState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class TorrServerManager(
    private val context: Context,
    private val port: Int = 8090
) {
    companion object {
        private const val TAG = "TorrServer"
        private const val READY_LOG_MARKER = "Start http server at"
    }

    private var process: Process? = null
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(200, TimeUnit.MILLISECONDS)
        .readTimeout(200, TimeUnit.MILLISECONDS)
        .build()

    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    val cacheDir: File by lazy {
        File(context.cacheDir, "torrserver_cache").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun startServer(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "startServer requested")

            if (isHttpResponding()) {
                Log.d(TAG, "Server already active on port $port")
                _state.value = ServerState.Running
                return@withContext Result.success(Unit)
            }

            if (process != null || isPortBound()) {
                internalStopServer()
            }

            _state.value = ServerState.Starting

            try {
                val binaryFile = prepareBinary()

                val command = listOf(
                    binaryFile.absolutePath,
                    "--port", port.toString(),
                    "--path", cacheDir.absolutePath
                )

                val pb = ProcessBuilder(command)
                    .directory(context.filesDir)
                    .redirectErrorStream(true)

                val proc = pb.start()
                process = proc

                val isReadyDeferred = CompletableDeferred<Boolean>()

                scope.launch {
                    try {
                        proc.inputStream.bufferedReader().use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                val currentLine = line ?: break
                                Log.v(TAG, "[TorrServer] $currentLine")

                                if (currentLine.contains(READY_LOG_MARKER, ignoreCase = true)) {
                                    if (!isReadyDeferred.isCompleted) {
                                        isReadyDeferred.complete(true)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Stream read error", e)
                    } finally {

                        if (!proc.isAlive && !isReadyDeferred.isCompleted) {
                            isReadyDeferred.complete(false)
                        }
                    }
                }

                scope.launch {
                    val endTime = System.currentTimeMillis() + 5000
                    while (System.currentTimeMillis() < endTime && proc.isAlive && !isReadyDeferred.isCompleted) {
                        if (isHttpResponding()) {
                            if (!isReadyDeferred.isCompleted) {
                                isReadyDeferred.complete(true)
                            }
                            break
                        }
                        delay(150.milliseconds)
                    }
                }

                val serverReady = withTimeoutOrNull(5000.milliseconds) {
                    isReadyDeferred.await()
                } ?: false

                if (serverReady && proc.isAlive) {
                    _state.value = ServerState.Running
                    Log.d(TAG, "Server started successfully")
                    Result.success(Unit)
                } else {
                    internalStopServer()
                    val err = "Server failed to start or died prematurely."
                    Log.e(TAG, err)
                    _state.value = ServerState.Error(err)
                    Result.failure(IOException(err))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch server binary", e)
                internalStopServer()
                _state.value = ServerState.Error(e.message ?: "Launch failed")
                Result.failure(e)
            }
        }
    }

    suspend fun stopServer() = mutex.withLock {
        withContext(Dispatchers.IO) {
            internalStopServer()
        }
    }

    private fun internalStopServer() {
        Log.d(TAG, "Forcing process teardown...")
        process?.let { proc ->
            try {
                proc.destroyForcibly()
                proc.waitFor(500, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                Log.e(TAG, "Error waiting for process kill", e)
            }
        }
        process = null
        _state.value = ServerState.Stopped
        Log.d(TAG, "Process destroyed completely")
    }

    fun clearTorrentCache(): Boolean {
        return cacheDir.deleteRecursively() && cacheDir.mkdirs()
    }

    private fun prepareBinary(): File {
        listOf(
            File(context.filesDir, "libtorrserver.so"),
            File(context.cacheDir, "libtorrserver.so")
        ).forEach { legacy ->
            if (legacy.exists()) legacy.delete()
        }

        val nativeLib = File(context.applicationInfo.nativeLibraryDir, "libtorrserver.so")
        if (nativeLib.exists() && nativeLib.length() > 0) {
            return nativeLib
        }

        throw IOException("libtorrserver.so missing in nativeLibraryDir: ${context.applicationInfo.nativeLibraryDir}")
    }

    private fun isHttpResponding(): Boolean {
        return try {
            val request = Request.Builder()
                .url("http://127.0.0.1:$port/echo")
                .get()
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun isPortBound(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 100)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}