package com.anymex.runtimehost.network

import com.anymex.runtimehost.LogLevel
import com.anymex.runtimehost.Logger
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

class LogInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.nanoTime()

        Logger.log(
            "→ ${request.method} ${request.url}"

        )

        try {
            val response = chain.proceed(request)

            val tookMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startTime
            )

            Logger.log(
                "← ${response.code} ${request.url} (${tookMs}ms)"
            )

            val server = response.header("server")?.lowercase()
            val cloudflare = response.code in listOf(403, 503) && server in listOf("cloudflare", "cloudflare-nginx")

            if (cloudflare) {
                Logger.log("⚠️ Detected Cloudflare protection", LogLevel.DEBUG)
            }

            return response

        } catch (e: Exception) {
            val tookMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startTime
            )

            Logger.log(
                "× ${request.method} ${request.url} (${tookMs}ms)\n$e"
            )

            throw e
        }
    }
}
