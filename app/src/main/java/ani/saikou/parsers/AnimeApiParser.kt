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

            val url = "$hostUrl/api/anilist/episodes/$anilistId?provider=$providerName"
            val res = client.get(url, headers = mapOf("x-api-key" to apiKey)).parsed<ApiResponse>()

            val mappedEpisodes = res.providerEpisodes.map { ep ->
                Episode(
                    number = ep.episodeNumber.toString(),
                    link = ep.episodeId,
                    title = ep.title,
                    description = ep.overview,
                    thumbnail = ep.thumbnail?.let { FileUrl(it) }
                )
            }

            if (mappedEpisodes.isEmpty()) {
                setUserText("No episodes found")
                return@tryWithSuspend null
            }

            val title = res.provider.name ?: res.provider.romaji ?: "Unknown"
            setUserText("Found: $title")


            val response = ShowResponse(
                name = title,
                link = res.provider.id,
                coverUrl = FileUrl(mediaObj.cover ?: ""),
                episodes = mappedEpisodes
            )

            showCache[anilistId] = response

            response
        }
    }

    @Serializable
    data class ApiResponse(
        val provider: ProviderData,
        val providerEpisodes: List<ProviderEpisode>

    )

    @Serializable
    data class ProviderData(
        val id: String,
        val name: String? = null,
        val romaji: String? = null
    )


    @Serializable
    data class ProviderEpisode(
        val episodeNumber: Int? = null,
        val episodeId: String,
        val title: String? = null,
        val overview: String? = null,
        val thumbnail: String? = null
    )
}