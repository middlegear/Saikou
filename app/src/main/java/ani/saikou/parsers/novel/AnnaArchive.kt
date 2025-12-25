package ani.saikou.parsers.novel

import ani.saikou.client
import ani.saikou.parsers.Book
import ani.saikou.parsers.NovelParser
import ani.saikou.parsers.ShowResponse
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class AnnaArchive : NovelParser() {

    override val name = "Anna's Archive"
    override val saveName = "anna"
    override val hostUrl = "https://annas-archive.org"

     override val volumeRegex = Regex("vol\\.? (\\d+(\\.\\d+)?)|volume (\\d+(\\.\\d+)?)", RegexOption.IGNORE_CASE)

    private fun parseShowResponse(it: Element?): ShowResponse? {
        it ?: return null
        if (!it.select("div.text-xs").text().contains("epub"))
            return null
        val name = it.select(".text-xl").text()
        var img = it.selectFirst("img")?.attr("src") ?: ""
        if(img=="") img = defaultImage
        val extra = mapOf(
            "0" to it.select("div.italic").text(),
            "1" to it.select("div.text-sm").text(),
            "2" to it.select("div.text-xs").text(),
        )
        return ShowResponse(name, "$hostUrl${it.attr("href")}", img, extra = extra)
    }

    override suspend fun search(query: String): List<ShowResponse> {
        val q = query.substringAfter("!$").replace("-", " ") // (minus) - does not display records containing the words after
        val vols = client.get("$hostUrl/search?ext=epub&q=$q")
            .document.getElementsByAttributeValueContaining("class", "h-[125]")
            .mapNotNull { div ->
                val a = div.selectFirst("a") ?: Jsoup.parse(div.data())
                parseShowResponse(a.selectFirst("a"))
            }
        return if(query.startsWith("!$")) vols.sortByVolume(q) else vols
    }

    override suspend fun loadBook(link: String, extra: Map<String, String>?): Book {
        return client.get(link).document.selectFirst("main")!!.let {
            val name = it.selectFirst("div.text-3xl")!!.text().substringBefore("\uD83D\uDD0D")
            var img = it.selectFirst("img")?.attr("src") ?: ""
            if(img=="") img = defaultImage
            val description = it.selectFirst("div.js-md5-top-box-description")?.text()
            val links = it.select("a.js-download-link")
                .filter { element ->
                    !element.text().contains("Fast") &&
                            !element.attr("href").contains("onion") &&
                            !element.attr("href").contains("/datasets")
                }.reversed() //libgen urls are faster
                .flatMap { a ->
                    LinkExtractor(a.attr("href")).extractLink() ?: emptyList()
                }
            Book(name, img, description, links)
        }
    }
    class LinkExtractor(private val url: String) {
        suspend fun extractLink(): List<String>? {
            return when {
                isLibgenUrl(url) || isLibraryLolUrl(url) -> LibgenExtractor(url)
                else -> listOf(url)
            }
        }

        private fun isLibgenUrl(url: String): Boolean {
            return url.contains("libgen")
        }

        private fun isLibraryLolUrl(url: String): Boolean {
            return url.contains("library.lol")
        }

        private suspend fun LibgenExtractor(url: String): List<String>? {
            return when {
                url.contains("ads.php") -> {
                    val response = client.get(url)
                    val links = response.document.select("table#main").first()?.getElementsByAttribute("href")?.first()?.attr("href")
                    listOf(url.substringBefore("ads.php") + links)
                }
                else -> {
                    val response = client.get(url)
                    val links = response.document.selectFirst("div#download")?.select("a")?.mapNotNull { it.attr("href") }
                    links?.takeWhile { !it.contains("localhost") }
                }
            }
        }

    }

}