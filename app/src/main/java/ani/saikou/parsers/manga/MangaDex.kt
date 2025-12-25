package ani.saikou.parsers.manga

import ani.saikou.client
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MangaDex : MangaParser() {

    override val name = "MangaDex"
    override val saveName = "manga_dex"
    override val hostUrl = "https://mangadex.org"

    val host = "https://api.mangadex.org"

    override suspend fun loadChapters(mangaLink: String, extra: Map<String, String>?): List<MangaChapter> {
        val old = showUserText
        setUserText("Getting Chapters...")
        val list = mutableListOf<MangaChapter>()

        val totalChapters = client.get("$host/manga/$mangaLink/feed?limit=0")
            .parsed<MangaResponse>().total ?: return list

        setUserText("Parsing Chapters...")
        (0..totalChapters step 200).reversed().toList().map { index ->
            val data = client
                .get("$host/manga/$mangaLink/feed?limit=200&order[volume]=desc&order[chapter]=desc&offset=$index")
                .parsedSafe<MangaResponse>()?.data?.reversed()

            data?.forEach {
                if (it.attributes.translatedLanguage == "en" && it.attributes.externalUrl == null) {
                    val chapter = (it.attributes.chapter ?: return@forEach).toString()
                    val title = it.attributes.title
                    list.add(MangaChapter(chapter, it.id, title))
                }
            }
        }
        list.sortBy { it.number.toFloatOrNull() }
        setUserText(old)
        return list
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {
        val res = client.get("$host/at-home/server/${chapterLink}").parsed<ChapterResponse>().chapter
        return res?.data?.map {
            MangaImage("https://uploads.mangadex.org/data/${res.hash}/${it}")
        } ?: listOf()
    }

    override suspend fun search(query: String): List<ShowResponse> {
        val json = client.get("$host/manga?limit=15&title=$query&order[followedCount]=desc&includes[]=cover_art").parsed<SearchResponse>()
        return json.data?.mapNotNull {
            val id = it.id ?: return@mapNotNull null
            val title = it.attributes?.title?.en ?: return@mapNotNull null
            val coverName = it.relationships?.find { i -> i.type == "cover_art" }?.attributes?.fileName
            val coverURL = if (coverName != null) "https://uploads.mangadex.org/covers/$id/$coverName.256.jpg" else ""
            ShowResponse(title, id, coverURL)
        } ?: listOf()
    }

    @Serializable
    private data class SearchResponse(
        @SerialName("result") val result: String? = null,
        @SerialName("data") val data: List<Datum>? = null,
        @SerialName("total") val total: Long? = null
    ) {
        @Serializable
        data class Datum(
            @SerialName("id") val id: String? = null,
            @SerialName("attributes") val attributes: DatumAttributes? = null,
            @SerialName("relationships") val relationships: List<Relationship>? = null
        )

        @Serializable
        data class DatumAttributes(
            @SerialName("title") val title: Title? = null
        )

        @Serializable
        data class Title(
            @SerialName("en") val en: String? = null
        )

        @Serializable
        data class Relationship(
            @SerialName("id") val id: String? = null,
            @SerialName("type") val type: String? = null,
            @SerialName("attributes") val attributes: RelationshipAttributes? = null
        )

        @Serializable
        data class RelationshipAttributes(
            @SerialName("fileName") val fileName: String? = null
        )
    }

    @Serializable
    private data class MangaResponse(
        @SerialName("result") val result: String? = null,
        @SerialName("data") val data: List<Datum>? = null,
        @SerialName("total") val total: Long? = null
    ) {
        @Serializable
        data class Datum(
            @SerialName("id") val id: String,
            @SerialName("attributes") val attributes: Attributes
        )

        @Serializable
        data class Attributes(
            @SerialName("volume") val volume: String? = null,
            @SerialName("chapter") val chapter: String? = null,
            @SerialName("title") val title: String? = null,
            @SerialName("translatedLanguage") val translatedLanguage: String? = null,
            @SerialName("externalUrl") val externalUrl : String? = null
        )
    }

    @Serializable
    private data class ChapterResponse(
        @SerialName("result") val result: String? = null,
        @SerialName("baseURL") val baseURL: String? = null,
        @SerialName("chapter") val chapter: Chapter? = null
    ) {
        @Serializable
        data class Chapter(
            @SerialName("hash") val hash: String? = null,
            @SerialName("data") val data: List<String>? = null,
            @SerialName("dataSaver") val dataSaver: List<String>? = null
        )
    }
}
