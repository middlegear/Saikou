package com.lagradost.cloudstream3

import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.Requests
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.Response

typealias NiceResponse = com.lagradost.nicehttp.NiceResponse

val NiceResponse.text: String get() = this.body.string()
val NiceResponse.document: Document get() = Jsoup.parse(this.text, this.url)
val NiceResponse.url: String get() = this.okhttpResponse.request.url.toString()
val NiceResponse.isSuccessful: Boolean get() = this.okhttpResponse.isSuccessful

/**
 * Extension to provide 'body' if it's missing or refers to okhttp3.ResponseBody
 * In NiceHttp 0.4.x, body is usually a property.
 */
// val NiceResponse.body: okhttp3.ResponseBody get() = this.okhttpResponse.body!!

inline fun <reified T : Any> NiceResponse.parsed(): T {
    return mapper.readValue<T>(this.text)
}

inline fun <reified T : Any> NiceResponse.parsedSafe(): T? {
    return try {
        this.parsed<T>()
    } catch (e: Exception) {
        null
    }
}

/**
 * Compatibility wrapper for Requests.get to support named parameters like 'referer' and 'interceptor'
 * if they are missing in the library version.
 */
suspend fun Requests.getSafe(
    url: String,
    headers: Map<String, String> = emptyMap(),
    referer: String? = null,
    interceptor: Any? = null,
    cookies: Map<String, String> = emptyMap()
): NiceResponse {
    val finalHeaders = if (referer != null) {
        headers + mapOf("Referer" to referer)
    } else {
        headers
    }
    // Note: NiceHttp 0.4.16 might already have these, but we use extension to be sure
    // and to handle cases where the library signature differs.
    return this.get(url, headers = finalHeaders, cookies = cookies)
}


/**
 * Mock for newSubtitleFile if missing
 */
fun newSubtitleFile(name: String, url: String): SubtitleFile {
    return SubtitleFile(name, url)
}
