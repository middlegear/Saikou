package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.loadData
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.DirectApiParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType
import ani.saikou.parsers.anime.extractors.Kwik
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import kotlin.math.absoluteValue
import kotlin.math.pow

class AnimePahe : DirectApiParser() {

    override val name = "AnimePahe"
    override val saveName = "animepahe"
    override val providerName = "animepahe"

    override val hostUrl = "https://kenjitsu.vercel.app"
    override val isDubAvailableSeparately = false


    override suspend fun search(query: String): List<ShowResponse> {
        return try {
            if (query.isBlank()) return emptyList()

            val encoded = URLEncoder.encode(query, "utf-8")
            val res = client.get("$hostUrl/api/animepahe/anime/search?q=$encoded")
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


    override suspend fun loadEpisodes(
        animeLink: String,
        extra: Map<String, String>?
    ): List<Episode> {
        return try {
            val url = "$hostUrl/api/animepahe/anime/$animeLink"
            val res = client.get(url).parsed<EpisodesResponse>()

            res.providerEpisodes.map { ep ->
                Episode(
                    number = ep.episodeNumber.toString(),
                    link = ep.episodeId,
                    title = ep.title,
                    thumbnail = ep.thumbnail

                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun loadVideoServers(
        episodeLink: String,
        extra: Map<String, String>?
    ): List<VideoServer> {
        return try {
            val res = client.get("$hostUrl/api/animepahe/episode/$episodeLink/servers")
                .parsed<EpisodeServersResponse>()

            val allServers = mutableListOf<VideoServer>()


            if (res.data.sub.isNotEmpty()) {
                allServers += VideoServer(
                    name = "Sub - Multi Quality",
                    embed = FileUrl("$hostUrl/api/animepahe/sources/$episodeLink?version=sub")
                )
            }
            if (res.data.dub.isNotEmpty()) {
                allServers += VideoServer(
                    name = "Dub - Multi Quality",
                    embed = FileUrl("$hostUrl/api/animepahe/sources/$episodeLink?version=dub")
                )
            }

            allServers
        } catch (e: Exception) {
            emptyList()
        }
    }

    @OptIn(InternalSerializationApi::class)
    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {

        return Kwik(server)
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
        val episodeNumber: Int,
        val thumbnail: String,
        val title: String? = null
    )

    @Serializable
    private data class EpisodeServersResponse(
        val data: EpisodeServers
    )

    @Serializable
    private data class ServerItem(
        val serverName: String,
        val serverId: String,
        val mediaId: String
    )

    @Serializable
    private data class EpisodeServers(
        val sub: List<ServerItem> = emptyList(),
        val dub: List<ServerItem> = emptyList(),
        val raw: List<ServerItem> = emptyList(),
        val episodeNumber: Int
    )


}
