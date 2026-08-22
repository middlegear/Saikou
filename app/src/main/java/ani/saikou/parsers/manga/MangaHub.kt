package ani.saikou.parsers.manga


import android.util.Log
import ani.saikou.BuildConfig
import ani.saikou.client
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse
import com.lagradost.nicehttp.JsonAsString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup

@OptIn(InternalSerializationApi::class)
class MangaHub : MangaParser() {

    override val name = "MangaHub"
    override val saveName = "manga_hub"
    override val hostUrl = "https://api.mghcdn.com/graphql"



    private val mapper = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun buildQuery(queryAction: () -> String): String =
        queryAction().trimIndent().replace("%", "$")


    private val searchQuery = buildQuery {
        """
        query SearchManga(%term: String!) {
          search(x: m01, q: %term, limit: 10) {
            rows {
              id
              title
              slug
              image
              rank
              latestChapter
              createdDate
            }
          }
        }
        """
    }
    private val chaptersQuery = buildQuery {
        """
    query FetchMangaChapters(%mangaId: Int!) { 
      chaptersByManga(mangaID: %mangaId) {
        number
        title
      }
    }
    """
    }

    private val chapterPagesQuery = buildQuery {
        """
query FetchChapterPages(%slugId: String!, %chapterNumber: Float!) {
  chapter(x: m01, slug: %slugId, number: %chapterNumber) {
    id
    title
    mangaID
    number
    slug
    date
    pages
    noAd
    s
    manga {
      id
      title
      slug
      mainSlug
      author
      isWebtoon
      isYaoi
      isPorn
      isSoftPorn
      isLicensed
    }
  }
}
""".trimIndent()

    }
//    class CFBypass(override val location: FileUrl) : WebViewBottomDialog() {
//        val mhubAccess = "mhub_access"
//
//        override var title = "Cloudflare Bypass"
//        override val webViewClient = object : WebViewClient() {
//            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
//                val cookie = cookies.getCookie(url.toString())
//                if (cookie?.contains(mhubAccess) == true) {
//                    val clearance = cookie.substringAfter("$mhubAccess=").substringBefore(";")
//                    privateCallback.invoke(mapOf("access" to clearance))
//                }
//                super.onPageStarted(view, url, favicon)
//            }
//        }
//
//        companion object {
//            fun newInstance(url: String) = CFBypass(FileUrl(url))
//        }
//    }
//
//    val at = "x-mhub-access"
//    var accessString: String? = null
//    private suspend fun getAccess(): String {
//        if (accessString != null) return accessString!!
//        val webView = CFBypass.newInstance("https://mangahub.io/")
//        val string = webViewInterface(webView)?.get("access")
//            ?: throw Exception(currContext()?.getString(R.string.access_not_available))
//        accessString = string
//        return string
//    }

    private val headers = mapOf(

        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "Origin" to "https://mangahub.io",
        "Referer" to "https://mangahub.io/"
    )
    override suspend fun search(query: String): List<ShowResponse> {
        if (query.isBlank()) return emptyList()

        val variables = SearchVariables(term = query)
        val jsonBody = mapper.encodeToString(SearchGraphQLRequest(searchQuery, variables))

        return try {

            val rawResponseString = client.post(
                hostUrl,
                headers = headers,
                json = JsonAsString(jsonBody)
            ).text

            val response = mapper.decodeFromString<SearchResponse>(rawResponseString)

            response.data?.search?.rows?.map { row ->

                ShowResponse(
                    name = row.title ?: "Unknown Title",
                    link = "${row.id}-slug-${row.slug}",
                    coverUrl = "https://thumb.mghcdn.com/${row.image}"
                )
            } ?: emptyList()

        } catch (e: Exception) {

            emptyList()
        }
    }

