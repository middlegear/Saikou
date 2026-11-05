package ani.saikou.parsers.anime.extractors

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.Subtitle
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType
import ani.saikou.parsers.anime.Anizone
import ani.saikou.parsers.anime.Anizone.Companion.string
import ani.saikou.tryWithSuspend
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.jsoup.nodes.Document
import java.net.URI

class AniZoneExtractor(override val server: VideoServer) : VideoExtractor() {

    private val playerRegex = Regex(
        """vidstackPlayer\(JSON\.parse\('(.*?)'\)\)""",
        RegexOption.DOT_MATCHES_ALL
    )

    override suspend fun extract(): VideoContainer {
        return tryWithSuspend(post = false, snackbar = true) {
            val document = client.get(server.embed.url).document
            val data = parsePlayerData(document)
                ?: return@tryWithSuspend VideoContainer(emptyList())

            val referer = URI(server.embed.url).resolve("/").toString()

            val videos = buildList {
                data["src"].string()?.takeIf { it.isNotBlank() }?.let { src ->
                    val isM3u8 = src.contains("m3u8")
                    add(
                        Video(
                            quality = null,
                            format = if (isM3u8) VideoType.M3U8 else VideoType.CONTAINER,
                            file = FileUrl(
                                url = src,
                                headers = mapOf("Referer" to referer)
                            ),
                            extraNote = "M3u8"
                        )
                    )
                }
            }

            val subtitles = (data["subtitles"] as? JsonArray).orEmpty()
                .filterIsInstance<JsonObject>()
                .filter { it["language"].string() == "en" }
                .mapNotNull { sub ->
                    val file = sub["file"].string() ?: return@mapNotNull null
                    Subtitle(
                        language = sub["title"].string() ?: "n/a",
                        url = file
                    )
                }

            VideoContainer(videos, subtitles)
        } ?: VideoContainer(emptyList())
    }

    private fun parsePlayerData(document: Document): JsonObject? {
        val attr = document.selectFirst("""div[x-data^="vidstackPlayer("]""")
            ?.attr("x-data")
            ?: return null

        val raw = playerRegex.find(attr)?.groupValues?.get(1) ?: return null

        return Anizone.parseAlpineJson(raw) as? JsonObject
    }
}