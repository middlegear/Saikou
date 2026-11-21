package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.utils.ExtractorApi
import java.util.Collections.synchronizedList
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import android.content.Context

object APIHolder {
    init {
        try {
            val impl = kotlin.reflect.jvm.internal.ReflectionFactoryImpl()
            val factoryField = kotlin.jvm.internal.Reflection::class.java.getDeclaredField("factory")
            factoryField.isAccessible = true
            factoryField.set(null, impl)
        } catch (_: Throwable) {}
    }

    val allProviders: MutableList<MainAPI> = synchronizedList(mutableListOf())
    val apis: MutableList<MainAPI> = synchronizedList(mutableListOf())
    
    val mapper: JsonMapper = JsonMapper.builder().addModule(kotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    fun addPluginMapping(provider: MainAPI) {
        if (!apis.contains(provider)) {
            apis.add(provider)
        }
    }
    
    fun removePluginMapping(provider: MainAPI) {
        apis.remove(provider)
    }

    fun getApiFromNameNull(apiName: String?): MainAPI? {
        if (apiName == null) return null
        return synchronized(apis) { apis.firstOrNull { it.name == apiName } }
    }

    fun getApiFromName(apiName: String?): MainAPI {
        return getApiFromNameNull(apiName) ?: apis.firstOrNull() ?: throw IllegalStateException("No API found for name: $apiName")
    }

    suspend fun getCaptchaToken(url: String, key: String, referer: String? = null): String? = null
    
    val unixTime: Long
        get() = System.currentTimeMillis() / 1000L
    val unixTimeMS: Long
        get() = System.currentTimeMillis()

    fun capitalize(str: String): String {
        return str.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
    }

    private var trackerCache: HashMap<String, AniSearch> = hashMapOf()

    suspend fun getTracker(
        titles: List<String>,
        types: Set<TrackerType>?,
        year: Int?,
    ): Tracker? = getTracker(titles, types, year, false)


    suspend fun getTracker(
        titles: List<String>,
        types: Set<TrackerType>?,
        year: Int?,
        lessAccurate: Boolean
    ): Tracker? {
        return try {
            require(titles.isNotEmpty()) { "titles must not be empty when calling getTracker" }

            val mainTitle = titles[0]
            val query = """
                query (${'$'}page: Int = 1 ${'$'}search: String ${'$'}sort: [MediaSort] = [POPULARITY_DESC, SCORE_DESC] ${'$'}type: MediaType) {
                  Page(page: ${'$'}page, perPage: 20) {
                    media(search: ${'$'}search sort: ${'$'}sort type: ${'$'}type) {
                      id
                      idMal
                      title { romaji english }
                      coverImage { extraLarge large }
                      bannerImage
                      seasonYear
                      format
                    }
                  }
                }
            """.trimIndent().trim()

            val search = trackerCache[mainTitle] ?: run {
                val body = mapOf(
                    "query" to query,
                    "variables" to mapOf(
                        "search" to mainTitle,
                        "sort" to "SEARCH_MATCH",
                        "type" to "ANIME",
                    )
                ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
                app.post("https://graphql.anilist.co", requestBody = body)
                    .parsedSafe<AniSearch>()
                    ?.also { trackerCache[mainTitle] = it }
            } ?: return null

            val res = search.data?.page?.media?.find { media ->
                val matchingYears = year == null || media.seasonYear == year
                val matchingTitles = media.title?.let { title ->
                    titles.any { userTitle -> title.isMatchingTitles(userTitle) }
                } ?: false
                val matchingTypes = types?.any { it.name.equals(media.format, true) } == true
                if (lessAccurate) matchingTitles || matchingTypes && matchingYears
                else matchingTitles && matchingTypes && matchingYears
            } ?: return null

            Tracker(
                res.idMal,
                null,
                res.id?.toString(),
                res.coverImage?.extraLarge ?: res.coverImage?.large,
                res.bannerImage
            )
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }
}

// Mapped in MainAPI.kt for binary compatibility

abstract class UiText {
    abstract fun asString(context: Context?): String
    fun asStringNull(context: Context?): String? = asString(context)
    class StringValue(val value: String): UiText() {
        override fun asString(context: Context?) = value
    }
}

fun txt(s: String?): UiText = UiText.StringValue(s ?: "")

enum class VideoWatchState {
    None,
    Watched
}

data class ResultEpisode(
    val headerName: String,
    val name: String?,
    val poster: String?,
    val episode: Int,
    val seasonIndex: Int?,
    val season: Int?,
    val data: String,
    val apiName: String,
    val id: Int,
    val index: Int,
    val position: Long = 0,
    val duration: Long = 0,
    val score: Score? = null,
    val description: String? = null,
    val isFiller: Boolean? = null,
    val tvType: TvType = TvType.Anime,
    val parentId: Int = 0,
    val videoWatchState: VideoWatchState = VideoWatchState.None,
    val totalEpisodeIndex: Int? = null,
    val airDate: Long? = null,
    val runTime: Int? = null,
)

data class SubtitleData(val name: String, val url: String, val origin: String? = null, val headers: Map<String, String> = mapOf())

data class LinkLoadingResult(
    val links: List<com.lagradost.cloudstream3.utils.ExtractorLink>,
    val subs: List<SubtitleData>,
    val syncData: HashMap<String, String>
)

// Mocks removed as they are now replaced by real implementations in MainActivity.kt and ParCollections.kt

object MvvmMock {
    fun logError(e: Throwable) {
        e.printStackTrace()
    }
}
fun logError(e: Throwable) = MvvmMock.logError(e)

inline fun <T> safe(f: () -> T): T? {
    return try {
        f()
    } catch (e: Exception) {
        logError(e)
        null
    }
}

object Log {
    fun d(tag: String, msg: String): Int = android.util.Log.d(tag, msg)
    fun i(tag: String, msg: String): Int = android.util.Log.i(tag, msg)
    fun e(tag: String, msg: String): Int = android.util.Log.e(tag, msg)
    fun w(tag: String, msg: String): Int = android.util.Log.w(tag, msg)
}

enum class RequestBodyTypes(val value: String) {
    JSON("application/json"),
    FORM("application/x-www-form-urlencoded");
    
    fun toMediaTypeOrNull() = value.toMediaTypeOrNull()
}

class WebViewResolver(
    val interceptUrl: Regex,
    val additionalUrls: List<Regex> = emptyList(),
    val userAgent: String? = null,
    val useOkhttp: Boolean = true,
    val script: String? = null,
    val scriptCallback: ((String) -> Unit)? = null,
    val timeout: Long = 60000L
) : okhttp3.Interceptor {
    companion object {
        var webViewUserAgent: String? = null
        val DEFAULT_TIMEOUT = 60_000L
        @kotlin.jvm.JvmName("getWebViewUserAgent1")
        fun getWebViewUserAgent(): String? = webViewUserAgent
    }

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        return chain.proceed(chain.request())
    }

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        method: String = "GET",
        requestCallBack: (okhttp3.Request) -> Boolean = { false },
    ) : Pair<okhttp3.Request?, List<okhttp3.Request>> {
        return null to emptyList()
    }

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
        method: String = "GET",
        requestCallBack: (okhttp3.Request) -> Boolean = { false },
    ) : Pair<okhttp3.Request?, List<okhttp3.Request>> {
        return null to emptyList()
    }

    suspend fun resolveUsingWebView(
        request: okhttp3.Request,
        requestCallBack: (okhttp3.Request) -> Boolean = { false }
    ): Pair<okhttp3.Request?, List<okhttp3.Request>> {
        return null to emptyList()
    }
}

fun getCaptchaToken(url: String, key: String, referer: String? = null): String? = null
