package ani.saikou.parsers.anime.extractors


import ani.saikou.*
import ani.saikou.parsers.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody


@OptIn(InternalSerializationApi::class)
class MegaUp(override val server: VideoServer) : VideoExtractor() {
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:149.0) Gecko/20100101 Firefox/149.0"

    override suspend fun extract(): VideoContainer {
        return tryWithSuspend(post = false, snackbar = true) {

            val mediaUrl = server.embed.url.replace(Regex("/(e|e2)/"), "/media/")
            val response = client.get(
                url = mediaUrl,
                headers = mapOf("User-Agent" to userAgent)
            ).parsed<MegaTokenResult>()

            val decrypted = MegaUp.decrypt(response.result, userAgent)
                ?: throw Exception("Sources decode failed")

            val subtitles = decrypted.result.tracks
                .filter { it.kind != "thumbnails" }
                .mapNotNull { track ->
                    track.label?.let {
                        Subtitle(language = it, url = track.file, type = SubtitleType.VTT)
                    }
                }


            val videos = decrypted.result.sources.map {
                Video(
                    quality = null,
                    format = VideoType.M3U8,
                    file = FileUrl(it.file)
                )
            }

            VideoContainer(videos, subtitles)
        } ?: VideoContainer(emptyList(), emptyList()).also {

        }
    }

    companion object {
        private const val TOKEN_URL = "https://enc-dec.app/api/enc-kai?text="
        private const val DECODE_URL = "https://enc-dec.app/api/dec-kai?text="
        private const val DECODE_M3U8_URL = "https://enc-dec.app/api/dec-mega"


        suspend fun generateToken(token: String?): String? {
            return try {

                val res = client.get("$TOKEN_URL${token}").parsed<MegaTokenResult>()
                res.result
            } catch (e: Exception) {
                null
            }
        }

        suspend fun decodeIframe(iframe: String): String? {
            return try {

                val response = client.get("$DECODE_URL$iframe").parsed<MegaIframeResult>()

                response.result.url
            } catch (e: Exception) {

                null
            }
        }


        suspend fun decrypt(encryptedData: String, userAgent: String): FinalResult? {
            val url = DECODE_M3U8_URL


            return try {
                val payload = DecryptPayload(
                    text = encryptedData,
                    agent = userAgent
                )

                val jsonPayload = Mapper.json.encodeToString(payload)

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = jsonPayload.toRequestBody(mediaType)
                val response = client.post(
                    url = url,
                    requestBody = body,
                    headers = mapOf(
                        "User-Agent" to userAgent
                    )
                )

                val rawResponse = response.text
                if (rawResponse.isBlank()) return null
                val parsed = response.parsed<FinalResult>()

                parsed
            } catch (e: Exception) {

                e.printStackTrace()
                null
            }
        }
    }

    @Serializable
    data class DecryptPayload(
        val text: String,
        val agent: String
    )

    @Serializable
    data class MegaTokenResult(
        @SerialName("result") val result: String
    )


    @Serializable
    data class MegaIframeResult(
        @SerialName("result") val result: MegaContent
    )

    @Serializable
    data class MegaContent(
        @SerialName("url") val url: String,
        @SerialName("skip") val skip: SkipData? = null
    )

    @Serializable
    data class SkipData(
        @SerialName("intro") val intro: List<Int> = emptyList(),
        @SerialName("outro") val outro: List<Int> = emptyList()
    )

    @Serializable
    data class FinalResult(
        @SerialName("result") val result: DecryptedResponse
    )

    @Serializable
    data class DecryptedResponse(
        @SerialName("sources") val sources: List<SourceItem> = emptyList(),
        @SerialName("tracks") val tracks: List<TrackItem> = emptyList()
    )

    @Serializable
    data class SourceItem(
        @SerialName("file") val file: String
    )


    @Serializable
    data class TrackItem(
        @SerialName("kind") val kind: String,
        @SerialName("file") val file: String,
        @SerialName("label") val label: String?=null
    )
}