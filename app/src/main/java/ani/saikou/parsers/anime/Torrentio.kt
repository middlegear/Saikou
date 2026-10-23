package ani.saikou.parsers.anime

import android.content.Context
import android.util.Log
import ani.saikou.BuildConfig
import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.connections.anilist.room.debrid.DebridRepository
import ani.saikou.currContext
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.TorrentioExtractor
import ani.saikou.parsers.anime.extractors.TorrentioExtractor.TorrentInfo
import ani.saikou.parsers.anime.extractors.TorrentioExtractor.TorrentProviderData
import ani.saikou.parsers.anime.extractors.TorrentioExtractor.TorrentioResponse
import ani.saikou.torrserver.utils.TorrentioConfig
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ani.saikou.tryWithSuspend

@OptIn(InternalSerializationApi::class)
class Torrentio : AnimeParser() {

    override val name = "Torrentio"
    override val saveName = "Torrentio"

    override val isDubAvailableSeparately = false
    override val hostUrl: String = BuildConfig.SERVER_URL
    val apiKey: String = BuildConfig.MY_CUSTOM_API_KEY

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
                headers = mapOf("x-api-key" to apiKey), timeout = 20L
            ).parsed<EpisodesResponse>()

            episodes.data.map { ep ->
                Episode(
                    number = ep.episodeNumber.toString(),
                    link = "${ep.kitsuId ?: ""}|${ep.imdbId ?: ""}|${ep.episodeNumber}|${ep.type ?: ""}",
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

            val parts = episodeLink.split("|")
            val kitsuId = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
            val imdbId = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            val episodeNumber = parts.getOrNull(2)
            val type = parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: "series"

            if (kitsuId == null && imdbId == null) {
                Log.d("Torrentio", "loadVideoServers: no kitsu or imdb id available")
                return@tryWithSuspend emptyList()
            }

            val kitsuStreamId: String? = kitsuId?.let {
                if (type == "movie") "kitsu:$it" else "kitsu:$it:$episodeNumber"
            }

            var activeStreamId: String? = null
            var torrentioResponse: TorrentioResponse? = null

            // 1. Try Kitsu ID
            if (kitsuStreamId != null) {
                activeStreamId = kitsuStreamId
                torrentioResponse = fetchTorrentioStreams(type, kitsuStreamId)
            } else {
                Log.d("Torrentio", "kitsu id is null, skipping to imdb id")
            }

            // 2. Fallback to IMDb ID directly as provided
            if ((torrentioResponse == null || torrentioResponse.streams.isEmpty()) && imdbId != null) {
                if (kitsuStreamId != null) {
                    Log.d("Torrentio", "kitsu id returned no streams, falling back to imdb id: $imdbId")
                }
                activeStreamId = imdbId
                torrentioResponse = fetchTorrentioStreams(type, imdbId)
            }

            // 3. Cache-bust retry: only reached when the kitsu and imdb fetches both failed
            if (torrentioResponse == null || torrentioResponse.streams.isEmpty()) {
                Log.d("Torrentio", "kitsu and imdb fetches failed, retrying with cache bust")

                for (candidate in listOfNotNull(kitsuStreamId, imdbId)) {
                    val retry = fetchTorrentioStreams(type, candidate, cacheBust = true)
                    if (retry != null && retry.streams.isNotEmpty()) {
                        torrentioResponse = retry
                        activeStreamId = candidate
                        break
                    }
                }
            }

            if (torrentioResponse == null || torrentioResponse.streams.isEmpty()) {
                Log.d("Torrentio", "Torrentio returned 0 streams for kitsu, imdb and cache-busted retries")
                return@tryWithSuspend emptyList()
            }

            val embedUrl = buildEmbedUrl(type, activeStreamId!!)

            val groupedByProvider = torrentioResponse.streams.groupBy { stream ->
                getProviderName(stream)
            }

            Log.d("Torrentio", "Providers found: ${groupedByProvider.keys}")

            groupedByProvider.mapNotNull { (provider, streams) ->
                val displayName =
                    providerDisplayNames[provider]
                        ?: provider.replaceFirstChar { it.uppercase() }

                Log.d("Torrentio", "Provider=$provider, displayName=$displayName, streams=${streams.size}")

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
        } ?: emptyList()
    }

    suspend fun buildEmbedUrl(type: String, streamId: String, cacheBust: Boolean = false): String {
        val segments = TorrentioConfig.load().toSegments().toMutableList()
        activeDebridSegment()?.let { segments += it }
        val base = "https://torrentio.strem.fun/${segments.joinToString("|")}/stream/$type/$streamId.json"
        return if (cacheBust) "$base?t=${System.currentTimeMillis()}" else base
    }

    private suspend fun activeDebridSegment(): String? {
        val context: Context = currContext() ?: return null
        val repository = DebridRepository(context)
        val active = runCatching { repository.getActive()?.provider }.getOrNull()
        val segment = runCatching { repository.activeConfigSegment() }.getOrNull()

        if (active != null && segment == null) {
            Log.w(
                "Torrentio",
                "Debrid provider '$active' is selected but unsupported or missing credentials; using magnet links"
            )
        }
        return segment
    }

    private fun redact(url: String): String =
        Regex("(${DebridRepository.CONFIG_KEYS.joinToString("|")})=[^|/]+").replace(url, "\$1=***")

    suspend fun fetchTorrentioStreams(
        type: String,
        streamId: String,
        cacheBust: Boolean = false
    ): TorrentioResponse? {
        val embedUrl = buildEmbedUrl(type, streamId, cacheBust)

        Log.d("Torrentio", "Stream id: $streamId (cacheBust=$cacheBust)")
        Log.d("Torrentio", "Final URL: ${redact(embedUrl)}")

        return try {
            val response = client.get(embedUrl, timeout = 15L)
            Log.d("Torrentio", "HTTP status: ${response.code}")

            val parsed = response.parsed<TorrentioResponse>()
            Log.d("Torrentio", "Parsed streams: ${parsed.streams.size}")
            parsed
        } catch (e: Exception) {
            Log.e(
                "Torrentio",
                "Exception while requesting Torrentio for $streamId: ${e::class.simpleName}"
            )
            null
        }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor {
        return TorrentioExtractor(server)
    }

    private val providerDisplayNames = mapOf(
        "eztv" to "EZTV",
        "rarbg" to "RARBG",
        "1337x" to "1337x",
        "ext" to "ExtraTorrent",
        "thepiratebay" to "The Pirate Bay",
        "kickasstorrents" to "KickassTorrents",
        "torrentgalaxy" to "TorrentGalaxy",
        "magnetdl" to "MagnetDL",
        "horriblesubs" to "HorribleSubs",
        "nyaasi" to "Nyaa.si",
        "tokyotosho" to "Tokyo Toshokan",
        "anidex" to "AniDex",
        "nekobt" to "NekoBT",
        "yts" to "YTS",
        "rutor" to "Rutor",
        "rutracker" to "Rutracker",
        "comando" to "Comando",
        "bludv" to "BluDV",
        "micoleaodublado" to "MicoLeaoDublado",
        "torrent9" to "Torrent9",
        "ilcorsaronero" to "ilCorSaRoNero",
        "mejortorrent" to "MejorTorrent",
        "wolfmax4k" to "Wolfmax4k",
        "cinecalidad" to "Cinecalidad",
        "besttorrents" to "BestTorrents"
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
        val title: String? = null,
        val kitsuId: String? = null,
        val thumbnail: String? = null,
        val episodeNumber: Int,
        val imdbId: String? = null,
        val summary: String? = null,
        val type: String? = null
    )
}