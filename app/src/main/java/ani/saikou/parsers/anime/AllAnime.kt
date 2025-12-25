package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.DirectApiParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.*
import ani.saikou.tryWithSuspend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable


@OptIn(InternalSerializationApi::class)
class AllAnime : DirectApiParser() {

    override val name = "AllAnime"
    override val providerName = "allanime"
    override val saveName = "AllAnime"
    override val hostUrl = "https://kenjitsu.vercel.app"
    override val isDubAvailableSeparately = false

    override suspend fun search(query: String): List<ShowResponse> {
        return try {
            if (query.isBlank()) return emptyList()

            val res = client.get("$hostUrl/api/allanime/anime/search?q=$query")
                .parsed<SearchApiResponse>()

            res.data.map {
                val title = it.name?:it.romaji?:"N/A"
                ShowResponse(
                    name =title,
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
            if (animeLink.isBlank()) return emptyList()
            val url = "$hostUrl/api/allanime/anime/$animeLink/episodes"
            val res = client.get(url).parsed<EpisodesResponse>()

            res.data.map { ep ->
                Episode(
                    number = ep.episodeNumber.toString(),
                    link = ep.episodeId,
                    title = ep.title ?: "Episode ${ep.episodeNumber}",
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

        val subServers = fetchServersForVersion(episodeLink, "sub", hostUrl)
        val dubServers = fetchServersForVersion(episodeLink, "dub", hostUrl)


        val preferred = if (selectDub) dubServers else subServers
        val fallback = if (selectDub) subServers else dubServers


        return preferred + fallback
    }

    /**
     * Helper: fetch servers for one version and label them clearly
     */
    private suspend fun fetchServersForVersion(
        episodeLink: String,
        version: String,
        hostUrl: String
    ): List<VideoServer> {
        return try {
            val label = if (version == "dub") "Dub" else "Sub"

            val res =
                client.get("$hostUrl/api/allanime/episode/$episodeLink/servers?version=$version")
                    .parsed<EpisodeServersResponse>()

            res.data.map { item ->
                val sourceUrl =
                    "$hostUrl/api/allanime/sources/$episodeLink?version=$version&server=${item.serverId}"

                VideoServer(
                    name = "$label - ${item.serverId}",
                    embed = FileUrl(sourceUrl)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        return when (server.name.substringAfterLast(" - ")) {
            "internal-s-mp4" -> InternalSMP4(server)
            "internal-yt-mp4" -> InternalYtMP4(server)
            "internal-default-hls" -> AllAnimeExtractor(server)
            "internal-ak" -> InternalAK(server)
            "mp4upload" -> Mp4Upload(server)

            else -> null
        }
    }

    // ====================== DATA CLASSES ======================

    @Serializable
    private data class SearchApiResponse(val data: List<SearchItems>)

    @Serializable
    private data class SearchItems(
        val id: String,
        val romaji:String?=null,
        val name: String?=null,
        val posterImage: String
    )

    @Serializable
    private data class EpisodesResponse(
        val data: List<EpisodeItem>
    )

    @Serializable
    private data class EpisodeItem(
        val episodeId: String,
        val title: String?,
        val hasDub: Boolean,
        val hasSub: Boolean,
        val episodeNumber: Int
    )

    @Serializable
    data class EpisodeServersResponse(
        val data: List<ServerItem> = emptyList()
    )

    @Serializable
    data class ServerItem(
        val serverName: String,
        val serverId: String
    )
}