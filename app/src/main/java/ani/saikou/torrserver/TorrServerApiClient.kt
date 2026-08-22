package ani.saikou.torrserver

import android.net.Uri
import android.util.Log
import ani.saikou.torrserver.models.TorrentFile
import ani.saikou.torrserver.models.TorrentStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TorrServerApiClient(
    private val baseUrl: String = "http://127.0.0.1:8090",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "TorrServer"
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun addTorrent(link: String, title: String = ""): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("action", "add")
            put("link", link)
            put("title", title.ifEmpty { "Anime Stream" })
            put("save_to_db", true)
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/torrents")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        try {
            val responseBody = client.executeAsync(request)
            val json = JSONObject(responseBody)
            json.optString("hash", "")
        } catch (e: Exception) {
            throw IOException("Failed to add torrent: ${e.message}", e)
        }
    }

    suspend fun getTorrentFiles(hash: String): List<TorrentFile> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("action", "get")
                put("hash", hash)
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/torrents")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            val responseBody = client.executeAsync(request)
            val json = JSONObject(responseBody)
            val fileArray = json.optJSONArray("file_stats") ?: return@withContext emptyList()

            val files = mutableListOf<TorrentFile>()
            for (i in 0 until fileArray.length()) {
                val item = fileArray.getJSONObject(i)
                files.add(
                    TorrentFile(
                        index = item.getInt("id"),
                        path = item.getString("path"),
                        size = item.getLong("length")
                    )
                )
            }
            files
        } catch (e: Exception) {
            throw IOException("Failed to get torrent files: ${e.message}", e)
        }
    }

    suspend fun getTorrentStatus(hash: String): TorrentStats? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("action", "get")
                put("hash", hash)
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/torrents")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            val responseBody = client.executeAsync(request)
            val json = JSONObject(responseBody)

            parseTorrentStatusJson(json, hash)
        } catch (e: Exception) {
            if (e.message?.contains("404") == true) {
                Log.d(TAG, "Torrent $hash no longer exists (404)")
            } else {
                Log.e(TAG, "Failed to get torrent status", e)
            }
            null
        }
    }



    suspend fun removeTorrent(hash: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("action", "rem")
                put("hash", hash)
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/torrents")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.executeAsync(request)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove torrent", e)
            false
        }
    }

    fun getStreamUrl(hash: String, fileName: String, fileIndex: Int = 0): String {
        val encodedFileName = Uri.encode(fileName)
        return "$baseUrl/stream/$encodedFileName?link=$hash&index=$fileIndex&play"
    }


//    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
//        try {
//            val request = Request.Builder()
//                .url("$baseUrl/echo")
//                .get()
//                .build()
//            val response = client.executeAsync(request)
//            response.isNotBlank()
//        } catch (e: Exception) {
//            false
//        }
//    }

    suspend fun updateSettings(settingsPayload: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("action", "set")
                put("sets", settingsPayload)
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/settings")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.executeAsync(request)
            Log.d(TAG, "Settings updated successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update settings", e)
            false
        }
    }

    suspend fun getSettings(): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("action", "get")
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/settings")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            val responseBody = client.executeAsync(request)
            JSONObject(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get settings", e)
            null
        }
    }

    private fun parseTorrentStatusJson(json: JSONObject, defaultHash: String): TorrentStats {
        val loaded = json.optLong("loaded_size", 0L)
        val total = json.optLong("torrent_size", 0L)
        val progress = if (total > 0) loaded.toFloat() / total.toFloat() else 0f

        return TorrentStats(
            hash = json.optString("hash", defaultHash),
            name = json.optString("title", json.optString("name", "Unknown")),
            progress = progress,
            downloadSpeed = json.optLong("download_speed", 0L),
            uploadSpeed = json.optLong("upload_speed", 0L),
            connectedSeeders = json.optInt("connected_seeders", 0),
            activePeers = json.optInt("active_peers", 0),
            totalPeers = json.optInt("total_peers", 0),
            state = json.optString("stat_string", "downloading"),
            statCode = json.optInt("stat", 0),
            preloadSize = json.optLong("preload_size", 0L),
            preloadedBytes = json.optLong("preloaded_bytes", 0L),
            loadedSize = loaded,
            torrentSize = total
        )
    }

    private suspend fun OkHttpClient.executeAsync(request: Request): String =
        suspendCancellableCoroutine { continuation ->
            val call = newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            val errorBody = it.body?.string() ?: "Unknown error"
                            continuation.resumeWithException(
                                IOException("HTTP Error ${it.code}: $errorBody")
                            )
                        } else {
                            val body = it.body?.string() ?: ""
                            continuation.resume(body)
                        }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(e)
                }
            })
        }
}