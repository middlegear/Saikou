package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.*
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

const val KITSU_MAX_SEARCH_LIMIT = 20

class KitsuApi: SyncAPI() {
    override var name = "Kitsu"
    override val idPrefix = "kitsu"

    private val apiUrl = "https://kitsu.io/api/edge"
    private val oauthUrl = "https://kitsu.io/api/oauth/token"
    override val hasInApp = true
    override var mainUrl = "https://kitsu.io"
    override val syncIdName = SyncIdName.Kitsu
    override val createAccountUrl = mainUrl
    override val icon = 0

    override val inAppLoginRequirement = listOf(AuthLoginRequirement.Email, AuthLoginRequirement.Password)

    override suspend fun login(payload: AuthLoginResponse): AuthToken? {
        val username = payload.email
        val password = payload.password

        val grantType = "password"

        val token = app.post(
            "$oauthUrl/token",
            data = mapOf(
                "grant_type" to grantType,
                "username" to username,
                "password" to password
            )
        ).parsed<ResponseToken>()
        return AuthToken(
            accessTokenLifetime = unixTime + token.expiresIn.toLong(),
            refreshToken = token.refreshToken,
            accessToken = token.accessToken,
        )
    }

    override suspend fun refreshToken(token: AuthToken): AuthToken {
        val res = app.post(
            "$oauthUrl/token",
            data = mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to token.refreshToken!!
            )
        ).parsed<ResponseToken>()

