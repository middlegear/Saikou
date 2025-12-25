package ani.saikou.parsers.anime.extractors


import ani.saikou.*
import ani.saikou.parsers.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@OptIn(InternalSerializationApi::class)
class MegaUp(override val server: VideoServer) : VideoExtractor() {

    @Serializable
    data class SourceItem(
        val url: String,
        val isM3u8: Boolean,
        val type: String
    )

    @Serializable
    data class Subtitle(
        val url: String,
        val kind: String,
        val lang: String,

    )

    @Serializable
    data class SourceData(
        val subtitles: List<Subtitle> = emptyList(),
        val sources: List<SourceItem> = emptyList()
    )

    @Serializable
    data class SourceResponse(
        val headers: Headers,
        val data: SourceData
    )

    @Serializable
    data class Headers(
        @SerialName("Referer")
        val referer: String?=null
    )

    override suspend fun extract(): VideoContainer {

          try {
              val response = client.get(server.embed.url)
                  .parsed<SourceResponse>()

              val videoReferer = response.headers.referer
              if (videoReferer.isNullOrEmpty() || response.data.sources.isEmpty()) {
                  return VideoContainer(emptyList())
              }
              val videos = response.data.sources.map {
                  Video(
                      quality = null,
                      format = VideoType.M3U8,
                      file = FileUrl(it.url),
                      extraNote = it.type
                  )
              }

              val realSubtitles = response.data.subtitles.filter { it.kind != "thumbnails" }
              val subs = realSubtitles.map { sub ->
                  Subtitle(
                      language = sub.lang,
                      url = sub.url
                  )
              }
              return VideoContainer(videos, subs)
          }catch(e: Exception){
              logError(e = e, post = true, snackbar = true)
              return VideoContainer(emptyList())
          }

    }
}