package ani.saikou.extensions

import ani.saikou.FileUrl
import ani.saikou.parsers.Subtitle
import ani.saikou.parsers.SubtitleType
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType

/**
 * A [VideoExtractor] that returns links already resolved by an extension
 * (Aniyomi/Aniyomi-HTTP-source or CloudStream). No scraping happens here: the runtime host
 * hands us direct URLs plus the headers/subtitles that belong to them.
 */
class RuntimeVideoExtractor(
    override val server: VideoServer,
    private val data: Map<String, Any?>,
) : VideoExtractor() {

    override suspend fun extract(): VideoContainer {
        val url = server.embed.url
        val format = when {
            data["isM3u8"] == true -> VideoType.M3U8
            data["isDash"] == true -> VideoType.DASH
            url.contains(".m3u8", ignoreCase = true) -> VideoType.M3U8
            url.contains(".mpd", ignoreCase = true) -> VideoType.DASH
            else -> VideoType.CONTAINER
        }

        val quality = (data["resolution"] as? Number)?.toInt()
            ?: Regex("""(\d{3,4})p""")
                .find(data.str("quality") ?: "")
                ?.groupValues?.get(1)?.toIntOrNull()

        val video = Video(
            quality = quality,
            format = format,
            file = FileUrl(url, server.embed.headers),
            size = null,
            extraNote = data.str("source")?.takeIf { it.isNotBlank() },
        )

        val subtitles = data["subtitles"].asMapList().mapNotNull { entry ->
            val file = entry.str("file") ?: entry.str("url") ?: return@mapNotNull null
            val label = entry.str("label") ?: entry.str("lang") ?: "Subtitle"
            Subtitle(
                language = label,
                file = FileUrl(file, entry.stringMap("headers")),
                type = subtitleTypeOf(file),
                headers = entry.stringMap("headers"),
            )
        }

        return VideoContainer(
            videos = listOf(video),
            subtitles = subtitles,
            audioTracks = emptyList(),
        )
    }

    private fun subtitleTypeOf(url: String): SubtitleType = when {
        url.contains(".ass", ignoreCase = true) -> SubtitleType.ASS
        url.contains(".srt", ignoreCase = true) -> SubtitleType.SRT
        else -> SubtitleType.VTT
    }
}
