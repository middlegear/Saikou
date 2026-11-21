@file:JvmName("OkHttpUtils")

package org.koitharu.kotatsu.parsers.util

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    val callback = object : Callback {
        override fun onFailure(call: Call, e: java.io.IOException) {
            continuation.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resumeWith(Result.success(response))
        }
    }
    continuation.invokeOnCancellation { cancel() }
    enqueue(callback)
}

public val Response.mimeType: String?
    get() = header("content-type")?.substringBefore(';')?.trim()?.nullIfEmpty()?.lowercase()

public val HttpUrl.isHttpOrHttps: Boolean
    get() = scheme.equals("https", ignoreCase = true) || scheme.equals("http", ignoreCase = true)

public fun Headers.Builder.mergeWith(other: Headers, replaceExisting: Boolean): Headers.Builder {
    for (name in other.names()) {
        val value = other[name] ?: continue
        if (replaceExisting || this[name] == null) {
            this[name] = value
        }
    }
    return this
}

public fun Response.copy(): Response = newBuilder()
    .body(peekBody(Long.MAX_VALUE))
    .build()

public fun Response.Builder.setHeader(name: String, value: String?): Response.Builder = if (value == null) {
    removeHeader(name)
} else {
    header(name, value)
}

public inline fun Response.map(mapper: (ResponseBody) -> ResponseBody): Response {
    contract {
        callsInPlace(mapper, InvocationKind.AT_MOST_ONCE)
    }
    return body.use { responseBody ->
        newBuilder()
            .body(mapper(responseBody))
            .build()
    }
}

public fun Response.headersContentLength(): Long = headersContentLength

public val Response.headersContentLength: Long
	get() = header("Content-Length")?.toLongOrNull() ?: -1L
