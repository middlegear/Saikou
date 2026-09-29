package ani.saikou.parsers.anime.extractors

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType
import ani.saikou.tryWithSuspend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

@OptIn(InternalSerializationApi::class)
class TorrentioExtractor(
    override val server: VideoServer
) : VideoExtractor() {

    @Serializable
    data class TorrentInfo(
        val name: String,
        val title: String,
        val infoHash: String? = null,
        val fileIdx: Int? = null,
        val sources: List<String> = emptyList(),
        val behaviorHints: BehaviorHints? = null,
        val url: String? = null
    )

    @Serializable
    data class BehaviorHints(
        val bingeGroup: String? = null,
        val filename: String? = null
    )

    @Serializable
    data class TorrentProviderData(
        val provider: String,
        val displayName: String,
        val streams: List<TorrentInfo>
    )

    override suspend fun extract(): VideoContainer {
        return tryWithSuspend(post = false, snackbar = true) {
            val jsonData = server.extraData?.get("providerData")

            val streams = if (!jsonData.isNullOrEmpty()) {
                val providerData = Json.decodeFromString<TorrentProviderData>(jsonData)
                providerData.streams
            } else {
                val response = client.get(server.embed.url).parsed<TorrentioResponse>()
                response.streams
            }

            createVideos(streams)

        } ?: VideoContainer(emptyList())
    }

    private fun createVideos(streams: List<TorrentInfo>): VideoContainer {
        val usingDebrid = streams.any { !it.url.isNullOrBlank() }

        val candidates = if (usingDebrid) {
            val direct = streams.filter { !it.url.isNullOrBlank() }
            direct.filterNot { isUncached(it.name) }.ifEmpty { direct }
        } else {
            streams
        }

        val videos = candidates.mapNotNull { stream ->
            val directUrl = stream.url?.takeIf { it.isNotBlank() }

            if (directUrl != null) {
                Video(
                    quality = getQuality(stream.name),
                    format = VideoType.CONTAINER,
                    file = FileUrl(directUrl),
                    extraNote = buildNote(stream)
                )
            } else {
                val magnet = buildMagnet(stream) ?: return@mapNotNull null
                Video(
                    quality = getQuality(stream.name),
                    format = VideoType.CONTAINER,
                    file = FileUrl(magnet),
                    extraNote = stream.title
                )
            }
        }

        return VideoContainer(videos, subtitles = emptyList())
    }

    private fun buildMagnet(stream: TorrentInfo): String? {
        val hash = stream.infoHash?.takeIf { it.isNotBlank() } ?: return null
        val magnetBuilder = StringBuilder("magnet:?xt=urn:btih:$hash")

        val filename = stream.behaviorHints?.filename
        if (!filename.isNullOrBlank()) {
            magnetBuilder.append("&dn=${URLEncoder.encode(filename, "UTF-8")}")
        }

        stream.fileIdx?.let { idx ->
            magnetBuilder.append("&index=$idx")
        }

        stream.sources.forEach { source ->
            if (source.startsWith("tracker:")) {
                val trackerUrl = source.removePrefix("tracker:")
                val encodedTracker = URLEncoder.encode(trackerUrl, "UTF-8")
                magnetBuilder.append("&tr=$encodedTracker")
            }
        }

        return magnetBuilder.toString()
    }

    private fun isUncached(name: String): Boolean = uncachedRegex.containsMatchIn(name)

    private fun buildNote(stream: TorrentInfo): String =
        if (isUncached(stream.name)) "${stream.title}\n(not cached)" else stream.title

    private fun getQuality(name: String): Int? {
        return when {
            name.contains("4k", ignoreCase = true) || name.contains("2160p", ignoreCase = true) -> 2160
            name.contains("1080p", ignoreCase = true) -> 1080
            name.contains("720p", ignoreCase = true) -> 720
            name.contains("480p", ignoreCase = true) -> 480
            else -> null
        }
    }

    @Serializable
    data class TorrentioResponse(
        val streams: List<TorrentInfo> = emptyList()
    )

    private companion object {
        val uncachedRegex = Regex("\\[[^\\]]*download]", RegexOption.IGNORE_CASE)
    }
}