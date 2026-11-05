package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.AniZoneExtractor
import ani.saikou.tryWithSuspend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import java.net.URLDecoder

class Anizone : AnimeParser() {

    override val name = "Anizone"
    override val saveName = "Anizone"
    override val hostUrl = "https://anizone.to"
    override val isDubAvailableSeparately = false

    private data class ParsedEpisode(val number: String, val title: String?, val thumbnail: String?)

    override suspend fun search(query: String): List<ShowResponse> =
        tryWithSuspend(post = false, snackbar = true) {

            val q = query.trim().let {
                if (PERCENT_ENCODED.containsMatchIn(it)) runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) else it
            }
            if (q.isBlank()) return@tryWithSuspend emptyList()

            val document = client.get("$hostUrl/anime", params = mapOf("search" to q)).document

            val xData = document.selectFirst("""div[x-data*="items: JSON.parse("]""")?.attr("x-data")
                ?: return@tryWithSuspend emptyList()
            val items = ITEMS_REGEX.find(xData)?.groupValues?.get(1)?.let { parseAlpineJson(it) }?.jsonArray
                ?: return@tryWithSuspend emptyList()

            items.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val slug = item["slug"].string() ?: return@mapNotNull null

                val titles = item["title_list"] as? JsonObject
                val mainTitle = item["main_title"].string()
                val title = titles.firstTitle("1", "10", "5") ?: mainTitle ?: slug
                val romaji = titles.firstTitle("5") ?: mainTitle

                ShowResponse(
                    name = title,
                    link = slug,
                    coverUrl = FileUrl(item["cover"].string().orEmpty()),
                    otherNames = listOfNotNull(romaji).filter { it != title },
                    total = (item["episode_count"] as? JsonPrimitive)?.intOrNull
                )
            }
        } ?: emptyList()

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> =
        tryWithSuspend(post = false, snackbar = true) {
            if (animeLink.isBlank()) return@tryWithSuspend emptyList()

            val id = animeLink.substringAfterLast('-')
            val document = client.get("$hostUrl/anime/$id").document

            val xData = document.selectFirst("main > div[x-data]")?.attr("x-data").orEmpty()
            val jsonItems = ITEMS_REGEX.find(xData)?.groupValues?.get(1)?.let { parseAlpineJson(it) }?.jsonArray

            val onPage: List<ParsedEpisode> = if (jsonItems != null) {

                jsonItems.mapNotNull { element ->
                    val item = element as? JsonObject ?: return@mapNotNull null
                    ParsedEpisode(
                        number = item["slug"].string() ?: return@mapNotNull null,
                        title = (item["title_list"] as? JsonObject).firstTitle("1", "5"),
                        thumbnail = item["snapshot"].string()
                    )
                }
            } else {

                document.select("ul.grid > li, ul.grid-cols-1 > li").mapNotNull { li ->
                    val href = li.selectFirst("a")?.attr("href")?.takeIf { it.isNotBlank() }
                    val number = href?.trimEnd('/')?.substringAfterLast('/') ?: return@mapNotNull null

                    val jsonTitle = PARSE_REGEX.find(li.attr("x-data"))?.groupValues?.get(1)
                        ?.let { parseAlpineJson(it) as? JsonObject }
                        .firstTitle("1", "5")

                    ParsedEpisode(
                        number = number,
                        title = jsonTitle ?: li.selectFirst("h3")?.text()?.trim()?.takeIf { it.isNotEmpty() },
                        thumbnail = li.selectFirst("div.absolute img")?.attr("src")?.takeIf { it.isNotBlank() }
                    )
                }
            }

            val total = document
                .select(""".text-slate-100.text-xs.lg\:text-base.flex.flex-wrap > span""")
                .getOrNull(2)?.selectFirst(".inline-block")?.text()
                ?.filter { it.isDigit() }?.toIntOrNull()
                ?: 0


            val episodes = if (total > 0) {
                val byNumber = onPage.mapNotNull { ep -> ep.number.toIntOrNull()?.let { it to ep } }.toMap()
                (1..total).map { byNumber[it] ?: ParsedEpisode(it.toString(), null, null) }
            } else onPage

            episodes.map { ep ->
                Episode(
                    number = ep.number,
                    link = "$id/${ep.number}",
                    title = ep.title ?: "Episode ${ep.number}",
                    thumbnail = ep.thumbnail?.let { FileUrl(it) }
                )
            }
        } ?: emptyList()

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?): List<VideoServer> =
        tryWithSuspend(post = false, snackbar = true) {
            if (episodeLink.isBlank()) return@tryWithSuspend emptyList()


            listOf(VideoServer(name = "MULTI", embed = FileUrl("$hostUrl/anime/$episodeLink"), extraData = null))
        } ?: emptyList()

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor = AniZoneExtractor(server)

    companion object {
        private val PERCENT_ENCODED = Regex("%[0-9A-Fa-f]{2}")
        private val ITEMS_REGEX = Regex("""items:\s*JSON\.parse\('(.*?)'\)\s*,""", RegexOption.DOT_MATCHES_ALL)
        private val PARSE_REGEX = Regex("""JSON\.parse\('(.*?)'\)""", RegexOption.DOT_MATCHES_ALL)

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }


        fun parseAlpineJson(raw: String): JsonElement? = runCatching {
            json.parseToJsonElement(
                raw.replace("""\u0022""", "\"")
                    .replace("""\\\/""", "/")
                    .replace("""\\u""", """\u""")
                    .replace("""\'""", "'")
            )
        }.getOrNull()

        fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull

        fun JsonObject?.firstTitle(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { this?.get(it).string()?.takeIf(String::isNotBlank) }
    }
}