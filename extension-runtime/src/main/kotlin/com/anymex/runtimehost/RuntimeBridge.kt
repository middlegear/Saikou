package com.anymex.runtimehost

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.Log
import com.anymex.runtimehost.aniyomi.AnimeSourceMethods
import com.anymex.runtimehost.aniyomi.AniyomiExtensionManager
import com.anymex.runtimehost.aniyomi.AniyomiSourceMethods
import com.anymex.runtimehost.aniyomi.MangaSourceMethods
import com.anymex.runtimehost.network.MangaImageProxy
import java.lang.ref.WeakReference
import java.net.URLEncoder

import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.AppCompatActivityWrapper
import com.lagradost.cloudstream3.utils.DataStore
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File
import java.security.MessageDigest
import eu.kanade.tachiyomi.network.normalizeUrl
import eu.kanade.tachiyomi.network.normalizeMediaUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient


@Suppress("unused")
object RuntimeBridge {

    private const val TAG = "RuntimeBridge"
    private var extensionManager: AniyomiExtensionManager? = null
    private var initialized = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val csPathRegistry = mutableMapOf<String, String>() 
    private val csMetadataRegistry = mutableMapOf<String, ProviderMetadata>() 
    private val aniyomiPathRegistry = mutableMapOf<String, String>() 
    private val aniyomiIsAnimeRegistry = mutableMapOf<String, Boolean>() 
    private val activeRequests = mutableMapOf<String, Job>() 

    private var lastKnownActivity: WeakReference<Any>? = null 

    data class ProviderMetadata(
        val id: String,
        val name: String,
        val mainUrl: String,
        val lang: String,
        val sourcePlugin: String?,
        val iconUrl: String?,
        val internalName: String?,
        val hasSettings: Boolean = false
    )

    @JvmOverloads
    fun initialize(context: Context, settingsMap: Map<String, Any?>? = null) {
        if (initialized) return

        Log.i(TAG, "Initializing Runtime Host - Version: 1.0.6")

        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            val initialActivity = resolveAppCompatActivity(context) ?: resolveActivity(context)
            if (initialActivity != null) {
                lastKnownActivity = WeakReference(initialActivity)
                com.lagradost.cloudstream3.MainActivity.activity = initialActivity
                com.lagradost.cloudstream3.MainActivity.context = initialActivity
            }
        }

        try {
            Injekt.addSingletonFactory<android.app.Application> {
                context.applicationContext as android.app.Application
            }
            eu.kanade.tachiyomi.network.NetworkHelper.initUserAgent(context.applicationContext)
            Injekt.addSingletonFactory { NetworkHelper(context.applicationContext) }
            Injekt.addSingletonFactory { Injekt.get<NetworkHelper>().client }
            Injekt.addSingletonFactory {
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            }

            DataStore.init(context.applicationContext)
            AcraApplication.context = context.applicationContext
            CloudStreamApp.context = context.applicationContext

            try {
                okhttp3.OkHttp.initialize(context.applicationContext)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to initialize OkHttp platform context manually", e)
            }

            (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityResumed(activity: Activity) {
                        lastKnownActivity = WeakReference(activity)
                        com.lagradost.cloudstream3.MainActivity.activity = activity
                        com.lagradost.cloudstream3.MainActivity.context = activity
                    }
                    override fun onActivityCreated(a: Activity, b: Bundle?) {
                        com.lagradost.cloudstream3.MainActivity.activity = a
                        com.lagradost.cloudstream3.MainActivity.context = a
                    }
                    override fun onActivityStarted(a: Activity) {}
                    override fun onActivityPaused(a: Activity) {}
                    override fun onActivityStopped(a: Activity) {}
                    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
                    override fun onActivityDestroyed(a: Activity) {
                        if (lastKnownActivity?.get() === a) lastKnownActivity = null
                        if (com.lagradost.cloudstream3.MainActivity.activity === a) {
                            com.lagradost.cloudstream3.MainActivity.activity = null
                            com.lagradost.cloudstream3.MainActivity.context = null
                        }
                    }
                }
            )

            extensionManager = AniyomiExtensionManager(context.applicationContext)
            Injekt.addSingletonFactory<AniyomiExtensionManager> { extensionManager!! }

