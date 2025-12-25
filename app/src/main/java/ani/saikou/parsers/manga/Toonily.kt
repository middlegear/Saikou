package ani.saikou.parsers.manga

import ani.saikou.FileUrl
import ani.saikou.Mapper
import ani.saikou.client
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse
import okhttp3.MultipartBody
import kotlinx.serialization.Serializable

class Toonily : MangaParser() {

    override val name = "Toonily"
    override val saveName = "toonily"
    override val hostUrl = "https://toonily.com"

    override suspend fun search(query: String): List<ShowResponse> {
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("action", "ajaxsearchpro_search")
            .addFormDataPart("aspp", query)
            .addFormDataPart("asid", "1")
            .addFormDataPart("asp_inst_id", "1_1")
            .addFormDataPart("options", "customset[]=wp-manga&asp_gen[]=content&asp_gen[]=title&filters_initial=1&filters_changed=0&qtranslate_lang=0&current_page_id=12")
            .build()
        val resp = client.post("$hostUrl/wp-admin/admin-ajax.php", requestBody = requestBody).text
        val jsonString = resp.substringAfter("ASPSTART_DATA___").substringBefore("___ASPEND_DATA")
        val json = Mapper.parse<SearchResponse>(jsonString)
        return json.results.map { manga ->
            ShowResponse(
                name = manga.title,
                link = manga.link,
                // TODO: Get better cover URL
                coverUrl = manga.image,
            )
        }
    }

    override suspend fun loadChapters(mangaLink: String, extra: Map<String, String>?): List<MangaChapter> {
        val doc = client.get(mangaLink).document
        val chapters = doc.select("#tab-chapter-listing > div > div > ul > li > a").reversed()
        return chapters.map {
            val link = it.attr("href")
            val number = link.substringAfter("/chapter-").substringBefore("/")
            MangaChapter(
                number = number,
                link = link,
                title = it.text(),
            )
        }
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {
        val doc = client.get(chapterLink).document
        return doc.select("div.reading-content > div > img").map { element ->
            MangaImage(
                url = FileUrl(
                    url = element.attr("data-src").filter { !it.isWhitespace() },
                    headers = mapOf("referer" to "$hostUrl/")
                )
            )
        }
    }


    @Serializable
    data class SearchResponse(
        val results: List<MangaSearchResult>
    ) {
        @Serializable
        data class MangaSearchResult(
            val title: String,
            val image: String,
            val link: String,
        )
    }
}
