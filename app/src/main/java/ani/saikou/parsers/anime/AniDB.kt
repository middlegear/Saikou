package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.AniDBExtractor
import ani.saikou.tryWithSuspend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import java.net.URLEncoder

@OptIn(InternalSerializationApi::class)
class AniDB : AnimeParser() {

    override val name = "AniDB"
    override val saveName = "AniDB"
    override val hostUrl = "https://anidb.app"
    override val isDubAvailableSeparately = false

    override suspend fun search(query: String): List<ShowResponse> {
        return tryWithSuspend(post = false, snackbar = true) {
            if (query.isBlank()) return@tryWithSuspend emptyList()
            val encoded = URLEncoder.encode(query, "utf-8")
            val doc = client.get("$hostUrl/browse?q=$encoded").document

            doc.select("div.anime-grid > a").mapNotNull { element ->
                val href = element.attr("href")
                val id = href.split("/").lastOrNull() ?: return@mapNotNull null
                val name = element.attr("title").ifBlank { element.text() }
                val poster = element.selectFirst("div > img")?.attr("src") ?: ""

                ShowResponse(
                    name = name,
                    link = id,
                    coverUrl = FileUrl(poster)
                )
            }
        } ?: emptyList()
    }

    override suspend fun loadEpisodes(
        animeLink: String,
        extra: Map<String, String>?
    ): List<Episode> {
        return tryWithSuspend(post = false, snackbar = true) {
            if (animeLink.isBlank()) return@tryWithSuspend emptyList()

            val numericId = animeLink.substringAfterLast("-")
            val url = "$hostUrl/api/frontend/anime/$numericId/episodes"
            val res = client.get(url).parsed<EpisodeResponse>()

            res.episodes
                .filter { (it.number ?: 0f) >= 0f }
                .sortedBy { it.number ?: 0f }
                .mapIndexed { index, ep ->
                    Episode(
                        number = (index + 1).toString(),
                        link = ep.id,
                        title = ep.title ?: "Episode ${index + 1}"
                    )
                }
        } ?: emptyList()
    }

    override suspend fun loadVideoServers(
        episodeLink: String,
        extra: Map<String, String>?
    ): List<VideoServer> {
        return tryWithSuspend(post = false, snackbar = true) {
            if (episodeLink.isBlank()) return@tryWithSuspend emptyList()

            val url = "$hostUrl/api/frontend/episode/$episodeLink/languages"
            val res = client.get(url).parsed<LanguageResponse>()

            val servers = mutableListOf<VideoServer>()

            res.languages.forEach { lang ->
                val embedUrl = lang.embed_url ?: return@forEach
                val isDub = lang.code?.equals("eng", ignoreCase = true) == true
                val prefix = if (isDub) "Dub" else "Sub"

                servers += VideoServer(
                    name = "$prefix - ${lang.name}",
                    embed = FileUrl(embedUrl)
                )
            }

            servers
        } ?: emptyList()
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor {
        return AniDBExtractor(server)
    }

    @Serializable
    private data class EpisodeResponse(
        val episodes: List<EpisodeItem> = emptyList()
    )

    @Serializable
    private data class EpisodeItem(
        val id: String,
        val number: Float? = null,
        val filler: Boolean = false,
        val title: String? = null
    )

    @Serializable
    private data class LanguageResponse(
        val languages: List<LanguageItem> = emptyList()
    )

    @Serializable
    private data class LanguageItem(
        val name: String,
        val code: String? = null,
        val embed_url: String? = null
    )
}