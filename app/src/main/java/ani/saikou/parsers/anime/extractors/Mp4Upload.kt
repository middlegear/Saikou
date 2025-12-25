package ani.saikou.parsers.anime.extractors

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.logError
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.Exception

@OptIn(InternalSerializationApi::class)
class Mp4Upload(override val server: VideoServer) : VideoExtractor() {

    @Serializable
    data class SourceItem(
        val url: String,
        val isM3u8: Boolean,
        val type: String,
    )

    @Serializable
    data class SourceData(
        val sources: List<SourceItem> = emptyList(),
    )

    @Serializable
    data class Headers(
        @SerialName("Referer")
        val referer: String? = null
    )

    @Serializable
    data class SourceResponse(
        val headers: Headers,
        val data: SourceData
    )

    override suspend fun extract(): VideoContainer {

        try {
            val response = client.get(server.embed.url)
                .parsed<SourceResponse>()

            val videoReferer = response.headers.referer
            if (videoReferer.isNullOrEmpty() || response.data.sources.isEmpty()) {
                return VideoContainer(emptyList())
            }
            val baseHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:146.0) Gecko/20100101 Firefox/146.0",
                "Accept" to "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5",
                "Accept-Language" to "en-US,en;q=0.5",
                "Accept-Encoding" to "identity",
                "Referer" to videoReferer,
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "video",
                "Sec-Fetch-Mode" to "no-cors",
                "Sec-Fetch-Site" to "same-site",
                "Priority" to "u=4"
            )

            val videos = response.data.sources.map {
                Video(
                    quality = null,
                    format = VideoType.CONTAINER,
                    file = FileUrl(it.url, baseHeaders),
                    extraNote = it.type
                )
            }


            return VideoContainer(videos)

        } catch (e: Exception) {
            logError(e = e, post = false, snackbar = false)
            return VideoContainer(emptyList())
        }
    }
}