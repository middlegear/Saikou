package ani.saikou.parsers

import ani.saikou.BuildConfig
import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.media.Media
import ani.saikou.tryWithSuspend

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
abstract class AnimeApiParser : AnimeParser() {

    override val hostUrl: String = BuildConfig.SERVER_URL

    open val apiKey: String = BuildConfig.MY_CUSTOM_API_KEY
    abstract val providerName: String

    private val showCache = mutableMapOf<Int, ShowResponse>()

    override suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        val anilistId = mediaObj.id
        if (anilistId <= 0) return null

        showCache[anilistId]?.let { cached ->
            setUserText("Selected: ${cached.name}")
            return cached
        }

        return tryWithSuspend(post = false, snackbar = true) {
            setUserText("Searching: ${mediaObj.name ?: mediaObj.userPreferredName ?: mediaObj.nameRomaji}")

            val url = "$hostUrl/api/anilist/anime/$anilistId/mappings?provider=$providerName"
            val res = client.get(url, headers = mapOf("x-api-key" to apiKey), timeout = 15L)
                .parsed<ApiResponse>()

            val providerId: String = if (providerName == "kitsu") {
                mediaObj.id.toString()
            } else {
                res.provider.id
            }

            if (providerId.isBlank()) {
                setUserText("No match found")
                return@tryWithSuspend null
            }

            val title = res.provider.name ?: res.provider.romaji ?: "Unknown"
            setUserText("Selected: $title")

            val response = ShowResponse(
                name = title,
                link = providerId,
                coverUrl = FileUrl(mediaObj.cover ?: "")
            )

            showCache[anilistId] = response

            response
        }
    }

    @Serializable
    data class ApiResponse(
        val provider: ProviderData
    )

    @Serializable
    data class ProviderData(
        val id: String,
        val name: String? = null,
        val romaji: String? = null,

    )
}