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
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class KickAssAnimeExtractor(override val server: VideoServer) : VideoExtractor() {

    override suspend fun extract(): VideoContainer {
        try {
            val name = server.name

            val finalUrl = if (server.embed.url.contains("/vast")) {
                server.embed.url.toHttpUrl().newBuilder()
                    .encodedPath("/cat-player/player")
                    .build()
                    .toString()
            } else {
                server.embed.url
            }
            val finalHttpUrl = finalUrl.toHttpUrlOrNull() ?: return VideoContainer(emptyList())
            val host = finalHttpUrl.host

            val html = client.get(finalUrl, headers = mapOf("User-Agent" to USER_AGENT)).text
            val cleanHtml = html.replace("&quot;", "\"")


            val videoHeaders = mapOf("Origin" to "https://$host")


            val fixUrl: (String) -> String = { rawUrl ->
                val trimmed = rawUrl.trim()
                when {
                    trimmed.startsWith("https://") || trimmed.startsWith("http://") ->
                        trimmed.replace(HTTP_PREFIX_REGEX, "$1//")
                    trimmed.startsWith("//") -> "https://${trimmed.substring(2)}"
                    trimmed.startsWith("/") -> "${finalHttpUrl.scheme}://${finalHttpUrl.host}$trimmed"
                    else -> trimmed
                }
            }

            val decodeHex: (String) -> ByteArray = { hex ->
                ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            }

            val playlistUrl: String
            val isHls: Boolean
            val subtitleList = mutableListOf<Pair<String, String>>() // url to label

            if (cleanHtml.contains("manifest\":[0,\"")) {
                val rawManifest = MANIFEST_REGEX.find(cleanHtml)?.groupValues?.get(1) ?: return VideoContainer(emptyList())
                playlistUrl = fixUrl(rawManifest)
                isHls = playlistUrl.contains(".m3u8")

                TRACK_REGEX.findAll(cleanHtml).forEach { match ->
                    val subUrl = fixUrl(match.groupValues[3].replace("\\/", "/"))
                    if (subUrl.toHttpUrlOrNull() != null) {
                        subtitleList += subUrl to "${match.groupValues[2]} (${match.groupValues[1]})"
                    }
                }
            } else {

                if (!html.contains("cid: '")) return VideoContainer(emptyList())

                val isBird = name == "BirdStream"
                val mid = if (name == "DuckStream") "mid" else "id"
                val query = finalHttpUrl.queryParameter(mid) ?: return VideoContainer(emptyList())

                val keyText = when (name) {
                    "VidStreaming" -> "e13d38099bf562e8b9851a652d2043d3"
                    "DuckStream" -> "4504447b74641ad972980a6b8ffd7631"
                    "BirdStream" -> "4b14d0ff625163e3c9c7a47926484bf2"
                    else -> return VideoContainer(emptyList())
                }
                val key = keyText.toByteArray()


                val cid = String(decodeHex(html.substringAfter("cid: '").substringBefore("'"))).split("|")
                val timeStamp = (System.currentTimeMillis() / 1000 + 60).toString()
                val route = cid[1].replace("player.php", "source.php")


                val signatureInput = cid[0] + USER_AGENT + route + query + (if (isBird) "" else timeStamp) + keyText
                val signature = MessageDigest.getInstance("SHA-1")
                    .digest(signatureInput.toByteArray())
                    .joinToString("") { "%02x".format(it) }

                val sourceUrl = buildString {
                    append("https://").append(host).append(route)
                    append("?$mid=$query")
                    if (!isBird) append("&e=$timeStamp")
                    append("&s=$signature")
                }

                val response = client.get(
                    sourceUrl,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to finalUrl,
                        "Origin" to "https://$host"
                    )
                ).text


                val (encryptedData, ivHex) = response.substringAfter(":\"")
                    .substringBefore('"')
                    .replace("\\", "")
                    .split(":")

                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(decodeHex(ivHex)))
                }
                val decrypted = String(
                    cipher.doFinal(Base64.decode(encryptedData, Base64.DEFAULT)),
                    Charsets.UTF_8
                )

                val video = json.parseToJsonElement(decrypted) as? JsonObject ?: return VideoContainer(emptyList())

                val hls = video["hls"].string().orEmpty()
                val dash = video["dash"].string().orEmpty()
                playlistUrl = fixUrl(hls.ifEmpty { dash })
                isHls = hls.isNotBlank()

                (video["subtitles"] as? JsonArray).orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .forEach { sub ->
                        val src = sub["src"].string() ?: return@forEach
                        subtitleList += fixUrl(src) to "${sub["name"].string().orEmpty()} (${sub["language"].string().orEmpty()})"
                    }
            }

            if (playlistUrl.isBlank()) return VideoContainer(emptyList())

            val videos = listOf(
                Video(
                    quality = null,
                    format = if (isHls) VideoType.M3U8 else VideoType.DASH,
                    file = FileUrl(playlistUrl, videoHeaders),
                    extraNote = name
                )
            )

            val subtitles = subtitleList.map { (url, label) ->
                val path = url.lowercase().substringBefore('?')
                Subtitle(
                    language = label,
                    url = url,
                    type = when {
                        path.endsWith(".ass") -> SubtitleType.ASS
                        path.endsWith(".srt") -> SubtitleType.SRT
                        else -> SubtitleType.VTT
                    },
                    headers = videoHeaders,
                    isDefault = false
                )
            }

            return VideoContainer(videos, subtitles)
        } catch (e: Exception) {
            logError(e = e, post = true, snackbar = true)
            return VideoContainer(emptyList())
        }
    }

    companion object {

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0"

        private val HTTP_PREFIX_REGEX = Regex("""^(https?:)//+""")
        private val MANIFEST_REGEX = Regex("""manifest":\[0,"(?:https?:)?(//[^"]+)"]""")
        private val TRACK_REGEX =
            Regex(""""language":\[\d+,"([^"]+)"][^}]+?"name":\[\d+,"([^"]+)"][^}]+?"src":\[\d+,"([^"]+)"]""")

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull
    }
}