            val aniyomiExtensionsPath = settingsMap?.get("aniyomiExtensionsPath") as? String
            if (aniyomiExtensionsPath != null) {
                Log.i(TAG, "Proactive Loading: Starting Aniyomi extension scan at $aniyomiExtensionsPath")
                scope.launch {
                    getInstalledAnimeExtensions(context.applicationContext, aniyomiExtensionsPath)
                    getInstalledMangaExtensions(context.applicationContext)
                    Log.i(TAG, "Proactive Loading: Aniyomi background scan completed")
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Init Step 2 Failed (Skipping Critical Setup): ${e.message}")
        }

        Log.i(TAG, "Runtime Host Initialized successfully")
        try {
            val cacheDir = File(context.cacheDir, "manga_pages_cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (_: Exception) {}
        initialized = true
    }

    fun cancelRequest(token: String): Boolean {
        val job = activeRequests[token] ?: return false
        Log.i(TAG, "Cancelling network request for token: $token")
        job.cancel()
        activeRequests.remove(token)
        return true
    }

    fun shutdown() {
        Log.i(TAG, "Shutting down Runtime Host...")
        try {
            initialized = false
            activeRequests.values.forEach { it.cancel() }
            activeRequests.clear()

            try {
                MangaImageProxy.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping MangaImageProxy: ${e.message}")
            }

            csPathRegistry.clear()
            csMetadataRegistry.clear()
            aniyomiPathRegistry.clear()
            aniyomiIsAnimeRegistry.clear()
            Log.i(TAG, "Runtime Host shutdown complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown: ${e.message}")
        }
    }

    fun getInstalledAnimeExtensions(context: Context, path: String? = null): List<Map<String, Any?>> {
        val extensions = extensionManager(context).fetchInstalledAnimeExtensions(path)
        
        extensions.forEach { ext ->
            ext.sources.forEach { source ->
                aniyomiPathRegistry[source.id.toString()] = path ?: ""
                aniyomiIsAnimeRegistry[source.id.toString()] = true
            }
        }

        return extensions.flatMap { ext ->
            ext.sources.map { source ->
                val baseUrl = (source as? AnimeHttpSource)?.baseUrl.orEmpty()
                val supportsLatest = (source as? eu.kanade.tachiyomi.animesource.AnimeCatalogueSource)?.supportsLatest ?: false
                val supportsPopular = source is eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
                mapOf(
                    "id" to source.id.toString(),
                    "name" to ext.name,
                    "sourceName" to source.name,
                    "baseUrl" to baseUrl,
                    "lang" to source.lang,
                    "isNsfw" to ext.isNsfw,
                    "iconUrl" to ext.iconUrl,
                    "version" to ext.versionName,
                    "pkgName" to ext.pkgName,
                    "itemType" to 1,
                    "hasUpdate" to ext.hasUpdate,
                    "isObsolete" to ext.isObsolete,
                    "isShared" to ext.isShared,
                    "isPrivate" to (!ext.isShared),
                    "supportsLatest" to supportsLatest,
                    "supportsPopular" to supportsPopular,
                )
            }
        }
    }

    fun getInstalledMangaExtensions(context: Context, path: String? = null): List<Map<String, Any?>> {
        val extensions = extensionManager(context).fetchInstalledMangaExtensions(path)
        
        extensions.forEach { ext ->
            ext.sources.forEach { source ->
                aniyomiPathRegistry[source.id.toString()] = path ?: "" 
                aniyomiIsAnimeRegistry[source.id.toString()] = false
            }
        }

        return extensions.flatMap { ext ->
            ext.sources.map { source ->
                val baseUrl = (source as? HttpSource)?.baseUrl.orEmpty()
                val supportsLatest = (source as? eu.kanade.tachiyomi.source.CatalogueSource)?.supportsLatest ?: false
                val supportsPopular = source is eu.kanade.tachiyomi.source.CatalogueSource
                mapOf(
                    "id" to source.id.toString(),
                    "name" to ext.name,
                    "sourceName" to source.name,
                    "baseUrl" to baseUrl,
                    "lang" to source.lang,
                    "isNsfw" to ext.isNsfw,
                    "iconUrl" to ext.iconUrl,
                    "version" to ext.versionName,
                    "pkgName" to ext.pkgName,
                    "itemType" to 0,
                    "hasUpdate" to ext.hasUpdate,
                    "isObsolete" to ext.isObsolete,
                    "isShared" to ext.isShared,
                    "isPrivate" to (!ext.isShared),
                    "supportsLatest" to supportsLatest,
                    "supportsPopular" to supportsPopular,
                )
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun aniyomiGetPopular(
        context: Context,
        sourceId: String,
        isAnime: Boolean,
        page: Int,
        parameters: Map<String, Any?>? = null,
    ): Map<String, Any?> {
        val token = parameters?.get("token") as? String
        val job = scope.async {
            val m = media(context, sourceId, isAnime)
            m.parameters = parameters
            val res = m.getPopular(page)
            mapOf("list" to res.animes.map { it.toMap() }, "hasNextPage" to res.hasNextPage)
        }
        if (token != null) activeRequests[token] = job
        return try {
            runBlocking { job.await() }
        } finally {
            if (token != null) activeRequests.remove(token)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun aniyomiGetLatestUpdates(
        context: Context,
        sourceId: String,
        isAnime: Boolean,
        page: Int,
        parameters: Map<String, Any?>? = null,
    ): Map<String, Any?> {
        val token = parameters?.get("token") as? String
        val job = scope.async {
            val m = media(context, sourceId, isAnime)
            m.parameters = parameters
            val res = m.getLatestUpdates(page)
            mapOf("list" to res.animes.map { it.toMap() }, "hasNextPage" to res.hasNextPage)
        }
        if (token != null) activeRequests[token] = job
        return try {
            runBlocking { job.await() }
        } finally {
            if (token != null) activeRequests.remove(token)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun aniyomiSearch(
        context: Context,
        sourceId: String,
        isAnime: Boolean,
        query: String,
        page: Int,
        filters: List<Any?>? = null,
        parameters: Map<String, Any?>? = null,
    ): Map<String, Any?> {
        val token = parameters?.get("token") as? String
        val job = scope.async {
            val m = media(context, sourceId, isAnime)
            val combinedParams = parameters?.toMutableMap() ?: mutableMapOf()
            if (filters != null) {
                combinedParams["filters"] = filters
            }
            m.parameters = combinedParams
            val res = m.getSearchResults(query, page)
            mapOf("list" to res.animes.map { it.toMap() }, "hasNextPage" to res.hasNextPage)
        }
        if (token != null) activeRequests[token] = job
        return try {
            runBlocking { job.await() }
        } finally {
            if (token != null) activeRequests.remove(token)
        }
    }

    @JvmStatic
    fun aniyomiGetFilterList(
        context: Context,
        sourceId: String,
        isAnime: Boolean
    ): List<Map<String, Any?>> {
        val m = media(context, sourceId, isAnime)
        return if (isAnime) {
            val source = (m as? com.anymex.runtimehost.aniyomi.AnimeSourceMethods)?.source ?: return emptyList()
            val filterList = try {
                source.getFilterList()
            } catch (e: Throwable) {
                Log.e("RuntimeBridge", "Error in getFilterList (Anime) for source $sourceId: ${e.message}", e)
                return emptyList()
            }
            filterList.list.map { serializeAnimeFilter(it) }
        } else {
            val source = (m as? com.anymex.runtimehost.aniyomi.MangaSourceMethods)?.source ?: return emptyList()
            val filterList = try {
                source.getFilterList()
            } catch (e: Throwable) {
                Log.e("RuntimeBridge", "Error in getFilterList (Manga) for source $sourceId: ${e.message}", e)
                return emptyList()
            }
            filterList.list.map { serializeMangaFilter(it) }
        }
    }

    private fun serializeAnimeFilter(filter: eu.kanade.tachiyomi.animesource.model.AnimeFilter<*>): Map<String, Any?> {
        val type = when (filter) {
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.Header -> "Header"
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.Separator -> "Separator"
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.CheckBox -> "CheckBox"
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.TriState -> "TriState"
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.Select<*> -> "Select"
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.Group<*> -> "Group"
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.Sort -> "Sort"
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.Text -> "Text"
            else -> "Unknown"
        }

        val state: Any? = when (filter) {
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.Group<*> -> {
                filter.state.map { serializeAnimeFilter(it as eu.kanade.tachiyomi.animesource.model.AnimeFilter<*>) }
            }
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.Sort -> {
                mapOf("index" to filter.state?.index, "ascending" to filter.state?.ascending)
            }
            else -> filter.state
        }

        val values: List<String>? = when (filter) {
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.Select<*> -> {
                filter.values.map { it.toString() }
            }
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.Sort -> {
                filter.values.toList()
            }
            else -> null
        }

        return mapOf(
            "name" to filter.name,
            "type" to type,
            "state" to state,
            "values" to values
        )
    }

    private fun serializeMangaFilter(filter: eu.kanade.tachiyomi.source.model.Filter<*>): Map<String, Any?> {
        val type = when (filter) {
            is eu.kanade.tachiyomi.source.model.Filter.Header -> "Header"
            is eu.kanade.tachiyomi.source.model.Filter.Separator -> "Separator"
            is eu.kanade.tachiyomi.source.model.Filter.CheckBox -> "CheckBox"
            is eu.kanade.tachiyomi.source.model.Filter.TriState -> "TriState"
            is eu.kanade.tachiyomi.source.model.Filter.Select<*> -> "Select"
            is eu.kanade.tachiyomi.source.model.Filter.Group<*> -> "Group"
            is eu.kanade.tachiyomi.source.model.Filter.Sort -> "Sort"
            is eu.kanade.tachiyomi.source.model.Filter.Text -> "Text"
            else -> "Unknown"
        }

        val state: Any? = when (filter) {
            is eu.kanade.tachiyomi.source.model.Filter.Group<*> -> {
                filter.state.map { serializeMangaFilter(it as eu.kanade.tachiyomi.source.model.Filter<*>) }
            }
            is eu.kanade.tachiyomi.source.model.Filter.Sort -> {
                mapOf("index" to filter.state?.index, "ascending" to filter.state?.ascending)
            }
            else -> filter.state
        }

        val values: List<String>? = when (filter) {
            is eu.kanade.tachiyomi.source.model.Filter.Select<*> -> {
                filter.values.map { it.toString() }
            }
            is eu.kanade.tachiyomi.source.model.Filter.Sort -> {
                filter.values.toList()
            }
            else -> null
        }

        return mapOf(
            "name" to filter.name,
            "type" to type,
            "state" to state,
            "values" to values
        )
    }

    @JvmStatic
    @JvmOverloads
    fun aniyomiGetDetail(
        context: Context,
        sourceId: String,
        isAnime: Boolean,
        mediaMap: Map<String, Any?>,
        parameters: Map<String, Any?>? = null,
    ): Map<String, Any?> {
        val token = parameters?.get("token") as? String
        val job = scope.async {
            val m = media(context, sourceId, isAnime)
            m.parameters = parameters
            val rawUrl = mediaMap["url"] as? String ?: ""
            val anime = SAnime.create().apply {
                title = mediaMap["title"] as? String ?: ""
                url = normalizeMediaUrl(rawUrl, m.baseUrl)
                thumbnail_url = (mediaMap["thumbnail_url"] as? String)?.takeIf { it.isNotBlank() }?.normalizeUrl()
                description = mediaMap["description"] as? String
                artist = mediaMap["artist"] as? String
                author = mediaMap["author"] as? String
                genre = mediaMap["genre"] as? String
            }
            
            val details = m.getDetails(anime)
            val eps = if (isAnime) m.getEpisodeList(anime) else m.getChapterList(anime)

            mapOf(
                "title" to anime.title,
                "url" to anime.url,
                "cover" to anime.thumbnail_url,
                "artist" to details.artist,
                "author" to details.author,
                "description" to details.description,
                "genre" to details.getGenres(),
                "status" to details.status,
                "episodes" to eps.map {
                    val parsed = parseEpisodeInfoFromName(it.name)
                    val seasonNum = parsed.second
                    val epNum = if (it.episode_number != -1f) {
                        it.episode_number
                    } else if (parsed.first >= 0f) {
                        parsed.first
                    } else if (it.name.contains("oneshot", ignoreCase = true)) {
                        1f
                    } else {
                        -1f
                    }
                    
                    mapOf(
                        "name" to it.name,
                        "url" to it.url,
                        "date_upload" to it.date_upload,
                        "episode_number" to epNum,
                        "season" to seasonNum,
                        "scanlator" to it.scanlator,
                        "description" to it.summary,
                        "memo" to it.summary
                    )
                }
            )
        }
        if (token != null) activeRequests[token] = job
        return try {
            runBlocking { job.await() }
        } finally {
            if (token != null) activeRequests.remove(token)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun aniyomiGetVideoList(
        context: Context,
        sourceId: String,
        isAnime: Boolean,
        episodeMap: Map<String, Any?>,
        parameters: Map<String, Any?>? = null,
    ): List<Map<String, Any?>> {
        val token = parameters?.get("token") as? String
        val job = scope.async {
            val m = media(context, sourceId, isAnime)
            m.parameters = parameters
            val rawEpisodeUrl = episodeMap["url"] as? String ?: ""
            val ep = SEpisode.create().apply {
                name = episodeMap["name"] as? String ?: ""
                url = normalizeMediaUrl(rawEpisodeUrl, m.baseUrl)
                episode_number = (episodeMap["episode_number"] as? Number)?.toFloat() ?: 0f
                scanlator = episodeMap["scanlator"] as? String
            }
            m.getVideoList(ep).map { videoToMap(it) }
        }
        if (token != null) activeRequests[token] = job
        return try {
            runBlocking { job.await() }
        } finally {
            if (token != null) activeRequests.remove(token)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun aniyomiStopHttpServer(
        context: Context,
        sourceId: String,
        isAnime: Boolean,
    ): Boolean {
        if (!isAnime) return false
        return try {
            val m = media(context, sourceId, isAnime)
            val httpSource = m.getHttpSource() as? AnimeHttpSource ?: return false
            val server = httpSource.server ?: return false
            if (server.isRunning()) {
                server.stop()
                Log.d("RuntimeBridge", "Successfully stopped HTTP server for source: $sourceId")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("RuntimeBridge", "Failed to stop HTTP server for source: $sourceId", e)
            false
        }
    }

    @JvmStatic
    @JvmOverloads
    fun aniyomiGetImageBytes(
        context: Context,
        sourceId: String,
        isAnime: Boolean,
        url: String,
    ): ByteArray? {
        return try {
            val m = media(context, sourceId, isAnime)
            val httpSource = m.getHttpSource()
            val client = when (httpSource) {
                is HttpSource -> httpSource.client
                is AnimeHttpSource -> httpSource.client
                else -> Injekt.get<NetworkHelper>().client
            }
            val headers = when (httpSource) {
                is HttpSource -> httpSource.headers
                is AnimeHttpSource -> httpSource.headers
                else -> okhttp3.Headers.Builder().build()
            }
            val request = okhttp3.Request.Builder()
                .url(url)
                .headers(headers)
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                Log.e("RuntimeBridge", "Failed to download image: HTTP ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e("RuntimeBridge", "Failed to get image bytes for url $url", e)
            null
        }
    }

    @JvmStatic
    @JvmOverloads
    fun aniyomiGetPageList(
        context: Context,
        sourceId: String,
        isAnime: Boolean,
        chapterMap: Map<String, Any?>,
        parameters: Map<String, Any?>? = null,
    ): List<Map<String, Any?>> {
        val token = parameters?.get("token") as? String
        val job = scope.async {
            val m = media(context, sourceId, isAnime)
            m.parameters = parameters
            val rawChapterUrl = chapterMap["url"] as? String ?: ""
            val chapter = SChapter.create().apply {
                name = chapterMap["name"] as? String ?: ""
                url = normalizeMediaUrl(rawChapterUrl, m.baseUrl)
                val rawMemo = (chapterMap["memo"] as? String)
                    ?: (chapterMap["description"] as? String)
                    ?: (chapterMap["extra"] as? String)
                if (!rawMemo.isNullOrBlank()) {
                    try {
                        memo = Json.parseToJsonElement(rawMemo) as? JsonObject
                    } catch (_: Exception) {}
                }
            }
            
            val pages = m.getPageList(chapter)
            val httpSource = m.getHttpSource() as? HttpSource

            val sourceName = httpSource?.name?.lowercase() ?: ""
            val sourceClassName = httpSource?.javaClass?.name?.lowercase() ?: ""
            val isBypassed = sourceName.contains("mangadex") ||
                    sourceClassName.contains("mangadex") ||
                    sourceId.equals("mangadex", ignoreCase = true)
            val isWhitelisted = sourceName.contains("mangafire") ||
                    sourceClassName.contains("mangafire") ||
                    sourceId.equals("mangafire", ignoreCase = true)

            val overridesClient = try {
                val networkHelper = uy.kohesive.injekt.Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
                httpSource?.client !== networkHelper.client
            } catch (e: Exception) {
                false
            }

            val hasCustomGetImage = if (httpSource != null) {
                try {
                    val method = httpSource.javaClass.getMethod("getImage", eu.kanade.tachiyomi.source.model.Page::class.java, kotlin.coroutines.Continuation::class.java)
                    method.declaringClass != HttpSource::class.java
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }

            val useProxy = !isBypassed && (isWhitelisted || hasCustomGetImage || overridesClient)
            Log.i("RuntimeBridge", "Source '${sourceId}': isBypassed=$isBypassed, isWhitelisted=$isWhitelisted, hasCustomGetImage=$hasCustomGetImage, overridesClient=$overridesClient -> useProxy=$useProxy")
            val proxyPort = if (useProxy) MangaImageProxy.start() else 0

            pages.map { page ->
                val imageUrl = try {
                    if (httpSource != null) {
                        httpSource.imageRequest(page).url.toString()
                    } else {
                        page.imageUrl ?: ""
                    }
                } catch (e: Exception) {
                    Log.e("RuntimeBridge", "Error getting imageRequest URL: ${e.message}", e)
                    page.imageUrl ?: ""
                }
                
                if (useProxy && proxyPort > 0) {
                    val proxyUrl = if (imageUrl.isNotEmpty() || page.url.isNotEmpty()) {
                        "http://127.0.0.1:$proxyPort/image?sourceId=${java.net.URLEncoder.encode(sourceId, "UTF-8")}&imageUrl=${java.net.URLEncoder.encode(imageUrl, "UTF-8")}&pageUrl=${java.net.URLEncoder.encode(page.url ?: "", "UTF-8")}&pageNumber=${page.index}"
                    } else {
                        imageUrl
                    }
                    mapOf("url" to proxyUrl, "headers" to emptyMap<String, String>())
                } else {
                    val headersMap = try {
                        if (httpSource != null) {
                            val reqHeaders = httpSource.imageRequest(page).headers
                            val map = mutableMapOf<String, String>()
                            for (i in 0 until reqHeaders.size) {
                                map[reqHeaders.name(i)] = reqHeaders.value(i)
                            }
                            map
                        } else {
                            emptyMap()
                        }
                    } catch (e: Exception) {
                        Log.e("RuntimeBridge", "Error getting imageRequest headers: ${e.message}", e)
                        emptyMap()
                    }
                    mapOf("url" to imageUrl, "headers" to headersMap)
                }
            }
        }
        if (token != null) activeRequests[token] = job
        return try {
            runBlocking { job.await() }
        } finally {
            if (token != null) activeRequests.remove(token)
        }
    }

    private data class PrefHandlers(
        val pref: androidx.preference.Preference,
        val click: androidx.preference.Preference.OnPreferenceClickListener?,
        val change: androidx.preference.Preference.OnPreferenceChangeListener?
    )

    private val sourcePreferences = mutableMapOf<String, MutableMap<String, PrefHandlers>>()

    @android.annotation.SuppressLint("RestrictedApi")
    fun aniyomiGetPreference(
        context: Context,
        sourceId: String,
        isAnime: Boolean,
    ): List<Map<String, Any?>> = runBlocking {
        sourcePreferences.remove(sourceId)
        
        val prefManager = androidx.preference.PreferenceManager(context)
        prefManager.sharedPreferencesName = "source_$sourceId"
        
        val screen: androidx.preference.PreferenceScreen = try {
            prefManager.createPreferenceScreen(context)
        } catch (t: Throwable) {
            android.util.Log.w("RuntimeBridge", "createPreferenceScreen failed ($t), using reflection fallback")
            try {
                val constructor = androidx.preference.PreferenceScreen::class.java.getDeclaredConstructor(
                    Context::class.java,
                    android.util.AttributeSet::class.java
                )
                constructor.isAccessible = true
                val ps = constructor.newInstance(context, null)
                try {
                    val onAttached = androidx.preference.Preference::class.java.getDeclaredMethod(
                        "onAttachedToHierarchy",
                        androidx.preference.PreferenceManager::class.java
                    )
                    onAttached.isAccessible = true
                    onAttached.invoke(ps, prefManager)
                } catch (_: Throwable) {}
                ps
            } catch (t2: Throwable) {
                android.util.Log.e("RuntimeBridge", "Failed to construct PreferenceScreen: $t2", t2)
                return@runBlocking emptyList<Map<String, Any?>>()
            }
        }
        
        try {
            media(context, sourceId, isAnime).setupPreferenceScreen(screen)
        } catch (e: com.anymex.runtimehost.aniyomi.NoPreferenceScreenException) {
            return@runBlocking emptyList<Map<String, Any?>>()
        } catch (t: Throwable) {
            android.util.Log.w("RuntimeBridge", "setupPreferenceScreen failed for source $sourceId: $t")
            if (screen.preferenceCount == 0) {
                return@runBlocking emptyList<Map<String, Any?>>()
            }
        }

        val list = mutableListOf<Map<String, Any?>>()
        val store = sourcePreferences.getOrPut(sourceId) { mutableMapOf() }

        fun walk(group: androidx.preference.PreferenceGroup) {
            for (i in 0 until group.preferenceCount) {
                val p = group.getPreference(i)
                store[p.key] = PrefHandlers(p, p.onPreferenceClickListener, p.onPreferenceChangeListener)

                val map = mutableMapOf(
                    "key" to p.key,
                    "title" to p.title?.toString(),
                    "summary" to p.summary?.toString(),
                    "enabled" to p.isEnabled,
                    "type" to when (p) {
                        is androidx.preference.ListPreference -> "list"
                        is androidx.preference.MultiSelectListPreference -> "multi_select"
                        is androidx.preference.SwitchPreferenceCompat -> "switch"
                        is androidx.preference.EditTextPreference -> "text"
                        is androidx.preference.CheckBoxPreference -> "checkbox"
                        else -> "other"
                    },
                    "value" to when (p) {
                        is androidx.preference.ListPreference -> p.value
                        is androidx.preference.MultiSelectListPreference -> p.values.toList()
                        is androidx.preference.SwitchPreferenceCompat -> p.isChecked
                        is androidx.preference.EditTextPreference -> p.text
                        is androidx.preference.CheckBoxPreference -> p.isChecked
                        else -> null
                    }
                )

                if (p is androidx.preference.ListPreference) {
                    map["entries"] = p.entries?.map { it.toString() }
                    map["entryValues"] = p.entryValues?.map { it.toString() }
                } else if (p is androidx.preference.MultiSelectListPreference) {
                    map["entries"] = p.entries?.map { it.toString() }
                    map["entryValues"] = p.entryValues?.map { it.toString() }
                }

                list += map
                if (p is androidx.preference.PreferenceCategory) walk(p)
            }
        }

        walk(screen)
        list
    }

    fun aniyomiSavePreference(
        context: Context,
        sourceId: String,
        key: String,
        action: String?,
        value: Any?,
    ): Boolean = runBlocking(Dispatchers.Main) {
        val isAnime = aniyomiIsAnimeRegistry[sourceId] ?: true
        val handler = sourcePreferences[sourceId]?.get(key) ?: return@runBlocking false
        val pref = handler.pref
        
        val actualAction = action ?: "change"

        val actualValue = if (pref is androidx.preference.MultiSelectListPreference && value is List<*>) {
            value.filterIsInstance<String>().toSet()
        } else {
            value
        }

        if (actualAction == "click") {
            handler.click?.onPreferenceClick(pref)
        } else {
            handler.change?.onPreferenceChange(pref, actualValue)
        }

        val prefs = context.getSharedPreferences("source_$sourceId", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        when (pref) {
            is androidx.preference.SwitchPreferenceCompat -> {
                val b = value as Boolean
                pref.isChecked = b
                editor.putBoolean(key, b)
            }
            is androidx.preference.ListPreference -> {
                val s = value as String
                pref.value = s
                editor.putString(key, s)
            }
            is androidx.preference.EditTextPreference -> {
                val s = value as String
                pref.text = s
                editor.putString(key, s)
            }
            is androidx.preference.MultiSelectListPreference -> {
                val newSet = when (value) {
                    is List<*> -> value.filterIsInstance<String>().toSet()
                    is Set<*> -> value.filterIsInstance<String>().toSet()
                    else -> emptySet()
                }
                pref.values = newSet.toMutableSet()
                editor.putStringSet(key, newSet)
            }
            is androidx.preference.CheckBoxPreference -> {
                val b = value as Boolean
                pref.isChecked = b
                editor.putBoolean(key, b)
            }
        }
        editor.apply()

        true
    }

    fun csLoadPlugin(context: Context, path: String): Boolean {
        val file = File(path)
        if (!file.exists()) {
            Log.e(TAG, "Plugin file not found: $path")
            return false
        }
        
        return runBlocking {
            val success = PluginManager.loadPlugin(context, file)
            if (success) {
                val pluginData = PluginManager.getPluginsOnline()
                APIHolder.apis.forEach { provider ->
                    if (provider.sourcePlugin == path || provider.sourcePlugin?.contains(file.name) == true) {
                        val data = pluginData.firstOrNull { it.filePath == provider.sourcePlugin }
                        val pluginInstance = PluginManager.plugins[provider.sourcePlugin]
                        val hasSettings = (pluginInstance as? com.lagradost.cloudstream3.plugins.Plugin)?.openSettings != null
                        csPathRegistry[provider.name] = path
                        csMetadataRegistry[provider.name] = ProviderMetadata(
                            id = provider.name,
                            name = provider.name,
                            mainUrl = provider.mainUrl,
                            lang = provider.lang,
                            sourcePlugin = provider.sourcePlugin,
                            iconUrl = data?.iconUrl,
                            internalName = data?.internalName,
                            hasSettings = hasSettings
                        )
                        Log.d(TAG, "Registered CloudStream Provider: ${provider.name}")
                    }
                }
            }
            success
        }
    }

    fun csUnloadPlugin(apiName: String): Boolean {
        if (apiName.isBlank()) return false
        
        val provider = APIHolder.apis.find { it.name.equals(apiName, ignoreCase = true) }
        val internalName = provider?.sourcePlugin ?: csMetadataRegistry[apiName]?.mainUrl ?: apiName
        
        Log.i(TAG, "Attempting to unload plugin: $apiName (Internal Path/ID: $internalName)")

        val removedCount = APIHolder.apis.count { 
            it.name.equals(apiName, ignoreCase = true) || 
            (it.sourcePlugin != null && it.sourcePlugin!!.endsWith(internalName, ignoreCase = true)) 
        }

        APIHolder.apis.removeIf { 
            it.name.equals(apiName, ignoreCase = true) || 
            (it.sourcePlugin != null && it.sourcePlugin!!.endsWith(internalName, ignoreCase = true)) 
        }

        csMetadataRegistry.remove(apiName)
        
        val fileName = internalName.substringAfterLast("/")
        if (fileName.length > 3) {
            val extraRemoved = APIHolder.apis.count { it.sourcePlugin?.endsWith(fileName, ignoreCase = true) == true }
            APIHolder.apis.removeIf { it.sourcePlugin?.endsWith(fileName, ignoreCase = true) == true }
            Log.d(TAG, "Extra removal check for '$fileName' hit $extraRemoved providers")
        }

        PluginManager.unloadPlugin(internalName)
        Log.i(TAG, "Unloaded $removedCount providers for plugin: $apiName")
        return true
    }

    fun csGetRegisteredProviders(): List<Map<String, Any?>> {
        return csMetadataRegistry.values.map { provider ->
            mapOf(
                "id" to provider.name,
                "name" to provider.name,
                "url" to provider.mainUrl,
                "language" to provider.lang,
                "plugin" to (provider.sourcePlugin ?: ""),
                "iconUrl" to (provider.iconUrl ?: ""),
                "internalName" to (provider.internalName ?: provider.name),
                "itemType" to 1,
                "hasSettings" to provider.hasSettings,
            )
        }
    }

    fun resolveActivity(context: Context): Activity? {
        var ctx: Context? = context
        while (ctx != null) {
            if (ctx is Activity) return ctx
            ctx = if (ctx is ContextWrapper) ctx.baseContext else null
        }
        val cached = lastKnownActivity?.get()
        if (cached is Activity) return cached
        return null
    }

    private fun isSubclassOfAppCompatActivity(cls: Class<*>): Boolean {
        var c: Class<*>? = cls
        while (c != null) {
            if (c.name == "androidx.appcompat.app.AppCompatActivity") return true
            c = c.superclass
        }
        return false
    }

    fun resolveAppCompatActivity(context: Context): androidx.appcompat.app.AppCompatActivity? {
        val act = resolveActivity(context)
        if (act != null && isSubclassOfAppCompatActivity(act.javaClass)) {
            return act as? androidx.appcompat.app.AppCompatActivity
        }
        return null
    }

    fun csOpenSettings(context: Context, pluginName: String): Boolean {
        val meta = csMetadataRegistry[pluginName] ?: return false
        val pluginInstance = PluginManager.plugins[meta.sourcePlugin]
        val plugin = pluginInstance as? com.lagradost.cloudstream3.plugins.Plugin ?: return false
        val openSettingsFn = plugin.openSettings ?: return false

        val act = resolveAppCompatActivity(context)
            ?: com.lagradost.cloudstream3.plugins.AppCompatActivityWrapper(resolveActivity(context) ?: context)

        com.lagradost.cloudstream3.MainActivity.activity = act
        com.lagradost.cloudstream3.MainActivity.context = act

        try {
            val styleClass = Class.forName("com.google.android.material.R\$style")
            val field = styleClass.getField("Theme_MaterialComponents_DayNight_NoActionBar")
            val themeId = field.getInt(null)
            act.setTheme(themeId)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to setTheme programmatically via reflection", e)
        }

        openSettingsFn(act)
        return true
    }


    @JvmStatic
    @JvmOverloads
    fun csSearch(
        context: Context,
        query: String,
        apiName: String?,
        page: Int,
        parameters: Map<String, Any?>? = null,
    ): Map<String, Any?> {
        val token = parameters?.get("token") as? String
        val job = scope.async {
            if (apiName != null) {
                val provider = APIHolder.apis.find { it.name.equals(apiName, ignoreCase = true) }
                    ?: return@async mapOf("list" to emptyList<Any>(), "hasNextPage" to false)
                com.anymex.runtimehost.cloudstream.CloudStreamSourceMethods(provider).search(query, page)
            } else {
                val allItems = APIHolder.apis.flatMap { provider ->
                    @Suppress("UNCHECKED_CAST")
                    com.anymex.runtimehost.cloudstream.CloudStreamSourceMethods(provider)
                        .search(query, page)["list"] as? List<Map<String, Any?>> ?: emptyList()
                }
                mapOf("list" to allItems, "hasNextPage" to false)
            }
        }
        if (token != null) activeRequests[token] = job
        return try {
            runBlocking { job.await() }
        } finally {
            if (token != null) activeRequests.remove(token)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun csGetDetail(
        context: Context,
        apiName: String,
        url: String,
        parameters: Map<String, Any?>? = null,
    ): Map<String, Any?> {
        val token = parameters?.get("token") as? String
        val job = scope.async {
            try {
                val provider = APIHolder.apis.find { it.name.equals(apiName, ignoreCase = true) }
                    ?: return@async mapOf("title" to null, "url" to url, "error" to "Provider not found")
                com.anymex.runtimehost.cloudstream.CloudStreamSourceMethods(provider).getDetails(url) ?: emptyMap()
            } catch (e: Exception) {
                Log.e(TAG, "csGetDetail failed: ${e.message}")
                mapOf("title" to null, "url" to url, "error" to e.message)
            }
        }
        if (token != null) activeRequests[token] = job
        return try {
            runBlocking { job.await() }
        } finally {
            if (token != null) activeRequests.remove(token)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun csGetVideoList(
        context: Context,
        apiName: String,
        url: String,
        parameters: Map<String, Any?>? = null,
    ): List<Map<String, Any?>> {
        val token = parameters?.get("token") as? String
        val job = scope.async {
            val provider = APIHolder.apis.find { it.name.equals(apiName, ignoreCase = true) }
                ?: throw IllegalArgumentException("Provider '$apiName' not found")
            com.anymex.runtimehost.cloudstream.CloudStreamSourceMethods(provider).loadLinks(url)
        }
        if (token != null) activeRequests[token] = job
        return try {
            runBlocking { job.await() }
        } finally {
            if (token != null) activeRequests.remove(token)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun csGetVideoListStream(
        context: Context,
        apiName: String,
        url: String,
        onLinkFound: (Map<String, Any?>) -> Unit,
        parameters: Map<String, Any?>? = null,
    ) {
        val token = parameters?.get("token") as? String
        val extractionJob = kotlinx.coroutines.Job()
        if (token != null) activeRequests[token] = extractionJob

        try {
            runBlocking(extractionJob + kotlinx.coroutines.Dispatchers.IO) {
                val provider = APIHolder.apis.find { it.name.equals(apiName, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Provider '$apiName' not found")
                
                com.anymex.runtimehost.cloudstream.CloudStreamSourceMethods(provider)
                    .loadLinksStream(url, onLinkFound)
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                Log.e("RuntimeBridge", "Extraction error: ${e.message}")
            }
        } finally {
            if (token != null) activeRequests.remove(token)
        }
        
        System.gc()
    }

    @JvmStatic
    @JvmOverloads
    fun kotatsuLoadExtensions(context: Context, folderPath: String?): List<Map<String, Any?>> {
        return runBlocking {
            com.anymex.runtimehost.kotatsu.KotatsuExtensionLoader.loadExtensions(context, folderPath)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun kotatsuGetPopular(context: Context, sourceId: String, page: Int): Map<String, Any?> {
        return runBlocking {
            com.anymex.runtimehost.kotatsu.KotatsuExtensionLoader.getPopular(sourceId, page)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun kotatsuGetLatestUpdates(context: Context, sourceId: String, page: Int): Map<String, Any?> {
        return runBlocking {
            com.anymex.runtimehost.kotatsu.KotatsuExtensionLoader.getLatestUpdates(sourceId, page)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun kotatsuSearch(context: Context, sourceId: String, query: String, page: Int): Map<String, Any?> {
        return runBlocking {
            com.anymex.runtimehost.kotatsu.KotatsuExtensionLoader.search(sourceId, query, page)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun kotatsuGetDetail(context: Context, sourceId: String, url: String, title: String, cover: String): Map<String, Any?> {
        return runBlocking {
            com.anymex.runtimehost.kotatsu.KotatsuExtensionLoader.getDetails(sourceId, url, title, cover)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun kotatsuGetPageList(context: Context, sourceId: String, url: String, name: String): List<Map<String, Any?>> {
        return runBlocking {
            com.anymex.runtimehost.kotatsu.KotatsuExtensionLoader.getPageList(sourceId, url, name)
        }
    }

    fun getAvailableAnimeExtensions(context: Context, repos: List<String>): List<Map<String, Any?>> = runBlocking {
        extensionManager(context).findAvailableAnimeExtensions(repos).map { ext ->
            mapOf(
                "name" to ext.name,
                "pkgName" to ext.pkgName,
                "version" to ext.versionName,
                "code" to ext.versionCode,
                "lang" to ext.lang,
                "isNsfw" to ext.isNsfw,
                "iconUrl" to ext.iconUrl,
                "apkUrl" to "${ext.repository.removeSuffix("index.min.json")}/apk/${ext.apkName}",
                "repository" to ext.repository,
                "isAnime" to true,
            )
        }
    }

    fun getAvailableMangaExtensions(context: Context, repos: List<String>): List<Map<String, Any?>> = runBlocking {
        extensionManager(context).findAvailableMangaExtensions(repos).map { ext ->
            mapOf(
                "name" to ext.name,
                "pkgName" to ext.pkgName,
                "version" to ext.versionName,
                "code" to ext.versionCode,
                "lang" to ext.lang,
                "isNsfw" to ext.isNsfw,
                "iconUrl" to ext.iconUrl,
                "apkUrl" to "${ext.repository.removeSuffix("index.min.json")}/apk/${ext.apkName}",
                "repository" to ext.repository,
                "isAnime" to false,
            )
        }
    }

    /**
     * Downloads an extension APK into the private extension folder that the loader scans.
     */
    @JvmStatic
    @JvmOverloads
    fun installAniyomiExtension(
        context: Context,
        apkUrl: String,
        isAnime: Boolean,
        fileName: String? = null,
    ): Boolean = runBlocking(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, if (isAnime) "exts" else "exts_manga")
            if (!dir.exists()) dir.mkdirs()
            val targetName = (fileName ?: apkUrl.substringAfterLast('/'))
                .substringAfterLast('/')
                .let { if (it.endsWith(".apk")) it else "$it.apk" }
            val target = File(dir, targetName)

            val client = try {
                Injekt.get<OkHttpClient>()
            } catch (t: Throwable) {
                OkHttpClient()
            }
            val request = okhttp3.Request.Builder().url(apkUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Extension download failed: HTTP ${response.code} for $apkUrl")
                    return@runBlocking false
                }
                val body = response.body ?: return@runBlocking false
                val tmp = File(dir, "$targetName.tmp")
                body.byteStream().use { input ->
                    tmp.outputStream().use { out -> input.copyTo(out) }
                }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return@runBlocking false
                }
                target.setReadOnly()
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to install extension from $apkUrl", t)
            false
        }
    }

    @JvmStatic
    @JvmOverloads
    fun uninstallAniyomiExtension(context: Context, pkgName: String, isAnime: Boolean): Boolean = runBlocking(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, if (isAnime) "exts" else "exts_manga")
            val apks = dir.listFiles { file -> file.isFile && file.extension == "apk" } ?: return@runBlocking false
            var removed = false
            for (apk in apks) {
                val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
                if (archive?.packageName == pkgName) {
                    apk.setWritable(true)
                    removed = apk.delete() || removed
                }
            }
            removed
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to uninstall extension $pkgName", t)
            false
        }
    }

    private fun extensionManager(context: Context): AniyomiExtensionManager {
        if (extensionManager == null) initialize(context)
        return extensionManager!!
    }

    private val sourceMethodsCache = java.util.concurrent.ConcurrentHashMap<String, AniyomiSourceMethods>()

    private fun media(context: Context, sourceId: String, isAnime: Boolean): AniyomiSourceMethods {
        val key = "$sourceId-$isAnime"
        var methods = sourceMethodsCache[key]
        if (methods == null) {
            methods = if (isAnime) AnimeSourceMethods(sourceId) else MangaSourceMethods(sourceId)
            sourceMethodsCache[key] = methods
        }
        return methods
    }

    private fun okhttp3.Headers?.toMap(): Map<String, String> = this?.names()?.associateWith { name -> get(name).orEmpty() }.orEmpty()

    private fun SAnime.toMap(): Map<String, Any?> = mapOf(
        "url" to url,
        "title" to title,
        "artist" to artist,
        "author" to author,
        "description" to description,
        "genre" to getGenres(),
        "status" to status,
        "thumbnail_url" to thumbnail_url,
        "background_url" to background_url,
        "update_strategy" to update_strategy.name,
        "fetch_type" to fetch_type.name,
        "season_number" to season_number,
        "initialized" to initialized
    )

    private fun videoToMap(it: Video): Map<String, Any?> = mapOf(
        "title" to it.videoTitle,
        "url" to it.videoUrl,
        "quality" to (it.videoTitle.takeIf { t -> t.isNotBlank() } ?: it.resolution?.let { "${it}p" } ?: "Default"),
        "resolution" to it.resolution,
        "bitrate" to it.bitrate,
        "headers" to it.headers?.toMap(),
        "preferred" to it.preferred,
        "subtitles" to it.subtitleTracks.map { t -> mapOf("file" to t.url, "label" to t.lang) },
        "audios" to it.audioTracks.map { t -> mapOf("file" to t.url, "label" to t.lang) },
        "timestamps" to it.timestamps.map { ts ->
            mapOf(
                "start" to ts.start,
                "end" to ts.end,
                "name" to ts.name,
                "type" to ts.type.name
            )
        },
        "mpvArgs" to it.mpvArgs.toMap(),
        "ffmpegStreamArgs" to it.ffmpegStreamArgs.toMap(),
        "ffmpegVideoArgs" to it.ffmpegVideoArgs.toMap(),
        "internalData" to it.internalData,
        "initialized" to it.initialized
    )

    private fun parseEpisodeInfoFromName(name: String): Pair<Float, Int?> {
        val seasonRegex = Regex("""(?i)\b(?:s|season)\s*(\d+)""")
        val seasonMatch = seasonRegex.find(name)
        val season = seasonMatch?.groupValues?.get(1)?.toIntOrNull()

        val cleanName = if (seasonMatch != null) name.replace(seasonMatch.value, "") else name

        val prefixRegex = Regex("""(?i)(?:chapter|ch\.|ch|ep\.|ep|episode|e)\s*(\d+(\.\d+)?)""")
        val prefixMatch = prefixRegex.find(cleanName)
        if (prefixMatch != null) {
            val epVal = prefixMatch.groupValues[1].toFloatOrNull() ?: -1f
            return Pair(epVal, season)
        }

        val fallbackRegex = Regex("""(\d+(\.\d+)?)""")
        val epVal = fallbackRegex.find(cleanName)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
        return Pair(epVal, season)
    }
}
