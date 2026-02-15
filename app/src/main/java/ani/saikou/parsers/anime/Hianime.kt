package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeApiParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.MegaCloud
import ani.saikou.tryWithSuspend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import java.net.URLEncoder

@OptIn(InternalSerializationApi::class)
class Hianime : AnimeApiParser() {

    override val name = "hianime"
    override val saveName = "hianime"
    override val providerName = "hianime"
    override val isDubAvailableSeparately = false


    override suspend fun search(query: String): List<ShowResponse> {
        return tryWithSuspend(post = true, snackbar = true) {
            if (query.isBlank()) return@tryWithSuspend emptyList()

            val encoded = URLEncoder.encode(query, "utf-8")
            val res = client.get(
                "$hostUrl/api/hianime/anime/search?q=$encoded",
                headers = mapOf("x-api-key" to apiKey)
            )
                .parsed<SearchApiResponse>()

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

            val url = "$hostUrl/api/hianime/anime/$animeLink"
            val res =
                client.get(url, headers = mapOf("x-api-key" to apiKey)).parsed<EpisodesResponse>()

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
                "$hostUrl/api/hianime/episode/$episodeLink/servers",
                headers = mapOf("x-api-key" to apiKey)
            ).parsed<EpisodeServersResponse>()

            val allServers = mutableListOf<VideoServer>()

            fun addServers(version: String, list: List<ServerItem>) {
                list.forEach { item ->
                    val serverName = "${version.uppercase()} - ${item.serverName}"
                    val embedUrl =
                        "$hostUrl/api/hianime/sources/$episodeLink?version=$version&server=${item.serverName}"
                    allServers += VideoServer(
                        name = serverName,
                        embed = FileUrl(embedUrl),
                        extraData = null
                    )
                }
            }

            addServers("sub", res.data.sub)
            addServers("dub", res.data.dub)
            addServers("raw", res.data.raw)

            allServers
        } ?: emptyList()
    }


    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor {

        return MegaCloud(server)
    }


    @Serializable
    private data class SearchApiResponse(
        val data: List<SearchItems>
    )

    @Serializable
    private data class SearchItems(
        val id: String,
        val name: String,
        val posterImage: String

    )

    @Serializable
    private data class EpisodesResponse(
        val providerEpisodes: List<EpisodeItem>
    )

    @Serializable
    private data class EpisodeItem(
        val episodeId: String,
        val title: String,
        val romaji: String,
        val episodeNumber: Int,
    )

    @Serializable
    private data class EpisodeServersResponse(
        val data: EpisodeServers
    )

    @Serializable
    private data class ServerItem(
        val serverName: String,
        val serverId: Int,
        val mediaId: Int
    )

    @Serializable
    private data class EpisodeServers(
        val sub: List<ServerItem> = emptyList(),
        val dub: List<ServerItem> = emptyList(),
        val raw: List<ServerItem> = emptyList(),
        val episodeNumber: Int
    )


}