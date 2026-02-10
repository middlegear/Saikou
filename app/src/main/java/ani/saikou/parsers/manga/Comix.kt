package ani.saikou.parsers.manga


import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse
import ani.saikou.tryWithSuspend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
class Comix : MangaParser() {
    override val saveName: String = "Comix"
    override val name: String = "Comix"

    override val hostUrl: String = "https://kenjitsu.vercel.app"
    override suspend fun search(query: String): List<ShowResponse> {
        return tryWithSuspend(post = false, snackbar = false) {
            if (query.isEmpty()) return@tryWithSuspend emptyList()
            val response =
                client.get("$hostUrl/api/comix/manga/search?q=$query").parsed<SearchApiResponse>()
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
        return tryWithSuspend(post = false, snackbar = false) {
            if (mangaLink.isEmpty()) return@tryWithSuspend emptyList()
            val response = client.get("$hostUrl/api/comix/manga/$mangaLink/chapters")
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

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {
        return tryWithSuspend(post = false, snackbar = false) {
            if (chapterLink.isEmpty()) return@tryWithSuspend emptyList()
            val response =
                client.get("$hostUrl/api/comix/sources/$chapterLink").parsed<ChapterImageResponse>()
            response.data.map {
                MangaImage(
                    url = FileUrl(it.url),
                    useTransformation = false
                )
            }
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


