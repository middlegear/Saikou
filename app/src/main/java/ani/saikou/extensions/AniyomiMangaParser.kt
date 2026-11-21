package ani.saikou.extensions

import ani.saikou.FileUrl
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse

/**
 * Exposes an installed Aniyomi/Tachiyomi manga extension source as a Saikou [MangaParser].
 * Pages go through the runtime host, which proxies header/cookie protected images.
 */
class AniyomiMangaParser(private val source: AniyomiSource) : MangaParser() {

    override val name: String = source.name

    override val saveName: String = source.key

    override val hostUrl: String = source.baseUrl.ifBlank { "https://localhost" }

    override val isNSFW: Boolean = source.isNsfw

    override val language: String = source.lang

    private val chapterNames = mutableMapOf<String, String>()
    private val chapterMemos = mutableMapOf<String, String>()

    override suspend fun search(query: String): List<ShowResponse> =
        ExtensionRuntime.aniyomiSearch(source.id, false, query)
            .mapNotNull { it.toShowResponse() }

    override suspend fun loadChapters(
        mangaLink: String,
        extra: Map<String, String>?,
    ): List<ani.saikou.parsers.MangaChapter> {
        val detail = ExtensionRuntime.aniyomiDetail(
            sourceId = source.id,
            isAnime = false,
            url = mangaLink,
            title = extra?.get("title"),
        )

        chapterNames.clear()
        chapterMemos.clear()

        return detail["episodes"].asMapList().mapNotNull { entry ->
            val url = entry.str("url") ?: return@mapNotNull null
            val chapterName = entry.str("name") ?: "Chapter"
            val memo = entry.str("memo") ?: entry.str("description")
            chapterNames[url] = chapterName
            if (!memo.isNullOrBlank()) chapterMemos[url] = memo

            ani.saikou.parsers.MangaChapter(
                number = episodeNumberOf(entry),
                link = url,
                title = chapterName,
                description = entry.str("description"),
            )
        }
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> =
        ExtensionRuntime.aniyomiPages(
            sourceId = source.id,
            chapterUrl = chapterLink,
            chapterName = chapterNames[chapterLink] ?: "Chapter",
            memo = chapterMemos[chapterLink],
        ).mapNotNull { page ->
            val url = page.str("url")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MangaImage(FileUrl(url, page.stringMap("headers")))
        }
}
