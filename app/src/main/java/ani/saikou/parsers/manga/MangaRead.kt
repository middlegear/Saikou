package ani.saikou.parsers.manga

import ani.saikou.client
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse

class MangaRead: MangaParser() {

    override val name = "MangaRead"
    override val saveName = "manga_read"
    override val hostUrl = "https://mangaread.org"

    override suspend fun search(query: String): List<ShowResponse> {
        val doc = client.get("$hostUrl/?s=${encode(query)}&post_type=wp-manga").document
        val data = doc.select("div.c-tabs-item > div > div > div > a")
        val imgs = data.select("img")
        return data.zip(imgs).map {
            ShowResponse(
                name = it.first.attr("title"),
                link = it.first.attr("href"),
                coverUrl = it.second.attr("data-src")
            )
        }
    }

    override suspend fun loadChapters(mangaLink: String, extra: Map<String, String>?): List<MangaChapter> {
        val doc = client.get(mangaLink).document
        val chapters = doc.select("div.page-content-listing.single-page > div > ul > li > a").reversed()
        val chapRegex = Regex("Chapter (\\d+)")
        return chapters.mapIndexed { _, chapter ->
            MangaChapter(
                number = chapRegex.find(chapter.text())?.groupValues?.get(1).toString(),
                link = chapter.attr("href"),
                title = chapter.text()
            )
        }
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {
        val doc = client.get(chapterLink).document
        val imgs = doc.select("div.reading-content > div > img")
        return imgs.map {
            MangaImage(url = it.attr("data-src").trim())
        }
    }

}
