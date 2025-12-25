package ani.saikou.parsers.manga

import ani.saikou.Mapper
import ani.saikou.client
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ComickFun : MangaParser() {

    override val name = "ComickFun"
    override val saveName = "comick_fun"
    override val hostUrl = "https://api.comick.app"
    private val imgUrl = "https://meo.comick.pictures/"

    override suspend fun search(query: String): List<ShowResponse> {
        val resp = Mapper.parse<List<SearchDatum>>(client.get("$hostUrl/search?q=${encode(query)}&tachiyomi=true").text)
        return resp.map { manga ->
            val mangaLink = "$hostUrl/comic/${manga.hid}/chapters?lang=en&chap-order=1"
            val coverLink = "$imgUrl${manga.mdCovers[0].b2Key}"
            ShowResponse(manga.title,mangaLink, coverLink)
        }
    }

    override suspend fun loadChapters(mangaLink: String, extra: Map<String, String>?): List<MangaChapter> {
        val initialRes = client.get(mangaLink).parsed<MangaChapterData>()
        val responses = mutableListOf(initialRes.chapters)
        var parsedChapters = initialRes.chapters.count()
        var pageNum = 1

        while (parsedChapters < (initialRes.totalChapters.toIntOrNull() ?: 0)) {
            try {
                val res = client.get(mangaLink + "&page=${++pageNum}").parsed<MangaChapterData>()
                responses += res.chapters
                parsedChapters += res.chapters.count()
            } catch (e: Exception) {
                break
            }
        }

        return responses.flatten().mapNotNull {
            if ((it.chap ?: "").isBlank()) return@mapNotNull null
            val chapterLink = "$hostUrl/chapter/${it.hid}?tachiyomi=true"
            MangaChapter(number = it.chap.toString(), link = chapterLink, title = it.title)
        }
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {
        val resp = client.get(chapterLink).parsed<MangaImageData>()
        return resp.chapter.images.map { MangaImage(url = it.url) }
    }

    @Serializable
    data class SearchDatum (
        val title: String,
        val hid: String,
        @SerialName("md_covers")
        val mdCovers: List<MdCover>,
    ) {
        @Serializable
        data class MdCover(
            @SerialName("b2key")
            val b2Key: String? = null
        )
    }

    @Serializable
    private data class MangaChapterData(
        @SerialName("chapters") val chapters: List<Chap>,
        @SerialName("total") val totalChapters: String
    )

    @Serializable
    private data class Chap(
        val chap: String? = null,
        val title: String? = null,
        val vol: String? = null,
        val lang: String? = null,
        val hid: String? = null,
    )

    @Serializable
    private data class MangaImageData(@SerialName("chapter") val chapter: Chapter) {

        @Serializable
        data class Chapter(@SerialName("images") val images: List<Image>) {

            @Serializable
            data class Image(@SerialName("url") val url: String)
        }
    }

}