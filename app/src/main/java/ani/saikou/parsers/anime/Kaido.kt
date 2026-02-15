package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeApiParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.MegaCloud
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import java.net.URLEncoder
@OptIn(InternalSerializationApi::class)
class Kaido : AnimeApiParser() {

    override val name = "Kaido"
    override val saveName = "Kaido"
    override val providerName = "hianime"
    override val hostUrl = "https://kenjitsu.vercel.app"
    override val isDubAvailableSeparately = false


    override suspend fun search(query: String): List<ShowResponse> {
        return try {
            if (query.isBlank()) return emptyList()

            val encoded = URLEncoder.encode(query, "utf-8")
            val res = client.get("$hostUrl/api/kaido/anime/search?q=$encoded")
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
            val url = "$hostUrl/api/kaido/anime/$animeLink"
            val res = client.get(url).parsed<EpisodesResponse>()

            res.providerEpisodes.map { ep ->
                Episode(
                    number = ep.episodeNumber.toString(),
                    link = ep.episodeId,
                    title = ep.title,

                )
            }.sortedBy { it.number.toFloatOrNull() ?: 0f }
        }  catch (e: Exception) {
            emptyList()
        }
    }
    override suspend fun loadVideoServers(
        episodeLink: String,
        extra: Map<String, String>?
    ): List<VideoServer> {
        return try {
            val encoded = URLEncoder.encode(episodeLink, "utf-8")
            val res = client.get("$hostUrl/api/kaido/episode/$encoded/servers")
                .parsed<EpisodeServersResponse>()

            val allServers = mutableListOf<VideoServer>()

            fun addServers(version: String, list: List<ServerItem>) {
                list.forEach { item ->
                    val serverName = "${version.uppercase()} - ${item.serverName}"
                    val embedUrl =
                        "$hostUrl/api/kaido/sources/$episodeLink?version=$version&server=${item.serverName}"

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
        } catch (e: Exception) {
            emptyList()
        }
    }



    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {

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