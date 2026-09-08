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

@OptIn(InternalSerializationApi::class)
class MegaPlay(override val server: VideoServer) : VideoExtractor() {


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
        val default: Boolean = false

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
        val referer: String? = null
    )

    override suspend fun extract(): VideoContainer {
        try {
            val response = client.get(server.embed.url, headers = mapOf("x-api-key" to apiKey))
                .parsed<SourceResponse>()

            val videoReferer = response.headers.referer
            if (videoReferer.isNullOrEmpty() || response.data.sources.isEmpty()) {
                return VideoContainer(emptyList())
            }

            val origin = videoReferer.removeSuffix("/")


            val baseHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                "Origin" to origin,
                "Referer" to videoReferer,
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

                val urlString = sub.url.lowercase()
                val subtitleType = when {
                    urlString.endsWith(".vtt") -> SubtitleType.VTT
                    urlString.endsWith(".ass") -> SubtitleType.ASS
                    urlString.endsWith(".srt") -> SubtitleType.SRT
                    else -> SubtitleType.VTT
                }

                Subtitle(
                    language = sub.lang,
                    url = sub.url,
                    type = subtitleType,
                    headers = baseHeaders,
                    isDefault = sub.default ?: false
                )
            }

            return VideoContainer(videos, subs)
        } catch (e: Exception) {
            logError(e = e, post = true, snackbar = true)
            return VideoContainer(emptyList())
        }
    }
}