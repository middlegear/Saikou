package ani.saikou.extensions

import ani.saikou.FileUrl
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer

/**
 * Exposes an installed Aniyomi/Tachiyomi anime extension source as a Saikou [AnimeParser],
 * so extension sources appear next to the built-in ones in every source picker.
 */
class AniyomiAnimeParser(private val source: AniyomiSource) : AnimeParser() {

    override val name: String = source.name

    override val saveName: String = source.key

    override val hostUrl: String = source.baseUrl.ifBlank { "https://localhost" }

    override val isNSFW: Boolean = source.isNsfw

    override val language: String = source.lang

    private val videoCache = mutableMapOf<String, Map<String, Any?>>()

    override suspend fun search(query: String): List<ShowResponse> =
        ExtensionRuntime.aniyomiSearch(source.id, true, query)
            .mapNotNull { it.toShowResponse() }

    override suspend fun loadEpisodes(
        animeLink: String,
        extra: Map<String, String>?,
    ): List<Episode> {
        val detail = ExtensionRuntime.aniyomiDetail(
            sourceId = source.id,
            isAnime = true,
            url = animeLink,
            title = extra?.get("title"),
        )
        return detail["episodes"].asMapList().mapNotNull { entry ->
            val number = episodeNumberOf(entry)
            entry.toEpisode(number)
        }
    }

    override suspend fun loadVideoServers(
        episodeLink: String,
        extra: Map<String, String>?,
    ): List<VideoServer> {
        val episodeName = extra?.get("name") ?: "Episode"
        val episodeNumber = extra?.get("number")?.toFloatOrNull() ?: 1f

        videoCache.clear()

        return ExtensionRuntime.aniyomiVideos(
            sourceId = source.id,
            isAnime = true,
            episodeUrl = episodeLink,
            episodeName = episodeName,
            episodeNumber = episodeNumber,
        ).mapNotNull { video ->
            val url = video.str("url") ?: return@mapNotNull null
            val label = video.str("title")
                ?: video.str("quality")
                ?: video.str("name")
                ?: "Video"
            videoCache[url] = video
            VideoServer(
                name = label,
                embed = FileUrl(url, video.stringMap("headers")),
            )
        }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        val data = videoCache[server.embed.url] ?: emptyMap()
        return RuntimeVideoExtractor(server, data)
    }
}
