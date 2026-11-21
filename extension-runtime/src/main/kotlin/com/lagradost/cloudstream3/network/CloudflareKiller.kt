package com.lagradost.cloudstream3.network

import android.webkit.CookieManager
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response

class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        
        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";").associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        }
    }

    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    fun getCookieHeaders(url: String): Headers {
        val builder = Headers.Builder()
        try {
            val cookieManager = CookieManager.getInstance()
            val cookie = cookieManager.getCookie(url)
            if (cookie != null) {
                val map = parseCookieMap(cookie)
                val cookieHeaderVal = map.entries.joinToString("; ") { "${it.key}=${it.value}" }
                if (cookieHeaderVal.isNotEmpty()) {
                    builder.add("Cookie", cookieHeaderVal)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return builder.build()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val urlStr = request.url.toString()
        val cookieHeaders = getCookieHeaders(urlStr)
        if (cookieHeaders.size > 0) {
            val builder = request.newBuilder()
            for (i in 0 until cookieHeaders.size) {
                builder.addHeader(cookieHeaders.name(i), cookieHeaders.value(i))
            }
            return chain.proceed(builder.build())
        }
        return chain.proceed(request)
    }
}
