package ani.saikou.torrserver

import android.content.Context
import android.system.Os
import android.system.OsConstants
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
    private val preferredPort: Int = 8090,
    private val maxPortFallbackAttempts: Int = 5
) {
    companion object {
        private const val TAG = "TorrServer"
        private const val READY_LOG_MARKER = "Start http server at"
        private const val PREFS_NAME = "torrserver_manager"
        private const val KEY_LAST_PID = "last_pid"
    }

    private var process: Process? = null
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(200, TimeUnit.MILLISECONDS)
        .readTimeout(200, TimeUnit.MILLISECONDS)
        .build()

    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    @Volatile
    var boundPort: Int = preferredPort
        private set

    val cacheDir: File by lazy {
        File(context.cacheDir, "torrserver_cache").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun startServer(): Result<Int> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val savedPid = prefs.getLong(KEY_LAST_PID, -1L)
            Log.d(TAG, "startServer requested (saved orphan pid on record: $savedPid)")

            if (isHttpResponding(boundPort)) {
                Log.d(TAG, "Server already active on port $boundPort")
                _state.value = ServerState.Running
                return@withContext Result.success(boundPort)
            }

            if (process != null || isPortBound(preferredPort)) {
                internalStopServer()
                killOrphanedProcessIfAny()
            }

            val portToUse = resolvePort()
            boundPort = portToUse

            _state.value = ServerState.Starting

            try {
                val binaryFile = prepareBinary()

                val command = listOf(
                    binaryFile.absolutePath,
                    "--port", portToUse.toString(),
                    "--path", cacheDir.absolutePath
                )

                val pb = ProcessBuilder(command)
                    .directory(context.filesDir)
                    .redirectErrorStream(true)

                val proc = pb.start()
                process = proc
                getProcessId(proc)?.let { savePid(it) }

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
                        if (isHttpResponding(portToUse)) {
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
                    Log.d(TAG, "Server started successfully on port $portToUse")
                    Result.success(portToUse)
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
            clearSavedPid()
            boundPort = preferredPort
        }
    }

    private fun resolvePort(): Int {
        if (!isPortBound(preferredPort)) return preferredPort

        Log.d(TAG, "Preferred port $preferredPort still occupied after cleanup; searching for a free port")
        for (offset in 1..maxPortFallbackAttempts) {
            val candidate = preferredPort + offset
            if (!isPortBound(candidate)) {
                Log.d(TAG, "Falling back to port $candidate")
                return candidate
            }
        }

        Log.e(
            TAG,
            "No free port found in range $preferredPort..${preferredPort + maxPortFallbackAttempts}; " +
                    "attempting $preferredPort anyway, launch may fail"
        )
        return preferredPort
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

    private fun killOrphanedProcessIfAny() {
        val pid = prefs.getLong(KEY_LAST_PID, -1L)
        if (pid <= 0) {
            Log.d(TAG, "No saved orphan pid to check")
            return
        }
        try {
            Os.kill(pid.toInt(), OsConstants.SIGKILL)
            Log.d(TAG, "Killed orphaned process pid=$pid")
        } catch (e: Exception) {
            Log.d(TAG, "No orphan to kill at pid=$pid (already dead or ESRCH): ${e.message}")
        } finally {
            clearSavedPid()
        }
        Thread.sleep(200)
    }

    private fun getProcessId(process: Process): Long? {
        return try {
            // Check API level 26+ native method first via reflection or direct call if compiled with high target
            val method = process.javaClass.getMethod("pid")
            (method.invoke(process) as? Number)?.toLong()
        } catch (e: Exception) {
            try {
                // Fallback for older internal implementations where field was named 'pid'
                val field = process.javaClass.getDeclaredField("pid").apply { isAccessible = true }
                (field.get(process) as? Number)?.toLong()
            } catch (ex: Exception) {
                null
            }
        }
    }

    private fun savePid(pid: Long) {
        prefs.edit().putLong(KEY_LAST_PID, pid).apply()
    }

    private fun clearSavedPid() {
        prefs.edit().remove(KEY_LAST_PID).apply()
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

    private fun isHttpResponding(port: Int): Boolean {
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

    private fun isPortBound(port: Int): Boolean {
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