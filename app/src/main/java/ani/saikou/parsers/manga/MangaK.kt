package ani.saikou.parsers.manga

import ani.saikou.FileUrl
import ani.saikou.Mapper.json
import ani.saikou.client
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


@OptIn(InternalSerializationApi::class)
class MangaK : MangaParser() {

    override val name = "MangaBuddy"
    override val saveName = "manga_buddy"
    override val hostUrl = "https://mangak.io"

    private val apiUrl = "https://api.mangak.io"

    private val headers = mapOf(
        "Referer" to "$hostUrl/"
    )

    override suspend fun search(query: String): List<ShowResponse> {

        val url = "$apiUrl/titles/search?exclude=yaoi&page=1&limit=7&q=$query"

        return try {

            val response = client.get(url)
            val bodyText = response.body.string()
            if (bodyText.isBlank()) {
                return emptyList()
            }

            val res = try {
                json.decodeFromString<SearchResponse>(bodyText)
            } catch (e: Exception) {
                return emptyList()
            }

            val results = res.data.items.mapIndexed { index, it ->
                ShowResponse(
                    name = it.name,
                    link = it.id,
                    coverUrl = FileUrl(
                        url = it.cover,
                        headers = headers
                    )
                )
            }

            results
        } catch (e: Exception) {
            emptyList()
        }
    }


    override suspend fun loadChapters(
        mangaLink: String,
        extra: Map<String, String>?
    ): List<MangaChapter> {

        val url = "$apiUrl/titles/$mangaLink/chapters"
        return try {
            val response = client.get(url)
            val body = response.body.string()
            val res = json.decodeFromString<ChapterResponse>(body ?: "")

            val withResolvedNumbers = res.data.chapters.map { chapter ->
                val resolvedNumber = chapter.chapterNumber
                    ?: Regex("""chapter-(\d+(?:\.\d+)?)""")
                        .find(chapter.url)
                        ?.groupValues?.get(1)?.toFloatOrNull()

                chapter to resolvedNumber
            }

            val sorted = withResolvedNumbers
                .sortedBy { (_, number) -> number ?: Float.MAX_VALUE }

            val result = sorted.map { (chapter, resolvedNumber) ->
                MangaChapter(
                    number = resolvedNumber?.toString() ?: chapter.name,
                    link = chapter.url,
                    title = chapter.name
                )
            }

            result

        } catch (e: Exception) {
            emptyList()
        }
    }


    override suspend fun loadImages(chapterLink: String): List<MangaImage> {

        return try {
            val url = if (chapterLink.startsWith("http")) {
                chapterLink
            } else {
                "$hostUrl$chapterLink"
            }


            val response = client.get(url)
            val body = response.body.string()

            if (body.isBlank()) {
                return emptyList()
            }

            val jsonStart = body.indexOf("<script id=\"__NEXT_DATA__\"")

            if (jsonStart == -1) {
                return emptyList()
            }

            val jsonStartTag = body.indexOf(">", jsonStart) + 1
            val jsonEndTag = body.indexOf("</script>", jsonStartTag)

            if (jsonStartTag <= 0 || jsonEndTag <= 0) {
                return emptyList()
            }

            val jsonString = body.substring(jsonStartTag, jsonEndTag)
            val root = json.decodeFromString<Map<String, JsonElement>>(jsonString)


            val pageProps = root["props"]
                ?.jsonObject
                ?.get("pageProps")
                ?.jsonObject

            if (pageProps == null) {
                return emptyList()
            }

            val initialChapter = pageProps["initialChapter"]?.jsonObject

            if (initialChapter == null) {
                return emptyList()
            }

            val images = initialChapter["images"]
                ?.jsonArray
                ?.map { it.jsonPrimitive.content }

            if (images.isNullOrEmpty()) {
                return emptyList()
            }

            val result = images.mapIndexed { index, it ->

                MangaImage(
                    FileUrl(it, headers)
                )
            }


            result

        } catch (e: Exception) {
            emptyList()
        }
    }

    @Serializable
    data class SearchResponse(
        val data: SearchData
    )

    @Serializable
    data class SearchData(
        val items: List<MangaItemDto> = emptyList(),

        )


    @Serializable
    data class MangaItemDto(
        val id: String,
        val name: String,
        val cover: String,
        val url: String
    )
    @Serializable
    data class ChapterResponse(
        val data: ChapterList
    )
    @Serializable
    data class ChapterList(
        val chapters: List<ChapterItem> = emptyList()
    )

    @Serializable
    data class ChapterItem(
        val url: String,
        val name: String,
        @SerialName("updated_at") val updatedAt: String? = null,
        @SerialName("chapter_number") val chapterNumber: Float? = null
    )

}