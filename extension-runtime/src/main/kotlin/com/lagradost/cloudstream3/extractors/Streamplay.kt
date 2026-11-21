package com.lagradost.cloudstream3.extractors
 
import com.lagradost.cloudstream3.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.getCaptchaToken
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI

open class Streamplay : ExtractorApi() {
    override val name = "Streamplay"
    override val mainUrl = "https://streamplay.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val request = app.get(url, headers = if (referer != null) mapOf("Referer" to referer) else emptyMap())
        val redirectUrl = request.url
        val mainServer = URI(redirectUrl).let {
            "${it.scheme}://${it.host}"
        }
        val key = redirectUrl.substringAfter("embed-").substringBefore(".html")
        val captchaKey = request.document.select("script").find { it.data().contains("sitekey:") }?.data()
                ?.substringAfterLast("sitekey: '")?.substringBefore("',")
        
        val token = if (captchaKey != null) {
                    getCaptchaToken(
                        redirectUrl,
                        captchaKey,
                        referer = "$mainServer/"
                    )
                } else null ?: throw ErrorLoadingException("can't bypass captcha")

        app.post(
            "$mainServer/player-$key-488x286.html",
            headers = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Content-Type" to "application/x-www-form-urlencoded",
                "Referer" to redirectUrl
            ),
            requestBody = "op=embed&token=$token".toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
        ).document.select("script").find { script ->
            script.data().contains("eval(function(p,a,c,k,e,d)")
        }?.let {
            val data = getAndUnpack(it.data()).substringAfter("sources=[").substringBefore(",desc")
                .replace("file", "\"file\"")
                .replace("label", "\"label\"")
            val sources = tryParseJson<List<Source>>("[$data}]")
            for (res in (sources ?: emptyList())) {
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        res.file ?: continue,
                    ) {
                        this.referer = "$mainServer/"
                        this.quality = when (res.label) {
                            "HD" -> Qualities.P720.value
                            "SD" -> Qualities.P480.value
                            else -> Qualities.Unknown.value
                        }
                        this.headers = mapOf(
                            "Range" to "bytes=0-"
                        )
                    }
                )
            }
        }
    }

    data class Source(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

}