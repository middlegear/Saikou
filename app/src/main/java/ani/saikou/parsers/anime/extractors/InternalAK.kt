package ani.saikou.parsers.anime.extractors


import ani.saikou.*
import ani.saikou.parsers.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable


@OptIn(InternalSerializationApi::class)
class InternalAK(override val server: VideoServer) : VideoExtractor() {


    @Serializable
    data class SourceItem(
        val url: String,
        val isM3u8: Boolean,
        val type: String,
        val quality: String? = null
    )

    @Serializable
    data class AudioTrack(
        val url: String,
        val type: String,
        val quality: String,

        )

    @Serializable
    data class Subtitle(
        val url: String,
        val kind: String,
        val lang: String,
    )

    @Serializable
    data class SourceData(
        val tracks: List<AudioTrack> = emptyList(),
        val sources: List<SourceItem> = emptyList(),
        val subtitles: List<Subtitle> = emptyList(),
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
                    extraNote = it.quality
                )
            }

            val realSubtitles = response.data.subtitles.filter { it.kind != "thumbnails" }
            val subs = realSubtitles.map { sub ->
                Subtitle(
                    language = sub.lang,
                    url = sub.url
                )
            }


            val realTracks = response.data.tracks
            val tracks = realTracks.map { track ->
                AudioTrack(
                    bitrate = track.quality,
                    url = track.url,
                    note = track.type
                )
            }

            return VideoContainer(videos, subs, tracks)
        } catch (e: Exception) {
            return VideoContainer(emptyList())
        }

    }
}