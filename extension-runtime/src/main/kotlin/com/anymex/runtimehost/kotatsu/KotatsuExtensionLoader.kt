@file:Suppress("DEPRECATION")
@file:OptIn(org.koitharu.kotatsu.parsers.InternalParsersApi::class)

package com.anymex.runtimehost.kotatsu

import android.content.Context
import android.graphics.BitmapFactory
import okhttp3.CookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

class KotatsuAndroidPluginClassLoader(
    dexPath: String,
    optimizedDirectory: String,
    librarySearchPath: String?,
    parent: ClassLoader
) : dalvik.system.DexClassLoader(dexPath, optimizedDirectory, librarySearchPath, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        val shouldDelegate = (name.startsWith("org.koitharu.kotatsu.parsers.") && 
                name != "org.koitharu.kotatsu.parsers.MangaParser" &&
                name != "org.koitharu.kotatsu.parsers.MangaParserAuthProvider" &&
                name != "org.koitharu.kotatsu.parsers.model.MangaParserSource" &&
                name != "org.koitharu.kotatsu.parsers.util.LinkResolver" &&
                !name.startsWith("org.koitharu.kotatsu.parsers.site.") &&
                !name.startsWith("org.koitharu.kotatsu.parsers.core.") &&
                !name.startsWith("org.koitharu.kotatsu.parsers.network.") &&
                !name.startsWith("org.koitharu.kotatsu.parsers.util.")) ||
                name.startsWith("okhttp3.") ||
                name.startsWith("okio.") ||
                name.startsWith("kotlin.") ||
                name.startsWith("java.") ||
                name.startsWith("javax.") ||
                name.startsWith("org.jsoup.") ||
                name.startsWith("org.json.")

        if (shouldDelegate) {
            return super.loadClass(name, resolve)
        }

        val loaded = findLoadedClass(name)
        if (loaded != null) {
            return loaded
        }

        return try {
            findClass(name)
        } catch (e: ClassNotFoundException) {
            super.loadClass(name, resolve)
        }
    }
}

object KotatsuExtensionLoader {
    val loadedParsers = ConcurrentHashMap<String, MangaParser>()
    private val classLoaders = ConcurrentHashMap<String, dalvik.system.DexClassLoader>()
    private val scanMutex = kotlinx.coroutines.sync.Mutex()
    private var initialized = false
    private val sourceIdToClassName = ConcurrentHashMap<String, String>()

