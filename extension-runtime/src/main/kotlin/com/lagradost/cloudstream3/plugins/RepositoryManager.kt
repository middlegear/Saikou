package com.lagradost.cloudstream3.plugins

import android.app.Activity
import android.content.Context
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.plugins.RepositoryManager.convertRawGitUrl
import com.lagradost.cloudstream3.ui.settings.extensions.REPOSITORIES_KEY
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import org.jsoup.Jsoup
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

data class SitePlugin(
    @JsonProperty("internalName") val internalName: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("authors") val authors: List<String>,
    @JsonProperty("description") val description: String?,
    @JsonProperty("updateURL") val updateURL: String?,
    @JsonProperty("plugins") val plugins: List<String>,
    @JsonProperty("tvTypes") val tvTypes: List<String>?,
    @JsonProperty("version") val version: Int,
    @JsonProperty("language") val language: String?,
    @JsonProperty("url") val url: String,
    @JsonProperty("iconUrl") val iconUrl: String?,
    @JsonProperty("status") val status: Int?,
    @JsonProperty("price") val price: Int?, // For extensions that may cost something, 0 means free
    var repositoryUrl: String
)

data class Repository(
    @JsonProperty("name") val name: String,
    @JsonProperty("description") val description: String?,
    @JsonProperty("pluginLists") val pluginLists: List<String>,
)

object RepositoryManager {
    val PREBUILT_REPOSITORIES = arrayOf<RepositoryData>(
        // Default Cloudstream repository
        RepositoryData(
            "Cloudstream",
            "https://raw.githubusercontent.com/recloudstream/cloudstream-extensions/builds/repo.json"
        )
    )

    private val httpClient = OkHttpClient.Builder().build()

    // Will convert standard github urls to raw git urls
    fun convertRawGitUrl(url: String): String {
        return url.replace(Regex("""^https?://github\.com/(.*)/blob/(.*)$"""), "https://raw.githubusercontent.com/$1/$2")
    }

    suspend fun getRepoPlugins(repositoryUrl: String): List<Pair<String, SitePlugin>>? {
        try {
            val request = Request.Builder().url(convertRawGitUrl(repositoryUrl)).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val repo = parseJson<Array<SitePlugin>>(body)
            return repo.map {
                it.repositoryUrl = repositoryUrl
                val pluginData = it
                pluginData.url to pluginData
            }
        } catch (e: Exception) {
            Log.e("RepositoryManager", "Failed to get plugins", e)
            return null
        }
    }

    suspend fun downloadPluginToFile(
        pluginUrl: String,
        file: File
    ): File? {
        try {
            val request = Request.Builder().url(convertRawGitUrl(pluginUrl)).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body ?: return null

            file.parentFile?.mkdirs()
            file.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
            return file
        } catch (e: Exception) {
            Log.e("RepositoryManager", "Failed to download plugins", e)
            return null
        }
    }

    fun getRepositories(): Array<RepositoryData> {
        return DataStore.getPrefs()?.let {
            DataStore.getKey<Array<RepositoryData>>(REPOSITORIES_KEY)
        } ?: emptyArray()
    }

    fun addRepository(repository: RepositoryData) {
        val currentKeys = getRepositories().toMutableList()
        currentKeys.add(repository)
        DataStore.setKey(REPOSITORIES_KEY, currentKeys.distinctBy { it.url }.toTypedArray())
    }

    fun removeRepository(context: Context, repositoryUrl: String) {
        val currentKeys = getRepositories().toMutableList()
        currentKeys.removeAll { it.url == repositoryUrl }
        DataStore.setKey(REPOSITORIES_KEY, currentKeys.toTypedArray())
        // Delete associated files
        val pluginDir = File(context.filesDir, "Plugins")
        PluginManager.getPluginSanitizedFileName(repositoryUrl).let { sanitizeName ->
            File(pluginDir, sanitizeName).deleteRecursively()
        }
    }

    suspend fun parseRepository(url: String): Repository? {
        return try {
            val request = Request.Builder().url(convertRawGitUrl(url)).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val bodyText = response.body?.string() ?: return null
            parseJson<Repository>(bodyText)
        } catch (e: Exception) {
            try {
                // Also parse HTML description (same as cloudstream repo manager)
                val doc = Jsoup.connect(url).get()
                val descriptionText = doc.selectFirst("head > meta[name=description]")?.attr("content")
                val titleText = doc.selectFirst("head > title")?.text()?.replace("GitHub - ", "")
                return Repository(titleText ?: "Github", descriptionText, arrayListOf(url))
            } catch (t: Throwable) {
                Log.e("RepositoryManager", "Failed to parse repository", t)
                null
            }
        }
    }
}
