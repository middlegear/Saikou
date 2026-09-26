package ani.saikou.others

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.currContext
import ani.saikou.media.Media
import ani.saikou.media.anime.Episode
import ani.saikou.media.anime.mpv.PlayerRepository
import ani.saikou.tryWithSuspend
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object TheMovieDatabase {

    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun fetchMetadata(media: Media): TmdbMetaResponse? {
        val anilistId = media.id
        return tryWithSuspend {
            val url = "https://api.kenjitsu.workers.dev/api/meta/anilist/$anilistId?platform=tmdb"
            val response = client.get(url, timeout = 15L)
            response.parsed<TmdbMetaResponse>()
        }
    }

    suspend fun getTmdbEpisodesDetails(media: Media): Map<String, Episode>? {
        val result = fetchMetadata(media) ?: return null
        val data = result.data ?: return null

        media.idTMDB = data.tmdbId.toString()

        val bestLogo = data.artWorks?.logos?.firstOrNull() ?: data.fanart?.logo?.firstOrNull()

        val bestBackdrop = data.fanart?.backdrop?.firstOrNull()
            ?: data.artWorks?.backdrop?.firstOrNull()

        media.anime?.tmdbLogo = bestLogo
        media.anime?.tmdbBackdrop = bestBackdrop


        preloadArtwork(bestLogo, bestBackdrop)

        val episodes = data.parsedEpisodes ?: return null

        return episodes.mapNotNull { ep ->
            val key = ep.absoluteEpisodeNumber?.toString()
                ?: return@mapNotNull null

            key to Episode(
                number = key,
                title = ep.title,
                desc = ep.summary,
                seasonNumber = ep.seasonNumber,
                seasonEpisodeNumber = ep.episodeNumber,
                thumb = FileUrl[ep.images],
            )
        }.toMap()
    }

    private fun preloadArtwork(vararg urls: String?) {
        val context = currContext()?.applicationContext ?: return

        urls.filterNotNull().forEach { url ->
            prefetchScope.launch {
                try {
                    Glide.with(context)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .skipMemoryCache(false)
                        .preload()
                } catch (_: Exception) {

                }
            }
        }
    }

    suspend fun fetchSkipTimes(
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        durationMs: Long?
    ): List<PlayerRepository.SkipInterval>? {
        val url = buildString {
            append("https://api.theintrodb.org/v3/media?tmdb_id=$tmdbId")
            if (season != null && episode != null) {
                append("&season=$season&episode=$episode")
            }
            if (durationMs != null) {
                append("&duration_ms=$durationMs")
            }
        }

        return tryWithSuspend {
            val response = client.get(url)
            val data = response.parsed<SkipTimeResponse>()
            mapSkipTimesToUnified(data)
        }
    }

    private fun mapSkipTimesToUnified(res: SkipTimeResponse): List<PlayerRepository.SkipInterval> {
        return buildList {
            fun addSegments(type: String, segments: List<RawSkipSegment>) {
                segments.forEach { segment ->
                    val startMs = segment.startMs ?: 0L
                    val endMs = segment.endMs

                    add(
                        PlayerRepository.SkipInterval(
                            startTimeMs = startMs,
                            endTimeMs = endMs,
                            type = type,
                            durationMs = endMs?.let { it - startMs },
                            startsAtBeginning = startMs == 0L,
                            endsAtMediaEnd = endMs == null
                        )
                    )
                }
            }

            addSegments("Opening", res.intro)
            addSegments("Recap", res.recap)
            addSegments("Ending", res.credits)
            addSegments("Preview", res.preview)
        }
    }

    @Serializable
    data class SkipTimeResponse(
        @SerialName("tmdb_id") val tmdbId: Int,
        val type: String,
        val season: Int? = null,
        val episode: Int? = null,
        val intro: List<RawSkipSegment> = emptyList(),
        val recap: List<RawSkipSegment> = emptyList(),
        val credits: List<RawSkipSegment> = emptyList(),
        val preview: List<RawSkipSegment> = emptyList()
    )

    @Serializable
    data class RawSkipSegment(
        @SerialName("start_ms") val startMs: Long?,
        @SerialName("end_ms") val endMs: Long?
    )

    @Serializable
    data class TmdbMetaResponse(
        val data: TmdbData? = null
    )

    @Serializable
    data class TmdbData(
        val tmdbId: Int? = null,
        val name: String? = null,
        val originalName: String? = null,
        val backdrop: String? = null,
        val posterImage: String? = null,
        val fanart: Fanart? = null,
        val artWorks: TmdbArtWorks? = null,
        val parsedEpisodes: List<Episodes>? = emptyList()
    )

    @Serializable
    data class TmdbArtWorks(
        val backdrop: List<String>? = emptyList(),
        val logos: List<String>? = emptyList(),
        val posterImages: List<String>? = emptyList()
    )

    @Serializable
    data class Fanart(
        val logo: List<String>? = emptyList(),
        val backdrop: List<String>? = emptyList()
    )



    @Serializable
    data class Episodes(
        val airDate: String? = null,
        val episodeNumber: Int? = null,
        val tmdbId: Int? = null,
        val title: String? = null,
        val summary: String? = null,
        val seasonNumber: Int? = null,
        val images: String? = null,
        val absoluteEpisodeNumber: Int? = null
    )
}