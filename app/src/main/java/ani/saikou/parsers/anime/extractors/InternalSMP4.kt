package ani.saikou.parsers.anime.extractors

import ani.saikou.FileUrl
import ani.saikou.client

import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable


@OptIn(InternalSerializationApi::class)
class InternalSMP4(override val server: VideoServer) : VideoExtractor() {


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


            val videos = response.data.sources.map {
                Video(

                    quality = null,
                    format = VideoType.CONTAINER,
                    file = FileUrl(it.url),
                    extraNote = it.type
                )
            }
            return VideoContainer(videos)
        } catch (e: Exception) {
            return VideoContainer(emptyList())
        }

    }
}