    fun getOrLoadParser(sourceId: String): MangaParser? {
        val loaded = loadedParsers[sourceId]
        if (loaded != null) return loaded

        synchronized(loadedParsers) {
            val loadedDoubleCheck = loadedParsers[sourceId]
            if (loadedDoubleCheck != null) return loadedDoubleCheck

            val className = sourceIdToClassName[sourceId] ?: return null
            for (classLoader in classLoaders.values) {
                try {
                    val clazz = classLoader.loadClass(className)
                    val mangaParserClass = classLoader.loadClass("org.koitharu.kotatsu.parsers.MangaParser")
                    if (mangaParserClass.isAssignableFrom(clazz)) {
                        val rawParser = try {
                            clazz.getDeclaredConstructor(MangaLoaderContext::class.java).newInstance(AndroidMangaLoaderContext)
                        } catch (e: Exception) {
                            clazz.getDeclaredConstructor().newInstance()
                        }
                        val parserInstance = KotatsuMangaParserWrapper(rawParser, classLoader)
                        loadedParsers[sourceId] = parserInstance
                        System.err.println("[Kotatsu-Android] Lazily loaded parser: ${parserInstance.source.title} ($sourceId)")
                        return parserInstance
                    }
                } catch (e: Exception) {
                }
            }
        }
        return null
    }

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        System.err.println("[Kotatsu-Android] Kotatsu runtime context initialized!")
    }

    class AndroidBitmap(val bitmap: android.graphics.Bitmap) : Bitmap {
        override val width: Int get() = bitmap.width
        override val height: Int get() = bitmap.height
        override fun drawBitmap(sourceBitmap: Bitmap, src: Rect, dst: Rect) {
            val srcBmp = (sourceBitmap as AndroidBitmap).bitmap
            val canvas = android.graphics.Canvas(bitmap)
            val srcRect = android.graphics.Rect(src.left, src.top, src.right, src.bottom)
            val dstRect = android.graphics.Rect(dst.left, dst.top, dst.right, dst.bottom)
            canvas.drawBitmap(srcBmp, srcRect, dstRect, null)
        }
    }

    object AndroidMangaLoaderContext : MangaLoaderContext() {
        override val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    var customUa = System.getProperty("anymex.ua.$host")
                    if (customUa.isNullOrEmpty()) {
                        val parts = host.split(".")
                        if (parts.size >= 2) {
                            val parentDomain = parts.takeLast(2).joinToString(".")
                            customUa = System.getProperty("anymex.ua.$parentDomain")
                        }
                    }
                    if (!customUa.isNullOrEmpty()) {
                        val newRequest = request.newBuilder()
                            .header("User-Agent", customUa)
                            .build()
                        chain.proceed(newRequest)
                    } else {
                        chain.proceed(request)
                    }
                }
                .build()
        }

        override val cookieJar: CookieJar = object : CookieJar {
            private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val list = mutableListOf<Cookie>()
                val memoryCookies = cookieStore[url.host]
                if (memoryCookies != null) {
                    list.addAll(memoryCookies)
                }
                try {
                    val cookieManager = android.webkit.CookieManager.getInstance()
                    val cookieString = cookieManager.getCookie(url.toString())
                    if (!cookieString.isNullOrEmpty()) {
                        cookieString.split(";").forEach { pair ->
                            val cleanPair = pair.trim()
                            if (cleanPair.isNotEmpty()) {
                                val cookie = Cookie.parse(url, cleanPair)
                                if (cookie != null) {
                                    if (list.none { it.name == cookie.name }) {
                                        list.add(cookie)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    System.err.println("[Kotatsu-Android] Failed to load cookies from CookieManager: ${e.message}")
                }
                return list
            }
        }

        override fun newParserInstance(source: MangaSource): MangaParser {
            throw UnsupportedOperationException("Context doesn't instantiate parsers directly")
        }

        override fun getParserSources(): List<MangaSource> {
            return loadedParsers.values.map { it.source }
        }

        override suspend fun evaluateJs(script: String): String? = null
        override suspend fun evaluateJs(baseUrl: String, script: String): String? = null

        override fun getConfig(source: MangaSource): MangaSourceConfig {
            return object : MangaSourceConfig {
                override fun <T> get(key: ConfigKey<T>): T {
                    return key.defaultValue
                }
            }
        }

        override fun getDefaultUserAgent(): String = 
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        override fun redrawImageResponse(response: Response, redraw: (image: Bitmap) -> Bitmap): Response {
            val body = response.body ?: return response
            return try {
                val bytes = body.bytes()
                val androidBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return response
                val mutableBmp = androidBmp.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                val srcBitmap = AndroidBitmap(mutableBmp)
                val dstBitmap = redraw(srcBitmap) as AndroidBitmap
                val bos = ByteArrayOutputStream()
                dstBitmap.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos)
                val newBody = ResponseBody.create(body.contentType(), bos.toByteArray())
                response.newBuilder().body(newBody).build()
            } catch (e: Exception) {
                System.err.println("[Kotatsu-Android] Redraw image response failed: ${e.message}")
                response
            }
        }

        override fun createBitmap(width: Int, height: Int): Bitmap {
            val config = android.graphics.Bitmap.Config.ARGB_8888
            val bmp = android.graphics.Bitmap.createBitmap(width, height, config)
            return AndroidBitmap(bmp)
        }
    }

    private fun getPageSize(parser: MangaParser): Int {
        return try {
            val field = parser.javaClass.getField("pageSize")
            field.get(parser) as Int
        } catch (e: Exception) {
            try {
                val field = parser.javaClass.getDeclaredField("pageSize")
                field.isAccessible = true
                field.get(parser) as Int
            } catch (e2: Exception) {
                20
            }
        }
    }

    suspend fun loadExtensions(context: Context, folderPath: String?): List<Map<String, Any?>> {
        return scanMutex.withLock {
            initialize(context)
            val path = folderPath ?: context.filesDir.absolutePath
            val folder = File(path)
            if (!folder.exists() || !folder.isDirectory) return@withLock emptyList()

            val cacheFile = File(folder, "kotatsu_extensions_cache.json")
            if (cacheFile.exists()) {
                try {
                    val cachedText = cacheFile.readText()
                    val jsonArray = org.json.JSONArray(cachedText)
                    val cachedList = mutableListOf<Map<String, Any?>>()
                    
                    folder.listFiles { file -> file.name == "plugin.jar" || file.name == "kotatsu_plugin.jar" || (file.extension == "jar" && file.name.contains("kotatsu")) }?.forEach { jar ->
                        try {
                            val tempJar = File(context.cacheDir, "kotatsu_${jar.name}")
                            if (tempJar.exists()) {
                                tempJar.setWritable(true)
                                tempJar.delete()
                            }
                            jar.copyTo(tempJar, overwrite = true)
                            tempJar.setReadOnly()
                            val classLoader = KotatsuAndroidPluginClassLoader(
                                tempJar.absolutePath,
                                context.cacheDir.absolutePath,
                                null,
                                KotatsuExtensionLoader::class.java.classLoader!!
                            )
                            classLoaders[jar.absolutePath] = classLoader
                        } catch (e: Exception) {
                            System.err.println("[Kotatsu-Android] Error initializing classloader from cache flow: ${e.message}")
                        }
                    }

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val map = mutableMapOf<String, Any?>()
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = obj.get(key)
                            if (value == org.json.JSONObject.NULL) {
                                map[key] = null
                            } else {
                                map[key] = value
                            }
                        }
                        cachedList.add(map)
                        
                        val idStr = map["id"] as? String
                        val className = map["className"] as? String
                        if (idStr != null && className != null) {
                            sourceIdToClassName[idStr] = className
                        }
                    }
                    System.err.println("[Kotatsu-Android] Loaded ${cachedList.size} sources from cache.")
                    return@withLock cachedList
                } catch (e: Exception) {
                    System.err.println("[Kotatsu-Android] Failed to load cache: ${e.message}. Rescanning...")
                }
            }

            val list = mutableListOf<Map<String, Any?>>()

            folder.listFiles { file -> file.name == "plugin.jar" || file.name == "kotatsu_plugin.jar" || (file.extension == "jar" && file.name.contains("kotatsu")) }?.forEach { jar ->
                System.err.println("[Kotatsu-Android] Scanning JAR: ${jar.name}")
                
                try {
                    val tempJar = File(context.cacheDir, "kotatsu_${jar.name}")
                    if (tempJar.exists()) {
                        tempJar.setWritable(true)
                        tempJar.delete()
                    }
                    jar.copyTo(tempJar, overwrite = true)
                    tempJar.setReadOnly()
                    
                    val classLoader = KotatsuAndroidPluginClassLoader(
                        tempJar.absolutePath,
                        context.cacheDir.absolutePath,
                        null,
                        KotatsuExtensionLoader::class.java.classLoader!!
                    )
                    
                    val classNames = mutableSetOf<String>()

                    fun extractClassNamesFromDex(bytes: ByteArray): List<String> {
                        val names = mutableListOf<String>()
                        try {
                            if (bytes.size < 112) return emptyList()
                            if (bytes[0].toChar() != 'd' || bytes[1].toChar() != 'e' || bytes[2].toChar() != 'x') {
                                return emptyList()
                            }
                            fun readInt(offset: Int): Int {
                                return (bytes[offset].toInt() and 0xFF) or
                                       ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                                       ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                                       ((bytes[offset + 3].toInt() and 0xFF) shl 24)
                            }
                            val stringIdsSize = readInt(56)
                            val stringIdsOff = readInt(60)
                            val typeIdsSize = readInt(64)
                            val typeIdsOff = readInt(68)
                            val classDefsSize = readInt(96)
                            val classDefsOff = readInt(100)

                            for (i in 0 until classDefsSize) {
                                val classDefOffset = classDefsOff + i * 32
                                if (classDefOffset + 4 > bytes.size) break
                                val classIdx = readInt(classDefOffset)

                                if (classIdx >= typeIdsSize) continue
                                val typeOffset = typeIdsOff + classIdx * 4
                                if (typeOffset + 4 > bytes.size) continue
                                val descriptorIdx = readInt(typeOffset)

                                if (descriptorIdx >= stringIdsSize) continue
                                val stringOffsetOffset = stringIdsOff + descriptorIdx * 4
                                if (stringOffsetOffset + 4 > bytes.size) continue
                                val stringDataOff = readInt(stringOffsetOffset)

                                if (stringDataOff >= bytes.size) continue

                                var offset = stringDataOff
                                var value = 0
                                var shift = 0
                                while (offset < bytes.size) {
                                    val b = bytes[offset++].toInt() and 0xFF
                                    value = value or ((b and 0x7F) shl shift)
                                    if ((b and 0x80) == 0) break
                                    shift += 7
                                }

                                val start = offset
                                while (offset < bytes.size && bytes[offset] != 0.toByte()) {
                                    offset++
                                }
                                
                                if (offset > start) {
                                    val stringBytes = bytes.copyOfRange(start, offset)
                                    val descriptor = String(stringBytes, Charsets.UTF_8)
                                    if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
                                        val className = descriptor.substring(1, descriptor.length - 1).replace('/', '.')
                                        names.add(className)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            System.err.println("[Kotatsu-Android] Error parsing DEX: ${e.message}")
                        }
                        return names
                    }

                    try {
                        val zipFile = ZipFile(tempJar)
                        for (entry in zipFile.entries()) {
                            if (entry.name.startsWith("classes") && entry.name.endsWith(".dex")) {
                                zipFile.getInputStream(entry).use { inputStream ->
                                    val bytes = inputStream.readBytes()
                                    classNames.addAll(extractClassNamesFromDex(bytes))
                                }
                            } else if (entry.name.endsWith(".class") && !entry.name.contains("$")) {
                                classNames.add(entry.name.replace('/', '.').replace('\\', '.').removeSuffix(".class"))
                            }
                        }
                        zipFile.close()
                    } catch (e: Exception) {
                        System.err.println("[Kotatsu-Android] Failed to scan zip/dex entries: ${e.message}")
                        e.printStackTrace(System.err)
                    }

                    var errorCount = 0
                    val maxErrorsToLog = 5
                    for (className in classNames) {
                        try {
                            val clazz = classLoader.loadClass(className)
                            val mangaParserClass = classLoader.loadClass("org.koitharu.kotatsu.parsers.MangaParser")
                            val isMangaParser = mangaParserClass.isAssignableFrom(clazz)
                            if (isMangaParser && 
                                !clazz.isInterface && 
                                !java.lang.reflect.Modifier.isAbstract(clazz.modifiers)) {
                                
                                val rawParser = try {
                                    clazz.getDeclaredConstructor(MangaLoaderContext::class.java).newInstance(AndroidMangaLoaderContext)
                                } catch (e: Exception) {
                                    val realException = if (e is java.lang.reflect.InvocationTargetException) e.targetException else e
                                    if (errorCount < maxErrorsToLog) {
                                        System.err.println("  [Kotatsu-Android] Failed to instantiate with MangaLoaderContext constructor for $className: ${realException.message} (${realException.javaClass.name})")
                                        realException.printStackTrace(System.err)
                                    }
                                    clazz.getDeclaredConstructor().newInstance()
                                }

                                val parserInstance = KotatsuMangaParserWrapper(rawParser, classLoader)
                                val source = parserInstance.source
                                val idStr = "kotatsu_" + source.name.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                                loadedParsers[idStr] = parserInstance
                                sourceIdToClassName[idStr] = className

                                System.err.println("  [Kotatsu-Android] Found Parser: ${source.title} ($idStr)")

                                val cleanDomain = try {
                                    parserInstance.domain
                                        .replace("https://", "")
                                        .replace("http://", "")
                                        .split("/")[0]
                                } catch (e: Exception) {
                                    ""
                                }
                                val iconUrl = if (cleanDomain.isNotEmpty()) {
                                    "https://www.google.com/s2/favicons?sz=128&domain=$cleanDomain"
                                } else {
                                    "https://raw.githubusercontent.com/KotatsuApp/Kotatsu/devel/metadata/en-US/icon.png"
                                }

                                val extObj = mapOf(
                                    "id" to idStr,
                                    "name" to source.title,
                                    "lang" to source.locale.ifEmpty { "all" },
                                    "type" to "manga",
                                    "baseUrl" to parserInstance.domain,
                                    "iconUrl" to iconUrl,
                                    "isNsfw" to (source.contentType == ContentType.HENTAI),
                                    "version" to "1.0.0",
                                    "pkgName" to "kotatsu.plugin",
                                    "className" to className,
                                    "itemType" to 0, 
                                    "hasUpdate" to false,
                                    "isObsolete" to false,
                                    "isShared" to false
                                )
                                 list.add(extObj)
                            }
                        } catch (e: Throwable) {
                            errorCount++
                            if (errorCount <= maxErrorsToLog) {
                                System.err.println("  [Kotatsu-Android] Failed to load/verify class $className: ${e.message} (${e.javaClass.name})")
                            }
                        }
                    }
                    if (errorCount > maxErrorsToLog) {
                        System.err.println("  [Kotatsu-Android] ... and ${errorCount - maxErrorsToLog} more class loading/instantiation errors omitted.")
                    }
                    classLoaders[jar.absolutePath] = classLoader
                } catch (e: Throwable) {
                    System.err.println("  [Kotatsu-Android] Error processing ${jar.name}: ${e.message}")
                }
            }

            System.err.println("[Kotatsu-Android] Scan complete. Found ${list.size} sources.")

            try {
                val jsonArr = org.json.JSONArray()
                for (item in list) {
                    val jsonObj = org.json.JSONObject(item)
                    jsonArr.put(jsonObj)
                }
                cacheFile.writeText(jsonArr.toString())
                System.err.println("[Kotatsu-Android] Saved cache to ${cacheFile.absolutePath}")
            } catch (e: Exception) {
                System.err.println("[Kotatsu-Android] Failed to save cache: ${e.message}")
            }

            list
        }
    }

    suspend fun getPopular(sourceId: String, page: Int): Map<String, Any?> = withContext(Dispatchers.IO) {
        val parser = getOrLoadParser(sourceId) ?: return@withContext mapOf("list" to emptyList<Any>(), "hasNextPage" to false)
        try {
            val offset = (page - 1) * getPageSize(parser)
            val list = parser.getList(offset, SortOrder.POPULARITY, MangaListFilter.EMPTY)
            val mappedList = list.map { m ->
                mapOf(
                    "title" to m.title,
                    "url" to m.url,
                    "cover" to m.coverUrl
                )
            }
            mapOf("list" to mappedList, "hasNextPage" to list.isNotEmpty())
        } catch (e: Exception) {
            e.printStackTrace()
            mapOf("list" to emptyList<Any>(), "hasNextPage" to false)
        }
    }

    suspend fun getLatestUpdates(sourceId: String, page: Int): Map<String, Any?> = withContext(Dispatchers.IO) {
        val parser = getOrLoadParser(sourceId) ?: return@withContext mapOf("list" to emptyList<Any>(), "hasNextPage" to false)
        try {
            val offset = (page - 1) * getPageSize(parser)
            val list = parser.getList(offset, SortOrder.UPDATED, MangaListFilter.EMPTY)
            val mappedList = list.map { m ->
                mapOf(
                    "title" to m.title,
                    "url" to m.url,
                    "cover" to m.coverUrl
                )
            }
            mapOf("list" to mappedList, "hasNextPage" to list.isNotEmpty())
        } catch (e: Exception) {
            e.printStackTrace()
            mapOf("list" to emptyList<Any>(), "hasNextPage" to false)
        }
    }

    suspend fun search(sourceId: String, query: String, page: Int): Map<String, Any?> = withContext(Dispatchers.IO) {
        val parser = getOrLoadParser(sourceId) ?: return@withContext mapOf("list" to emptyList<Any>(), "hasNextPage" to false)
        try {
            val offset = (page - 1) * getPageSize(parser)
            val filter = MangaListFilter(query = query)
            val list = parser.getList(offset, SortOrder.RELEVANCE, filter)
            val mappedList = list.map { m ->
                mapOf(
                    "title" to m.title,
                    "url" to m.url,
                    "cover" to m.coverUrl
                )
            }
            mapOf("list" to mappedList, "hasNextPage" to list.isNotEmpty())
        } catch (e: Exception) {
            e.printStackTrace()
            mapOf("list" to emptyList<Any>(), "hasNextPage" to false)
        }
    }

    suspend fun getDetails(sourceId: String, url: String, title: String, cover: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        val parser = getOrLoadParser(sourceId) ?: return@withContext emptyMap()
        try {
            val dummyManga = Manga(
                id = 0L,
                title = title,
                altTitles = emptySet(),
                url = url,
                publicUrl = "",
                rating = 0f,
                contentRating = null,
                coverUrl = cover,
                tags = emptySet(),
                state = null,
                authors = emptySet(),
                source = parser.source
            )
            val details = parser.getDetails(dummyManga)
            val chapters = details.chapters.orEmpty().reversed()
            val mappedChapters = chapters.map { ch ->
                val chNum = ch.number
                mapOf(
                    "name" to (ch.title ?: "Chapter $chNum"),
                    "url" to ch.url,
                    "chapter_number" to chNum,
                    "episode_number" to chNum,
                    "date_upload" to ch.uploadDate,
                    "scanlator" to (ch.scanlator ?: "")
                )
            }

            val statusVal = when (details.state) {
                MangaState.ONGOING -> 1
                MangaState.FINISHED -> 2
                else -> 0
            }

            mapOf(
                "title" to details.title,
                "url" to details.url,
                "cover" to (details.largeCoverUrl ?: details.coverUrl ?: cover),
                "description" to (details.description ?: ""),
                "author" to (details.authors.firstOrNull() ?: ""),
                "artist" to "",
                "genre" to details.tags.map { it.title },
                "status" to statusVal,
                "episodes" to mappedChapters
            )
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    suspend fun getPageList(sourceId: String, url: String, name: String): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val parser = getOrLoadParser(sourceId) ?: return@withContext emptyList()
        try {
            val dummyChapter = MangaChapter(
                id = 0L,
                title = name,
                number = 0f,
                volume = 0,
                url = url,
                scanlator = null,
                uploadDate = 0L,
                branch = null,
                source = parser.source
            )
            val pages = parser.getPages(dummyChapter)
            val pageUrls = coroutineScope {
                pages.map { page ->
                    async {
                        try {
                            parser.getPageUrl(page)
                        } catch (e: Exception) {
                            page.url
                        }
                    }
                }.awaitAll()
            }

            val headers = try {
                parser.getRequestHeaders().names().associateWith { parser.getRequestHeaders()[it] ?: "" }
            } catch (e: Exception) {
                emptyMap<String, String>()
            }

            pageUrls.map { pUrl ->
                mapOf(
                    "url" to pUrl,
                    "headers" to headers
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

class KotatsuMangaParserWrapper(
    val rawParser: Any,
    val childClassLoader: ClassLoader
) : MangaParser {

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        return try {
            val interceptMethod = rawParser.javaClass.getMethod("intercept", okhttp3.Interceptor.Chain::class.java)
            interceptMethod.invoke(rawParser, chain) as okhttp3.Response
        } catch (e: Exception) {
            chain.proceed(chain.request())
        }
    }

    override val source: MangaSource by lazy {
        val rawSource = rawParser.javaClass.getMethod("getSource").invoke(rawParser)
        val name = try {
            rawSource.javaClass.getMethod("getName").invoke(rawSource) as String
        } catch (e: Exception) {
            try {
                rawSource.javaClass.getMethod("name").invoke(rawSource) as String
            } catch (ex: Exception) {
                rawSource.toString()
            }
        }
        val title = try {
            rawSource.javaClass.getMethod("getTitle").invoke(rawSource) as String
        } catch (e: Exception) {
            name
        }
        val locale = try {
            rawSource.javaClass.getMethod("getLocale").invoke(rawSource) as String
        } catch (e: Exception) {
            ""
        }
        val isBroken = try {
            rawSource.javaClass.getMethod("isBroken").invoke(rawSource) as Boolean
        } catch (e: Exception) {
            false
        }
        val contentTypeVal = try {
            val rawContentType = rawSource.javaClass.getMethod("getContentType").invoke(rawSource)
            val ctName = rawContentType.javaClass.getMethod("name").invoke(rawContentType) as String
            ContentType.valueOf(ctName)
        } catch (e: Exception) {
            ContentType.OTHER
        }

        object : MangaSource {
            override val name: String = name
            override val title: String = title
            override val locale: String = locale
            override val isBroken: Boolean = isBroken
            override val contentType: ContentType = contentTypeVal
        }
    }

    override val availableSortOrders: Set<SortOrder> by lazy {
        try {
            val rawSet = rawParser.javaClass.getMethod("getAvailableSortOrders").invoke(rawParser) as Set<*>
            rawSet.map { rawOrder ->
                val name = rawOrder!!.javaClass.getMethod("name").invoke(rawOrder) as String
                SortOrder.valueOf(name)
            }.toSet()
        } catch (e: Exception) {
            setOf(SortOrder.POPULARITY)
        }
    }

    override val searchQueryCapabilities: MangaSearchQueryCapabilities
        get() = throw UnsupportedOperationException()

    override val filterCapabilities: MangaListFilterCapabilities by lazy {
        try {
            val rawCap = rawParser.javaClass.getMethod("getFilterCapabilities").invoke(rawParser)
            val isMultipleTagsSupported = getFieldValue(rawCap, "isMultipleTagsSupported") as? Boolean ?: false
            val isTagsExclusionSupported = getFieldValue(rawCap, "isTagsExclusionSupported") as? Boolean ?: false
            val isSearchSupported = getFieldValue(rawCap, "isSearchSupported") as? Boolean ?: false
            val isSearchWithFiltersSupported = getFieldValue(rawCap, "isSearchWithFiltersSupported") as? Boolean ?: false
            val isYearSupported = getFieldValue(rawCap, "isYearSupported") as? Boolean ?: false
            val isYearRangeSupported = getFieldValue(rawCap, "isYearRangeSupported") as? Boolean ?: false
            val isOriginalLocaleSupported = getFieldValue(rawCap, "isOriginalLocaleSupported") as? Boolean ?: false
            val isAuthorSearchSupported = getFieldValue(rawCap, "isAuthorSearchSupported") as? Boolean ?: false

            MangaListFilterCapabilities(
                isMultipleTagsSupported = isMultipleTagsSupported,
                isTagsExclusionSupported = isTagsExclusionSupported,
                isSearchSupported = isSearchSupported,
                isSearchWithFiltersSupported = isSearchWithFiltersSupported,
                isYearSupported = isYearSupported,
                isYearRangeSupported = isYearRangeSupported,
                isOriginalLocaleSupported = isOriginalLocaleSupported,
                isAuthorSearchSupported = isAuthorSearchSupported
            )
        } catch (e: Exception) {
            MangaListFilterCapabilities(isSearchSupported = true)
        }
    }

    override val config: MangaSourceConfig
        get() {
            return object : MangaSourceConfig {
                override fun <T> get(key: ConfigKey<T>): T {
                    return key.defaultValue
                }
            }
        }

    override val configKeyDomain: ConfigKey.Domain
        get() = throw UnsupportedOperationException()

    override val domain: String
        get() = rawParser.javaClass.getMethod("getDomain").invoke(rawParser) as String

    override suspend fun getList(query: MangaSearchQuery): List<Manga> {
        return emptyList()
    }

    override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val childSortOrderClass = childClassLoader.loadClass("org.koitharu.kotatsu.parsers.model.SortOrder")
        val childOrder = childSortOrderClass.getMethod("valueOf", String::class.java).invoke(null, order.name)

        val filterClass = childClassLoader.loadClass("org.koitharu.kotatsu.parsers.model.MangaListFilter")
        val emptySet = emptySet<Any>()
        val filterConstructor = filterClass.getConstructor(
            String::class.java,
            Set::class.java,
            Set::class.java,
            java.util.Locale::class.java,
            java.util.Locale::class.java,
            Set::class.java,
            Set::class.java,
            Set::class.java,
            Set::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java
        )
        val childFilter = filterConstructor.newInstance(
            filter.query,
            emptySet,
            emptySet,
            null,
            null,
            emptySet,
            emptySet,
            emptySet,
            emptySet,
            -1,
            -1,
            -1,
            null
        )

        val getListMethod = rawParser.javaClass.methods.first { m ->
            m.name == "getList" && m.parameterTypes.size == 4 && m.parameterTypes[0] == Int::class.javaPrimitiveType
        }

        val rawResult = invokeSuspendMethod(getListMethod, rawParser, offset, childOrder, childFilter) as List<*>
        return rawResult.map { mapChildMangaToParent(it!!) }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val childManga = mapParentMangaToChild(manga)
        val getDetailsMethod = rawParser.javaClass.methods.first { m ->
            m.name == "getDetails" && m.parameterTypes.size == 2
        }
        val rawResult = invokeSuspendMethod(getDetailsMethod, rawParser, childManga)
        return mapChildMangaToParent(rawResult!!)
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val childChapter = mapParentChapterToChild(chapter)
        val getPagesMethod = rawParser.javaClass.methods.first { m ->
            m.name == "getPages" && m.parameterTypes.size == 2
        }
        val rawResult = invokeSuspendMethod(getPagesMethod, rawParser, childChapter) as List<*>
        return rawResult.map { mapChildPageToParent(it!!) }
    }

    override suspend fun getPageUrl(page: MangaPage): String {
        val childPage = mapParentPageToChild(page)
        val getPageUrlMethod = rawParser.javaClass.methods.first { m ->
            m.name == "getPageUrl" && m.parameterTypes.size == 2
        }
        return invokeSuspendMethod(getPageUrlMethod, rawParser, childPage) as String
    }

    override suspend fun getFilterOptions(): MangaListFilterOptions {
        return MangaListFilterOptions(availableTags = emptySet())
    }

    override suspend fun getFavicons(): Favicons {
        return Favicons.EMPTY
    }

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {}

    override suspend fun getRelatedManga(seed: Manga): List<Manga> {
        return emptyList()
    }

    override fun getRequestHeaders(): Headers {
        return try {
            val method = rawParser.javaClass.getMethod("getRequestHeaders")
            method.invoke(rawParser) as Headers
        } catch (e: Exception) {
            Headers.Builder().build()
        }
    }

    override suspend fun resolveLink(link: HttpUrl): Manga? {
        val getResolveLinkMethod = rawParser.javaClass.methods.firstOrNull { m ->
            m.name == "resolveLink" && m.parameterTypes.size == 2 && m.parameterTypes[0] == HttpUrl::class.java
        } ?: return null
        val rawResult = invokeSuspendMethod(getResolveLinkMethod, rawParser, link) ?: return null
        return mapChildMangaToParent(rawResult)
    }

    private fun getFieldValue(obj: Any?, fieldName: String): Any? {
        if (obj == null) return null
        return try {
            val field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(obj)
        } catch (e: Exception) {
            val getterName = "get" + fieldName.replaceFirstChar { it.uppercase() }
            try {
                val method = obj.javaClass.getMethod(getterName)
                method.invoke(obj)
            } catch (ex: Exception) {
                null
            }
        }
    }

    private fun mapChildMangaToParent(childManga: Any): Manga {
        val id = getFieldValue(childManga, "id") as Long
        val title = getFieldValue(childManga, "title") as String
        val altTitles = (getFieldValue(childManga, "altTitles") as? Set<*>)?.map { it.toString() }?.toSet() ?: emptySet()
        val url = getFieldValue(childManga, "url") as String
        val publicUrl = getFieldValue(childManga, "publicUrl") as? String ?: ""
        val rating = (getFieldValue(childManga, "rating") as? Number)?.toFloat() ?: 0f

        val rawContentRating = getFieldValue(childManga, "contentRating")
        val contentRatingVal = if (rawContentRating != null) {
            try {
                val name = rawContentRating.javaClass.getMethod("name").invoke(rawContentRating) as String
                ContentRating.valueOf(name)
            } catch (e: Exception) {
                null
            }
        } else null

        val coverUrl = getFieldValue(childManga, "coverUrl") as? String

        val rawTags = (getFieldValue(childManga, "tags") as? Set<*>) ?: emptySet<Any>()
        val tagsVal = rawTags.mapNotNull { t ->
            val tId = getFieldValue(t, "key") as? String ?: getFieldValue(t, "id") as? String ?: return@mapNotNull null
            val tTitle = getFieldValue(t, "title") as? String ?: return@mapNotNull null
            MangaTag(title = tTitle, key = tId, source = this.source)
        }.toSet()

        val rawState = getFieldValue(childManga, "state")
        val stateVal = if (rawState != null) {
            try {
                val name = rawState.javaClass.getMethod("name").invoke(rawState) as String
                MangaState.valueOf(name)
            } catch (e: Exception) {
                null
            }
        } else null

        val authors = (getFieldValue(childManga, "authors") as? Set<*>)?.map { it.toString() }?.toSet() ?: emptySet()
        val largeCoverUrl = getFieldValue(childManga, "largeCoverUrl") as? String
        val description = getFieldValue(childManga, "description") as? String

        val rawChapters = getFieldValue(childManga, "chapters") as? List<*>
        val chaptersVal = rawChapters?.map { ch ->
            mapChildChapterToParent(ch!!)
        }

        return Manga(
            id = id,
            title = title,
            altTitles = altTitles,
            url = url,
            publicUrl = publicUrl,
            rating = rating,
            contentRating = contentRatingVal,
            coverUrl = coverUrl,
            tags = tagsVal,
            state = stateVal,
            authors = authors,
            largeCoverUrl = largeCoverUrl,
            description = description,
            chapters = chaptersVal,
            source = this.source
        )
    }

    private fun mapChildChapterToParent(childChapter: Any): MangaChapter {
        val id = getFieldValue(childChapter, "id") as Long
        val titleVal = getFieldValue(childChapter, "title") as? String
        val number = (getFieldValue(childChapter, "number") as? Number)?.toFloat() ?: 0f
        val volume = (getFieldValue(childChapter, "volume") as? Number)?.toInt() ?: 0
        val url = getFieldValue(childChapter, "url") as String
        val scanlator = getFieldValue(childChapter, "scanlator") as? String
        val uploadDate = (getFieldValue(childChapter, "uploadDate") as? Number)?.toLong() ?: 0L
        val branch = getFieldValue(childChapter, "branch") as? String

        return MangaChapter(
            id = id,
            title = titleVal,
            number = number,
            volume = volume,
            url = url,
            scanlator = scanlator,
            uploadDate = uploadDate,
            branch = branch,
            source = this.source
        )
    }

    private fun mapChildPageToParent(childPage: Any): MangaPage {
        val id = getFieldValue(childPage, "id") as Long
        val url = getFieldValue(childPage, "url") as String
        val previewUrl = getFieldValue(childPage, "preview") as? String ?: getFieldValue(childPage, "previewUrl") as? String

        return MangaPage(
            id = id,
            url = url,
            preview = previewUrl,
            source = this.source
        )
    }

    private fun mapParentMangaToChild(parentManga: Manga): Any {
        val mangaClass = childClassLoader.loadClass("org.koitharu.kotatsu.parsers.model.Manga")
        val contentRatingClass = childClassLoader.loadClass("org.koitharu.kotatsu.parsers.model.ContentRating")
        val mangaStateClass = childClassLoader.loadClass("org.koitharu.kotatsu.parsers.model.MangaState")
        val mangaTagClass = childClassLoader.loadClass("org.koitharu.kotatsu.parsers.model.MangaTag")
        val mangaSourceClass = childClassLoader.loadClass("org.koitharu.kotatsu.parsers.model.MangaSource")
        
        val contentRating = parentManga.contentRating
        val childContentRating = if (contentRating != null) {
            contentRatingClass.getMethod("valueOf", String::class.java).invoke(null, contentRating.name)
        } else null
        
        val state = parentManga.state
        val childState = if (state != null) {
            mangaStateClass.getMethod("valueOf", String::class.java).invoke(null, state.name)
        } else null
        
        val childSource = rawParser.javaClass.getMethod("getSource").invoke(rawParser)

        val childTags = parentManga.tags.map { t ->
            val constructor = mangaTagClass.constructors.firstOrNull { it.parameterTypes.size == 3 }
            if (constructor != null) {
                constructor.newInstance(t.title, t.key, childSource)
            } else {
                val constructor2 = mangaTagClass.getConstructor(String::class.java, String::class.java)
                constructor2.newInstance(t.title, t.key)
            }
        }.toSet()
        
        val childChapters = parentManga.chapters?.map { ch ->
            mapParentChapterToChild(ch)
        }
        
        val constructor = mangaClass.getConstructor(
            Long::class.javaPrimitiveType,
            String::class.java,
            Set::class.java,
            String::class.java,
            String::class.java,
            Float::class.javaPrimitiveType,
            contentRatingClass,
            String::class.java,
            Set::class.java,
            mangaStateClass,
            Set::class.java,
            String::class.java,
            String::class.java,
            List::class.java,
            mangaSourceClass
        )
        
        return constructor.newInstance(
            parentManga.id,
            parentManga.title,
            parentManga.altTitles,
            parentManga.url,
            parentManga.publicUrl,
            parentManga.rating,
            childContentRating,
            parentManga.coverUrl,
            childTags,
            childState,
            parentManga.authors,
            parentManga.largeCoverUrl,
            parentManga.description,
            childChapters,
            childSource
        )
    }

    private fun mapParentChapterToChild(parentChapter: MangaChapter): Any {
        val chapterClass = childClassLoader.loadClass("org.koitharu.kotatsu.parsers.model.MangaChapter")
        val mangaSourceClass = childClassLoader.loadClass("org.koitharu.kotatsu.parsers.model.MangaSource")
        val childSource = rawParser.javaClass.getMethod("getSource").invoke(rawParser)
        
        val constructor = chapterClass.getConstructor(
            Long::class.javaPrimitiveType,
            String::class.java,
            Float::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
            Long::class.javaPrimitiveType,
            String::class.java,
            mangaSourceClass
        )
        
        return constructor.newInstance(
            parentChapter.id,
            parentChapter.title,
            parentChapter.number,
            parentChapter.volume,
            parentChapter.url,
            parentChapter.scanlator,
            parentChapter.uploadDate,
            parentChapter.branch,
            childSource
        )
    }

    private fun mapParentPageToChild(parentPage: MangaPage): Any {
        val pageClass = childClassLoader.loadClass("org.koitharu.kotatsu.parsers.model.MangaPage")
        val mangaSourceClass = childClassLoader.loadClass("org.koitharu.kotatsu.parsers.model.MangaSource")
        val childSource = rawParser.javaClass.getMethod("getSource").invoke(rawParser)
        
        val constructor = pageClass.getConstructor(
            Long::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
            mangaSourceClass
        )
        
        return constructor.newInstance(
            parentPage.id,
            parentPage.url,
            parentPage.preview,
            childSource
        )
    }

    private suspend fun invokeSuspendMethod(method: java.lang.reflect.Method, target: Any, vararg args: Any?): Any? {
        return kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn { cont ->
            val newArgs = arrayOf(*args, cont)
            val result = method.invoke(target, *newArgs)
            if (result == kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
                kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
            } else {
                result
            }
        }
    }
}

