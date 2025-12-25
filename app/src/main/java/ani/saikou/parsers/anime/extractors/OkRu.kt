package ani.saikou.parsers.anime.extractors

import ani.saikou.client
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType

class OkRu(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer {
        val player = client.get(server.embed.url).document.html()
        val mediaUrl = Regex("https://vd\\d+\\.mycdn\\.me/e[^\\\\]+").find(player)

        return VideoContainer(
            listOf(
                Video(null, VideoType.M3U8, mediaUrl!!.value),
                Video(null, VideoType.DASH, mediaUrl.next()!!.value)
            )
        )
    }
}