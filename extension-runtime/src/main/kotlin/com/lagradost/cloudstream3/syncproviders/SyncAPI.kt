package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.ui.SyncWatchType


abstract class SyncAPI : AuthAPI() {
    open var requireLibraryRefresh: Boolean = true
    open val syncIdName: SyncIdName? = null

    open suspend fun updateStatus(
        auth: AuthData?,
        id: String,
        newStatus: AbstractSyncStatus
    ): Boolean = throw NotImplementedError()

    open suspend fun status(auth: AuthData?, id: String): AbstractSyncStatus? =
        throw NotImplementedError()

    open suspend fun load(auth: AuthData?, id: String): SyncResult? = throw NotImplementedError()

    open suspend fun search(auth: AuthData?, query: String): List<SyncSearchResult>? =
        throw NotImplementedError()

    open suspend fun library(auth: AuthData?): LibraryMetadata? = throw NotImplementedError()

    open fun urlToId(url: String): String? = null

    data class SyncSearchResult(
        override val name: String,
        override val apiName: String,
        var syncId: String,
        override val url: String,
        override var posterUrl: String?,
        override var type: TvType? = null,
        override var quality: SearchQuality? = null,
        override var posterHeaders: Map<String, String>? = null,
        override var id: Int? = null,
        override var score: Score? = null,
    ) : SearchResponse

    abstract class AbstractSyncStatus {
        abstract var status: SyncWatchType
        abstract var score: Score?
        abstract var watchedEpisodes: Int?
        abstract var isFavorite: Boolean?
        abstract var maxEpisodes: Int?
    }

    data class SyncStatus(
        override var status: SyncWatchType,
        override var score: Score?,
        override var watchedEpisodes: Int?,
        override var isFavorite: Boolean? = null,
        override var maxEpisodes: Int? = null,
    ) : AbstractSyncStatus()

    data class SyncResult(
        var id: String,
        var totalEpisodes: Int? = null,
        var title: String? = null,
        var publicScore: Score? = null,
        var duration: Int? = null,
        var synopsis: String? = null,
        var airStatus: ShowStatus? = null,
        var nextAiring: NextAiring? = null,
        var studio: List<String>? = null,
        var genres: List<String>? = null,
        var synonyms: List<String>? = null,
        var trailers: List<String>? = null,
        var isAdult: Boolean? = null,
        var posterUrl: String? = null,
        var backgroundPosterUrl: String? = null,
        var startDate: Long? = null,
        var endDate: Long? = null,
        var recommendations: List<SyncSearchResult>? = null,
        var nextSeason: SyncSearchResult? = null,
        var prevSeason: SyncSearchResult? = null,
        var actors: List<ActorData>? = null,
    )

    data class LibraryMetadata(
        val allLibraryLists: List<LibraryList>,
        val supportedListSorting: Set<Any>? = null
    )

    data class LibraryList(
        val name: Any, // UiText in original, simplified here
        val items: List<LibraryItem>
    )

    data class LibraryItem(
        override val name: String,
        override val url: String,
        val syncId: String,
        val episodesCompleted: Int?,
        val episodesTotal: Int?,
        val personalRating: Score?,
        val lastUpdatedUnixTime: Long?,
        override val apiName: String,
        override var type: TvType?,
        override var posterUrl: String?,
        override var posterHeaders: Map<String, String>?,
        override var quality: SearchQuality?,
        val releaseDate: java.util.Date?,
        override var id: Int? = null,
        val plot: String? = null,
        override var score: Score? = null,
        val tags: List<String>? = null
    ) : SearchResponse
}

open class SyncRepo(override val api: SyncAPI) : AuthRepo(api) {
    val syncIdName = api.syncIdName
    var requireLibraryRefresh: Boolean
        get() = api.requireLibraryRefresh
        set(value) {
            api.requireLibraryRefresh = value
        }
}
