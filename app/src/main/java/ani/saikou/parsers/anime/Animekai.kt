package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.DirectApiParser
import ani.saikou.parsers.Episode

import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.MegaUp


import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import java.net.URLEncoder

@OptIn(InternalSerializationApi::class)
class Animekai : DirectApiParser() {

    override val name = "animekai"
    override val providerName = "animekai"
    override val saveName = "Animekai"
    override val hostUrl = "https://kenjitsu.vercel.app"
    override val isDubAvailableSeparately = false

    override suspend fun search(query: String): List<ShowResponse> {
        return try {
            if (query.isBlank()) return emptyList()
            val encoded = URLEncoder.encode(query, "utf-8")
            val res = client.get("$hostUrl/api/animekai/anime/search?q=$encoded")
                .parsed<SearchApiResponse>()

            res.data.map {
                ShowResponse(
                    name = it.name,
                    link = it.id,
                    coverUrl = FileUrl(it.posterImage)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List< Episode> {
        return try {
            val url = "$hostUrl/api/animekai/anime/$animeLink"
            val res = client.get(url).parsed<EpisodesResponse>()

            res.providerEpisodes.map { ep ->
                Episode(
                    number = ep.episodeNumber.toString(),
                    link = ep.episodeId,
                    title = ep.title,
                )
            }.sortedBy { it.number.toFloatOrNull() ?: 0f }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun loadVideoServers(
        episodeLink: String,
        extra: Map<String, String>?
    ): List<VideoServer> {


        return try {
            val encoded = URLEncoder.encode(episodeLink, "utf-8")
            val res = client.get("$hostUrl/api/animekai/episode/$encoded/servers")
                .parsed<EpisodeServersResponse>()

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
        } catch (e: Exception) {
            emptyList()
        }
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