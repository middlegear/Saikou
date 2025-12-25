package ani.saikou.parsers.anime.extractors


import ani.saikou.*
import ani.saikou.parsers.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable


@OptIn(InternalSerializationApi::class)
class InternalYtMP4(override val server: VideoServer) : VideoExtractor() {


    @Serializable
    data class SourceItem(
        val url: String,
        val isM3u8: Boolean,
        val type: String
    )

    @Serializable
    data class Subtitle(
        val url: String,
        val kind: String,
        val lang: String,

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
        val Referer: String
    )

    override suspend fun extract(): VideoContainer {
        try {
            val response = client.get(server.embed.url)
                .parsed<SourceResponse>()

            val videoReferer = response.headers.Referer
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
                    format = VideoType.CONTAINER,
                    file = FileUrl(it.url, baseHeaders),
                    extraNote = it.type
                )
            }

            return VideoContainer(videos)
        } catch (e: Exception) {

            return VideoContainer(emptyList())
        }
    }
}