    override suspend fun loadChapters(
        mangaLink: String,
        extra: Map<String, String>?
    ): List<MangaChapter> {
        if (mangaLink.isBlank()) return emptyList()
        val mangaIdStr = mangaLink.substringBefore("-slug-")
        val mangaSlug = mangaLink.substringAfter("-slug-")

        val mangaIdInt = mangaIdStr.toIntOrNull()
            ?: throw Exception("Failed to parse Manga ID from token metadata")

        val variables = ChaptersVariables(mangaId = mangaIdInt)
        val jsonBody = mapper.encodeToString(ChaptersGraphQLRequest(chaptersQuery, variables))


        return try {
            val rawResponseString = client.post(
                hostUrl,
                headers = headers,
                json = JsonAsString(jsonBody)
            ).text

            val response = mapper.decodeFromString<ChaptersResponse>(rawResponseString)

            response.data?.chaptersByManga?.map { chapter ->

                MangaChapter(
                    number = chapter.number.toString(),
                    title = chapter.title,
                    link = "${mangaSlug}-chapter-${chapter.number}"
                )
            } ?: emptyList()

        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> =
        withContext(Dispatchers.IO) {
            if (chapterLink.isBlank()) return@withContext emptyList()

            val slugId = chapterLink.substringBefore("-chapter-")
            val chapterNum = chapterLink.substringAfter("-chapter-")

            try {
                val chapterUrl = "https://mangahub.io/chapter/$slugId/chapter-$chapterNum"

                val html = client.get(
                    chapterUrl,
                    headers = mapOf(
                        "Accept-Language" to "en-US,en;q=0.9",
                        "Referer" to "https://mangahub.io/",
                        "Origin" to "https://mangahub.io",
                        "Content-Type" to "application/json"
                    )
                ).text

                val document = Jsoup.parse(html)

                val pages = document.select("#mangareader div._2aWyJ img.PB0mN")
                    .mapIndexedNotNull { index, img ->
                        val imageUrl = img.attr("src")
                        imageUrl.takeIf { it.isNotBlank() }?.let {
                            PageResponse(imageUrl = it, page = index + 1)
                        }
                    }

                if (pages.isEmpty()) return@withContext emptyList()

                val images = pages
                    .sortedBy { it.page }
                    .mapTo(mutableListOf()) { MangaImage(it.imageUrl) }

                val firstUrl = pages.first().imageUrl
                val basePath = firstUrl.substringBeforeLast("/") + "/"
                val extension = firstUrl.substringAfterLast(".")

                fun url(page: Int) = "$basePath$page.$extension"

                var lastKnown = pages.maxOf { it.page }
                val step = 6

                while (true) {
                    val next = lastKnown + step

                    val exists = try {
                        val res = client.get(url(next))
                        !res.text.contains("not_found") &&
                                !res.text.contains("does not exist")
                    } catch (_: Exception) {
                        false
                    }

                    if (!exists) {
                        for (p in (lastKnown + 1)..next) {
                            images += MangaImage(url(p))
                        }
                        break
                    }

                    for (p in (lastKnown + 1)..next) {
                        images += MangaImage(url(p))
                    }

                    lastKnown = next
                }

                images

            } catch (e: Exception) {
                emptyList()
            }
        }

    @Serializable
    data class PageResponse(
        @SerialName("imageUrl") val imageUrl: String,
        @SerialName("page") val page: Int
    )


    @Serializable
    data class SearchGraphQLRequest(
        @SerialName("query") val query: String,
        @SerialName("variables") val variables: SearchVariables
    )

    @Serializable
    data class SearchVariables(
        @SerialName("term") val term: String
    )

    @Serializable
    data class SearchResponse(
        @SerialName("data") val data: SearchData? = null
    )

    @Serializable
    data class SearchData(
        @SerialName("search") val search: SearchResult? = null
    )

    @Serializable
    data class SearchResult(
        @SerialName("rows") val rows: List<MangaRow> = emptyList()
    )

    @Serializable
    data class MangaRow(
        @SerialName("id") val id: Int,
        @SerialName("title") val title: String? = null,
        @SerialName("slug") val slug: String,
        @SerialName("image") val image: String? = null,

        )

    @Serializable
    data class ChaptersGraphQLRequest(
        @SerialName("query") val query: String,
        @SerialName("variables") val variables: ChaptersVariables
    )

    @Serializable
    data class ChaptersVariables(
        @SerialName("mangaId") val mangaId: Int
    )

    @Serializable
    data class ChaptersResponse(
        @SerialName("data") val data: ChaptersData? = null
    )

    @Serializable
    data class ChaptersData(
        @SerialName("chaptersByManga") val chaptersByManga: List<ChapterRow> = emptyList()
    )

    @Serializable
    data class ChapterRow(
        @SerialName("number") val number: Float,
        @SerialName("title") val title: String? = null
    )

    @Serializable
    data class PagesGraphQLRequest(
        @SerialName("query") val query: String,
        @SerialName("variables") val variables: PagesVariables
    )

    @Serializable
    data class PagesVariables(
        @SerialName("slugId") val slugId: String,
        @SerialName("chapterNumber") val chapterNumber: Float
    )

    @Serializable
    data class PagesResponse(
        @SerialName("data") val data: PagesData? = null
    )

    @Serializable
    data class PagesData(
        @SerialName("chapter") val chapter: ChapterFullDetails? = null
    )

    @Serializable
    data class ChapterFullDetails(
        @SerialName("id") val id: Int,
        @SerialName("title") val title: String? = null,
        @SerialName("mangaID") val mangaID: Int,
        @SerialName("number") val number: Float,
        @SerialName("slug") val slug: String? = null,
        @SerialName("date") val date: String? = null,
        // JsonElement dynamically handles whatever shape the underlying pages data takes
        @SerialName("pages") val pages: kotlinx.serialization.json.JsonElement? = null,
        @SerialName("noAd") val noAd: Boolean? = null,
        @SerialName("s") val s: Int? = null,
        @SerialName("manga") val manga: MangaSubDetails? = null
    )

    @Serializable
    data class MangaSubDetails(
        @SerialName("id") val id: Int,
        @SerialName("title") val title: String? = null,
        @SerialName("slug") val slug: String? = null,
        @SerialName("mainSlug") val mainSlug: String? = null,
        @SerialName("author") val author: String? = null,
        @SerialName("isWebtoon") val isWebtoon: Boolean? = null,
        @SerialName("isYaoi") val isYaoi: Boolean? = null,
        @SerialName("isPorn") val isPorn: Boolean? = null,
        @SerialName("isSoftPorn") val isSoftPorn: Boolean? = null,
        @SerialName("isLicensed") val isLicensed: Boolean? = null
    )
}