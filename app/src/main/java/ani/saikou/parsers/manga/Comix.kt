package ani.saikou.parsers.manga


import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.MangaApiParser
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.ShowResponse
import ani.saikou.tryWithSuspend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
class Comix : MangaApiParser() {
    override val saveName: String = "Comix"
    override val name: String = "Comix"
    override val providerName: String = "comix"

    override suspend fun search(query: String): List<ShowResponse> {
        return tryWithSuspend(post = false, snackbar = true) {
            if (query.isEmpty()) return@tryWithSuspend emptyList()
            val response =
                client.get(
                    "$hostUrl/api/comix/manga/search?q=$query",
                    headers = mapOf("x-api-key" to apiKey)
                ).parsed<SearchApiResponse>()
            response.data.map {
                ShowResponse(
                    name = it.name,
                    link = it.id,
                    coverUrl = it.posterImage
                )
            }
        } ?: emptyList()
    }

    override suspend fun loadChapters(
        mangaLink: String,
        extra: Map<String, String>?
    ): List<MangaChapter> {
        return tryWithSuspend(post = false, snackbar = true) {
            if (mangaLink.isEmpty()) return@tryWithSuspend emptyList()
            val response = client.get(
                "$hostUrl/api/comix/manga/$mangaLink/chapters",
                headers = mapOf("x-api-key" to apiKey)
            )
                .parsed<ChaptersApiResponse>()
            response.data.map {
                MangaChapter(
                    number = it.chapterNumber,
                    link = it.chapterId,
                    title = it.title,
                    description = null
                )

            }
        } ?: emptyList()
    }

    private val imageUrlCache = mutableMapOf<String, List<MangaImage>>()

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {
        if (chapterLink.isEmpty()) return emptyList()

        imageUrlCache[chapterLink]?.let { return it }

        return tryWithSuspend(post = false, snackbar = true) {
            val response = client.get(
                "$hostUrl/api/comix/sources/$chapterLink",
                headers = mapOf("x-api-key" to apiKey)
            )
                .parsed<ChapterImageResponse>()

            val referer = response.headers.referer
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0",
                "Accept" to "image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5",
                "Accept-Language" to "en-US,en;q=0.5",
                "Accept-Encoding" to "gzip, deflate, br, zstd",
                "Referer" to referer,
                "Connection" to "keep-alive",
            )
            val images = response.data.map {
                MangaImage(
                    url = FileUrl(it.url, headers = headers),
                    useTransformation = false
                )
            }
            imageUrlCache[chapterLink] = images
            images
        } ?: emptyList()
    }


    @Serializable
    private data class SearchApiResponse(val data: List<SearchItem>)

    @Serializable
    private data class SearchItem(
        val id: String,
        val name: String,
        val posterImage: String
    )


    @Serializable
    private data class ChaptersApiResponse(val data: List<ChapterItem>)

    @Serializable
    private data class ChapterItem(
        val chapterId: String,
        val official: Boolean? = null,
        val title: String? = null,
        val language: String? = null,
        val releaseDate: String? = null,
        val scanlationGroup: String? = null,
        val chapterNumber: String,
    )

    @Serializable
    private data class ChapterImageResponse(
        val headers: Headers,
        val data: List<ChapterImage>
    )

    @Serializable
    private data class Headers(
        @SerialName("Referer")
        val referer: String
    )

    @Serializable
    private data class ChapterImage(
        val url: String,
        val page: Int
    )
}


