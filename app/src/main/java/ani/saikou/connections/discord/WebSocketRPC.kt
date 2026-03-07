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

    private var assetManager: ImageProxy? = null
    private var currentConfig: RPCConfig? = null
    private var sentInitialPresence = false
    private var lastStartUnix: Long? = null
    private var lastEndUnix: Long? = null
    private var totalDurationMs: Long? = null
    private var isClosed = false

    private var isConnecting = false

    private var isReady = false
    private var pendingPresence: Presence? = null

    private fun handleConnectionLoss() {
        webSocket = null
        heartbeatJob?.cancel()
        sentInitialPresence = false
    }

    private fun resetSession() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        lastSequence = null
        sentInitialPresence = false
    }
    fun disconnect() {
        webSocket?.close(1000, "RPC disabled by user")
        webSocket = null
        resetSession()
        isClosed = true
    }

    data class RPCConfig(
        val title: String,
        val episode: String,
        val episodeTitle: String?,
        val totalEpisodes: String?,
        val coverUrl: String?,
        val shareLink: String?,
        val episodeThumbnail: String? = null,
    )

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
                    connect()
                }

                0 -> {
                    if (payload["t"]?.jsonPrimitive?.content == "READY") {
                        Log.d("RPC", "Gateway Ready")
                        isReady = true

                        pendingPresence?.let {
                            Log.d("RPC", "Flushing cached presence...")
                            sendToGateway(3, it)
                            pendingPresence = null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RPC", "Parse error: ${e.message}")
        }
    }


    fun connect() {

        if (!rpcRepository.loadRpcPreference()) {
            Log.d("RPC", "Connection aborted: RPC is disabled in settings.")
            disconnect()
            return
        }

        isConnecting = true
        isClosed = false
        resetSession()


        Log.d("RPC", "Connecting to Discord Gateway...")

        val request = Request.Builder()
            .url("wss://gateway.discord.gg/?v=10&encoding=json")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnecting = false
                Log.d("RPC", "WebSocket connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleGatewayMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnecting = false
                Log.e("RPC", "WebSocket Error (Broken Pipe?): ${t.message}")
                handleConnectionLoss()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnecting = false
                Log.d("RPC", "Gateway is closing connection: $code")
                handleConnectionLoss()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnecting = false
                Log.d("RPC", "WebSocket closed")
            }

        })
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


    fun onDurationReady(
        config: RPCConfig,
        durationMs: Long,
        currentPosMs: Long
    ) {
        if (sentInitialPresence || durationMs <= 0 || !rpcRepository.loadRpcPreference()) {
            Log.d("RPC", "Skipping onDurationReady")
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
        if (!rpcRepository.loadRpcPreference() || !sentInitialPresence) {
            Log.d("RPC", "Ignoring onPlaybackChanged: presence not initialized")
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

        if (webSocket == null || isClosed) {
            Log.w("RPC", "WebSocket not connected → reconnecting")
            connect()
            return
        }

        this.currentConfig = config

        if (sentInitialPresence) {
            updatePresence(isPlaying = isCurrentlyPlaying)
        } else {
            Log.d("RPC", "Episode updated — waiting for first duration to send initial presence")
        }
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

        if (webSocket == null) {
            Log.d("RPC", "Socket null during update. Reconnecting...")
            connect()
            return
        }



        scope.launch {
            try {
                val largeImgUrl = config.episodeThumbnail ?: config.coverUrl

                val discordLarge = largeImgUrl?.let {
                    assetManager?.fetchDiscordUri(it)
                }
                val discordSmall = assetManager?.fetchDiscordUri(
                    "https://cdn.discordapp.com/icons/1091762044946092105/a_b485448e33d24a7bb35e3d63a4a4539c.gif?size=1024"
                )
                val activity = Activity(
                    applicationId = applicationId,
                    name = if (isPlaying) config.title else null,
                    type = 3,
                    details = if (isPlaying) buildEpisodeString(config) else null, // removed config.title
//                    state = if (isPlaying) buildEpisodeString(config) else null,
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
//                        smallUrl = "https://github.com/saikou-app/saikou"
                    ) else null,
                    buttons = listOf("View on AniList", "Get Saikou"),
                    metadata = Metadata(
                        buttonUrls = listOf(
                            config.shareLink ?: "https://anilist.co/",
                            "https://github.com/middlegear/Saikou"
                        )
                    )
                )
                val presence = Presence(listOf(activity), status = "online", afk = !isPlaying)

                if (isReady && webSocket != null) {
                    sendToGateway(3, presence)
                } else {
                    Log.d("RPC", "Not ready yet, caching presence update")
                    pendingPresence = presence
                    if (webSocket == null && !isConnecting) connect()
                }

            } catch (e: Exception) {
                Log.e("RPC", "Error updating presence: ${e.message}")
                handleConnectionLoss()
            }
        }
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


    fun close() {

        if (isClosed || webSocket == null) return
        isClosed = true

        Log.d("RPC", "Closing RPC connection")

        heartbeatJob?.cancel()
        heartbeatJob = null

        webSocket?.close(1000, "Normal closure")
        webSocket = null

        sentInitialPresence = false
        assetManager = null
        isReady = false
        pendingPresence = null
        isConnecting = false
    }

}