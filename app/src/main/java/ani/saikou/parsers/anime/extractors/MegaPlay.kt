package ani.saikou.parsers.anime.extractors


import android.util.Base64
import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.logError
import ani.saikou.parsers.Subtitle
import ani.saikou.parsers.SubtitleType
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import androidx.core.net.toUri


class MegaPlay(override val server: VideoServer) : VideoExtractor() {

    override suspend fun extract(): VideoContainer {
        try {
            val videoUrl = server.embed.url

            val referer = server.embed.headers["Referer"]


            val pageHeaders = mutableMapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "X-Requested-With" to "XMLHttpRequest"
            )
            if (referer != null) pageHeaders["Referer"] = "$referer/"

            val document = client.get(videoUrl, headers = pageHeaders).document

            val mediaId = document.selectFirst("body div.mg3-player > div.fix-area")
                ?.attr("data-id")?.trim()?.takeIf { it.isNotEmpty() }
                ?: TITLE_ID_REGEX.find(document.title())?.groupValues?.get(1)
                ?: run {
                    return VideoContainer(emptyList())
                }


            val params = mutableMapOf("id" to mediaId)
            videoUrl.toUri().getQueryParameter("s")?.takeIf { it.isNotEmpty() }?.let { params["s"] = it }

            val sourcesText = client.get(
                "$STREAM_URL/getSources",
                headers = mapOf(
                    "Accept" to "application/json,*/*",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to videoUrl
                ),
                params = params
            ).text

            val result = json.parseToJsonElement(sourcesText) as? JsonObject ?: run {
                return VideoContainer(emptyList())
            }


            val sources = result["sources"]
            var m3u8: String? = ((sources as? JsonObject)?.get("file").string()) ?: sources.string()

            val enc = result["enc"].string()?.takeIf { it.isNotEmpty() }
            if (enc != null) {
                val encrypted = Base64.decode(enc, Base64.URL_SAFE)
                if (encrypted.isEmpty() || encrypted.size % 16 != 0) {

                    return VideoContainer(emptyList())
                }


                val key = ByteArray(32).also { AES_KEY.toByteArray(Charsets.UTF_8).copyInto(it) }
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                    init(
                        Cipher.DECRYPT_MODE,
                        SecretKeySpec(key, "AES"),
                        IvParameterSpec(AES_IV.toByteArray(Charsets.UTF_8))
                    )
                }
                val decrypted = json.parseToJsonElement(
                    String(cipher.doFinal(encrypted), Charsets.UTF_8)
                ) as? JsonObject
                decrypted?.get("file").string()?.let { m3u8 = it }
            }

            var streamUrl = m3u8?.takeIf { it.isNotEmpty() } ?: run {

                return VideoContainer(emptyList())
            }


            if (!TOKEN_PARAM_REGEX.containsMatchIn(streamUrl)) {
                PATH_KEY_REGEX.find(streamUrl)?.let { match ->
                    val pathKey = "${match.groupValues[1].lowercase()}/${match.groupValues[2].lowercase()}"
                    val payload = "${System.currentTimeMillis() / 1000 + 90}|$pathKey"

                    val mac = Mac.getInstance("HmacSHA256").apply {
                        init(SecretKeySpec(TOKEN_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
                    }
                    val flags = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                    val signature = Base64.encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)), flags)
                    val token = "${Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), flags)}.$signature"

                    streamUrl = streamUrl.toUri().buildUpon()
                        .appendQueryParameter("token", token)
                        .build()
                        .toString()
                }
            }


            val uri = videoUrl.toUri()
            val origin = "${uri.scheme}://${uri.authority}"

            val baseHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                "Origin" to origin,
                "Referer" to "$origin/"
            )

            val videos = listOf(
                Video(
                    quality = null,
                    format = if (M3U8_REGEX.containsMatchIn(streamUrl)) VideoType.M3U8 else VideoType.CONTAINER,
                    file = FileUrl(streamUrl, baseHeaders),
                    extraNote = "M3u8"
                )
            )

            val subs = (result["tracks"] as? JsonArray).orEmpty()
                .filterIsInstance<JsonObject>()
                .filter { it["label"].string()?.lowercase()?.contains("english") == true }
                .mapNotNull { track ->
                    val url = track["file"].string() ?: return@mapNotNull null
                    val lower = url.lowercase()
                    Subtitle(
                        language = track["label"].string() ?: "English",
                        url = url,
                        type = when {
                            lower.endsWith(".ass") -> SubtitleType.ASS
                            lower.endsWith(".srt") -> SubtitleType.SRT
                            else -> SubtitleType.VTT
                        },
                        headers = baseHeaders,
                        isDefault = (track["default"] as? JsonPrimitive)?.booleanOrNull ?: false
                    )
                }

            return VideoContainer(videos, subs)
        } catch (e: Exception) {

            logError(e = e, post = true, snackbar = true)
            return VideoContainer(emptyList())
        }
    }

    companion object {

        private const val STREAM_URL = "https://megaplay.buzz/stream"
        private const val AES_KEY = "i?LMTAx0Q6,:}50U"
        private const val AES_IV = "W0;27ToaUpl_P%'c"
        private const val TOKEN_SECRET = "MpCdnT0k3n!9f2K#xQ7vL5mR8wN1pY4s"

        private val TITLE_ID_REGEX = Regex("""File\s+(\d+)""", RegexOption.IGNORE_CASE)
        private val TOKEN_PARAM_REGEX = Regex("""[?&]token=""", RegexOption.IGNORE_CASE)
        private val PATH_KEY_REGEX = Regex("""/([a-f0-9]{32})/([a-f0-9]{32})/""", RegexOption.IGNORE_CASE)
        private val M3U8_REGEX = Regex("""\.m3u8(?:$|[?#])""", RegexOption.IGNORE_CASE)

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull
    }
}