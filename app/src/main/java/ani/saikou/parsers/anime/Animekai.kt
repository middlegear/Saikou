package ani.saikou.parsers.anime


import ani.saikou.BuildConfig
import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeApiParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.MegaUp
import ani.saikou.tryWithSuspend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*

@OptIn(InternalSerializationApi::class)
class Animekai : AnimeApiParser() {
//    override val hostUrl: String = BuildConfig.SUPER_CLIPPING
    override val name = "AnimeKai"
    override val providerName = "animekai"
    override val saveName = "AnimeKai"
    override val isDubAvailableSeparately = false
    val providerUrl = "https://anikai.to"
    override suspend fun search(query: String): List<ShowResponse> {
        return tryWithSuspend(post = false, snackbar = true) {
            if (query.isBlank()) return@tryWithSuspend emptyList()

            val encodedQuery = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val searchUrl = "$providerUrl/browser?keyword=$encodedQuery"


            val response = client.get(searchUrl).text
            val document = Jsoup.parse(response)

            val selector = "div.aitem-wrapper.regular div.aitem"
            val elements = document.select(selector)


            elements.map { element ->

                val anchor = element.select("div.inner a").firstOrNull()


                val name = element.select("div.inner a").text().trim().ifBlank {

                    element.select(".title").text().trim()
                }

                val link = anchor?.attr("href")?.replace("/watch/", "")?.trim() ?: ""


                val img = element.select("img").firstOrNull()
                val posterUrl = when {
                    !img?.attr("data-src").isNullOrBlank() -> img?.attr("data-src")
                    !img?.attr("src").isNullOrBlank() -> img?.attr("src")
                    else -> ""
                }


                ShowResponse(
                    name = name,
                    link = link,
                    coverUrl = FileUrl(posterUrl as String)
                )
            }
        } ?: emptyList()
    }


    override suspend fun loadEpisodes(
        animeLink: String,
        extra: Map<String, String>?
    ): List<Episode> {

        return tryWithSuspend(post = false, snackbar = true) {
            if (animeLink.isBlank()) return@tryWithSuspend emptyList()
            val url = "$hostUrl/api/animekai/anime/$animeLink"
            val res =
                client.get(url, headers = mapOf("x-api-key" to apiKey)).parsed<EpisodesResponse>()

            res.providerEpisodes.map { ep ->
                Episode(
                    number = ep.episodeNumber.toString(),
                    link = ep.episodeId,
                    title = ep.title,
                )
            }.sortedBy { it.number.toFloatOrNull() ?: 0f }
        } ?: emptyList()
    }

    override suspend fun loadVideoServers(
        episodeLink: String,
        extra: Map<String, String>?
    ): List<VideoServer> {
        return tryWithSuspend(post = false, snackbar = true) {

            val token = episodeLink.split("-token-").getOrNull(1)
                ?: throw Exception("Invalid episodeId: $episodeLink")

            val generatedToken = MegaUp.generateToken(token)
            val listUrl = "$providerUrl/ajax/links/list?token=$token&_=$generatedToken"
            val htmlJson = client.get(listUrl).parsed<AjaxResultResponse>()
            val document = Jsoup.parse(htmlJson.result)

            val servers = CopyOnWriteArrayList<VideoServer>()


            val categories = listOf(
                "div.server-wrap div.server-items[data-id=\"sub\"] span.server" to "HardSub",
                "div.server-wrap div.server-items[data-id=\"dub\"] span.server" to "Dub",
                "div.server-wrap div.server-items[data-id=\"softsub\"] span.server" to "SoftSub"
            )


            coroutineScope {
                val tasks = categories.flatMap { (selector, label) ->
                    document.select(selector)
                        .filterNot { element ->
                            element.text().trim().equals("Server 1", ignoreCase = true)
                        }
                        .map { element ->

                            val mediaId = element.attr("data-lid")
                            val serverName = element.text().trim()

                            async {
                                try {

                                    val mediaToken = MegaUp.generateToken(mediaId)
                                    val linkViewUrl =
                                        "$providerUrl/ajax/links/view?id=$mediaId&_=$mediaToken"

                                    val linkResponse =
                                        client.get(linkViewUrl).parsed<MegaUp.MegaTokenResult>()

                                    val decodedIframe =
                                        MegaUp.decodeIframe(linkResponse.result)

                                    if (decodedIframe != null) {
                                        servers.add(
                                            VideoServer(
                                                name = "$label - $serverName",
                                                embed = FileUrl(decodedIframe)
                                            )
                                        )
                                    }

                                    Unit
                                } catch (e: Exception) {
                                    throw e
                                }
                            }
                        }
                }

                tasks.awaitAll()
            }

            servers.sortedBy {
                if (it.name.contains("HardSub")) 0
                else if (it.name.contains("Dub")) 1
                else 2
            }
        } ?: emptyList()
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor {
        return MegaUp(server)
    }



    @Serializable
    private data class EpisodesResponse(
        val providerEpisodes: List<EpisodeItem>
    )

    @Serializable
    private data class EpisodeItem(
        val episodeId: String,
        val title: String,
        val hasDub: Boolean,
        val hasSub: Boolean,
        val episodeNumber: Int
    )

    @Serializable
    data class AjaxResultResponse(
        val result: String
    )


}