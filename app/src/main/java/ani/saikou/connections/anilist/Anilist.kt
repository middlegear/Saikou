package ani.saikou.connections.anilist

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import ani.saikou.Mapper
import ani.saikou.R
import ani.saikou.client
import ani.saikou.currContext
import ani.saikou.logError
import ani.saikou.openLinkInBrowser
import ani.saikou.tryWithSuspend
import java.io.File
import java.util.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

object Anilist {
    val query: AnilistQueries = AnilistQueries()
    val mutation: AnilistMutations = AnilistMutations()

    var token: String? = null
    var username: String? = null
    var adult: Boolean = false
    var userid: Int? = null
    var avatar: String? = null
    var bg: String? = null
    var episodesWatched: Int? = null
    var chapterRead: Int? = null

    var genres: ArrayList<String>? = null
    var tags: Map<Boolean, List<String>>? = null

    var lastErrorMessage: String? = null

    // Mutex to ensure token loading thread safety
    private val tokenMutex = Mutex()
    private var isTokenChecked = false

    val sortBy = listOf(
        "SCORE_DESC", "POPULARITY_DESC", "TRENDING_DESC",
        "TITLE_ENGLISH", "TITLE_ENGLISH_DESC", "SCORE"
    )

    val seasons = listOf("WINTER", "SPRING", "SUMMER", "FALL")

    val anime_formats = listOf(
        "TV", "TV SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC"
    )

    val manga_formats = listOf("MANGA", "NOVEL", "ONE SHOT")

    val authorRoles = listOf("Original Creator", "Story & Art", "Story")

    private val cal: Calendar = Calendar.getInstance()
    private val currentYear = cal.get(Calendar.YEAR)
    private val currentSeason: Int = when (cal.get(Calendar.MONTH)) {
        0, 1, 2   -> 0
        3, 4, 5   -> 1
        6, 7, 8   -> 2
        9, 10, 11 -> 3
        else      -> 0
    }

    private fun getSeason(next: Boolean): Pair<String, Int> {
        var newSeason = if (next) currentSeason + 1 else currentSeason - 1
        var newYear = currentYear
        if (newSeason > 3) {
            newSeason = 0
            newYear++
        } else if (newSeason < 0) {
            newSeason = 3
            newYear--
        }
        return seasons[newSeason] to newYear
    }

    val currentSeasons = listOf(
        getSeason(false),
        seasons[currentSeason] to currentYear,
        getSeason(true)
    )

    fun loginIntent(context: Context) {
        val clientID = 6818
        try {
            CustomTabsIntent.Builder().build().launchUrl(
                context,
                "https://anilist.co/api/v2/oauth/authorize?client_id=$clientID&response_type=token".toUri()
            )
        } catch (e: ActivityNotFoundException) {
            openLinkInBrowser("https://anilist.co/api/v2/oauth/authorize?client_id=$clientID&response_type=token")
        }
    }


    suspend fun ensureToken(context: Context? = currContext()): String? {
        if (isTokenChecked && token != null) return token
        return tokenMutex.withLock {
            if (!isTokenChecked) {
                context?.let { ctx ->
                    if ("anilistToken" in ctx.fileList()) {
                        token = File(ctx.filesDir, "anilistToken").readText().trim()
                    }
                }
                isTokenChecked = true
            }
            token
        }
    }

    fun getSavedToken(context: Context): Boolean {
        if ("anilistToken" in context.fileList()) {
            token = File(context.filesDir, "anilistToken").readText().trim()
            isTokenChecked = true
            return true
        }
        isTokenChecked = true
        return false
    }

    fun removeSavedToken(context: Context) {
        token = null
        username = null
        adult = false
        userid = null
        avatar = null
        bg = null
        episodesWatched = null
        chapterRead = null
        isTokenChecked = false
        if ("anilistToken" in context.fileList()) {
            File(context.filesDir, "anilistToken").delete()
        }
    }

    @Serializable
    data class GraphQlErrorResponse(
        val data: String? = null,
        val errors: List<GraphQlError>? = null
    )

    @Serializable
    data class GraphQlError(
        val message: String? = null,
        val status: Int? = null
    )

    fun snackForStatus(status: Int?, fallbackMessage: String?): String {
        val ctx = currContext()
        return when (status) {
            429 -> ctx?.getString(R.string.anilist_rate_limited)
                ?: "Too many requests. Please wait a moment and try again."

            403 -> {
                fallbackMessage?.takeIf { it.isNotBlank() }
                    ?: ctx?.getString(R.string.anilist_temporarily_disabled)
                    ?: "AniList is temporarily unavailable. Check their Discord for status."
            }

            401 -> ctx?.getString(R.string.anilist_unauthorized)
                ?: "Session expired or invalid. Please log in again."

            404 -> ctx?.getString(R.string.anilist_not_found)
                ?: "Requested resource was not found on AniList."

            in 500..599 -> ctx?.getString(R.string.anilist_down)
                ?: "Seems like Anilist is down, maybe try using a VPN or wait for it to come back."

            else -> {
                fallbackMessage?.takeIf { it.isNotBlank() }
                    ?: ctx?.getString(R.string.error_getting_data)
                    ?: "Error getting Data from Anilist."
            }
        }
    }

    fun captureGraphQlErrors(text: String): Boolean {
        if (!text.contains("\"errors\"")) return false

        val parsed = try {
            Mapper.parse<GraphQlErrorResponse>(text)
        } catch (e: Throwable) {
            logError(e, post = false)
            null
        }

        val errs = parsed?.errors.orEmpty()
        if (errs.isEmpty()) {
            lastErrorMessage = currContext()?.getString(R.string.error_getting_data)
                ?: "Error getting Data from Anilist."
            return true
        }

        val first = errs[0]
        lastErrorMessage = snackForStatus(first.status, first.message)

        if (errs.size > 1) {
            val extra = errs.drop(1).joinToString("\n") { e ->
                e.message?.takeIf { it.isNotBlank() } ?: "Unknown error"
            }
            lastErrorMessage = "$lastErrorMessage\n$extra"
        }

        return true
    }

    suspend inline fun <reified T : Any> executeQuery(
        query: String,
        variables: String = "",
        force: Boolean = false,
        useToken: Boolean = true,
        show: Boolean = false,
        cache: Int? = null
    ): T? {
        lastErrorMessage = null

        ensureToken()

        return tryWithSuspend(post = true, snackbar = true) {
            val data = mapOf(
                "query" to query,
                "variables" to variables
            )
            val headers = mutableMapOf(
                "Content-Type" to "application/json",
                "Accept" to "application/json"
            )


            if (token != null || force) {
                if (token != null && useToken) {
                    headers["Authorization"] = "Bearer $token"
                }

                val json = client.post(
                    "https://graphql.anilist.co/",
                    headers,
                    data = data,
                    cacheTime = cache ?: 10
                )

                val statusCode = json.code

                if (!json.text.startsWith("{")) {
                    lastErrorMessage = snackForStatus(statusCode, null)
                    throw Exception(lastErrorMessage)
                }

                if (show) println("Response ($statusCode) : ${json.text}")

                if (captureGraphQlErrors(json.text)) {
                    throw Exception(lastErrorMessage)
                }

                if (statusCode !in 200..299) {
                    lastErrorMessage = snackForStatus(statusCode, null)
                    throw Exception(lastErrorMessage)
                }

                json.parsed()
            } else {
                null
            }
        }
    }
}