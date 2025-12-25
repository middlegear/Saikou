package ani.saikou.parsers.manga

import ani.saikou.client
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse

class ColoredManga: MangaParser() {

    override val hostUrl = "https://coloredmanga.com"
    override val name = "ColoredManga"
    override val saveName = "colored_manga"

    override suspend fun search(query: String): List<ShowResponse> {
        val doc = client.get("$hostUrl/?s=${encode(query)}&post_type=wp-manga").document
        val mediaData = doc.select("h3.h4 a")
        val titleData = doc.select("div.tab-thumb.c-image-hover a img")
        val data = titleData.zip(mediaData)
        return data.map {
            val name = it.second.text()
            val link = it.second.attr("href")
            val cover = it.first.select("img").attr("src")
            ShowResponse(name = name, link = link, coverUrl = cover)
        }
    }

    override suspend fun loadChapters(mangaLink: String, extra: Map<String, String>?): List<MangaChapter> {
        val doc = client.get(mangaLink).document
        val chapNumRe = Regex("([0-9]+(?:\\.[0-9]+)?)")
        val chaps = doc.select("ul.main a").filter { element ->
            element.attr("href").trim().startsWith("https")
        }
        return chaps.reversed().mapNotNull {
            val num = chapNumRe.find(it.text())?.groupValues?.get(1) ?: return@mapNotNull null
            MangaChapter(number = num, link = it.attr("href"))
        }
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {
        val doc = client.get(chapterLink).document
        val imgs = doc.select("div.reading-content > div > img")
        return imgs.map {
            MangaImage(url = it.attr("src").trim())
        }
    }

}
