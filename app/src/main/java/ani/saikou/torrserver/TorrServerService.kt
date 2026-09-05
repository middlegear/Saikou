package ani.saikou.torrserver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import ani.saikou.R
import ani.saikou.loadData
import ani.saikou.saveData
import ani.saikou.torrserver.models.ServerState
import ani.saikou.torrserver.models.TorrentStats
import ani.saikou.torrserver.utils.TorrentSettings
import ani.saikou.torrserver.utils.toTorrServerJson
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.minutes

class TorrServerService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val serverMutex = Mutex()

    lateinit var manager: TorrServerManager
        private set
    lateinit var apiClient: TorrServerApiClient
        private set
    lateinit var controller: TorrServerPlaybackController
        private set

    private var startupJob: Job? = null
    private var inactivityJob: Job? = null

    @Volatile
    private var isServerReady = false

    @Volatile
    private var settingsApplied = false
    private var currentSettings: TorrentSettings = TorrentSettings()
    private var wakeLock: PowerManager.WakeLock? = null

    inner class LocalBinder : Binder() {
        val service: TorrServerService get() = this@TorrServerService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        currentSettings = loadData(TORRENT_SETTINGS_KEY, toast = false) ?: TorrentSettings()

        manager = TorrServerManager(applicationContext)
        apiClient = TorrServerApiClient()
        controller = TorrServerPlaybackController(apiClient)

        acquireWakeLock()
        startForegroundNotification()

        observePlaybackStats()

        startupJob = serviceScope.launch {
            Log.d(TAG, "Starting server...")
            val result = manager.startServer()
            if (result.isSuccess) {
                val port = result.getOrNull()
                if (port != null) apiClient.updatePort(port)
                isServerReady = true
                Log.d(TAG, "Server started successfully on port $port")
                applySettingsToServer(currentSettings, clearCache = false)
                settingsApplied = true
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Log.e(TAG, "Failed to start server: $error")
            }
        }
    }

    private fun observePlaybackStats() {
        serviceScope.launch {
            controller.stats.collect { stats ->
                when (stats.stage) {
                    TorrentStats.Stage.RESOLVING_MAGNET,
                    TorrentStats.Stage.METADATA_DOWNLOAD,
                    TorrentStats.Stage.HTTP_HANDOFF,
                    TorrentStats.Stage.READY -> {
                        cancelInactivityTimer()
                    }
                    TorrentStats.Stage.STOPPED,
                    TorrentStats.Stage.ERROR -> {
                        startInactivityTimer()
                    }
                }
            }
        }
    }

    private fun startInactivityTimer() {
        if (inactivityJob?.isActive == true) return
        inactivityJob = serviceScope.launch {
            Log.d(TAG, "Inactivity timer started: will shut down in 10 minutes if idle")
            delay(INACTIVITY_TIMEOUT)
            Log.d(TAG, "Inactivity timeout reached (10 min idle). Stopping service...")
            stopServerAndRelease()
            withContext(Dispatchers.Main) {
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private fun cancelInactivityTimer() {
        if (inactivityJob?.isActive == true) {
            Log.d(TAG, "Inactivity timer canceled due to server activity")
            inactivityJob?.cancel()
            inactivityJob = null
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: flags=$flags, startId=$startId")

        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.d(TAG, "Stop action triggered from notification")

            currentSettings.enableTorrentServer = false
            saveData(TORRENT_SETTINGS_KEY, currentSettings)

            CoroutineScope(Dispatchers.IO).launch {
                stopServerAndRelease()
                withContext(Dispatchers.Main) {
                    stopForegroundCompat()
                    stopSelf()
                }
            }
            return START_NOT_STICKY
        }

        val incomingSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getSerializableExtra(EXTRA_SETTINGS, TorrentSettings::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getSerializableExtra(EXTRA_SETTINGS) as? TorrentSettings
        }

        if (incomingSettings != null) {
            val settingsChanged = incomingSettings != currentSettings
            currentSettings = incomingSettings

            if (settingsChanged) {
                serviceScope.launch {
                    controller.releaseStream()
                    applySettingsToServer(currentSettings, clearCache = true)
                    settingsApplied = true
                }
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Service onTaskRemoved - swiped away from recents")

        CoroutineScope(Dispatchers.IO).launch(NonCancellable) {
            stopServerAndRelease()
            withContext(Dispatchers.Main) {
                stopForegroundCompat()
                stopSelf()
            }
        }

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        startupJob?.cancel()
        inactivityJob?.cancel()
        releaseWakeLock()

        CoroutineScope(Dispatchers.IO).launch(NonCancellable) {
            stopServerAndRelease()
        }

        serviceScope.cancel()
        super.onDestroy()
    }

    suspend fun resolveStreamUrl(
        magnetOrUrl: String,
        fileIndex: Int? = null,
        preferredFileName: String? = null
    ): String? {
        Log.d(TAG, "resolveStreamUrl called")

        cancelInactivityTimer()

        startupJob?.join()

        if (!isServerReady) {
            Log.e(TAG, "Server failed to start or is not ready")
            startInactivityTimer()
            return null
        }

        serverMutex.withLock {
            if (manager.state.value !is ServerState.Running) {
                Log.d(TAG, "Server not running, starting...")
                val result = manager.startServer()
                if (result.isFailure) {
                    Log.e(TAG, "Failed to start server")
                    startInactivityTimer()
                    return null
                }
                result.getOrNull()?.let { apiClient.updatePort(it) }
                isServerReady = true
                applySettingsToServer(currentSettings, clearCache = false)
                settingsApplied = true
            }
        }

        return controller.resolveStreamUrl(magnetOrUrl, fileIndex, preferredFileName)
    }

    fun releaseStream() {
        Log.d(TAG, "releaseStream")
        controller.releaseStream()
        startInactivityTimer()
    }

    fun getStats() = controller.getCurrentStats()

    private suspend fun stopServerAndRelease() {
        try {
            controller.releaseStream()
            manager.stopServer()
        } catch (e: Exception) {
            Log.e(TAG, "Error during background shutdown", e)
        }
    }

    private suspend fun applySettingsToServer(
        settings: TorrentSettings,
        clearCache: Boolean = false
    ) {
        serverMutex.withLock {
            if (!isServerReady) {
                Log.e(TAG, "Server never became ready, settings not applied")
                return
            }

            try {
                if (clearCache) {
                    controller.releaseStream()
                    val cleaned = manager.clearTorrentCache()
                    Log.d(TAG, "Cache cleared on settings change: $cleaned")
                }

                val payload = settings.toTorrServerJson()
                val applied = apiClient.updateSettings(payload)
                Log.d(TAG, "Settings applied: $applied")
            } catch (e: Exception) {
                Log.e(TAG, "Error applying settings", e)
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Saikou:TorrServerWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(30.minutes.inWholeMilliseconds)
            }
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    private fun startForegroundNotification() {
        val channelId = "torrserver_channel"

        val channel = NotificationChannel(
            channelId,
            "TorrServer Background Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "TorrServer is running as a local streaming proxy"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)

        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = notificationIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val stopIntent = Intent(this, TorrServerService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Saikou")
            .setContentText("P2P Streaming is Active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )

        contentPendingIntent?.let { builder.setContentIntent(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1001,
                builder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1001, builder.build())
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val TAG = "TorrServer"
        private val INACTIVITY_TIMEOUT = 10.minutes
        const val EXTRA_SETTINGS = "extra_torrent_settings"
        const val TORRENT_SETTINGS_KEY = "torrent_settings"
        const val ACTION_STOP_SERVICE = "ani.saikou.action.STOP_TORRSERVER"

        fun startOrStop(context: Context, settings: TorrentSettings) {
            Log.d(TAG, "startOrStop: enable=${settings.enableTorrentServer}")
            val intent = Intent(context, TorrServerService::class.java)
            intent.putExtra(EXTRA_SETTINGS, settings)

            if (settings.enableTorrentServer) {
                context.startForegroundService(intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}