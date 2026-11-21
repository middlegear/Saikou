package ani.saikou.extensions

import ani.saikou.FileUrl
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer

/**
 * Exposes a CloudStream provider (from a loaded plugin) as a Saikou [AnimeParser].
 * CloudStream providers cover movies and series as well as anime.
 */
class CloudStreamParser(private val provider: CloudStreamProvider) : AnimeParser() {

    override val name: String = provider.name

    override val saveName: String = provider.key

    override val hostUrl: String = provider.url.ifBlank { "https://localhost" }

    override val isNSFW = false

    override val language: String = provider.language

    private val videoCache = mutableMapOf<String, Map<String, Any?>>()

    private val episodeCache = mutableMapOf<String, Map<String, Any?>>()

    override suspend fun search(query: String): List<ShowResponse> =
        ExtensionRuntime.cloudStreamSearch(provider.name, query)
            .mapNotNull { it.toShowResponse() }

    override suspend fun loadEpisodes(
        animeLink: String,
        extra: Map<String, String>?,
    ): List<Episode> {
        val detail = ExtensionRuntime.cloudStreamDetail(provider.name, animeLink)
        episodeCache.clear()
        return detail["episodes"].asMapList().mapNotNull { entry ->
            val number = episodeNumberOf(entry)
            val url = entry.str("url") ?: return@mapNotNull null
            episodeCache[url] = entry
            entry.toEpisode(number)
        }
    }

    override suspend fun loadVideoServers(
        episodeLink: String,
        extra: Map<String, String>?,
    ): List<VideoServer> {
        videoCache.clear()
        return ExtensionRuntime.cloudStreamVideos(provider.name, episodeLink).mapNotNull { link ->
            val url = link.str("url") ?: return@mapNotNull null
            val label = link.str("title") ?: link.str("name") ?: link.str("quality") ?: "Video"
            videoCache[url] = link
            VideoServer(
                name = label,
                embed = FileUrl(url, link.stringMap("headers")),
            )
        }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? =
        RuntimeVideoExtractor(server, videoCache[server.embed.url] ?: emptyMap())
}
