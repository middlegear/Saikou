package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.getSize
import ani.saikou.parsers.*
import java.net.URI

class Haho : AnimeParser() {
    override val name: String = "Haho"
    override val saveName: String = "haho_moe"
    override val hostUrl: String = "https://haho.moe"

    override val isNSFW: Boolean = true

    override val isDubAvailableSeparately: Boolean = false

    private var cookieHeader = "Cookie" to "__ddg1_=;__ddg2_=;loop-view=thumb"

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val map = mutableMapOf<String, Episode>()
        val htmlResponse = client.get(animeLink, mapOf(cookieHeader)).document
        (1..htmlResponse.select(".entry-episodes > h2 > span.badge.badge-secondary.align-top").text().toInt()).forEach {
            val num = it.toString()
            map[num] = (Episode(num, "$animeLink/$num"))
        }
        htmlResponse.select(".episode-loop>li").forEach {
            val num = it.select(".episode-slug")[0].text().replace("Episode ", "")
            val link = "$animeLink/$num"
            val title = it.select(".episode-title")[0].text()
            val thumb = it.select("img")[0].attr("src")
            val desc = it.select("a[data-content]").attr("data-content")
            map[num] = Episode(num, link, title, FileUrl(thumb, mapOf(cookieHeader)), desc)
        }
        return map.values.toList()
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String,String>?): List<VideoServer> {
        val htmlResponse = client.get(episodeLink, mapOf(cookieHeader)).document
        return htmlResponse.select("ul.dropdown-menu > li > a.dropdown-item").map {
            var server = it.text().replace(" ", "").replace("/-", "")
            val dub = it.select("[title=Audio: English]").first() != null
            server = if (dub) "Dub - $server" else server
            val headers = mapOf(cookieHeader, "referer" to episodeLink)
            val url = "$hostUrl/embed?" + URI(it.attr("href")).query
            VideoServer(server, FileUrl(url, headers))
        }
    }

    override suspend fun search(query: String): List<ShowResponse> {
        val htmlResponse = client.get("$hostUrl/anime?q=$query&s=vtt-d", mapOf(cookieHeader))
        return htmlResponse.document.select("ul.loop.anime-loop.thumb > li > a").map {
            ShowResponse(
                it.attr("title"),
                it.attr("href"),
                FileUrl(it.select(".image")[0].attr("src"), mapOf(cookieHeader))
            )
        }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor {
        return object : VideoExtractor(){

            override val server = server

            val url = server.embed.url
            val headers = server.embed.headers

            override suspend fun extract(): VideoContainer {
                val list = mutableListOf<Video>()
                client.get(url, headers).document.select("video#player>source").forEach {
                    val uri = it.attr("src")
                    if (uri.isNotEmpty())
                        list.add(Video(it.attr("title").replace("p","").toIntOrNull(),VideoType.CONTAINER,uri, getSize(uri)))
                }
                return VideoContainer(list)
            }

        }
    }
}