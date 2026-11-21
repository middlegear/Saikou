package com.lagradost.cloudstream3.syncproviders.providers

import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.LoadResponse.Companion.readIdFromString
import com.lagradost.cloudstream3.mvvm.debugPrint
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.*
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.ui.library.ListSorting
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.DataStore.toYear
import java.math.BigInteger
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration


private const val CLIENT_ID = ""
private const val CLIENT_SECRET = ""

class SimklApi : SyncAPI() {
    override var name = "Simkl"
    override val idPrefix = "simkl"

    override val redirectUrlIdentifier = "simkl"
    override val hasOAuth2 = true
    override val hasPin = true
    override var requireLibraryRefresh = true
    override var mainUrl = "https://simkl.com"
    override val icon = 0
    override val createAccountUrl = "$mainUrl/signup"
    override val syncIdName = SyncIdName.Simkl

    private var lastScoreTime = -1L

    private object SimklCache {
        private const val SIMKL_CACHE_KEY = "SIMKL_API_CACHE"

        enum class CacheTimes(val value: String) {
            OneMonth("30d"),
            ThirtyMinutes("30m")
        }

        private class SimklCacheWrapper<T>(
            @JsonProperty("obj") val obj: T?,
            @JsonProperty("validUntil") val validUntil: Long,
            @JsonProperty("cacheTime") val cacheTime: Long = unixTime,
        ) {
            fun isFresh(): Boolean {
                return validUntil > unixTime
            }

            fun remainingTime(): Duration {
                val unixTime = unixTime
                return if (validUntil > unixTime) {
                    (validUntil - unixTime).toDuration(DurationUnit.SECONDS)
                } else {
                    Duration.ZERO
                }
            }
        }

        fun <T> setKey(path: String, value: T, cacheTime: Duration) {
            debugPrint { "Set cache: $SIMKL_CACHE_KEY/$path for ${cacheTime.inWholeDays} days or ${cacheTime.inWholeSeconds} seconds." }
            setKey(
                SIMKL_CACHE_KEY,
                path,
                SimklCacheWrapper(value, unixTime + cacheTime.inWholeSeconds).toJson()
            )
        }

        inline fun <reified T : Any> getKey(path: String): T? {
            val type = mapper.typeFactory.constructParametricType(
                SimklCacheWrapper::class.java,
                T::class.java
            )
            val cache = getKey<String>(SIMKL_CACHE_KEY, path)?.let {
                mapper.readValue<SimklCacheWrapper<T>>(it, type)
            }

            return if (cache?.isFresh() == true) {
                debugPrint {
                    "Cache hit at: $SIMKL_CACHE_KEY/$path. " +
                            "Remains fresh for ${cache.remainingTime().inWholeDays} days or ${cache.remainingTime().inWholeSeconds} seconds."
                }
                cache.obj
            } else {
                debugPrint { "Cache miss at: $SIMKL_CACHE_KEY/$path" }
                removeKey(SIMKL_CACHE_KEY, path)
                null
            }
        }
    }