        return AuthToken(
            accessToken = res.accessToken,
            refreshToken = res.refreshToken,
            accessTokenLifetime = unixTime + res.expiresIn.toLong()
        )
    }

    override suspend fun user(token: AuthToken?): AuthUser? {
        val user = app.get(
            "$apiUrl/users?filter[self]=true",
            headers = mapOf(
                "Authorization" to "Bearer ${token?.accessToken ?: return null}"
            ), cacheTime = 0
        ).parsed<KitsuResponse>()

        if (user.data.isEmpty()) {
           return null
        }

        return AuthUser(
            id = user.data[0].id.toInt(),
            name = user.data[0].attributes.name,
            profilePicture = user.data[0].attributes.avatar?.original
        )
    }

    override suspend fun search(auth: AuthData?, query: String): List<SyncSearchResult>? {
        val accessToken = auth?.token?.accessToken ?: return null
        val animeSelectedFields = arrayOf("titles","canonicalTitle","posterImage","episodeCount")
        val url = "$apiUrl/anime?filter[text]=$query&page[limit]=$KITSU_MAX_SEARCH_LIMIT&fields[anime]=${animeSelectedFields.joinToString(",")}"
        val res = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer $accessToken",
            ), cacheTime = 0
        ).parsed<KitsuResponse>()
        return res.data.map {
            val attributes = it.attributes

            val title = attributes.canonicalTitle ?: attributes.titles?.enJp ?: attributes.titles?.jaJp ?: "No title"

            SyncSearchResult(
                title,
                this.name,
                it.id,
                "$mainUrl/anime/${it.id}/",
                attributes.posterImage?.large ?: attributes.posterImage?.medium
            )
        }
    }

    override suspend fun load(auth : AuthData?, id: String): SyncResult? {
        val accessToken = auth?.token?.accessToken ?: return null
        if (id.toIntOrNull() == null) {
            return null
        }

        data class KitsuLoadResponse(
            @field:JsonProperty(value = "data")
            val data: KitsuAnimeData,
        )

        val url = "$apiUrl/anime/$id"

        val anime = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer $accessToken"
            )
        ).parsed<KitsuLoadResponse>().data.attributes

        val res = SyncResult(id)
        res.totalEpisodes = anime.episodeCount
        res.title = anime.canonicalTitle ?: anime.titles?.enJp ?: anime.titles?.jaJp.orEmpty()
        res.publicScore =  Score.from(anime.ratingTwenty?.toInt(), 20)
        res.duration = anime.episodeLength
        res.synopsis = anime.synopsis
        res.airStatus = when(anime.status) {
            "finished" -> ShowStatus.Completed
            "current" -> ShowStatus.Ongoing
            else -> null
        }
        res.startDate = anime.startDate?.let { java.time.LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond }
        res.endDate = anime.endDate?.let { java.time.LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond }
        return res
    }

    override suspend fun status(auth : AuthData?, id: String): AbstractSyncStatus? {
        val accessToken = auth?.token?.accessToken ?: return null
        val userId = auth.user.id

        val selectedFields = arrayOf("status","ratingTwenty", "progress")

        val url =
            "$apiUrl/library-entries?filter[userId]=$userId&filter[animeId]=$id&fields[libraryEntries]=${selectedFields.joinToString(",")}"

        val anime = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer $accessToken"
            )
        ).parsed<KitsuResponse>().data.firstOrNull()?.attributes

        if (anime == null) {
            return SyncStatus(
                score = null,
                status = SyncWatchType.NONE,
                isFavorite = null,
                watchedEpisodes = null
            )
        }

        return SyncStatus(
            score = Score.from(anime.ratingTwenty?.toInt(), 20),
            status = SyncWatchType.fromInternalId(kitsuStatusAsString.indexOf(anime.status)),
            isFavorite = null,
            watchedEpisodes = anime.progress,
        )
    }

    override fun urlToId(url: String): String? =
        Regex("""/anime/((.*)/|(.*))""").find(url)?.groupValues?.get(1)

    override suspend fun updateStatus(
        auth : AuthData?,
        id: String,
        newStatus: AbstractSyncStatus
    ): Boolean {
        return setScoreRequest(
            auth ?: return false,
            id.toIntOrNull() ?: return false,
            fromIntToAnimeStatus(newStatus.status),
            newStatus.score?.toInt(20),
            newStatus.watchedEpisodes
        )
    }

    private suspend fun setScoreRequest(
        auth : AuthData,
        id: Int,
        status: KitsuStatusType? = null,
        score: Int? = null,
        numWatchedEpisodes: Int? = null,
    ): Boolean {
        val libraryEntryId = getAnimeLibraryEntryId(auth, id)
        if (libraryEntryId != null) {
            if (status == null || status == KitsuStatusType.None) {
                val res = app.delete(
                    "$apiUrl/library-entries/$libraryEntryId",
                    headers = mapOf(
                        "Authorization" to "Bearer ${auth.token.accessToken}"
                    ),
                )
                return res.isSuccessful
            }
            return setScoreRequest(
                auth,
                libraryEntryId,
                kitsuStatusAsString[maxOf(0, status.value)],
                score,
                numWatchedEpisodes
            )
        }

        val data = mapOf(
            "data" to mapOf(
                "type" to "libraryEntries",
                "attributes" to mapOf(
                    "ratingTwenty" to score,
                    "progress" to numWatchedEpisodes,
                    "status" to status?.let { kitsuStatusAsString[maxOf(0, it.value)] },
                ),
                "relationships" to mapOf(
                    "anime" to mapOf(
                        "data" to mapOf(
                            "type" to "anime",
                            "id" to id.toString()
                        )
                    ),
                    "user" to mapOf(
                        "data" to mapOf(
                            "type" to "users",
                            "id" to auth.user.id
                        )
                    )
                )
            )
        )

        val res = app.post(
            "$apiUrl/library-entries",
            headers = mapOf(
                "content-type" to "application/vnd.api+json",
                "Authorization" to "Bearer ${auth.token.accessToken}"
            ),
            requestBody = data.toJson().toRequestBody()
        )
        return res.isSuccessful
    }

    private suspend fun setScoreRequest(
        auth : AuthData,
        id: Int,
        status: String? = null,
        score: Int? = null,
        numWatchedEpisodes: Int? = null,
    ):  Boolean {
        val data = mapOf(
            "data" to mapOf(
                "type" to "libraryEntries",
                "id" to id.toString(),
                "attributes" to mapOf(
                    "ratingTwenty" to score,
                    "progress" to numWatchedEpisodes,
                    "status" to status
                )
            )
        )

        val res = app.patch(
            "$apiUrl/library-entries/$id",
            headers = mapOf(
                "content-type" to "application/vnd.api+json",
                "Authorization" to "Bearer ${auth.token.accessToken}"
            ),
            requestBody = data.toJson().toRequestBody()
        )
        return res.isSuccessful
    }

    private suspend fun getAnimeLibraryEntryId(auth: AuthData, id: Int): Int? {
        val userId = auth.user.id
        val res = app.get(
            "$apiUrl/library-entries?filter[userId]=$userId&filter[animeId]=$id",
            headers = mapOf(
                "Authorization" to "Bearer ${auth.token.accessToken}"
            ),
        ).parsed<KitsuResponse>().data.firstOrNull() ?: return null
        return res.id.toInt()
    }

    override suspend fun library(auth : AuthData?): LibraryMetadata? {
        val listData = getKitsuAnimeListSmart(auth ?: return null)?.groupBy {
            it.attributes.status ?: ""
        }?.map { group ->
            LibraryList(group.key, group.value.map { it.toLibraryItem() })
        } ?: emptyList()

        return LibraryMetadata(listData, null)
    }

    private suspend fun getKitsuAnimeListSmart(auth : AuthData): Array<KitsuNode>? {
        return if (requireLibraryRefresh) {
            val list = getKitsuAnimeList(auth.token, auth.user.id)
            setKey(KITSU_CACHED_LIST, auth.user.id.toString(), list)
            list
        } else {
            getKey<Array<KitsuNode>>(KITSU_CACHED_LIST, auth.user.id.toString()) as? Array<KitsuNode>
        }
    }

    private suspend fun getKitsuAnimeList(token: AuthToken, userId: Int): Array<KitsuNode> {
        val animeSelectedFields = arrayOf("titles","canonicalTitle","posterImage","synopsis","startDate","episodeCount")
        val libraryEntriesSelectedFields = arrayOf("progress","rating","updatedAt", "status")
        val limit = 500
        var url = "$apiUrl/library-entries?filter[userId]=$userId&filter[kind]=anime&include=anime&page[limit]=$limit&page[offset]=0&fields[anime]=${animeSelectedFields.joinToString(",")}&fields[libraryEntries]=${libraryEntriesSelectedFields.joinToString(",")}"
        val fullList = mutableListOf<KitsuNode>()

        while (true) {
            val data: KitsuResponse = getKitsuAnimeListSlice(token, url)
            data.data.forEachIndexed { index, value ->
                value.anime = data.included?.get(index)
            }
            fullList.addAll(data.data)
            url = data.links?.next ?: break
        }
        return fullList.toTypedArray()
    }

    private suspend fun getKitsuAnimeListSlice(token: AuthToken, url: String): KitsuResponse {
        return app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer ${token.accessToken}",
            )
        ).parsed<KitsuResponse>()
    }

    data class ResponseToken(
        @JsonProperty("token_type") val tokenType: String,
        @JsonProperty("expires_in") val expiresIn: Int,
        @JsonProperty("access_token") val accessToken: String,
        @JsonProperty("refresh_token") val refreshToken: String,
    )

    data class KitsuNode(
        @JsonProperty("id") val id: String,
        @JsonProperty("attributes") val attributes: KitsuNodeAttributes,
        var anime: KitsuAnimeData? = null
    ) {
        fun toLibraryItem(): LibraryItem {
            val animeItem = this.anime
            val attributes = animeItem?.attributes
            return LibraryItem(
                attributes?.canonicalTitle ?: attributes?.titles?.enJp ?: attributes?.titles?.jaJp.orEmpty(),
                "https://kitsu.io/anime/${this.id}",
                this.id,
                this.attributes.progress,
                attributes?.episodeCount,
                Score.from(this.attributes.ratingTwenty.toString(), 20),
                parseDateLong(this.attributes.updatedAt),
                "Kitsu",
                TvType.Anime,
                attributes?.posterImage?.large ?: attributes?.posterImage?.medium,
                null,
                null,
                plot = attributes?.synopsis,
                releaseDate = attributes?.startDate?.let { try { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it) } catch(_: Exception) { null } }
            )
        }
    }

    data class KitsuAnimeAttributes(
        @JsonProperty("titles") val titles: KitsuTitles?,
        @JsonProperty("canonicalTitle") val canonicalTitle: String?,
        @JsonProperty("posterImage") val posterImage: KitsuPosterImage?,
        @JsonProperty("synopsis") val synopsis: String?,
        @JsonProperty("startDate") val startDate: String?,
        @JsonProperty("endDate") val endDate: String?,
        @JsonProperty("episodeCount") val episodeCount: Int?,
        @JsonProperty("episodeLength") val episodeLength: Int?,
        @JsonProperty("ratingTwenty") val ratingTwenty: Float? = null,
        @JsonProperty("status") val status: String? = null,
    )

    data class KitsuAnimeData(
        @JsonProperty("id") val id: String,
        @JsonProperty("attributes") val attributes: KitsuAnimeAttributes,
    )

    data class KitsuNodeAttributes(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("avatar") val avatar: KitsuUserAvatar? = null,
        @JsonProperty("progress") val progress: Int? = null,
        @JsonProperty("ratingTwenty") val ratingTwenty: Float? = null,
        @JsonProperty("updatedAt") val updatedAt: String? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("canonicalTitle") val canonicalTitle: String? = null,
        @JsonProperty("titles") val titles: KitsuTitles? = null,
        @JsonProperty("posterImage") val posterImage: KitsuPosterImage? = null,
    )

    data class KitsuPosterImage(
        @JsonProperty("large") val large: String?,
        @JsonProperty("medium") val medium: String?,
    )

    data class KitsuTitles(
        @JsonProperty("en_jp") val enJp: String?,
        @JsonProperty("ja_jp") val jaJp: String?
    )

    data class KitsuUserAvatar(
        @JsonProperty("original") val original: String?
    )

    data class KitsuLinks(
        @JsonProperty("next") val next: String?,
    )

    data class KitsuResponse(
        @JsonProperty("links") val links: KitsuLinks? = null,
        @JsonProperty("data") val data: List<KitsuNode>,
        @JsonProperty("included") val included: List<KitsuAnimeData>? = null,
    )

    companion object {
        const val KITSU_CACHED_LIST: String = "kitsu_cached_list"
        private fun parseDateLong(string: String?): Long? {
            return try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).parse(
                    string ?: return null
                )?.time?.div(1000)
            } catch (e: Exception) {
                null
            }
        }

        private val kitsuStatusAsString =
            arrayOf("current", "completed", "on_hold", "dropped", "planned")
        private fun fromIntToAnimeStatus(inp: SyncWatchType): KitsuStatusType {
            return when (inp) {
                SyncWatchType.NONE ->  KitsuStatusType.None
                SyncWatchType.WATCHING ->  KitsuStatusType.Watching
                SyncWatchType.COMPLETED ->  KitsuStatusType.Completed
                SyncWatchType.ONHOLD ->  KitsuStatusType.OnHold
                SyncWatchType.DROPPED ->  KitsuStatusType.Dropped
                SyncWatchType.PLANTOWATCH ->  KitsuStatusType.PlanToWatch
                SyncWatchType.REWATCHING ->  KitsuStatusType.Watching
            }
        }

        enum class KitsuStatusType(var value: Int) {
            Watching(0),
            Completed(1),
            OnHold(2),
            Dropped(3),
            PlanToWatch(4),
            None(-1)
        }
    }
}

