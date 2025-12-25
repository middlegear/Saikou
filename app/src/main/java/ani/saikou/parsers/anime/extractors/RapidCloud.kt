package ani.saikou.parsers.anime.extractors


import ani.saikou.*
import ani.saikou.parsers.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable


@OptIn(InternalSerializationApi::class)
class RapidCloud(override val server: VideoServer) : VideoExtractor() {

    @Serializable
    data class KaidoSourceItem(
        val url: String,
        val isM3u8: Boolean = true,
        val type: String = "hls"
    )

    @Serializable
    data class KaidoSubtitle(
        val url: String,
        val lang: String,
        val default: Boolean = false
    )

    @Serializable
    data class KaidoData(
        val subtitles: List<KaidoSubtitle> = emptyList(),
        val sources: List<KaidoSourceItem> = emptyList()
    )

    @Serializable
    data class KaidoResponse(
        val headers: Map<String, String>? = null,
        val data: KaidoData
    )

    override suspend fun extract(): VideoContainer {
      try {
          val response = client.get(server.embed.url)
              .parsed<KaidoResponse>()


          val videos = response.data.sources.map {
              Video(
                  quality = null,
                  format = VideoType.M3U8,
                  file = FileUrl(it.url),
                  extraNote = it.type
              )
          }


          val subs = response.data.subtitles.map {
              Subtitle(
                  language = it.lang,
                  url = it.url
              )
          }

          return VideoContainer(videos, subs)
      }catch (e: Exception){return VideoContainer(emptyList())
      }
    }
}