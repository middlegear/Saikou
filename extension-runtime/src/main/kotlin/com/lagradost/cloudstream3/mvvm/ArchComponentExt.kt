package com.lagradost.cloudstream3.mvvm

import android.util.Log
import com.lagradost.cloudstream3.ErrorLoadingException
import kotlinx.coroutines.*
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

const val DEBUG_EXCEPTION = "THIS IS A DEBUG EXCEPTION!"
const val DEBUG_PRINT = "DEBUG PRINT"

inline fun debugPrint(block: () -> String) {
    Log.d(DEBUG_PRINT, block())
}

class DebugException(message: String) : Exception("$DEBUG_EXCEPTION\n$message")

fun logError(throwable: Throwable) {
    Log.d("ApiError", "-------------------------------------------------------------------")
    Log.d("ApiError", "safeApiCall: " + throwable.localizedMessage)
    Log.d("ApiError", "safeApiCall: " + throwable.message)
    throwable.printStackTrace()
    Log.d("ApiError", "-------------------------------------------------------------------")
}

/** Catches any exception (or error) and only logs it. Will return null on exceptions. */
fun <T> safe(apiCall: () -> T): T? {
    return try {
        apiCall.invoke()
    } catch (throwable: Throwable) {
        logError(throwable)
        return null
    }
}

/** Catches any exception (or error) and only logs it. Will return null on exceptions. */
suspend fun <T> safeAsync(apiCall: suspend () -> T): T? {
    return try {
        apiCall.invoke()
    } catch (throwable: Throwable) {
        logError(throwable)
        return null
    }
}

fun Throwable.getAllMessages(): String {
    return (this.localizedMessage ?: "") + (this.cause?.getAllMessages()?.let { "\n$it" } ?: "")
}

fun Throwable.getStackTracePretty(showMessage: Boolean = true): String {
    val prefix = if (showMessage) this.localizedMessage?.let { "\n$it" } ?: "" else ""
    return prefix + this.stackTrace.joinToString(separator = "\n") {
        "${it.fileName} ${it.lineNumber}"
    }
}

fun CoroutineScope.launchSafe(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val obj: suspend CoroutineScope.() -> Unit = {
        try {
            block()
        } catch (throwable: Throwable) {
            logError(throwable)
        }
    }
    return this.launch(context, start, obj)
}

sealed class Resource<out T> {
    data class Success<out T>(val value: T) : Resource<T>()
    data class Failure(
        val isNetworkError: Boolean,
        val errorString: String,
    ) : Resource<Nothing>()

    data class Loading(val url: String? = null) : Resource<Nothing>()
}

suspend fun <T> safeApiCall(
    apiCall: suspend () -> T,
): Resource<T> {
    return withContext(Dispatchers.IO) {
        try {
            Resource.Success(apiCall.invoke())
        } catch (throwable: Throwable) {
            logError(throwable)
            when (throwable) {
                is SocketTimeoutException, is InterruptedIOException ->
                    Resource.Failure(true, "Connection Timeout\nPlease try again later.")
                is UnknownHostException ->
                    Resource.Failure(true, "Cannot connect to server, try again later.\n${throwable.message}")
                is ErrorLoadingException ->
                    Resource.Failure(true, throwable.message ?: "Error loading, try again later.")
                is SSLHandshakeException ->
                    Resource.Failure(true, (throwable.message ?: "SSLHandshakeException") + "\nTry a VPN or DNS.")
                else ->
                    Resource.Failure(false, throwable.getStackTracePretty())
            }
        }
    }
}

fun <T> throwAbleToResource(throwable: Throwable): Resource<T> {
    logError(throwable)
    return when (throwable) {
        is SocketTimeoutException, is InterruptedIOException ->
            Resource.Failure(true, "Connection Timeout\nPlease try again later.")
        is UnknownHostException ->
            Resource.Failure(true, "Cannot connect to server, try again later.\n${throwable.message}")
        is ErrorLoadingException ->
            Resource.Failure(true, throwable.message ?: "Error loading, try again later.")
        is SSLHandshakeException ->
            Resource.Failure(true, (throwable.message ?: "SSLHandshakeException") + "\nTry a VPN or DNS.")
        else ->
            Resource.Failure(false, throwable.getStackTracePretty())
    }
}
