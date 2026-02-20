package ani.saikou.parsers


import ani.saikou.BuildConfig
import ani.saikou.media.Media
import ani.saikou.client

import ani.saikou.tryWithSuspend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
abstract class MangaApiParser : MangaParser() {

    override val hostUrl: String = BuildConfig.SERVER_URL
    abstract val providerName: String

    open val apiKey: String = BuildConfig.MY_CUSTOM_API_KEY


    private val providerCache = mutableMapOf<String, ShowResponse>()
    override suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        val anilistId = mediaObj.id.toString()
        if (mediaObj.id <= 0) return null

        providerCache[anilistId]?.let { cached ->

            setUserText("Selected: ${cached.name}")

            return cached

        }
        return tryWithSuspend(post = false, snackbar = true) {

            setUserText("Searching: ${mediaObj.name ?: mediaObj.userPreferredName ?: mediaObj.nameRomaji}")

            val response =
                client.get(
                    "$hostUrl/api/anilist/manga/mappings/${anilistId}?provider=$providerName",
                    headers = mapOf("x-api-key" to apiKey)
                ).parsed<MangaProvider>()

            val title = response.provider.run { name ?: romaji ?: "Unknown Title" }

            setUserText("Found: $title")

            val result = ShowResponse(
                name = title,
                link = response.provider.id,
                coverUrl = response.provider.posterImage
            )


            if (response.provider.id.isBlank()) {
                setUserText("No results found ")
                return@tryWithSuspend null
            }
            providerCache[anilistId] = result
            result

        }
    }

    @Serializable
    private data class MangaProvider(val provider: Provider)

    @Serializable
    private data class Provider(
        val id: String,
        val name: String? = null,
        val romaji: String? = null,
        val posterImage: String
    )
}