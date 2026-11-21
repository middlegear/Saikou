package eu.kanade.tachiyomi.network

import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit.MINUTES

private val DEFAULT_CACHE_CONTROL = CacheControl.Builder().maxAge(10, MINUTES).build()
private val DEFAULT_HEADERS = Headers.Builder().build()
private val DEFAULT_BODY: RequestBody = FormBody.Builder().build()

private val GLUED_DOMAIN_REGEX = Regex("^(https?://[a-zA-Z0-9.-]+\\.(?:com|net|org|top|me|to|ru|cc|io|tv|co|xyz|info|app|dev|site|online|club|fun|vip|pro|mobi|live|link|one|ws|is|li|la|sh|moe|lat|bio))([0-9].*)$")

fun String.normalizeUrl(): String {
    if (isBlank()) return this
    val trimmed = trim()
    val lastHttp = maxOf(trimmed.lastIndexOf("https://"), trimmed.lastIndexOf("http://"))
    var target = if (lastHttp > 0) trimmed.substring(lastHttp) else trimmed
    if (target.startsWith("//")) {
        target = "https:$target"
    } else if (!target.startsWith("http://") && !target.startsWith("https://")) {
        target = "https://$target"
    }
    val gluedMatch = GLUED_DOMAIN_REGEX.matchEntire(target)
    if (gluedMatch != null) {
        val domain = gluedMatch.groupValues[1]
        val path = gluedMatch.groupValues[2].trimStart('/')
        target = "$domain/$path"
    }
    return target
}

fun normalizeMediaUrl(url: String, baseUrl: String? = null): String {
    if (url.isBlank()) return url
    val trimmed = url.trim()
    val lastHttp = maxOf(trimmed.lastIndexOf("https://"), trimmed.lastIndexOf("http://"))
    val cleanUrl = if (lastHttp > 0) trimmed.substring(lastHttp) else trimmed

    if (!baseUrl.isNullOrBlank()) {
        val cleanBase = baseUrl.trimEnd('/')
        if (cleanUrl.startsWith(cleanBase)) {
            val rest = cleanUrl.substring(cleanBase.length)
            return rest.ifEmpty { "/" }
        }
    }

    if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
        return cleanUrl
    }
    if (cleanUrl.startsWith("//")) {
        return "https:$cleanUrl"
    }
    return cleanUrl
}

fun resolveUrl(base: String, path: String): String {
    if (path.isBlank()) return base
    val trimmedPath = path.trim()
    val lastHttp = maxOf(trimmedPath.lastIndexOf("https://"), trimmedPath.lastIndexOf("http://"))
    if (lastHttp > 0) {
        return trimmedPath.substring(lastHttp)
    }
    if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
        return trimmedPath
    }
    if (trimmedPath.startsWith("//")) {
        return "https:$trimmedPath"
    }
    val cleanBase = base.trimEnd('/')
    val cleanPath = trimmedPath.trimStart('/')
    return "$cleanBase/$cleanPath"
}

fun GET(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request {
    val nUrl = url.normalizeUrl().toHttpUrl()
    return GET(nUrl, headers, cache)
}

fun GET(
    url: HttpUrl,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request {
    return Request.Builder()
        .url(url)
        .headers(headers)
        .cacheControl(cache)
        .build()
}

fun POST(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request {
    return Request.Builder()
        .url(url.normalizeUrl())
        .post(body)
        .headers(headers)
        .cacheControl(cache)
        .build()
}

fun PUT(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request {
    return Request.Builder()
        .url(url.normalizeUrl())
        .put(body)
        .headers(headers)
        .cacheControl(cache)
        .build()
}

fun DELETE(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request {
    return Request.Builder()
        .url(url.normalizeUrl())
        .delete(body)
        .headers(headers)
        .cacheControl(cache)
        .build()
}

suspend fun OkHttpClient.get(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response {
    return newCall(GET(url, headers, cache)).awaitSuccess()
}

suspend fun OkHttpClient.get(
    url: HttpUrl,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response {
    return newCall(GET(url, headers, cache)).awaitSuccess()
}

suspend fun OkHttpClient.post(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response {
    return newCall(POST(url, headers, body, cache)).awaitSuccess()
}
