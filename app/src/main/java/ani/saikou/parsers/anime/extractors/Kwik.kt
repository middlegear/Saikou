package ani.saikou.parsers.anime.extractors


import ani.saikou.*
import ani.saikou.parsers.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable


@InternalSerializationApi
class Kwik(override val server: VideoServer) : VideoExtractor() {

    @Serializable
    data class SourceItem(
        val url: String,
        val isM3u8: Boolean = true,
        val type: String,
        val quality: String
    )

    @Serializable
    data class KaidoSubtitle(
        val url: String,
        val lang: String,
        val default: Boolean = false
    )

    @Serializable
    data class SourceData(
        val subtitles: List<KaidoSubtitle> = emptyList(),
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

            val baseHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:146.0) Gecko/20100101 Firefox/146.0",
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.5",
                "Accept-Encoding" to "gzip, deflate, br, zstd",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site",
                "TE" to "trailers",

                )


            val videos = response.data.sources.map { sourceItem ->


                val host = java.net.URL(sourceItem.url).authority

                val specificHeaders = baseHeaders.toMutableMap()

                specificHeaders["Host"] = host

                Video(
                    quality = sourceItem.quality.removeSuffix("p").toIntOrNull(),
                    format = VideoType.M3U8,
                    file = FileUrl(sourceItem.url),
                    extraNote = sourceItem.quality
                )
            }



            return VideoContainer(videos,)
        } catch (e: Exception) {
            return VideoContainer(emptyList())
        }
    }
}
