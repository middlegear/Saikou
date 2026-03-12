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
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var lastSequence: Int? = null
    private var presenceUpdateJob: Job? = null

    private var assetManager: ImageProxy? = null
    private var currentConfig: RPCConfig? = null
    private var lastStartUnix: Long? = null
    private var lastEndUnix: Long? = null
    private var totalDurationMs: Long? = null

    private var connectionState: ConnectionState = ConnectionState.Disconnected
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

    private fun handleConnectionLoss() {
        Log.d("RPC", "Connection lost, resetting to Disconnected")
        webSocket = null
        heartbeatJob?.cancel()
        presenceUpdateJob?.cancel()
        setConnectionState(ConnectionState.Disconnected)
    }

    private fun resetSession() {
        Log.d("RPC", "Resetting session")
        heartbeatJob?.cancel()
        heartbeatJob = null
        presenceUpdateJob?.cancel()
        presenceUpdateJob = null
        lastSequence = null
        sentInitialPresence = false
        pendingPresence = null
    }


    fun connect() {
        if (!rpcRepository.loadRpcPreference()) {
            Log.d("RPC", "Connection aborted: RPC is disabled in settings.")
            setConnectionState(ConnectionState.Closed)
            return
        }

        if (!canAttemptConnection()) {
            Log.d("RPC", "Cannot connect: current state is ${connectionState::class.simpleName}")
            return
        }

        setConnectionState(ConnectionState.Connecting)
        resetSession()

        Log.d("RPC", "Connecting to Discord Gateway...")

        val request = Request.Builder()
            .url("wss://gateway.discord.gg/?v=10&encoding=json")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("RPC", "WebSocket connected (waiting for Hello)")
                setConnectionState(ConnectionState.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleGatewayMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("RPC", "WebSocket Error: ${t.message}")
                handleConnectionLoss()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("RPC", "Gateway closing: $code")
                handleConnectionLoss()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("RPC", "WebSocket closed")
                if (connectionState !is ConnectionState.Closed) {
                    handleConnectionLoss()
                }
            }
        })
    }

    fun close() {
        if (connectionState is ConnectionState.Closed) {
            Log.d("RPC", "Already closed, ignoring close()")
            return
        }

        Log.d("RPC", "Closing connection")
        presenceUpdateJob?.cancel()

        if (webSocket != null && isReadyForPresence()) {
            scope.launch {
                try {

                    val clearPresence =
                        Presence(activities = emptyList(), status = "online", afk = true)
                    webSocket?.send(
                        json.encodeToString(
                            GatewayPayload(op = 3, d = clearPresence)
                        )
                    )
                    Log.d("RPC", "Cleared presence on Discord")
                } catch (e: Exception) {
                    Log.e("RPC", "Error clearing presence: ${e.message}")
                } finally {
                    performClose()
                }
            }
        } else {
            performClose()
        }
    }

    private fun performClose() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        resetSession()
        assetManager = null
        setConnectionState(ConnectionState.Closed)
    }

    fun onDurationReady(
        config: RPCConfig,
        durationMs: Long,
        currentPosMs: Long
    ) {
        if (durationMs <= 0 || !rpcRepository.loadRpcPreference()) {
            Log.d("RPC", "Skipping onDurationReady: invalid duration or RPC disabled")
            return
        }

        if (sentInitialPresence) {
            Log.d("RPC", "Initial presence already sent, skipping")
            return
        }

        Log.d("RPC", "onDurationReady: ${durationMs / 1000}s")

        this.currentConfig = config
        this.totalDurationMs = durationMs

        updateTimestamps(currentPosMs)
        updatePresence(isPlaying = true)

        sentInitialPresence = true
    }

    fun onPlaybackChanged(isPlaying: Boolean, currentPosMs: Long) {
        if (!rpcRepository.loadRpcPreference() || !sentInitialPresence || !isConnected()) {
            Log.d("RPC", "Ignoring onPlaybackChanged: presence not initialized or disconnected")
            return
        }

        if (isPlaying) {
            updateTimestamps(currentPosMs)
        }

        Log.d("RPC", "onPlaybackChanged: isPlaying=$isPlaying")
        updatePresence(isPlaying)
    }

    fun updateEpisode(config: RPCConfig, isCurrentlyPlaying: Boolean = true) {
        if (!rpcRepository.loadRpcPreference()) {
            Log.d("RPC", "RPC disabled, skipping update")
            return
        }

        this.currentConfig = config

        when {
            !isConnected() && canAttemptConnection() -> {
                Log.w("RPC", "Not connected, initiating connection for episode update")
                connect()
            }

            isConnected() && sentInitialPresence -> {
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

                9 -> { // Invalid Session
                    Log.w("RPC", "Invalid Session Opcode received. Resetting.")
                    handleConnectionLoss()

                    scope.launch {
                        delay(1000)
                        connect()
                    }
                }

                0 -> {
                    if (payload["t"]?.jsonPrimitive?.content == "READY") {
                        Log.d("RPC", "Gateway Ready")
                        setConnectionState(ConnectionState.Ready)

                        pendingPresence?.let {
                            sendToGateway(3, it)
                            pendingPresence = null
                        }


                        if (currentConfig != null && sentInitialPresence) {
                            scope.launch {
                                try {
                                    val presence =
                                        buildPresenceData(currentConfig!!, isPlaying = true)
                                    sendToGateway(3, presence)
                                    Log.d("RPC", "Sent presence on gateway ready (reconnect)")
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
                        "https://saikou-image-proxy.kenjitsu.workers.dev",
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
        if (!rpcRepository.loadRpcPreference()) {
            Log.d("RPC", "RPC disabled in settings, closing")
            close()
            return
        }

        val config = currentConfig ?: run {
            Log.e("RPC", "Cannot update presence: config is null")
            return
        }


        if (connectionState is ConnectionState.Closed) {
            Log.d("RPC", "RPC was closed but is re-enabled, reconnecting...")
            setConnectionState(ConnectionState.Disconnected)
            connect()
            return
        }


        if (!isConnected() && !canAttemptConnection()) {
            Log.w("RPC", "Cannot update presence: state is ${connectionState::class.simpleName}")
            return
        }

        if (!isConnected()) {
            Log.d("RPC", "Not connected, attempting to connect...")
            connect()
            return
        }


        presenceUpdateJob?.cancel()
        presenceUpdateJob = scope.launch {
            delay(200)
            try {
                val presence = buildPresenceData(config, isPlaying)

                when {
                    isReadyForPresence() && webSocket != null -> {
                        sendToGateway(3, presence)
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

    private fun startHeartbeat(interval: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(interval)
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
        }
    }

    private inline fun <reified T> sendToGateway(op: Int, d: T) {
        try {
            val payload = GatewayPayload(op = op, d = d)
            webSocket?.send(json.encodeToString(payload))
            Log.d("RPC", "Sent opcode $op to gateway")
        } catch (e: Exception) {
            Log.e("RPC", "Failed to send payload (op=$op): ${e.message}")
        }
    }
}