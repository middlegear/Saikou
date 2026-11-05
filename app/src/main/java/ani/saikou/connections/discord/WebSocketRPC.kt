package ani.saikou.connections.discord

import android.content.Context
import android.util.Log
import ani.saikou.connections.discord.rpc.RpcRepository
import ani.saikou.connections.discord.rpc.serializers.*
import ani.saikou.connections.discord.auth.DiscordRepository
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds


class WebSocketRPC(private val context: Context) {

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        object Ready : ConnectionState()
        object Closed : ConnectionState()
    }

    private val rpcRepository: RpcRepository by lazy { RpcRepository(context) }
    private val discordRepository: DiscordRepository by lazy { DiscordRepository(context) }

    private val applicationId: String = "876336799774056754"
    private val json =
        Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }


    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    private val lock = Any()

    @Volatile
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var presenceUpdateJob: Job? = null

    @Volatile
    private var lastSequence: Int? = null

    @Volatile
    private var awaitingAck = false
    private var reconnectAttempts = 0

    private var assetManager: ImageProxy? = null
    private var currentConfig: RPCConfig? = null
    private var lastStartUnix: Long? = null
    private var lastEndUnix: Long? = null
    private var totalDurationMs: Long? = null

    @Volatile
    private var lastIsPlaying = false

    @Volatile
    private var connectionState: ConnectionState = ConnectionState.Disconnected

    @Volatile
    private var sentInitialPresence = false
    private var pendingPresence: Presence? = null

    data class RPCConfig(
        val title: String,
        val episode: String,
        val episodeTitle: String?,
        val totalEpisodes: String?,
        val coverUrl: String?,
        val shareLink: String?,
        val episodeThumbnail: String? = null,
    )

    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 6
        const val MAX_BACKOFF_MS = 30_000L
    }

    private fun isEnabled(): Boolean = rpcRepository.loadRpcPreference()

    private fun isConnected(): Boolean =
        connectionState is ConnectionState.Connected || connectionState is ConnectionState.Ready

    private fun isReadyForPresence(): Boolean =
        connectionState is ConnectionState.Ready

    private fun canAttemptConnection(): Boolean =
        connectionState is ConnectionState.Disconnected

    private fun setConnectionState(newState: ConnectionState) {
        val oldState = connectionState
        connectionState = newState
        Log.d(
            "RPC",
            "State transition: ${oldState::class.simpleName} → ${newState::class.simpleName}"
        )
    }

    private inline fun ifCurrent(ws: WebSocket, block: () -> Unit) {
        synchronized(lock) {
            if (ws === webSocket) block()
        }
    }

    private fun handleConnectionLoss() {
        synchronized(lock) {
            if (connectionState is ConnectionState.Closed) return
            Log.d("RPC", "Connection lost, resetting to Disconnected")
            webSocket = null
            heartbeatJob?.cancel()
            heartbeatJob = null
            presenceUpdateJob?.cancel()
            awaitingAck = false
            setConnectionState(ConnectionState.Disconnected)
            scheduleReconnect()
        }
    }


    private fun resetSession() {
        Log.d("RPC", "Resetting session")
        heartbeatJob?.cancel()
        heartbeatJob = null
        presenceUpdateJob?.cancel()
        presenceUpdateJob = null
        lastSequence = null
        awaitingAck = false
        pendingPresence = null
    }

    private fun scheduleReconnect() {
        if (!isEnabled()) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.d("RPC", "Max reconnect attempts reached; waiting for ensureConnected()")
            return
        }
        reconnectJob?.cancel()
        val delayMs = min(MAX_BACKOFF_MS, 1000L shl reconnectAttempts.coerceAtMost(5))
        reconnectAttempts++
        Log.d("RPC", "Reconnecting in ${delayMs}ms (attempt $reconnectAttempts)")
        reconnectJob = scope.launch {
            delay(delayMs.milliseconds)
            connect()
        }
    }

    fun connect() {
        synchronized(lock) {
            if (!isEnabled()) {
                Log.d("RPC", "Connection aborted: RPC is disabled in settings.")
                setConnectionState(ConnectionState.Closed)
                return
            }

            if (!canAttemptConnection()) {
                Log.d("RPC", "Cannot connect: current state is ${connectionState::class.simpleName}")
                return
            }

            reconnectJob?.cancel()
            setConnectionState(ConnectionState.Connecting)
            resetSession()

            Log.d("RPC", "Connecting to Discord Gateway...")

            val request = Request.Builder()
                .url("wss://gateway.discord.gg/?v=10&encoding=json")
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    ifCurrent(webSocket) {
                        Log.d("RPC", "WebSocket connected (waiting for Hello)")
                        setConnectionState(ConnectionState.Connected)
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    ifCurrent(webSocket) { handleGatewayMessage(text) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    ifCurrent(webSocket) {
                        Log.e("RPC", "WebSocket Error: ${t.message}")
                        handleConnectionLoss()
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    ifCurrent(webSocket) {
                        Log.d("RPC", "Gateway closing: $code")
                        webSocket.close(1000, null)
                        handleConnectionLoss()
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    ifCurrent(webSocket) {
                        Log.d("RPC", "WebSocket closed")
                        handleConnectionLoss()
                    }
                }
            })
        }
    }

    /**
     * Call when the player comes back to the foreground. Re-establishes the connection if it
     * was lost (or never recovered) while backgrounded, and re-sends the current presence.
     */
    fun ensureConnected() {
        if (!isEnabled()) return
        synchronized(lock) {
            reconnectAttempts = 0
            when (connectionState) {
                is ConnectionState.Closed -> {
                    Log.d("RPC", "ensureConnected: reopening closed connection")
                    setConnectionState(ConnectionState.Disconnected)
                    connect()
                }

                is ConnectionState.Disconnected -> connect()
                is ConnectionState.Connecting -> Unit
                is ConnectionState.Connected, is ConnectionState.Ready -> {
                    if (sentInitialPresence) updatePresence(lastIsPlaying)
                }
            }
        }
    }

    fun close() {
        val ws: WebSocket?
        val wasReady: Boolean
        synchronized(lock) {
            if (connectionState is ConnectionState.Closed) {
                Log.d("RPC", "Already closed, ignoring close()")
                return
            }
            Log.d("RPC", "Closing connection")
            wasReady = isReadyForPresence()
            ws = webSocket
            webSocket = null
            reconnectJob?.cancel()
            reconnectJob = null
            resetSession()
            assetManager = null
            setConnectionState(ConnectionState.Closed)
        }

        if (ws != null) {
            try {
                if (wasReady) {
                    val clearPresence =
                        Presence(activities = emptyList(), status = "online", afk = true)
                    ws.send(json.encodeToString(GatewayPayload(op = 3, d = clearPresence)))
                    Log.d("RPC", "Cleared presence on Discord")
                }
            } catch (e: Exception) {
                Log.e("RPC", "Error clearing presence: ${e.message}")
            } finally {
                ws.close(1000, "Normal closure")
            }
        }
    }

    fun onDurationReady(
        config: RPCConfig,
        durationMs: Long,
        currentPosMs: Long
    ) {
        if (durationMs <= 0 || !isEnabled()) {
            Log.d("RPC", "Skipping onDurationReady: invalid duration or RPC disabled")
            return
        }

        Log.d("RPC", "onDurationReady: ${durationMs / 1000}s")

        this.currentConfig = config
        this.totalDurationMs = durationMs

        updateTimestamps(currentPosMs)

        sentInitialPresence = true
        updatePresence(isPlaying = true)
    }

    fun onPlaybackChanged(isPlaying: Boolean, currentPosMs: Long) {
        if (!isEnabled() || !sentInitialPresence) {
            Log.d("RPC", "Ignoring onPlaybackChanged: presence not initialized")
            return
        }

        lastIsPlaying = isPlaying
        if (isPlaying) {
            updateTimestamps(currentPosMs)
        }

        Log.d("RPC", "onPlaybackChanged: isPlaying=$isPlaying")
        updatePresence(isPlaying)
    }

    fun updateEpisode(config: RPCConfig, isCurrentlyPlaying: Boolean = true) {
        if (!isEnabled()) {
            Log.d("RPC", "RPC disabled, skipping update")
            return
        }

        val episodeChanged = currentConfig?.let {
            it.episode != config.episode || it.title != config.title
        } ?: false

        this.currentConfig = config

        if (episodeChanged) {
            sentInitialPresence = false
            totalDurationMs = null
            lastStartUnix = null
            lastEndUnix = null
        }

        when {
            !isConnected() && canAttemptConnection() -> {
                Log.w("RPC", "Not connected, initiating connection for episode update")
                connect()
            }

            isConnected() && (sentInitialPresence || episodeChanged) -> {
                updatePresence(isPlaying = isCurrentlyPlaying)
            }

            else -> {
                Log.d(
                    "RPC",
                    "Episode updated — waiting for first duration to send initial presence"
                )
            }
        }
    }


    private fun handleGatewayMessage(text: String) {
        try {
            val payload = json.parseToJsonElement(text).jsonObject
            payload["s"]?.jsonPrimitive?.intOrNull?.let { lastSequence = it }

            when (payload["op"]?.jsonPrimitive?.intOrNull) {
                10 -> { // Hello
                    val interval =
                        payload["d"]?.jsonObject?.get("heartbeat_interval")?.jsonPrimitive?.longOrNull
                            ?: 41250L
                    startHeartbeat(interval)
                    sendIdentify()
                }

                1 -> { // Server requested an immediate heartbeat
                    sendHeartbeat()
                }

                11 -> { // Heartbeat ACK
                    awaitingAck = false
                }

                7 -> { // Reconnect requested
                    Log.w("RPC", "Gateway requested reconnect")
                    val ws = webSocket
                    handleConnectionLoss()
                    ws?.close(4000, "reconnect requested")
                }

                9 -> { // Invalid Session (we don't resume; a fresh identify is enough for presence)
                    Log.w("RPC", "Invalid Session Opcode received. Resetting.")
                    val ws = webSocket
                    handleConnectionLoss()
                    ws?.close(4000, "invalid session")
                }

                0 -> {
                    if (payload["t"]?.jsonPrimitive?.content == "READY") {
                        Log.d("RPC", "Gateway Ready")
                        reconnectAttempts = 0
                        setConnectionState(ConnectionState.Ready)

                        val pending = pendingPresence
                        pendingPresence = null
                        val config = currentConfig

                        if (pending != null) {
                            sendToGateway(3, pending)
                        } else if (config != null && sentInitialPresence) {
                            val playing = lastIsPlaying
                            scope.launch {
                                try {
                                    val presence = buildPresenceData(config, playing)
                                    sendToGateway(3, presence)
                                    Log.d("RPC", "Re-sent presence on gateway ready")
                                } catch (e: Exception) {
                                    Log.e("RPC", "Error sending presence on ready: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RPC", "Parse error: ${e.message}")
        }
    }

    private fun sendIdentify() {
        scope.launch {
            try {
                val userToken = discordRepository.getSavedToken()

                if (userToken != null) {
                    Log.d("RPC", "Token retrieved, initializing asset manager...")
                    assetManager = ImageProxy(
                        applicationId,
                        userToken,
                        client,
                        json
                    )
                    val data = Identify(userToken, IdentifyProperties("windows", "chrome", "disco"))
                    sendToGateway(2, data)
                    Log.d("RPC", "Identify sent")
                } else {
                    Log.e("RPC", "Cannot identify: No user token found.")
                    close()
                }
            } catch (e: Exception) {
                Log.e("RPC", "Error in sendIdentify: ${e.message}")
                close()
            }
        }
    }

    private fun updateTimestamps(currentPosMs: Long) {
        val duration = totalDurationMs ?: return
        val now = System.currentTimeMillis()

        lastStartUnix = now - currentPosMs
        lastEndUnix = now + (duration - currentPosMs)
    }

    private fun updatePresence(isPlaying: Boolean) {
        lastIsPlaying = isPlaying

        if (!isEnabled()) {
            Log.d("RPC", "RPC disabled in settings, closing")
            close()
            return
        }

        val config = currentConfig ?: run {
            Log.e("RPC", "Cannot update presence: config is null")
            return
        }

        synchronized(lock) {
            if (connectionState is ConnectionState.Closed) {
                Log.d("RPC", "RPC was closed but is re-enabled, reconnecting...")
                setConnectionState(ConnectionState.Disconnected)
                connect()
                return
            }

            if (connectionState is ConnectionState.Connecting) {
                // Will be sent on READY (sentInitialPresence + lastIsPlaying).
                Log.d("RPC", "Still connecting; presence will be sent on ready")
                return
            }

            if (!isConnected()) {
                Log.d("RPC", "Not connected, attempting to connect...")
                reconnectAttempts = 0
                connect()
                return
            }
        }

        presenceUpdateJob?.cancel()
        presenceUpdateJob = scope.launch {
            try {
                val presence = buildPresenceData(config, isPlaying)

                when {
                    isReadyForPresence() && webSocket != null -> {
                        if (!sendToGateway(3, presence)) {
                            Log.w("RPC", "Send failed, socket is dead")
                            handleConnectionLoss()
                        }
                    }

                    isConnected() -> {
                        Log.d("RPC", "Connected but not ready")
                        pendingPresence = presence
                    }

                    else -> {
                        Log.w(
                            "RPC",
                            "Invalid state for presence update: ${connectionState::class.simpleName}"
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RPC", "Error updating presence: ${e.message}")
                handleConnectionLoss()
            }
        }
    }

    private suspend fun buildPresenceData(config: RPCConfig, isPlaying: Boolean): Presence {
        val largeImgUrl = config.episodeThumbnail ?: config.coverUrl

        val discordLarge = largeImgUrl?.let {
            assetManager?.fetchDiscordUri(it, buildEpisodeString(config))
        }

        val discordSmall = assetManager?.fetchDiscordUri(
            "https://cdn.discordapp.com/icons/1091762044946092105/a_b485448e33d24a7bb35e3d63a4a4539c.gif?size=1024",
            "Discord app icon attachment"
        )

        val activity = Activity(
            applicationId = applicationId,
            name = if (isPlaying) config.title else null,
            type = 3,
            details = if (isPlaying) buildEpisodeString(config) else null,
            timestamps = if (isPlaying) Timestamps(
                start = lastStartUnix,
                end = lastEndUnix
            ) else null,
            assets = if (isPlaying) Assets(
                largeImage = discordLarge,
                largeText = config.title,
                largeUrl = config.shareLink?.takeIf { !it.isBlank() },
                smallImage = discordSmall,
                smallText = "Saikou",
            ) else null,
            buttons = listOf("View on AniList", "Get Saikou"),
            metadata = Metadata(
                buttonUrls = listOf(
                    config.shareLink ?: "https://anilist.co/",
                    "https://github.com/middlegear/Saikou"
                )
            )
        )

        return Presence(listOf(activity), status = "online", afk = !isPlaying)
    }

    private fun buildEpisodeString(c: RPCConfig): String {
        val titlePart = if (!c.episodeTitle.isNullOrBlank()) " ${c.episodeTitle}" else ""
        return " $titlePart"
    }

    private fun sendHeartbeat() {
        try {
            webSocket?.send(json.encodeToString(buildJsonObject {
                put("op", 1)
                put("d", lastSequence)
            }))
            Log.d("RPC", "Heartbeat sent")
        } catch (e: Exception) {
            Log.e("RPC", "Error sending heartbeat: ${e.message}")
        }
    }

    private fun startHeartbeat(interval: Long) {
        heartbeatJob?.cancel()
        awaitingAck = false
        heartbeatJob = scope.launch {
            delay((interval * Random.nextDouble()).toLong().milliseconds)
            while (isActive) {
                if (awaitingAck) {

                    Log.w("RPC", "Heartbeat not acknowledged, reconnecting")
                    val ws = webSocket
                    handleConnectionLoss()
                    ws?.cancel()
                    return@launch
                }
                awaitingAck = true
                sendHeartbeat()
                delay(interval.milliseconds)
            }
        }
    }

    private inline fun <reified T> sendToGateway(op: Int, d: T): Boolean {
        return try {
            val payload = GatewayPayload(op = op, d = d)
            val ok = webSocket?.send(json.encodeToString(payload)) ?: false
            Log.d("RPC", "Sent opcode $op to gateway (ok=$ok)")
            ok
        } catch (e: Exception) {
            Log.e("RPC", "Failed to send payload (op=$op): ${e.message}")
            false
        }
    }
}