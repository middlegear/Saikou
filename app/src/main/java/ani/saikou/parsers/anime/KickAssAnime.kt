package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.KickAssAnimeExtractor
import ani.saikou.tryWithSuspend
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

@OptIn(InternalSerializationApi::class)
class KickAssAnime : AnimeParser() {

    override val name = "KickAssAnime"
    override val saveName = "KickAssAnime"
    override val hostUrl = "https://kaa.lt"
    override val isDubAvailableSeparately = false

    private val apiUrl: String
        get() = "$hostUrl/api/show"

    override suspend fun search(query: String): List<ShowResponse> =
        tryWithSuspend(post = false, snackbar = true) {
            if (query.isBlank()) return@tryWithSuspend emptyList()

            val body = buildJsonObject {
                put("page", 1)
                put("query", query)
            }.toString().toRequestBody("application/json".toMediaType())

            val res = client.post(
                "$hostUrl/api/fsearch",
                headers = mapOf(
                    "Accept" to "application/json, text/plain, */*",
                    "Referer" to "$hostUrl/search?q=${URLEncoder.encode(query, "utf-8")}"
                ),
                requestBody = body
            ).parsed<SearchResponse>()

            res.result.map { item ->
                ShowResponse(
                    name = item.title.ifBlank { item.title_en.orEmpty() },
                    link = item.slug,
                    coverUrl = FileUrl("$hostUrl/${item.poster.url.trimStart('/')}")
                )
            }
        } ?: emptyList()

    override suspend fun loadEpisodes(
        animeLink: String,
        extra: Map<String, String>?
    ): List<Episode> =
        tryWithSuspend(post = false, snackbar = true) {
            if (animeLink.isBlank()) return@tryWithSuspend emptyList()


            val languages = client.get("$apiUrl/$animeLink/language").parsed<LanguagesResponse>().result
            val langOrder = languages.sortedWith(
                compareBy<String>({ it != PREFERRED_LANG }, { it != SECONDARY_LANG })
            )


            for (lang in langOrder) {
                val firstPage = runCatching {
                    client.get(
                        "$apiUrl/$animeLink/episodes",
                        params = mapOf("page" to "1", "lang" to lang)
                    ).parsed<EpisodesResponse>()
                }.getOrNull()
                if (firstPage == null || firstPage.result.isEmpty()) continue

                val otherPages = coroutineScope {
                    (2..firstPage.pages.size).map { page ->
                        async {
                            runCatching {
                                client.get(
                                    "$apiUrl/$animeLink/episodes",
                                    params = mapOf("page" to page.toString(), "lang" to lang)
                                ).parsed<EpisodesResponse>().result
                            }.getOrDefault(emptyList())
                        }
                    }.awaitAll().flatten()
                }

                return@tryWithSuspend (firstPage.result + otherPages).map { ep ->
                    Episode(
                        number = ep.episode_string,
                        link = "$animeLink/episode/ep-${ep.episode_string}-${ep.slug}",
                        title = ep.title?.takeIf { it.isNotBlank() } ?: "Episode ${ep.episode_string}"
                    )
                }
            }

            emptyList<Episode>()
        } ?: emptyList()

    override suspend fun loadVideoServers(
        episodeLink: String,
        extra: Map<String, String>?
    ): List<VideoServer> =
        tryWithSuspend(post = false, snackbar = true) {
            if (episodeLink.isBlank()) return@tryWithSuspend emptyList()

            client.get("$apiUrl/$episodeLink").parsed<ServersResponse>().servers.map { server ->
                VideoServer(
                    name = server.name,
                    embed = FileUrl(server.src),
                    extraData = null
                )
            }
        } ?: emptyList()

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor = KickAssAnimeExtractor(server)

    companion object {
        private const val PREFERRED_LANG = "ja-JP"
        private const val SECONDARY_LANG = "en-US"
    }

    @Serializable
    private data class Poster(val url: String = "")

    @Serializable
    private data class ShowItem(
        val title: String = "",
        val title_en: String? = null,
        val slug: String,
        val poster: Poster = Poster()
    )

    @Serializable
    private data class SearchResponse(val result: List<ShowItem> = emptyList())

    @Serializable
    private data class LanguagesResponse(val result: List<String> = emptyList())

    @Serializable
    private data class EpisodeItem(
        val episode_string: String,
        val title: String? = null,
        val slug: String
    )

    @Serializable
    private data class EpisodesResponse(
        val pages: List<JsonElement> = emptyList(),
        val result: List<EpisodeItem> = emptyList()
    )

    @Serializable
    private data class ServerItem(val name: String, val src: String)

    @Serializable
    private data class ServersResponse(val servers: List<ServerItem> = emptyList())
}