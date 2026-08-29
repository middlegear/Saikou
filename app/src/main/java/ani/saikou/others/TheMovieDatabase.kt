package ani.saikou.others

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.currContext
import ani.saikou.media.Media
import ani.saikou.media.anime.Episode
import ani.saikou.media.anime.mpv.PlayerRepository
import ani.saikou.tryWithSuspend
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object TheMovieDatabase {

    private suspend fun fetchMetadata(media: Media): TmdbMetaResponse? {
        return tryWithSuspend {
            val anilistId = media.id
            val response =
                client.get("https://api.kenjitsu.workers.dev/api/meta/anilist/$anilistId?platform=tmdb", timeout = 15L)
            response.parsed<TmdbMetaResponse>()
        }
    }

    suspend fun getTmdbEpisodesDetails(media: Media): Map<String, Episode>? {
        val result = fetchMetadata(media) ?: return null
        val data = result.data ?: return null

        media.idTMDB = data.tmdbId.toString()

        val bestLogo = data.artWorks?.logos?.firstOrNull()
            ?.let { it.large ?: it.original ?: it.medium }
        val bestBackdrop = data.coverImage?.large
            ?: data.coverImage?.original
            ?: data.artWorks?.coverImages?.firstOrNull()?.large

        media.anime?.tmdbLogo = bestLogo
        media.anime?.tmdbBackdrop = bestBackdrop

        preloadArtwork(
            bestLogo,
            bestBackdrop,
            data.posterImage?.large ?: data.posterImage?.original
        )

        val episodes = data.parsedEpisodes ?: return null

        return episodes.mapNotNull { ep ->
            val num = ep.episodeNumber?.toString() ?: return@mapNotNull null
            num to Episode(
                number = num,
                title = ep.title,
                desc = ep.summary,
                seasonNumber = ep.seasonNumber,
                absoluteEpisodeNumber = ep.absoluteEpisodeNumber,
                thumb = FileUrl[ep.images?.medium ?: ep.images?.original ?: ep.images?.large],
            )
        }.toMap()
    }

    private suspend fun preloadArtwork(vararg urls: String?) = coroutineScope {
        val context = currContext() ?: return@coroutineScope
        val appContext = context.applicationContext

        urls.filterNotNull()
            .mapNotNull { url -> FileUrl[url] }
            .map { fileUrl ->
                launch {
                    tryWithSuspend {
                        preloadOne(appContext, fileUrl)
                    }
                }
            }.joinAll()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun preloadOne(context: android.content.Context, model: FileUrl) {
        suspendCancellableCoroutine<Unit> { cont ->
            val target = Glide.with(context)
                .load(model)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<android.graphics.drawable.Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        if (cont.isActive) cont.resume(Unit) {}
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        model: Any,
                        target: Target<android.graphics.drawable.Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        if (cont.isActive) cont.resume(Unit) {}
                        return false
                    }
                })
                .preload()

            cont.invokeOnCancellation {
                Glide.with(context).clear(target)
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
        val coverImage: TmdbImageItem? = null,
        val posterImage: TmdbImageItem? = null,
        val artWorks: TmdbArtWorks? = null,
        val parsedEpisodes: List<Episodes>? = emptyList()
    )

    @Serializable
    data class TmdbArtWorks(
        val coverImages: List<TmdbImageItem>? = emptyList(),
        val logos: List<TmdbImageItem>? = emptyList(),
        val posterImages: List<TmdbImageItem>? = emptyList()
    )

    @Serializable
    data class TmdbImageItem(
        val small: String? = null,
        val medium: String? = null,
        val large: String? = null,
        val original: String? = null,
    )

    @Serializable
    data class Episodes(
        val airDate: String? = null,
        val episodeNumber: Int? = null,
        val tmdbId: Int? = null,
        val title: String? = null,
        val summary: String? = null,
        val seasonNumber: Int? = null,
        val images: TmdbImageItem? = null,
        val absoluteEpisodeNumber: Int? = null
    )
}