object Kitsu {
    private suspend fun getKitsuData(query: String): KitsuResponse {
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "Connection" to "keep-alive",
            "DNT" to "1",
            "Origin" to "https://kitsu.io",
        )

        return app.post(
            "https://api.kitsu.io/graphql",
            headers = headers,
            data = mapOf("query" to query)
        ).parsed()
    }

    private val cache: MutableMap<Pair<String, String>, Map<Int, KitsuResponse.Node>> =
        mutableMapOf()

    var isEnabled = true

    suspend fun getEpisodesDetails(
        malId: String?,
        anilistId: String?,
        isResponseRequired: Boolean = true,
    ): Map<Int, KitsuResponse.Node>? {
        if (!isResponseRequired && !isEnabled) return null
        if (anilistId != null) {
            try {
                val map = getKitsuEpisodesDetails(anilistId, "ANILIST_ANIME")
                if (!map.isNullOrEmpty()) return map
            } catch (e: Exception) {
                logError(e)
            }
        }
        if (malId != null) {
            try {
                val map = getKitsuEpisodesDetails(malId, "MYANIMELIST_ANIME")
                if (!map.isNullOrEmpty()) return map
            } catch (e: Exception) {
                logError(e)
            }
        }
        return null
    }

    @Throws
    suspend fun getKitsuEpisodesDetails(id: String, site: String): Map<Int, KitsuResponse.Node>? {
        if (id.isBlank() || site.isBlank()) return null

        if (cache.containsKey(id to site)) {
            return cache[id to site]
        }

        val query = """
query {
  lookupMapping(externalId: $id, externalSite: $site) {
    __typename
    ... on Anime {
      id
      episodes(first: 2000) {
        nodes {
          number
          titles {
            canonical
          }
          description
          thumbnail {
            original {
              url
            }
          }
        }
      }
    }
  }
}"""
        val result = getKitsuData(query)
        val map = (result.data?.lookupMapping?.episodes?.nodes ?: return null).mapNotNull { ep ->
            val num = ep?.num ?: return@mapNotNull null
            num to ep
        }.toMap()
        if (map.isNotEmpty()) {
            cache[id to site] = map
        }
        return map
    }

    data class KitsuResponse(
        val data: Data? = null
    ) {
        data class Data(
            val lookupMapping: LookupMapping? = null
        )

        data class LookupMapping(
            val id: String? = null,
            val episodes: Episodes? = null
        )

        data class Episodes(
            val nodes: List<Node?>? = null
        )

        data class Node(
            @JsonProperty("number")
            val num: Int? = null,
            val titles: Titles? = null,
            val description: Description? = null,
            val thumbnail: Thumbnail? = null
        )

        data class Description(
            val en: String? = null
        )

        data class Thumbnail(
            val original: Original? = null
        )

        data class Original(
            val url: String? = null
        )

        data class Titles(
            val canonical: String? = null
        )
    }
}
