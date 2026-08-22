package ani.saikou.parsers.anime

import android.util.Log
import ani.saikou.BuildConfig
import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeApiParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.TorrentioExtractor
import ani.saikou.parsers.anime.extractors.TorrentioExtractor.TorrentInfo
import ani.saikou.parsers.anime.extractors.TorrentioExtractor.TorrentProviderData
import ani.saikou.parsers.anime.extractors.TorrentioExtractor.TorrentioResponse
import ani.saikou.tryWithSuspend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@OptIn(InternalSerializationApi::class)
class Torrentio : AnimeApiParser() {

    override val name = "Torrentio"
    override val saveName = "Torrentio"
    override val providerName = "kitsu"
    override val isDubAvailableSeparately = false

    override suspend fun search(query: String): List<ShowResponse> {
        return tryWithSuspend(post = false, snackbar = true) {
            if (query.isBlank()) return@tryWithSuspend emptyList()

            val res = client.get(
                "$hostUrl/api/anilist/anime/search?q=$query", headers = mapOf("x-api-key" to apiKey)
            ).parsed<SearchApiResponse>()

            res.data.map {
                ShowResponse(
                    name = it.title.english ?: it.title.romaji ?: it.title.native ?: "",
                    link = it.id.toString(),
                    coverUrl = FileUrl(it.image ?: it.bannerImage as String),
                )
            }
        } ?: emptyList()
    }
    override suspend fun loadEpisodes(
        animeLink: String, extra: Map<String, String>?
    ): List<Episode> {
        return tryWithSuspend(post = false, snackbar = true) {
            if (animeLink.isBlank()) return@tryWithSuspend emptyList()

            val kitsuUrl = "$hostUrl/api/anilist/anime/$animeLink/episodes"

            val episodes = client.get(
                kitsuUrl,
                headers = mapOf("x-api-key" to apiKey)
            ).parsed<EpisodesResponse>()

            episodes.data.map { ep ->
                Episode(
                    number = ep.episodeNumber.toString(),
                    link = "kitsu:${ep.kitsuId}:${ep.episodeNumber}",
                    title = ep.title,
                    thumbnail = ep.thumbnail?.let { FileUrl(it) },
                    description = ep.summary
                )
            }
        } ?: emptyList()
    }

    override suspend fun loadVideoServers(
        episodeLink: String, extra: Map<String, String>?
    ): List<VideoServer> {
        return tryWithSuspend(post = false, snackbar = true) {
            if (episodeLink.isBlank()) {
                Log.d("Torrentio", "loadVideoServers: episodeLink is blank")
                return@tryWithSuspend emptyList()
            }

            val embedUrl =
                "https://torrentio.strem.fun/providers=horriblesubs,nyaasi,tokyotosho,anidex,nekobt,yts,eztv|sort=seeders/stream/series/${episodeLink}.json" // sort by seeds for now adding BD later

            Log.d("Torrentio", "========== REQUEST ==========")
            Log.d("Torrentio", "Episode link: $episodeLink")
            Log.d("Torrentio", "Final URL: $embedUrl")

            try {
                val response = client.get(embedUrl)

                Log.d("Torrentio", "HTTP status: ${response.code}")

                val torrentioResponse = response.parsed<TorrentioResponse>()


                Log.d(
                    "Torrentio",
                    "Parsed streams: ${torrentioResponse.streams.size}"
                )

                if (torrentioResponse.streams.isEmpty()) {
                    Log.d("Torrentio", "Torrentio returned 0 streams")
                    return@tryWithSuspend emptyList()
                }

                val groupedByProvider = torrentioResponse.streams.groupBy { stream ->
                    getProviderName(stream)
                }

                Log.d(
                    "Torrentio",
                    "Providers found: ${groupedByProvider.keys}"
                )

                groupedByProvider.mapNotNull { (provider, streams) ->
                    val displayName =
                        providerDisplayNames[provider]
                            ?: provider.replaceFirstChar { it.uppercase() }

                    Log.d(
                        "Torrentio",
                        "Provider=$provider, displayName=$displayName, streams=${streams.size}"
                    )

                    val providerData = TorrentProviderData(
                        provider = provider,
                        displayName = displayName,
                        streams = streams
                    )

                    val jsonData = Json.encodeToString(providerData)

                    VideoServer(
                        name = displayName,
                        embed = FileUrl(embedUrl),
                        extraData = mapOf(
                            "providerData" to jsonData
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(
                    "Torrentio",
                    "Exception while requesting Torrentio",
                    e
                )

                emptyList()
            }
        } ?: emptyList()
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor {
        return TorrentioExtractor(server)
    }

    private val providerDisplayNames = mapOf(
        "horriblesubs" to "HorribleSubs",
        "nyaasi" to "Nyaa.si",
        "tokyotosho" to "Tokyo Toshokan",
        "anidex" to "AniDex",
        "nekobt" to "NekoBT",
        "yts" to "YTS",
        "eztv" to "EZTV"
    )

    private fun getProviderName(stream: TorrentInfo): String {
        val titleLower = stream.title.lowercase()
        val nameLower = stream.name.lowercase()

        for (provider in providerDisplayNames.keys) {
            if (titleLower.contains(provider) || nameLower.contains(provider)) {
                return provider
            }
        }

        val titleParts = stream.title.split(Regex("\\[|\\]"))
        for (part in titleParts) {
            val cleanPart = part.trim().lowercase()
            for (provider in providerDisplayNames.keys) {
                if (cleanPart.contains(provider)) {
                    return provider
                }
            }
        }

        return "unknown"
    }

    @Serializable
    private data class SearchApiResponse(
        val data: List<SearchItem>
    )

    @Serializable
    private data class SearchItem(
        val id: Int,
        val image: String? = null,
        val bannerImage: String? = null,
        val title: Title
    )

    @Serializable
    private data class Title(
        val romaji: String? = null, val english: String? = null, val native: String? = null
    )

    @Serializable
    private data class EpisodesResponse(
        val data: List<EpisodeItem>
    )

    @Serializable
    private data class EpisodeItem(
        val title: String?=null,
        val kitsuId: String,
        val thumbnail: String? = null,
        val episodeNumber: Int,
        val summary: String? = null
    )
}