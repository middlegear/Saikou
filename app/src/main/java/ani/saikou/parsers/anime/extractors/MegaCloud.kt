package ani.saikou.parsers.anime.extractors

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.logError
import ani.saikou.parsers.Subtitle
import ani.saikou.parsers.SubtitleType
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URL

@OptIn(InternalSerializationApi::class)
class MegaCloud(override val server: VideoServer) : VideoExtractor() {


    @Serializable
    data class SourceItem(
        val url: String,
        val isM3u8: Boolean,
        val type: String
    )

    @Serializable
    data class Subtitle(
        val url: String,
        val lang: String,
//        val kind:String, fix the api stuff
        val default: Boolean
    )

    @Serializable
    data class SourceData(
        val subtitles: List<Subtitle> = emptyList(),
        val sources: List<SourceItem> = emptyList()
    )

    @Serializable
    data class SourceResponse(
        val headers: Headers,
        val data: SourceData
    )

    @Serializable
    data class Headers(
        @SerialName("Referer")
        val referer: String?=null
    )

    override suspend fun extract(): VideoContainer {
        try {
            val response = client.get(server.embed.url)
                .parsed<SourceResponse>()

            val videoReferer = response.headers.referer
            if (videoReferer.isNullOrEmpty() || response.data.sources.isEmpty()) {
                return VideoContainer(emptyList())
            }

            val origin = videoReferer.removeSuffix("/")


            val baseHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.5",
                "Accept-Encoding" to "gzip, deflate, br, zstd",
                "Origin" to origin,
                "Referer" to videoReferer,
                "Connection" to "keep-alive",
                "Pragma" to "no-cache",
                "Cache-Control" to "no-cache"
            )
            val videos = response.data.sources.map {
                Video(
                    quality = null,
                    format = VideoType.M3U8,
                    file = FileUrl(it.url, baseHeaders),
                    extraNote = it.type
                )
            }

            val realSubtitles = response.data.subtitles.filter { it.lang != "thumbnails" }

            val subs = realSubtitles.map { sub ->
                Subtitle(
                    language = sub.lang,
                    url = sub.url
                )
            }

            return VideoContainer(videos, subs)
        } catch (e: Exception) {
            logError(e = e, post = true, snackbar = true)
            return VideoContainer(emptyList())
        }
    }
}