package ani.saikou.parsers.manga


import ani.saikou.FileUrl
import ani.saikou.Mapper.json
import ani.saikou.client
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse
import ani.saikou.tryWithSuspend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(InternalSerializationApi::class)
class Comix : MangaParser() {
    override val saveName: String = "Comix"
    override val name: String = "Comix"
    override val hostUrl = "https://comix.to"


    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0",
        "Accept" to "image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5",
        "Accept-Language" to "en-US,en;q=0.5",
        "Accept-Encoding" to "gzip, deflate, br, zstd",
        "Referer" to "$hostUrl/",
        "Connection" to "keep-alive",
    )

    override suspend fun search(query: String): List<ShowResponse> {
        if (query.isBlank()) return emptyList()
        val link =
            "$hostUrl/api/v2/manga?exclude_genres[]=87264&keyword=${encode(query)}&order[relevance]=desc&limit=20"

        return tryWithSuspend(post = false, true) {
            val res = client.get(
                url = link,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0",
                    "Accept" to "*/*",
                    "Accept-Language" to "en-US,en;q=0.9",
                    "Referer" to "https://comix.to/home",
                    "Content-Type" to "application/json",
                )
            )
            val jsonResponse = res.parsed<ComixResponse>()

            jsonResponse.result.items.map { item ->
                val poster = item.poster.large ?: item.poster.medium ?: item.poster.small
                ShowResponse(
                    name = item.title ?: item.slug,
                    link = item.hash_id,
                    coverUrl = FileUrl(poster as String)

                )
            }
        } ?: emptyList()
    }


    override suspend fun loadChapters(
        mangaLink: String,
        extra: Map<String, String>?
    ): List<MangaChapter> {
        if (mangaLink.isBlank()) return emptyList()


        val mangaId = mangaLink.split("-").firstOrNull() ?: return emptyList()

        val allChapters = mutableListOf<MangaChapter>()
        var currentPage = 1
        var totalPages = 1

        return tryWithSuspend(post = false, snackbar = true) {
            do {
                val url =
                    "$hostUrl/api/v2/manga/$mangaId/chapters?limit=100&page=$currentPage&order[volume]=asc"

                val response = client.get(
                    url = url,
                    headers = mapOf(
                        "Referer" to "$hostUrl/title/$mangaLink",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0",
                        "Accept" to "application/json"
                    )
                ).parsed<ChapterResponse>()

                val items = response.result.items
                if (items.isEmpty()) break

                totalPages = response.result.pagination.lastPage

                val mapped = items.map { item ->
                    MangaChapter(

                        link = "$mangaLink$-id-$${item.chapter_id}-chapter-${item.number}",
                        number = item.number as String,
                        title = item.name ?: "Chapter ${item.number}"
                    )
                }

                allChapters.addAll(mapped)
                currentPage++

            } while (currentPage <= totalPages)

            allChapters
        } ?: emptyList()
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {
        if (chapterLink.isEmpty()) return emptyList()
        val mangaPath = chapterLink.replace("$-id-$", "/")
        val url = "$hostUrl/title/$mangaPath"

        return tryWithSuspend(post = false, snackbar = true) {
            val response = client.get(url).text

            val sources = mutableListOf<MangaImage>()

            val regex = Regex("""self\.__next_f\.push\(\[1,"([\s\S]*?)"\]\)""")
            val matches = regex.findAll(response)
            for (match in matches) {
                try {

                    val rawContent = match.groupValues[1]

                    val unescaped = unescapeJava(rawContent)

                    if (unescaped.startsWith("d:")) {
                        val jsonPart = unescaped.substring(2)

                        if (jsonPart.contains("\"images\"") && jsonPart.contains("\"url\"")) {
                            val root = json.parseToJsonElement(jsonPart)

                            findImagesInJson(root).forEach { imageUrl ->
                                sources.add(
                                    MangaImage(
                                        url = FileUrl(
                                            imageUrl,
                                            headers = headers
                                        ),
                                        useTransformation = false
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {

                }
            }

            if (sources.isEmpty()) throw Exception("Failed to extract images")
            sources
        } ?: emptyList()
    }


    private fun unescapeJava(escaped: String): String {
        return escaped.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }


    private fun findImagesInJson(element: JsonElement): List<String> {
        val urls = mutableListOf<String>()

        if (element is JsonObject) {
            if (element.containsKey("chapter")) {
                val chapter = element["chapter"]?.jsonObject
                val images = chapter?.get("images")?.jsonArray
                images?.forEach { img ->
                    val url = img.jsonObject["url"]?.jsonPrimitive?.content
                        ?: img.jsonObject["src"]?.jsonPrimitive?.content
                    if (url != null) urls.add(url)
                }
            } else {
                element.values.forEach { urls.addAll(findImagesInJson(it)) }
            }
        } else if (element is JsonArray) {
            element.forEach { urls.addAll(findImagesInJson(it)) }
        }

        return urls
    }


    @Serializable
    data class ComixResponse(
        val result: MangaResult
    )

    @Serializable
    data class MangaResult(
        val items: List<MangaItem>,

    )

    @Serializable
    data class MangaItem(
        val hash_id: String,
        val slug: String,
        val title: String?,
        val poster: Poster,

        )

    @Serializable
    data class Poster(val large: String?, val medium: String?, val small: String?)


    @Serializable
    data class Pagination(
        val total: Int,
        @SerialName("last_page")
        val lastPage: Int,
        @SerialName("current_page")
        val currentPage: Int
    )


    @Serializable
    data class ChapterResponse(
        val result: ChapterResult
    )

    @Serializable
    data class ChapterResult(
        val items: List<ChapterItem>,
        val pagination: Pagination
    )

    @Serializable
    data class ChapterItem(
        val chapter_id: Int,
        val number: String? = null,
        val name: String? = null,


        )


}


