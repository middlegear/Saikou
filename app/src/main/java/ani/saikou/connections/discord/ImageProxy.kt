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
    private val applicationId: String,
    private val userToken: String?,
    private val client: OkHttpClient,
    private val json: Json
) {
    private val TAG = "RPC"

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class ExternalAssetRequest(val urls: List<String>)

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class ExternalAssetResponseItem(val external_asset_path: String? = null)

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

        if (userToken.isNullOrBlank()) {
            Log.w(TAG, "No user token, cannot convert image: $imageUrl")
            return null
        }

        Log.d(TAG, "Requesting conversion directly from Discord: $imageUrl")

        val result = runCatching {
            val payload = ExternalAssetRequest(urls = listOf(imageUrl))
            val jsonBody = json.encodeToString(payload)

            val request = Request.Builder()
                .url("https://discord.com/api/v9/applications/$applicationId/external-assets")
                .header("Authorization", userToken.toString())
                .header("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Discord returned ${response.code}: ${response.body?.string()}")
                    return@runCatching null
                }

                val bodyStr = response.body?.string() ?: return@runCatching null
                json.decodeFromString<List<ExternalAssetResponseItem>>(bodyStr)
            }
        }.onFailure {
            Log.e(TAG, "Failed to contact Discord: ${it.message}", it)
        }.getOrNull()

        val rawPath = result?.firstOrNull()?.external_asset_path?.trim()

        if (rawPath.isNullOrBlank()) {
            Log.w(TAG, "Discord failed or returned empty path, NOT caching.")
            return null
        }

        val normalizedPath = normalizePath(rawPath)
        val finalUri = "mp:$normalizedPath"

        Log.d(TAG, "Caching successful response for: $cacheKey -> $finalUri")
        cachedImages[cacheKey] = finalUri
        return finalUri
    }

    private fun normalizePath(path: String): String {
        val looksLikeAppOrSnowflake =
            path.startsWith(applicationId) || Regex("^\\d{17,19}").containsMatchIn(path)

        return if (!path.startsWith("external/") && looksLikeAppOrSnowflake) {
            "external/$path"
        } else {
            path
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