package ani.saikou.parsers

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.media.Media
import ani.saikou.tryWithSuspend

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
abstract class DirectApiParser : AnimeParser() {

    open val apiUrl: String = "https://kenjitsu.vercel.app"
    abstract val providerName: String


    private val episodesCache = mutableMapOf<Int, List<Episode>>()

    private suspend fun fetchEpisodes(anilistId: Int): List<Episode> {


        return tryWithSuspend(post = false, snackbar = false) {
            if (anilistId <= 0) return@tryWithSuspend emptyList()
            val url = "$apiUrl/api/anilist/episodes/$anilistId?provider=$providerName"
            val res = client.get(url).parsed<ApiResponse>()

            val episodesList = res.providerEpisodes


            if (episodesList.isEmpty()) {
                setUserText("Failed to fetch episodes for $anilistId")
                return@tryWithSuspend emptyList()
            }

            episodesList.map { ep ->

                Episode(
                    number = ep.episodeNumber.toString(),
                    link = ep.episodeId,
                    title = ep.title,
                    description = ep.overview,
                    thumbnail = ep.thumbnail?.let { FileUrl(it) }
                )
            }


        } ?: emptyList()
    }

    override suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        return tryWithSuspend(post = true, snackbar = true) {
            val mediaId = mediaObj.id


            val memoryCached = episodesCache[mediaId]
            if (memoryCached != null) {
                return@tryWithSuspend ShowResponse(
                    name = mediaObj.name ?: mediaObj.nameRomaji,
                    link = mediaId.toString(),
                    coverUrl = mediaObj.cover.toString(),
                    episodes = memoryCached
                )
            }


            val title = mediaObj.name ?: mediaObj.nameRomaji
            setUserText("Fetching $title")

            val episodes = fetchEpisodes(mediaId)

            if (episodes.isEmpty()) {
                setUserText("No episodes found")
                return@tryWithSuspend null
            }

            setUserText("Found $title")


            val response = ShowResponse(
                name = title,
                link = mediaId.toString(),
                coverUrl = mediaObj.cover.toString(),
                episodes = episodes
            )

            episodesCache[mediaId] = episodes


            response
        }
    }


    @Serializable
    data class ApiResponse(
        val data: Data,
        val providerEpisodes: List<ProviderEpisode>
    )

    @Serializable
    data class Data(
        val anilistId: Int,
        val title: Title? = null,
        val image: String? = null
    )

    @Serializable
    data class Title(
        val romaji: String,
        val english: String?
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