package ani.saikou.extensions

import ani.saikou.FileUrl
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse

/**
 * Exposes a Kotatsu manga parser (loaded from a Kotatsu plugin jar) as a Saikou [MangaParser].
 */
class KotatsuMangaParser(private val source: KotatsuSource) : MangaParser() {

    override val name: String = source.name

    override val saveName: String = source.key

    override val hostUrl: String = source.baseUrl.ifBlank { "https://localhost" }

    override val isNSFW: Boolean = source.isNsfw

    override val language: String = source.lang

    private val titles = mutableMapOf<String, String>()
    private val covers = mutableMapOf<String, String>()
    private val chapterNames = mutableMapOf<String, String>()

    override suspend fun search(query: String): List<ShowResponse> =
        ExtensionRuntime.kotatsuSearch(source.id, query).mapNotNull { entry ->
            val title = entry.str("title") ?: return@mapNotNull null
            val url = entry.str("url") ?: return@mapNotNull null
            val cover = entry.str("cover") ?: ""
            titles[url] = title
            covers[url] = cover
            ShowResponse(title, url, cover)
        }

    override suspend fun loadChapters(
        mangaLink: String,
        extra: Map<String, String>?,
    ): List<ani.saikou.parsers.MangaChapter> {
        val detail = ExtensionRuntime.kotatsuDetail(
            sourceId = source.id,
            url = mangaLink,
            title = extra?.get("title") ?: titles[mangaLink] ?: "",
            cover = extra?.get("cover") ?: covers[mangaLink] ?: "",
        )

        chapterNames.clear()

        return detail["episodes"].asMapList().mapNotNull { entry ->
            val url = entry.str("url") ?: return@mapNotNull null
            val chapterName = entry.str("name") ?: "Chapter"
            chapterNames[url] = chapterName
            ani.saikou.parsers.MangaChapter(
                number = episodeNumberOf(entry),
                link = url,
                title = chapterName,
                description = entry.str("scanlator"),
            )
        }
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> =
        ExtensionRuntime.kotatsuPages(
            sourceId = source.id,
            url = chapterLink,
            name = chapterNames[chapterLink] ?: "Chapter",
        ).mapNotNull { page ->
            val url = page.str("url")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MangaImage(FileUrl(url, page.stringMap("headers")))
        }
}
