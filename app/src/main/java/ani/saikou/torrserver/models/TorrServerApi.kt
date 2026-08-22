package ani.saikou.torrserver.models

data class TorrentFile(
    val index: Int,
    val path: String,
    val size: Long
)

data class TorrentStats(
    val stage: Stage = Stage.STOPPED,
    val prebufferProgress: Float = 0f,
    val hash: String = "",
    val name: String = "",
    val progress: Float = 0f,
    val downloadSpeed: Long = 0L,
    val uploadSpeed: Long = 0L,
    val connectedSeeders: Int = 0,
    val activePeers: Int = 0,
    val totalPeers: Int = 0,
    val state: String = "unknown",
    val statCode: Int = 0,
    val preloadSize: Long = 0L,
    val preloadedBytes: Long = 0L,
    val loadedSize: Long = 0L,
    val torrentSize: Long = 0L
) {
    enum class Stage {
        RESOLVING_MAGNET,
        METADATA_DOWNLOAD,
        HTTP_HANDOFF,
        READY,
        ERROR,
        STOPPED
    }
}

data class CacheState(
    val capacity: Long,
    val filled: Long,
    val hash: String,
    val pieces: Map<String, PieceState>?,
    val piecesCount: Int,
    val piecesLength: Long,
    val readers: List<ReaderState>?,
    val torrent: TorrentStats?
)

data class PieceState(
    val completed: Boolean,
    val id: Int,
    val length: Long,
    val priority: Int,
    val size: Long
)

data class ReaderState(
    val reader: Int,
    val start: Long,
    val end: Long
)

sealed class ServerState {
    data object Stopped : ServerState()
    data object Starting : ServerState()
    data object Running : ServerState()
    data class Error(val message: String) : ServerState()
}