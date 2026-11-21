package ani.saikou.extensions

import android.content.Context
import android.util.Log
import ani.saikou.App
import com.anymex.runtimehost.RuntimeBridge
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single entry point to the AnyMeX extension runtime that is bundled with Saikou.
 *
 * The runtime supports four extension ecosystems:
 *  - Aniyomi / Tachiyomi extension APKs (anime + manga)
 *  - CloudStream plugins (single-file dex plugins)
 *  - Kotatsu manga parser jars
 * plus everything those ecosystems expose (filters, preferences, direct video extraction,
 * image proxying for header/cookie protected pages and repository browsing).
 */
object ExtensionRuntime {

    private const val TAG = "ExtensionRuntime"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var initialized = false

    private var applicationContext: Context? = null

    @Volatile
    private var animeSourcesCache: List<AniyomiSource> = emptyList()

    @Volatile
    private var mangaSourcesCache: List<AniyomiSource> = emptyList()

    @Volatile
    private var cloudStreamCache: List<CloudStreamProvider> = emptyList()

    @Volatile
    private var kotatsuCache: List<KotatsuSource> = emptyList()

    val context: Context
        get() = applicationContext
            ?: App.currentContext()?.applicationContext
            ?: throw IllegalStateException("Extension runtime was not initialized")

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            applicationContext = app
            try {
                RuntimeBridge.initialize(app)
                initialized = true
                preload()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize the extension runtime", t)
            }
        }
    }

    /** Loads every installed extension off the main thread. Safe to call repeatedly. */
    fun preload() {
        val ctx = applicationContext ?: return
        scope.launch {
            runCatching { refreshAniyomiSources() }
            runCatching { refreshCloudStreamProviders() }
            runCatching { refreshKotatsuSources() }
        }
        ExtensionStore.ensureDefaults(ctx)
    }

    /** Cancels an in-flight runtime request previously started with a token. */
    fun cancelRequest(token: String): Boolean = runCatching { RuntimeBridge.cancelRequest(token) }
        .getOrDefault(false)

    fun shutdown() {
        runCatching { RuntimeBridge.shutdown() }
        initialized = false
        animeSourcesCache = emptyList()
        mangaSourcesCache = emptyList()
        cloudStreamCache = emptyList()
        kotatsuCache = emptyList()
    }

    // ------------------------------------------------------------------ Aniyomi / Tachiyomi

    suspend fun refreshAniyomiSources() {
        val ctx = context
        val anime = withContext(Dispatchers.IO) {
            RuntimeBridge.getInstalledAnimeExtensions(ctx).map { it.toAniyomiSource(true) }
        }
        val manga = withContext(Dispatchers.IO) {
            RuntimeBridge.getInstalledMangaExtensions(ctx).map { it.toAniyomiSource(false) }
        }
        animeSourcesCache = anime
        mangaSourcesCache = manga
        extensionVersion = System.currentTimeMillis()
    }

    private fun isEnabled(key: String): Boolean = ExtensionStore.isSourceEnabled(context, key)

    fun animeSources(): List<AniyomiSource> = animeSourcesCache.filter { isEnabled(it.key) }

    fun mangaSources(): List<AniyomiSource> = mangaSourcesCache.filter { isEnabled(it.key) }

    fun allAnimeSources(): List<AniyomiSource> = animeSourcesCache

    fun allMangaSources(): List<AniyomiSource> = mangaSourcesCache

    suspend fun aniyomiSearch(
        sourceId: String,
        isAnime: Boolean,
        query: String,
        page: Int = 1,
        filters: List<Any?>? = null,
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        RuntimeBridge.aniyomiSearch(context, sourceId, isAnime, query, page, filters, null)
            .let { it["list"].asMapList() }
    }

    suspend fun aniyomiPopular(
        sourceId: String,
        isAnime: Boolean,
        page: Int = 1,
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        RuntimeBridge.aniyomiGetPopular(context, sourceId, isAnime, page, null)
            .let { it["list"].asMapList() }
    }

    suspend fun aniyomiLatest(
        sourceId: String,
        isAnime: Boolean,
        page: Int = 1,
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        RuntimeBridge.aniyomiGetLatestUpdates(context, sourceId, isAnime, page, null)
            .let { it["list"].asMapList() }
    }

    suspend fun aniyomiDetail(
        sourceId: String,
        isAnime: Boolean,
        url: String,
        title: String? = null,
        cover: String? = null,
        description: String? = null,
    ): Map<String, Any?> = withContext(Dispatchers.IO) {
        val media = mutableMapOf<String, Any?>(
            "url" to url,
            "title" to (title ?: ""),
        )
        if (!cover.isNullOrBlank()) media["thumbnail_url"] = cover
        if (!description.isNullOrBlank()) media["description"] = description
        RuntimeBridge.aniyomiGetDetail(context, sourceId, isAnime, media, null)
    }

    suspend fun aniyomiFilters(sourceId: String, isAnime: Boolean): List<Map<String, Any?>> =
        withContext(Dispatchers.IO) {
            RuntimeBridge.aniyomiGetFilterList(context, sourceId, isAnime)
        }

    suspend fun aniyomiVideos(
        sourceId: String,
        isAnime: Boolean,
        episodeUrl: String,
        episodeName: String,
        episodeNumber: Float,
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val episode = mutableMapOf<String, Any?>(
            "url" to episodeUrl,
            "name" to episodeName,
            "episode_number" to episodeNumber,
        )
        RuntimeBridge.aniyomiGetVideoList(context, sourceId, isAnime, episode, null)
    }

    suspend fun aniyomiPages(
        sourceId: String,
        chapterUrl: String,
        chapterName: String,
        memo: String? = null,
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val chapter = mutableMapOf<String, Any?>(
            "url" to chapterUrl,
            "name" to chapterName,
        )
        if (!memo.isNullOrBlank()) chapter["memo"] = memo
        RuntimeBridge.aniyomiGetPageList(context, sourceId, false, chapter, null)
    }

    suspend fun aniyomiImageBytes(sourceId: String, isAnime: Boolean, url: String): ByteArray? =
        withContext(Dispatchers.IO) {
            RuntimeBridge.aniyomiGetImageBytes(context, sourceId, isAnime, url)
        }

    suspend fun stopAniyomiHttpServer(sourceId: String): Boolean = withContext(Dispatchers.IO) {
        RuntimeBridge.aniyomiStopHttpServer(context, sourceId, true)
    }

    suspend fun aniyomiPreferences(sourceId: String, isAnime: Boolean): List<ExtensionPreference> =
        withContext(Dispatchers.IO) {
            RuntimeBridge.aniyomiGetPreference(context, sourceId, isAnime)
                .map { it.toExtensionPreference() }
        }

    suspend fun saveAniyomiPreference(
        sourceId: String,
        key: String,
        value: Any?,
        action: String? = "change",
    ): Boolean = withContext(Dispatchers.IO) {
        RuntimeBridge.aniyomiSavePreference(context, sourceId, key, action, value)
    }

    suspend fun availableExtensions(isAnime: Boolean): List<AvailableAniyomiExtension> =
        withContext(Dispatchers.IO) {
            val repos = ExtensionStore.repositories(context, isAnime)
            if (repos.isEmpty()) return@withContext emptyList()
            val raw = if (isAnime) {
                RuntimeBridge.getAvailableAnimeExtensions(context, repos)
            } else {
                RuntimeBridge.getAvailableMangaExtensions(context, repos)
            }
            raw.map { it.toAvailableExtension(isAnime) }
        }

    suspend fun installExtension(extension: AvailableAniyomiExtension): Boolean =
        withContext(Dispatchers.IO) {
            val installed = RuntimeBridge.installAniyomiExtension(
                context,
                extension.apkUrl,
                extension.isAnime,
                extension.pkgName,
            )
            if (installed) refreshAniyomiSources()
            installed
        }

    suspend fun installExtensionFromFile(file: File, isAnime: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, if (isAnime) "exts" else "exts_manga")
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, file.name)
            file.copyTo(target, overwrite = true)
            target.setReadOnly()
            refreshAniyomiSources()
            true
        }

    suspend fun uninstallExtension(pkgName: String, isAnime: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val removed = RuntimeBridge.uninstallAniyomiExtension(context, pkgName, isAnime)
            if (removed) refreshAniyomiSources()
            removed
        }

    // -------------------------------------------------------------------------- CloudStream

    suspend fun refreshCloudStreamProviders() {
        val providers = withContext(Dispatchers.IO) {
            RuntimeBridge.csGetRegisteredProviders().map { it.toCloudStreamProvider() }
        }
        cloudStreamCache = providers
    }

    fun cloudStreamProviders(): List<CloudStreamProvider> =
        cloudStreamCache.filter { isEnabled(it.key) }

    fun allCloudStreamProviders(): List<CloudStreamProvider> = cloudStreamCache

    suspend fun loadCloudStreamPlugin(path: String): Boolean = withContext(Dispatchers.IO) {
        val loaded = RuntimeBridge.csLoadPlugin(context, path)
        if (loaded) {
            ExtensionStore.markCloudStreamPlugin(context, path)
            refreshCloudStreamProviders()
        }
        loaded
    }

    suspend fun unloadCloudStreamPlugin(name: String): Boolean = withContext(Dispatchers.IO) {
        val unloaded = RuntimeBridge.csUnloadPlugin(name)
        if (unloaded) refreshCloudStreamProviders()
        unloaded
    }

    suspend fun openCloudStreamSettings(name: String): Boolean = withContext(Dispatchers.IO) {
        RuntimeBridge.csOpenSettings(context, name)
    }

    suspend fun cloudStreamSearch(
        apiName: String?,
        query: String,
        page: Int = 1,
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        RuntimeBridge.csSearch(context, query, apiName, page, null)
            .let { it["list"].asMapList() }
    }

    suspend fun cloudStreamDetail(apiName: String, url: String): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            RuntimeBridge.csGetDetail(context, apiName, url, null)
        }

    suspend fun cloudStreamVideos(apiName: String, url: String): List<Map<String, Any?>> =
        withContext(Dispatchers.IO) {
            RuntimeBridge.csGetVideoList(context, apiName, url, null)
        }

    /**
     * Streams links as the CloudStream provider resolves them instead of waiting for all of them.
     */
    suspend fun cloudStreamVideosStream(
        apiName: String,
        url: String,
        onLinkFound: (Map<String, Any?>) -> Unit,
    ) = withContext(Dispatchers.IO) {
        RuntimeBridge.csGetVideoListStream(context, apiName, url, onLinkFound, null)
    }

    // ------------------------------------------------------------------------------ Kotatsu

    suspend fun refreshKotatsuSources(): List<KotatsuSource> = withContext(Dispatchers.IO) {
        val folder = ExtensionStore.kotatsuFolder(context)
        val sources = RuntimeBridge.kotatsuLoadExtensions(context, folder.absolutePath)
            .map { it.toKotatsuSource() }
        kotatsuCache = sources
        sources
    }

    fun kotatsuSources(): List<KotatsuSource> = kotatsuCache.filter { isEnabled(it.key) }

    fun allKotatsuSources(): List<KotatsuSource> = kotatsuCache

    suspend fun installKotatsuPlugin(file: File): Boolean = withContext(Dispatchers.IO) {
        val folder = ExtensionStore.kotatsuFolder(context)
        file.copyTo(File(folder, file.name), overwrite = true)
        refreshKotatsuSources()
        true
    }

    suspend fun kotatsuSearch(sourceId: String, query: String, page: Int = 1): List<Map<String, Any?>> =
        withContext(Dispatchers.IO) {
            RuntimeBridge.kotatsuSearch(context, sourceId, query, page)
                .let { it["list"].asMapList() }
        }

    suspend fun kotatsuDetail(
        sourceId: String,
        url: String,
        title: String,
        cover: String,
    ): Map<String, Any?> = withContext(Dispatchers.IO) {
        RuntimeBridge.kotatsuGetDetail(context, sourceId, url, title, cover)
    }

    suspend fun kotatsuPages(
        sourceId: String,
        url: String,
        name: String,
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        RuntimeBridge.kotatsuGetPageList(context, sourceId, url, name)
    }

    suspend fun kotatsuPopular(sourceId: String, page: Int = 1): List<Map<String, Any?>> =
        withContext(Dispatchers.IO) {
            RuntimeBridge.kotatsuGetPopular(context, sourceId, page)
                .let { it["list"].asMapList() }
        }

    suspend fun kotatsuLatest(sourceId: String, page: Int = 1): List<Map<String, Any?>> =
        withContext(Dispatchers.IO) {
            RuntimeBridge.kotatsuGetLatestUpdates(context, sourceId, page)
                .let { it["list"].asMapList() }
        }

    /** Incremented whenever the installed extension set changes so source lists can rebuild. */
    @Volatile
    var extensionVersion: Long = 0L
        internal set
}
