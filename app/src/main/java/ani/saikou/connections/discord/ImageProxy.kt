package ani.saikou.connections.discord

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ImageProxy(
    private val workerUrl: String = "https://saikou-image-proxy.kenjitsu.workers.dev",
    private val userToken: String?,
    private val client: OkHttpClient,
    private val json: Json
) {
    private val TAG = "RPC"

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class WorkerRequest(
        val imageUrl: String,
        val userToken: String? = null
    )

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class WorkerResponse(
        val uri: String?,
        val error: String? = null
    )

    private val cachedImages = mutableMapOf<String, String>()


    suspend fun fetchDiscordUri(imageUrl: String?, cacheKey: String): String? {
        if (imageUrl.isNullOrBlank()) return null

        cachedImages[cacheKey]?.let { convertedImage ->
            Log.d(TAG, "Local Image cache hit: $cacheKey")
            return convertedImage
        }

        if (imageUrl.startsWith("mp:")) {
            return imageUrl
        }

        if (!imageUrl.startsWith("http")) {
            Log.w(TAG, "Invalid image URL, skipping: $imageUrl")
            return null
        }

        Log.d(TAG, "Requesting conversion via Worker: $imageUrl")

        val payload = WorkerRequest(
            imageUrl = imageUrl,
            userToken = userToken?.takeIf { it.isNotBlank() }
        )
        val jsonBody = json.encodeToString(payload)

        val result = runCatching {
            val request = Request.Builder()
                .url("$workerUrl/convert-image")
                .header("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()


            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Worker returned ${response.code}")
                    return@runCatching null
                }

                val bodyStr = response.body?.string() ?: return@runCatching null
                json.decodeFromString<WorkerResponse>(bodyStr)
            }
        }.onFailure {
            Log.e(TAG, "Failed to contact worker: ${it.message}", it)
        }.getOrNull()

        val finalUri = result?.uri
        return if (!finalUri.isNullOrBlank()) {
            Log.d(TAG, "Caching successful response for: $cacheKey")
            cachedImages[cacheKey] = finalUri
            finalUri
        } else {
            Log.w(TAG, "Worker failed or returned empty URI, NOT caching.")
            null
        }
    }

    private suspend inline fun Call.await(): Response {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    cont.resumeWith(Result.success(response))
                }
            })
        }
    }
}