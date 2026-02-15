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
        val cached: Boolean? = null,
        val error: String? = null
    )

    suspend fun fetchDiscordUri(imageUrl: String?): String? {
        if (imageUrl.isNullOrBlank()) return null

        if (imageUrl.startsWith("mp:")) {
            return imageUrl
        }

        if (!imageUrl.startsWith("http") && !imageUrl.startsWith("https")) {
            Log.w(TAG, "Invalid image URL, skipping worker: $imageUrl")
            return null
        }

        Log.d(TAG, "Requesting conversion via Worker: $imageUrl")

        val payload = WorkerRequest(
            imageUrl = imageUrl,
            userToken = userToken?.takeIf { it.isNotBlank() }
        )
        val jsonBody = json.encodeToString(payload)

        Log.d(TAG, "Sending payload: $jsonBody")

        val result = runCatching {
            val response = client.newCall(
                Request.Builder()
                    .url("$workerUrl/convert-image")
                    .header("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()
            ).await()

            Log.d(TAG, "Worker response code: ${response.code}")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                Log.w(TAG, "Worker returned ${response.code} – $errorBody")
                return@runCatching null
            }

            val bodyStr = response.body?.string() ?: return@runCatching null
            Log.d(TAG, "Worker response body: $bodyStr")

            val workerResponse = json.decodeFromString<WorkerResponse>(bodyStr)
            Log.d(TAG, "Worker response: $workerResponse")
            workerResponse
        }.getOrElse {
            Log.e(TAG, "Failed to contact worker: ${it.message}", it)
            null
        }

        val finalUri = result?.uri?.takeIf { it.startsWith("mp:") } ?: "mp:$imageUrl"
        Log.d(TAG, "Final URI for $imageUrl: $finalUri")
        return finalUri
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