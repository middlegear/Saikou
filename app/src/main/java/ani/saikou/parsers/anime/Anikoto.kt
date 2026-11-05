package ani.saikou.parsers.anime


import ani.saikou.BuildConfig
import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.MegaPlay
import ani.saikou.tryWithSuspend
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import java.net.URLEncoder

@OptIn(InternalSerializationApi::class)
class Anikoto : AnimeParser() {

    override val name = "anikoto"
    override val saveName = "anikoto"
    override val hostUrl: String = BuildConfig.SERVER_URL
    val apiKey: String = BuildConfig.MY_CUSTOM_API_KEY
    override val isDubAvailableSeparately = false

    override suspend fun search(query: String): List<ShowResponse> {
        return tryWithSuspend(post = true, snackbar = true) {
            if (query.isBlank()) return@tryWithSuspend emptyList()

            val encoded = URLEncoder.encode(query, "utf-8")
            val res = client.get(
                "$hostUrl/api/anikoto/anime/search?q=$encoded",
                headers = mapOf("x-api-key" to apiKey)
            ).parsed<SearchApiResponse>()

            res.data.map {
                ShowResponse(
                    name = it.name,
                    link = it.id,
                    coverUrl = FileUrl(it.posterImage)
                )
            }
        } ?: emptyList()
    }

    override suspend fun loadEpisodes(
        animeLink: String,
        extra: Map<String, String>?
    ): List<Episode> {
        return tryWithSuspend(post = true, snackbar = true) {
            if (animeLink.isBlank()) return@tryWithSuspend emptyList()

            val res = client.get(
                "$hostUrl/api/anikoto/anime/$animeLink",
                headers = mapOf("x-api-key" to apiKey)
            ).parsed<EpisodesResponse>()

            res.providerEpisodes.map { ep ->
                Episode(
                    number = ep.episodeNumber.toString(),
                    link = ep.episodeId,
                    title = ep.title,
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

            val res = client.get(
                "$hostUrl/api/anikoto/episode/$episodeLink/servers",
                headers = mapOf("x-api-key" to apiKey)
            ).parsed<EpisodeServersResponse>()

            val candidates = listOf("sub" to res.data.sub, "dub" to res.data.dub)
                .flatMap { (version, list) ->
                    list.map { item -> version to item }
                }

            coroutineScope {
                candidates.map { (version, item) ->
                    async {
                        runCatching {
                            val resolved = client.get(
                                "$SITE_URL/ajax/server",
                                headers = mapOf(
                                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                                    "X-Requested-With" to "XMLHttpRequest"
                                ),
                                params = mapOf("get" to item.eid)
                            ).parsed<EmbedResponse>()

                            VideoServer(
                                name = "${version.uppercase()} - ${item.serverName}",
                                embed = FileUrl(resolved.result.url, mapOf("Referer" to "$SITE_URL/")),
                            )
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }
        } ?: emptyList()
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor = MegaPlay(server)

    companion object {
        private const val SITE_URL = "https://anikototv.to"
    }

    @Serializable
    private data class SearchApiResponse(val data: List<SearchItems>)

    @Serializable
    private data class SearchItems(
        val id: String,
        val name: String,
        val posterImage: String
    )

    @Serializable
    private data class EpisodesResponse(val providerEpisodes: List<EpisodeItem>)

    @Serializable
    private data class EpisodeItem(
        val episodeId: String,
        val title: String,
        val episodeNumber: Int,
    )

    @Serializable
    private data class EpisodeServersResponse(val data: EpisodeServers)

    @Serializable
    private data class ServerItem(
        val serverName: String,
        val serverId: String,
        val mediaId: String,
        val eid: String
    )

    @Serializable
    private data class EpisodeServers(
        val sub: List<ServerItem> = emptyList(),
        val dub: List<ServerItem> = emptyList(),
        val raw: List<ServerItem> = emptyList(),
        val episodeNumber: Int
    )

    @Serializable
    private data class EmbedResponse(val result: EmbedResult)

    @Serializable
    private data class EmbedResult(val url: String)
}