    companion object {
        private const val CLIENT_ID_COMP: String = CLIENT_ID
        private const val CLIENT_SECRET_COMP: String = CLIENT_SECRET
        const val SIMKL_CACHED_LIST: String = "simkl_cached_list"
        const val SIMKL_CACHED_LIST_TIME: String = "simkl_cached_time"

        private const val SIMKL_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        fun getUnixTime(string: String?): Long? {
            return try {
                SimpleDateFormat(SIMKL_DATE_FORMAT, Locale.getDefault()).apply {
                    this.timeZone = TimeZone.getTimeZone("UTC")
                }.parse(
                    string ?: return null
                )?.toInstant()?.epochSecond
            } catch (e: Exception) {
                logError(e)
                return null
            }
        }

        fun getDateTime(unixTime: Long?): String? {
            return try {
                SimpleDateFormat(SIMKL_DATE_FORMAT, Locale.getDefault()).apply {
                    this.timeZone = TimeZone.getTimeZone("UTC")
                }.format(
                    Date.from(
                        Instant.ofEpochSecond(
                            unixTime ?: return null
                        )
                    )
                )
            } catch (e: Exception) {
                null
            }
        }

        fun getPosterUrl(poster: String): String {
            return "https://simkl.net/posters/${poster}_m.jpg"
        }

        private fun getUrlFromId(id: Int): String {
            return "https://simkl.com/anime/$id"
        }

        enum class SimklListStatusType(
            var value: Int,
            @StringRes val stringRes: Int,
            val originalName: String?
        ) {
            Watching(0, 0, "watching"),
            Completed(1, 0, "completed"),
            Paused(2, 0, "hold"),
            Dropped(3, 0, "dropped"),
            Planning(4, 0, "plantowatch"),
            ReWatching(5, 0, "watching"),
            None(-1, 0, null);

            companion object {
                fun fromString(string: String): SimklListStatusType? {
                    return SimklListStatusType.entries.firstOrNull {
                        it.originalName == string
                    }
                }
            }
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        data class TokenRequest(
            @JsonProperty("code") val code: String,
            @JsonProperty("client_id") val clientId: String = CLIENT_ID,
            @JsonProperty("client_secret") val clientSecret: String = CLIENT_SECRET,
            @JsonProperty("redirect_uri") val redirectUri: String = "cloudstreamapp://simkl",
            @JsonProperty("grant_type") val grantType: String = "authorization_code"
        )

        data class TokenResponse(
            @JsonProperty("access_token") val accessToken: String,
            @JsonProperty("token_type") val tokenType: String,
            @JsonProperty("scope") val scope: String
        )

        data class SettingsResponse(
            @JsonProperty("user")
            val user: User,
            @JsonProperty("account")
            val account: Account,
        ) {
            data class User(
                @JsonProperty("name")
                val name: String,
                @JsonProperty("avatar")
                val avatar: String
            )

            data class Account(
                @JsonProperty("id")
                val id: Int,
            )
        }

        data class PinAuthResponse(
            @JsonProperty("result") val result: String,
            @JsonProperty("device_code") val deviceCode: String,
            @JsonProperty("user_code") val userCode: String,
            @JsonProperty("verification_url") val verificationUrl: String,
            @JsonProperty("expires_in") val expiresIn: Int,
            @JsonProperty("interval") val interval: Int,
        )

        data class PinExchangeResponse(
            @JsonProperty("result") val result: String,
            @JsonProperty("message") val message: String? = null,
            @JsonProperty("access_token") val accessToken: String? = null,
        )

        data class ActivitiesResponse(
            @JsonProperty("all") val all: String?,
            @JsonProperty("tv_shows") val tvShows: UpdatedAt,
            @JsonProperty("anime") val anime: UpdatedAt,
            @JsonProperty("movies") val movies: UpdatedAt,
        ) {
            data class UpdatedAt(
                @JsonProperty("all") val all: String?,
                @JsonProperty("removed_from_list") val removedFromList: String?,
                @JsonProperty("rated_at") val ratedAt: String?,
            )
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        data class EpisodeMetadata(
            @JsonProperty("title") val title: String?,
            @JsonProperty("description") val description: String?,
            @JsonProperty("season") val season: Int?,
            @JsonProperty("episode") val episode: Int,
            @JsonProperty("img") val img: String?
        ) {
            companion object {
                fun convertToEpisodes(list: List<EpisodeMetadata>?): List<MediaObject.Season.Episode>? {
                    return list?.map {
                        MediaObject.Season.Episode(it.episode)
                    }
                }

                fun convertToSeasons(list: List<EpisodeMetadata>?): List<MediaObject.Season>? {
                    return list?.filter { it.season != null }?.groupBy {
                        it.season
                    }?.mapNotNull { (season, episodes) ->
                        convertToEpisodes(episodes)?.let { MediaObject.Season(season!!, it) }
                    }?.ifEmpty { null }
                }
            }
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        open class MediaObject(
            @JsonProperty("title") val title: String?,
            @JsonProperty("year") val year: Int?,
            @JsonProperty("ids") val ids: Ids?,
            @JsonProperty("total_episodes") val totalEpisodes: Int? = null,
            @JsonProperty("status") val status: String? = null,
            @JsonProperty("poster") val poster: String? = null,
            @JsonProperty("type") val type: String? = null,
            @JsonProperty("seasons") val seasons: List<Season>? = null,
            @JsonProperty("episodes") val episodes: List<Season.Episode>? = null
        ) {
            fun hasEnded(): Boolean {
                return status == "released" || status == "ended"
            }

            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            data class Season(
                @JsonProperty("number") val number: Int,
                @JsonProperty("episodes") val episodes: List<Episode>
            ) {
                data class Episode(@JsonProperty("number") val number: Int)
            }

            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            data class Ids(
                @JsonProperty("simkl") val simkl: Int?,
                @JsonProperty("imdb") val imdb: String? = null,
                @JsonProperty("tmdb") val tmdb: String? = null,
                @JsonProperty("mal") val mal: String? = null,
                @JsonProperty("anilist") val anilist: String? = null,
            ) {
                companion object {
                    fun fromMap(map: Map<SimklSyncServices, String>): Ids {
                        return Ids(
                            simkl = map[SimklSyncServices.Simkl]?.toIntOrNull(),
                            imdb = map[SimklSyncServices.Imdb],
                            tmdb = map[SimklSyncServices.Tmdb],
                            mal = map[SimklSyncServices.Mal],
                            anilist = map[SimklSyncServices.AniList]
                        )
                    }
                }
            }

            fun toSyncSearchResult(): SyncSearchResult? {
                return SyncSearchResult(
                    this.title ?: return null,
                    "Simkl",
                    this.ids?.simkl?.toString() ?: return null,
                    getUrlFromId(this.ids.simkl ?: return null),
                    this.poster?.let { getPosterUrl(it) },
                    if (this.type == "movie") TvType.Movie else TvType.TvSeries
                )
            }
        }

        class SimklScoreBuilder private constructor() {
            data class Builder(
                private var url: String? = null,
                private var headers: Map<String, String>? = null,
                private var ids: MediaObject.Ids? = null,
                private var score: Int? = null,
                private var status: Int? = null,
                private var addEpisodes: Pair<List<MediaObject.Season>?, List<MediaObject.Season.Episode>?>? = null,
                private var removeEpisodes: Pair<List<MediaObject.Season>?, List<MediaObject.Season.Episode>?>? = null,
                private var onList: Boolean = false
            ) {
                fun token(token: AuthToken) = apply { this.headers = getHeaders(token) }
                fun apiUrl(url: String) = apply { this.url = url }
                fun ids(ids: MediaObject.Ids) = apply { this.ids = ids }
                fun score(score: Int?, oldScore: Int?) = apply {
                    if (score != oldScore) {
                        this.score = score
                    }
                }

                fun status(newStatus: Int?, oldStatus: Int?) = apply {
                    onList = oldStatus != null
                    if (newStatus != oldStatus) {
                        this.status = newStatus
                    } else {
                        this.status = null
                    }
                }

                fun episodes(
                    allEpisodes: List<EpisodeMetadata>?,
                    newEpisodes: Int?,
                    oldEpisodes: Int?,
                ) = apply {
                    if (allEpisodes == null || newEpisodes == null) return@apply

                    fun getEpisodes(rawEpisodes: List<EpisodeMetadata>) =
                        if (rawEpisodes.any { it.season != null }) {
                            EpisodeMetadata.convertToSeasons(rawEpisodes) to null
                        } else {
                            null to EpisodeMetadata.convertToEpisodes(rawEpisodes)
                        }

                    if (newEpisodes > (oldEpisodes ?: 0)) {
                        this.addEpisodes = getEpisodes(allEpisodes.take(newEpisodes))

                        if (!onList) {
                            status = SimklListStatusType.Watching.value
                        }
                    }
                    if ((oldEpisodes ?: 0) > newEpisodes) {
                        this.removeEpisodes = getEpisodes(allEpisodes.drop(newEpisodes))
                    }
                }

                suspend fun execute(): Boolean {
                    val time = getDateTime(unixTime)
                    val headers = this.headers ?: emptyMap()
                    return if (this.status == SimklListStatusType.None.value) {
                        app.post(
                            "$url/sync/history/remove",
                            json = StatusRequest(
                                shows = listOf(HistoryMediaObject(ids = ids)),
                                movies = emptyList()
                            ),
                            headers = headers
                        ).isSuccessful
                    } else {
                        val statusResponse = this.status?.let { setStatus ->
                            val newStatus =
                                SimklListStatusType.entries
                                    .firstOrNull { it.value == setStatus }?.originalName
                                    ?: SimklListStatusType.Watching.originalName!!

                            app.post(
                                "${this.url}/sync/add-to-list",
                                json = StatusRequest(
                                    shows = listOf(
                                        StatusMediaObject(
                                            null,
                                            null,
                                            ids,
                                            newStatus,
                                        )
                                    ), movies = emptyList()
                                ),
                                headers = headers
                            ).isSuccessful
                        } ?: true

                        val episodeRemovalResponse = removeEpisodes?.let { (seasons, episodes) ->
                            app.post(
                                "${this.url}/sync/history/remove",
                                json = StatusRequest(
                                    shows = listOf(
                                        HistoryMediaObject(
                                            ids = ids,
                                            seasons = seasons,
                                            episodes = episodes
                                        )
                                    ),
                                    movies = emptyList()
                                ),
                                headers = headers
                            ).isSuccessful
                        } ?: true

                        val shouldRate =
                            score != null && status != SimklListStatusType.Planning.value
                        val realScore = if (shouldRate) score else null

                        val historyResponse =
                            if (addEpisodes != null || shouldRate) {
                                app.post(
                                    "${this.url}/sync/history",
                                    json = StatusRequest(
                                        shows = listOf(
                                            HistoryMediaObject(
                                                null,
                                                null,
                                                ids,
                                                addEpisodes?.first,
                                                addEpisodes?.second,
                                                realScore,
                                                realScore?.let { time },
                                            )
                                        ), movies = emptyList()
                                    ),
                                    headers = headers
                                ).isSuccessful
                            } else {
                                true
                            }

                        statusResponse && episodeRemovalResponse && historyResponse
                    }
                }
            }
        }

        fun getHeaders(token: AuthToken): Map<String, String> =
            mapOf("Authorization" to "Bearer ${token.accessToken}", "simkl-api-key" to CLIENT_ID)

        suspend fun getEpisodes(
            simklId: Int?,
            type: String?,
            episodes: Int?,
            hasEnded: Boolean?
        ): Array<EpisodeMetadata>? {
            if (simklId == null) return null

            val cacheKey = "Episodes/$simklId"
            val cache = SimklCache.getKey<Array<EpisodeMetadata>>(cacheKey)

            if (cache != null && cache.size >= (episodes ?: 0)) {
                return cache
            }

            if (type == "anime" && episodes != null) {
                return episodes.takeIf { it > 0 }?.let {
                    (1..it).map { episode ->
                        EpisodeMetadata(
                            null, null, null, episode, null
                        )
                    }.toTypedArray()
                }
            }
            val url = when (type) {
                "anime" -> "https://simkl.com/anime/"
                "tv" -> "https://simkl.com/tv/"
                "movie" -> return null
                else -> return null
            }

            return app.get(url, params = mapOf("client_id" to CLIENT_ID))
                .parsedSafe<Array<EpisodeMetadata>>()?.also {
                    val cacheTime =
                        if (hasEnded == true) SimklCache.CacheTimes.OneMonth.value else SimklCache.CacheTimes.ThirtyMinutes.value

                    SimklCache.setKey(cacheKey, it, Duration.parse(cacheTime))
                }
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        class HistoryMediaObject(
            @JsonProperty("title") title: String? = null,
            @JsonProperty("year") year: Int? = null,
            @JsonProperty("ids") ids: Ids? = null,
            @JsonProperty("seasons") seasons: List<Season>? = null,
            @JsonProperty("episodes") episodes: List<Season.Episode>? = null,
            @JsonProperty("rating") val rating: Int? = null,
            @JsonProperty("rated_at") val ratedAt: String? = null,
        ) : MediaObject(title, year, ids, seasons = seasons, episodes = episodes)

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        class StatusMediaObject(
            @JsonProperty("title") title: String?,
            @JsonProperty("year") year: Int?,
            @JsonProperty("ids") ids: Ids?,
            @JsonProperty("to") val to: String,
            @JsonProperty("watched_at") val watchedAt: String? = getDateTime(unixTime)
        ) : MediaObject(title, year, ids)

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        data class StatusRequest(
            @JsonProperty("movies") val movies: List<MediaObject>,
            @JsonProperty("shows") val shows: List<MediaObject>
        )

        data class AllItemsResponse(
            @JsonProperty("shows")
            val shows: List<ShowMetadata> = emptyList(),
            @JsonProperty("anime")
            val anime: List<ShowMetadata> = emptyList(),
            @JsonProperty("movies")
            val movies: List<MovieMetadata> = emptyList(),
        ) {
            companion object {
                fun merge(first: AllItemsResponse?, second: AllItemsResponse?): AllItemsResponse {
                    fun <T> MutableList<T>.replaceOrAddItem(newItem: T, predicate: (T) -> Boolean) {
                        for (i in this.indices) {
                            if (predicate(this[i])) {
                                this[i] = newItem
                                return
                            }
                        }
                        this.add(newItem)
                    }

                    fun <T : Metadata> merge(
                        first: List<T>?,
                        second: List<T>?
                    ): List<T> {
                        return (first?.toMutableList() ?: mutableListOf()).apply {
                            second?.forEach { secondShow ->
                                this.replaceOrAddItem(secondShow) {
                                    it.getIds().simkl == secondShow.getIds().simkl
                                }
                            }
                        }
                    }

                    return AllItemsResponse(
                        merge(first?.shows, second?.shows),
                        merge(first?.anime, second?.anime),
                        merge(first?.movies, second?.movies),
                    )
                }
            }

            interface Metadata {
                val lastWatchedAt: String?
                val status: String?
                val userRating: Int?
                val watchedEpisodesCount: Int?
                val totalEpisodesCount: Int?

                fun getIds(): ShowMetadata.Show.Ids
                fun toLibraryItem(): LibraryItem
            }

            data class MovieMetadata(
                @JsonProperty("last_watched_at") override val lastWatchedAt: String?,
                @JsonProperty("status") override val status: String,
                @JsonProperty("user_rating") override val userRating: Int?,
                @JsonProperty("watched_episodes_count") override val watchedEpisodesCount: Int?,
                @JsonProperty("total_episodes_count") override val totalEpisodesCount: Int?,
                val movie: ShowMetadata.Show
            ) : Metadata {
                override fun getIds(): ShowMetadata.Show.Ids = this.movie.ids

                override fun toLibraryItem(): LibraryItem {
                    return LibraryItem(
                        this.movie.title,
                        "https://simkl.com/movies/${movie.ids.simkl}",
                        movie.ids.simkl.toString(),
                        this.watchedEpisodesCount,
                        this.totalEpisodesCount,
                        Score.from10(this.userRating),
                        getUnixTime(lastWatchedAt) ?: 0,
                        "Simkl",
                        TvType.Movie,
                        this.movie.poster?.let { getPosterUrl(it) },
                        null,
                        null,
                        this.movie.year?.toYear(),
                        movie.ids.simkl
                    )
                }
            }

            data class ShowMetadata(
                @JsonProperty("last_watched_at") override val lastWatchedAt: String?,
                @JsonProperty("status") override val status: String,
                @JsonProperty("user_rating") override val userRating: Int?,
                @JsonProperty("watched_episodes_count") override val watchedEpisodesCount: Int?,
                @JsonProperty("total_episodes_count") override val totalEpisodesCount: Int?,
                @JsonProperty("show") val show: Show
            ) : Metadata {
                override fun getIds(): Show.Ids = this.show.ids

                override fun toLibraryItem(): LibraryItem {
                    return LibraryItem(
                        this.show.title,
                        "https://simkl.com/shows/${show.ids.simkl}",
                        show.ids.simkl.toString(),
                        this.watchedEpisodesCount,
                        this.totalEpisodesCount,
                        Score.from10(this.userRating),
                        getUnixTime(lastWatchedAt) ?: 0,
                        "Simkl",
                        TvType.Anime,
                        this.show.poster?.let { getPosterUrl(it) },
                        null,
                        null,
                        this.show.year?.toYear(),
                        show.ids.simkl
                    )
                }

                data class Show(
                    @JsonProperty("title") val title: String,
                    @JsonProperty("poster") val poster: String?,
                    @JsonProperty("year") val year: Int?,
                    @JsonProperty("ids") val ids: Ids,
                ) {
                    data class Ids(
                        @JsonProperty("simkl") val simkl: Int,
                        @JsonProperty("imdb") val imdb: String?,
                        @JsonProperty("tmdb") val tmdb: String?,
                        @JsonProperty("mal") val mal: String?,
                        @JsonProperty("anilist") val anilist: String?,
                    ) {
                        fun matchesId(database: SimklSyncServices, id: String): Boolean {
                            return when (database) {
                                SimklSyncServices.Simkl -> this.simkl == id.toIntOrNull()
                                SimklSyncServices.AniList -> this.anilist == id
                                SimklSyncServices.Mal -> this.mal == id
                                SimklSyncServices.Tmdb -> this.tmdb == id
                                SimklSyncServices.Imdb -> this.imdb == id
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun getUser(token: AuthToken): SettingsResponse =
        app.post("$mainUrl/users/settings", headers = getHeaders(token))
            .parsed<SettingsResponse>()

    class SimklEpisodeConstructor(
        private val simklId: Int?,
        private val type: String?,
        private val totalEpisodeCount: Int?,
        private val hasEnded: Boolean?
    ) {
        suspend fun getEpisodes(): Array<EpisodeMetadata>? {
            return getEpisodes(simklId, type, totalEpisodeCount, hasEnded)
        }
    }

    class SimklSyncStatus(
        override var status: SyncWatchType,
        override var score: Score?,
        val oldScore: Int?,
        override var watchedEpisodes: Int?,
        val episodeConstructor: SimklEpisodeConstructor,
        override var isFavorite: Boolean? = null,
        override var maxEpisodes: Int? = null,
        val oldEpisodes: Int,
        val oldStatus: String?
    ) : AbstractSyncStatus()

    override suspend fun status(auth: AuthData?, id: String): AbstractSyncStatus? {
        if (auth == null) return null
        val realIds = readIdFromString(id)
        val idKey = realIds.toList().map { "${it.first.name}=${it.second}" }.sorted().joinToString()

        val cachedObject = SimklCache.getKey<MediaObject>(idKey)
        val searchResult: MediaObject = cachedObject
            ?: (searchByIds(realIds)?.firstOrNull()?.also { result ->
                val cacheTime =
                    if (result.hasEnded()) SimklCache.CacheTimes.OneMonth.value else SimklCache.CacheTimes.ThirtyMinutes.value
                SimklCache.setKey(idKey, result, Duration.parse(cacheTime))
            }) ?: return null

        val episodeConstructor = SimklEpisodeConstructor(
            searchResult.ids?.simkl,
            searchResult.type,
            searchResult.totalEpisodes,
            searchResult.hasEnded()
        )

        val foundItem = getSyncListSmart(auth)?.let { list ->
            listOf(list.shows, list.anime, list.movies).flatten().firstOrNull { show ->
                realIds.any { (database, id) ->
                    show.getIds().matchesId(database, id)
                }
            }
        }

        if (foundItem != null) {
            return SimklSyncStatus(
                status = foundItem.status?.let {
                    SyncWatchType.fromInternalId(
                        SimklListStatusType.fromString(it)?.value
                    )
                } ?: return null,
                score = Score.from10(foundItem.userRating),
                watchedEpisodes = foundItem.watchedEpisodesCount,
                maxEpisodes = searchResult.totalEpisodes,
                episodeConstructor = episodeConstructor,
                oldEpisodes = foundItem.watchedEpisodesCount ?: 0,
                oldScore = foundItem.userRating,
                oldStatus = foundItem.status
            )
        } else {
            return SimklSyncStatus(
                status = SyncWatchType.fromInternalId(SimklListStatusType.None.value),
                score = null,
                watchedEpisodes = 0,
                maxEpisodes = if (searchResult.type == "movie") 0 else searchResult.totalEpisodes,
                episodeConstructor = episodeConstructor,
                oldEpisodes = 0,
                oldStatus = null,
                oldScore = null
            )
        }
    }

    override suspend fun updateStatus(
        auth: AuthData?,
        id: String,
        newStatus: AbstractSyncStatus
    ): Boolean {
        val parsedId = readIdFromString(id)
        lastScoreTime = unixTime
        val simklStatus = newStatus as? SimklSyncStatus

        val builder = SimklScoreBuilder.Builder()
            .apiUrl(this.mainUrl)
            .score(newStatus.score?.toInt(10), simklStatus?.oldScore)
            .status(
                newStatus.status.internalId,
                (newStatus as? SimklSyncStatus)?.oldStatus?.let { oldStatus ->
                    SimklListStatusType.entries.firstOrNull {
                        it.originalName == oldStatus
                    }?.value
                })
            .token(auth?.token ?: return false)
            .ids(MediaObject.Ids.fromMap(parsedId))

        val episodes = simklStatus?.episodeConstructor?.getEpisodes()

        val watchedEpisodes =
            if (newStatus.status.internalId == SimklListStatusType.Completed.value) {
                episodes?.size
            } else {
                newStatus.watchedEpisodes
            }

        builder.episodes(episodes?.toList(), watchedEpisodes, simklStatus?.oldEpisodes)

        requireLibraryRefresh = true
        return builder.execute()
    }

    private suspend fun searchByIds(serviceMap: Map<SimklSyncServices, String>): Array<MediaObject>? {
        if (serviceMap.isEmpty()) return emptyArray()

        return app.get(
            "$mainUrl/search/id",
            params = mapOf("client_id" to CLIENT_ID) + serviceMap.map { (service, id) ->
                service.originalName to id
            }
        ).parsedSafe()
    }

    override suspend fun search(auth: AuthData?, query: String): List<SyncSearchResult>? {
        return app.get(
            "$mainUrl/search/", params = mapOf("client_id" to CLIENT_ID, "q" to query)
        ).parsedSafe<Array<MediaObject>>()?.mapNotNull { it.toSyncSearchResult() }
    }

    override fun loginRequest(): AuthLoginPage? {
        val lastLoginState = BigInteger(130, SecureRandom()).toString(32)
        val url =
            "https://simkl.com/oauth/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=cloudstreamapp://simkl&state=$lastLoginState"

        return AuthLoginPage(
            url = url,
            payload = lastLoginState
        )
    }

    override suspend fun load(auth: AuthData?, id: String): SyncResult? = null

    private suspend fun getSyncListSince(auth: AuthData, since: Long?): AllItemsResponse? {
        val params = getDateTime(since)?.let {
            mapOf("date_from" to it)
        } ?: emptyMap()

        return app.get(
            "$mainUrl/sync/all-items/",
            params = params,
            headers = getHeaders(auth.token)
        ).parsedSafe()
    }

    private suspend fun getActivities(token: AuthToken): ActivitiesResponse? {
        return app.post("$mainUrl/sync/activities", headers = getHeaders(token)).parsedSafe()
    }

    private fun getSyncListCached(auth: AuthData): AllItemsResponse? {
        return getKey<AllItemsResponse>(SIMKL_CACHED_LIST, auth.user.id.toString())
    }

    private suspend fun getSyncListSmart(auth: AuthData): AllItemsResponse? {
        val activities = getActivities(auth.token)
        val userId = auth.user.id.toString()
        val lastCacheUpdate = getKey<Long>(SIMKL_CACHED_LIST_TIME, auth.user.id.toString())
        val lastRemoval = listOf(
            activities?.tvShows?.removedFromList,
            activities?.anime?.removedFromList,
            activities?.movies?.removedFromList
        ).maxOf {
            getUnixTime(it) ?: -1
        }
        val lastRealUpdate =
            listOf(
                activities?.tvShows?.all,
                activities?.anime?.all,
                activities?.movies?.all,
            ).maxOf {
                getUnixTime(it) ?: -1
            }

        val list = if (lastCacheUpdate == null || lastCacheUpdate < lastRemoval) {
            setKey(SIMKL_CACHED_LIST_TIME, userId, lastRemoval)
            getSyncListSince(auth, null)
        } else if (lastCacheUpdate < lastRealUpdate || lastCacheUpdate < lastScoreTime) {
            setKey(SIMKL_CACHED_LIST_TIME, userId, lastCacheUpdate)
            AllItemsResponse.merge(
                getSyncListCached(auth),
                getSyncListSince(auth, lastCacheUpdate)
            )
        } else {
            getSyncListCached(auth)
        }

        setKey(SIMKL_CACHED_LIST, userId, list)

        return list
    }

    override suspend fun library(auth: AuthData?): LibraryMetadata? {
        val list = getSyncListSmart(auth ?: return null) ?: return null

        val baseMap =
            SimklListStatusType.entries
                .filter { it.value >= 0 && it.value != SimklListStatusType.ReWatching.value }
                .associate {
                    it.stringRes to emptyList<LibraryItem>()
                }

        val syncMap = listOf(list.anime, list.movies, list.shows)
            .flatten()
            .groupBy {
                it.status
            }
            .mapNotNull { (status, list) ->
                val stringRes =
                    status?.let { SimklListStatusType.fromString(it)?.stringRes }
                        ?: return@mapNotNull null
                val libraryList = list.map { it.toLibraryItem() }
                stringRes to libraryList
            }.toMap()

        return LibraryMetadata(
            (baseMap + syncMap).map { LibraryList(txt(it.key.toString()), it.value) }, setOf(
                ListSorting.AlphabeticalA,
                ListSorting.AlphabeticalZ,
                ListSorting.UpdatedNew,
                ListSorting.UpdatedOld,
                ListSorting.ReleaseDateNew,
                ListSorting.ReleaseDateOld,
                ListSorting.RatingHigh,
                ListSorting.RatingLow,
            )
        )
    }

    override fun urlToId(url: String): String? {
        val simklUrlRegex = Regex("""https://simkl.com/(.+)/(\d+)""")
        return simklUrlRegex.find(url)?.groupValues?.get(1) ?: ""
    }

    override suspend fun pinRequest(): AuthPinData? {
        val res = app.get(
            "$mainUrl/oauth/pin?client_id=$CLIENT_ID"
        ).parsed<PinResponse>()

        return AuthPinData(
            deviceCode = res.deviceCode,
            userCode = res.userCode,
            verificationUrl = res.verificationUrl,
            expiresIn = res.expiresIn,
            interval = res.interval
        )
    }

    override suspend fun login(payload: AuthPinData): AuthToken? {
        val res = app.post(
            "$mainUrl/search/id?client_id=$CLIENT_ID&device_code=${payload.deviceCode}",
            data = mapOf(
                "client_id" to CLIENT_ID,
                "client_secret" to CLIENT_SECRET,
                "device_code" to payload.deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
            )
        ).parsed<ResponseToken>()
        return AuthToken(
            accessToken = res.accessToken,
            accessTokenLifetime = unixTime + 31536000 
        )
    }

    data class PinResponse(
        @JsonProperty("user_code") val userCode: String,
        @JsonProperty("device_code") val deviceCode: String,
        @JsonProperty("verification_url") val verificationUrl: String,
        @JsonProperty("expires_in") val expiresIn: Int,
        @JsonProperty("interval") val interval: Int,
    )

    data class ResponseToken(
        @JsonProperty("access_token") val accessToken: String,
        @JsonProperty("token_type") val tokenType: String,
        @JsonProperty("scope") val scope: String? = null,
    )

    override suspend fun login(redirectUrl: String, payload: String?): AuthToken? {
        val uri = redirectUrl.toUri()
        val state = uri.getQueryParameter("state")
        if (state != payload) return null

        val code = uri.getQueryParameter("code") ?: return null
        val tokenResponse = app.post(
            "$mainUrl/oauth/token", json = TokenRequest(code)
        ).parsedSafe<TokenResponse>() ?: return null

        return AuthToken(
            accessToken = tokenResponse.accessToken,
        )
    }

    override suspend fun user(token: AuthToken?): AuthUser? {
        val user = getUser(token ?: return null)
        return AuthUser(
            id = user.account.id,
            name = user.user.name,
            profilePicture = user.user.avatar
        )
    }
}
