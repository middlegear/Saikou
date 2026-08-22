package ani.saikou.parsers.anime

import ani.saikou.BuildConfig
import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeApiParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.AniZoneExtractor
import ani.saikou.tryWithSuspend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import kotlin.collections.mapOf

@OptIn(InternalSerializationApi::class)
class Anizone : AnimeApiParser() {

    override val name = "Anizone"
    override val saveName = "Anizone"
    override val providerName = "anizone"
    override val isDubAvailableSeparately = false

    override suspend fun search(query: String): List<ShowResponse> {
        return tryWithSuspend(post = false, snackbar = true) {
            if (query.isBlank()) return@tryWithSuspend emptyList()
            val encoded = URLEncoder.encode(query, "utf-8")
            val res = client.get(
                "$hostUrl/api/anizone/anime/search?q=$encoded",
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
            val url = "$hostUrl/api/anizone/anime/$animeLink"
            val res =
                client.get(url, headers = mapOf("x-api-key" to apiKey)).parsed<EpisodesResponse>()

            res.providerEpisodes.map { ep ->
                Episode(
                    number = ep.episodeNumber.toString(),
                    link = ep.episodeId,
                    title = ep.title ?: "Episode ${ep.episodeNumber}",
                    thumbnail = ep.thumbnail?.let { FileUrl(it) },
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
            val embedUrl = "$hostUrl/api/anizone/sources/$episodeLink"

            return@tryWithSuspend listOf(
                VideoServer(
                    name = "MULTI",
                    embed = FileUrl(embedUrl),
                    extraData = null
                )
            )

        } ?: emptyList()
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor {
        return AniZoneExtractor(server)
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
        val title: String?,
        val thumbnail: String?=null,
        val episodeNumber: Int?=null
    )


}