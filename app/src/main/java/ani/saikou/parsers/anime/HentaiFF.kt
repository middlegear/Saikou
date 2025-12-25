package ani.saikou.parsers.anime

import android.util.Base64
import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.getSize
import ani.saikou.parsers.*
import ani.saikou.parsers.anime.extractors.FPlayer
import ani.saikou.tryWith
import java.net.URI

open class HentaiFF : AnimeParser() {
    override val name: String = "HentaiFF"
    override val saveName: String = "hentai_ff"
    override val hostUrl: String = "https://hentaiff.com"

    override val isDubAvailableSeparately = false
    override val isNSFW: Boolean = true

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val map = mutableMapOf<String, Episode>()
        val pageBody = client.get(animeLink).document.body()
        val notRaw = mutableListOf<Episode>()
        val raw = mutableListOf<Episode>()
        pageBody.select("div.eplister>ul>li>a").reversed().forEach { i ->
            i.select(".epl-num").text().split(" ").apply {
                val num = this[0]
                val title = this.getOrNull(1)
                (if (title == "RAW") raw else notRaw)
                    .add(Episode(num, i.attr("href"), title))
            }
        }
        raw.map { map[it.number] = it }
        notRaw.map { map[it.number] = it }
        return map.values.toList()
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String,String>?): List<VideoServer> {
        return client.get(episodeLink).document.select("select.mirror>option").mapNotNull {
            tryWith {
                val base64 = it.attr("value")
                val link = String(Base64.decode(base64, Base64.DEFAULT)).substringAfter("src=\"").substringBefore("\"")
                VideoServer(it.text(), link)
            }
        }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        val url = server.embed.url
        val extractor = when {
            "amhentai" in url -> FPlayer(server)
            "cdnview" in url -> CdnView(server)
            "hentaistream" in url -> HentaiStream(server)
            else -> null
        }
        return extractor
    }


    override suspend fun search(query: String): List<ShowResponse> {
        setUserText("Searching can take a while...")
        return client.get("${hostUrl}/?s=$query", timeout = 30).document.body()
            .select(".bs>.bsx>a").map {
                val link = it.attr("href").toString()
                val title = it.attr("title")
                val cover = it.select("img").attr("src")
                ShowResponse(title, link, cover)
            }
    }

    private class CdnView(override val server: VideoServer) : VideoExtractor() {
        override suspend fun extract(): VideoContainer {
            val host = URI.create(server.embed.url).host
            val link = "https://" + host + client.get(server.embed.url).document.select("source").attr("src")
            return VideoContainer(listOf(Video(null, VideoType.M3U8, link)))
        }
    }

    private class HentaiStream(override val server: VideoServer) : VideoExtractor() {
        private val header = mapOf("referer" to "https://hentaistream.moe/")
        override suspend fun extract(): VideoContainer {
            val base64 = server.embed.url.substringAfter("html#")
            val link = String(Base64.decode(base64, Base64.DEFAULT)).substringAfter("url=").substringBefore(";")
            return VideoContainer(listOf(
                FileUrl("${link}x264.720p.mp4", header).let {
                    Video(720, VideoType.CONTAINER, it, getSize(it),"MP4")
                },
                FileUrl("${link}av1.1080p.webm", header).let {
                    Video(1080, VideoType.CONTAINER, it, getSize(it),"WEBM")
                },
                FileUrl("${link}av1.2160p.webm", header).let {
                    Video(2160, VideoType.CONTAINER, it, getSize(it),"WEBM")
                }
            ))
        }
    }
}