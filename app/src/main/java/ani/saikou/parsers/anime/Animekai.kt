package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeApiParser
import ani.saikou.parsers.Episode

import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.MegaUp
import ani.saikou.tryWithSuspend


import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
class Animekai : AnimeApiParser() {

    override val name = "AnimeKai"
    override val providerName = "animekai"
    override val saveName = "AnimeKai"
    override val isDubAvailableSeparately = false

    override suspend fun search(query: String): List<ShowResponse> {
        return tryWithSuspend(post = false, true) {
            if (query.isBlank()) return@tryWithSuspend emptyList()
            val res = client.get(
                "$hostUrl/api/animekai/anime/search?q=$query",
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

        return tryWithSuspend(post = false, snackbar = true) {
            if (animeLink.isBlank()) return@tryWithSuspend emptyList()
            val url = "$hostUrl/api/animekai/anime/$animeLink"
            val res =
                client.get(url, headers = mapOf("x-api-key" to apiKey)).parsed<EpisodesResponse>()

            res.providerEpisodes.map { ep ->
                Episode(
                    number = ep.episodeNumber.toString(),
                    link = ep.episodeId,
                    title = ep.title,
                )
            }.sortedBy { it.number.toFloatOrNull() ?: 0f }
        } ?: emptyList()
    }


    override suspend fun loadVideoServers(
        episodeLink: String,
        extra: Map<String, String>?
    ): List<VideoServer> {

        return tryWithSuspend(post = false, true) {
            if (episodeLink.isEmpty()) return@tryWithSuspend emptyList()
            val res = client.get(
                "$hostUrl/api/animekai/episode/$episodeLink/servers",
                headers = mapOf("x-api-key" to apiKey)
            ).parsed<EpisodeServersResponse>()

            val servers = mutableListOf<VideoServer>()

            fun addVersionIfAvailable(version: String, list: List<ServerItem>, label: String) {
                list.forEach { item ->
                    val sourceUrl =
                        "$hostUrl/api/animekai/sources/$episodeLink?version=$version&server=${item.serverName}"
                    servers += VideoServer(
                        name = "$label - ${item.serverName}",
                        embed = FileUrl(sourceUrl)
                    )
                }
            }


            addVersionIfAvailable("sub", res.data.sub, "HardSub")
            addVersionIfAvailable("dub", res.data.dub, "Dub")
            addVersionIfAvailable("raw", res.data.raw, "SoftSub")


            servers.sortBy {
                when {
                    it.name.startsWith("Sub") -> 0
                    it.name.startsWith("Dub") -> 1
                    else -> 2
                }
            }

            servers

        } ?: emptyList()
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        return MegaUp(server)
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
    private data class EpisodesResponse(
        val providerEpisodes: List<EpisodeItem>
    )

    @Serializable
    private data class EpisodeItem(
        val episodeId: String,
        val title: String,
        val hasDub: Boolean,
        val hasSub: Boolean,
        val episodeNumber: Int
    )

    @Serializable
    private data class EpisodeServersResponse(
        val data: EpisodeServers
    )

    @Serializable
    private data class ServerItem(
        val serverName: String,
        val mediaId: String,
        val serverId: Int
    )

    @Serializable
    private data class EpisodeServers(
        val sub: List<ServerItem> = emptyList(),
        val dub: List<ServerItem> = emptyList(),
        val raw: List<ServerItem> = emptyList(),
        val episodeNumber: Int
    )
}