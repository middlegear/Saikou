package ani.saikou.torrserver

import android.content.Context
import android.system.ErrnoException
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


class TorrServerManager(private val context: Context) {
    companion object {
        private const val TAG = "TorrServer"
        private const val READY_LOG_MARKER = "Start http server at"
        private const val PREFS_NAME = "torrserver_manager"
        private const val KEY_LAST_PID = "last_pid"

        const val PORT = 47935

        private const val START_TIMEOUT_MS = 5000L
        private const val ORPHAN_KILL_SETTLE_MS = 200L
        private const val PORT_CHECK_TIMEOUT_MS = 150
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

    val cacheDir: File by lazy {
        File(context.cacheDir, "torrserver_cache").apply {
            if (!exists()) mkdirs()
        }
    }


    suspend fun startServer(): Result<Int> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val savedPid = prefs.getLong(KEY_LAST_PID, -1L)
            Log.d(TAG, "startServer requested on fixed port $PORT (saved pid on record: $savedPid)")

            if (isHttpResponding(PORT)) {
                Log.d(TAG, "Server already active on port $PORT")
                _state.value = ServerState.Running
                return@withContext Result.success(PORT)
            }

            if (process != null) {
                internalStopServer()
            }

            if (isPortBound(PORT)) {
                Log.d(TAG, "Port $PORT is occupied; attempting to clear our own orphaned process")
                val killResult = killOrphanedProcessIfAny()

                if (killResult == OrphanKillResult.NOT_OURS) {
                    val err = "Port $PORT is in use by another app. Close any other " +
                            "torrent-streaming app and try again."
                    Log.e(TAG, err)
                    _state.value = ServerState.Error(err)
                    return@withContext Result.failure(IOException(err))
                }

                Thread.sleep(ORPHAN_KILL_SETTLE_MS)

                if (isPortBound(PORT)) {
                    val err = "Port $PORT is still in use after cleanup. It may be held by " +
                            "another app; close it and try again."
                    Log.e(TAG, err)
                    _state.value = ServerState.Error(err)
                    return@withContext Result.failure(IOException(err))
                }
            }

            _state.value = ServerState.Starting

            try {
                val binaryFile = prepareBinary()

                val command = listOf(
                    binaryFile.absolutePath,
                    "--port", PORT.toString(),
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
                    val endTime = System.currentTimeMillis() + START_TIMEOUT_MS
                    while (System.currentTimeMillis() < endTime && proc.isAlive && !isReadyDeferred.isCompleted) {
                        if (isHttpResponding(PORT)) {
                            if (!isReadyDeferred.isCompleted) {
                                isReadyDeferred.complete(true)
                            }
                            break
                        }
                        delay(150.milliseconds)
                    }
                }

                val serverReady = withTimeoutOrNull(START_TIMEOUT_MS.milliseconds) {
                    isReadyDeferred.await()
                } ?: false

                if (serverReady && proc.isAlive) {
                    _state.value = ServerState.Running
                    Log.d(TAG, "Server started successfully on port $PORT")
                    Result.success(PORT)
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

    private enum class OrphanKillResult {
        CLEARED,
        NOT_OURS
    }

    private fun killOrphanedProcessIfAny(): OrphanKillResult {
        val pid = prefs.getLong(KEY_LAST_PID, -1L)
        if (pid <= 0) {
            Log.d(TAG, "No saved orphan pid to check")
            return OrphanKillResult.CLEARED
        }

        return try {
            Os.kill(pid.toInt(), OsConstants.SIGKILL)
            Log.d(TAG, "Killed orphaned process pid=$pid")
            OrphanKillResult.CLEARED
        } catch (e: ErrnoException) {
            when (e.errno) {
                OsConstants.ESRCH -> {
                    Log.d(TAG, "No orphan at pid=$pid (already dead)")
                    OrphanKillResult.CLEARED
                }
                OsConstants.EPERM -> {
                    Log.w(TAG, "pid=$pid belongs to another app/UID — not ours to kill")
                    OrphanKillResult.NOT_OURS
                }
                else -> {
                    Log.w(TAG, "Unexpected errno killing pid=$pid: ${e.errno}")
                    OrphanKillResult.NOT_OURS
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error killing pid=$pid: ${e.message}")
            OrphanKillResult.NOT_OURS
        } finally {
            clearSavedPid()
        }
    }

    private fun getProcessId(process: Process): Long? {
        return try {
            val method = process.javaClass.getMethod("pid")
            (method.invoke(process) as? Number)?.toLong()
        } catch (e: Exception) {
            try {
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
                socket.connect(InetSocketAddress("127.0.0.1", port), PORT_CHECK_TIMEOUT_MS)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}