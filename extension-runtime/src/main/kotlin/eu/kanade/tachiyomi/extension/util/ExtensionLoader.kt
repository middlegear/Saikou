package eu.kanade.tachiyomi.extension.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.anymex.runtimehost.LogLevel
import com.anymex.runtimehost.Logger
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import dalvik.system.BaseDexClassLoader
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

/**
 * Class that handles the loading of the extensions. Supports two kinds of extensions:
 *
 * 1. Shared extension: This extension is installed to the system with package
 * installer, so other variants of Tachiyomi and its forks can also use this extension.
 *
 * 2. Private extension: This extension is put inside private data directory of the
 * running app, so this extension can only be used by the running app and not shared
 * with other apps.
 *
 * When both kinds of extensions are installed with a same package name, shared
 * extension will be used unless the version codes are different. In that case the
 * one with higher version code will be used.
 */
private const val ANIME_PACKAGE = "tachiyomi.animeextension"
private const val MANGA_PACKAGE = "tachiyomi.extension"

internal object ExtensionLoader {
    private const val XX_METADATA_SOURCE_CLASS = ".class"
    private const val XX_METADATA_SOURCE_FACTORY = ".factory"
    private const val XX_METADATA_NSFW = "n.nsfw"
    private const val XX_METADATA_HAS_README = ".hasReadme"
    private const val XX_METADATA_HAS_CHANGELOG = ".hasChangelog"

    const val ANIME_LIB_VERSION_MIN = 12
    const val ANIME_LIB_VERSION_MAX = 17
    const val MANGA_LIB_VERSION_MIN = 1.2
    const val MANGA_LIB_VERSION_MAX = 1.6
    val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                PackageManager.GET_SIGNING_CERTIFICATES else 0)


    fun loadAnimeExtensions(context: Context): List<AnimeLoadResult> {
        val pkgManager = context.packageManager
        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }
        val extPkgs = installedPackages.filter { isPackageAnExtension(MediaType.ANIME, it) }

        if (extPkgs.isEmpty()) return emptyList()

        return runBlocking {
            val deferred = extPkgs.map {
                async { loadAnimeExtension(context, it.packageName, it) }
            }
            deferred.map { it.await() }
        }
    }

    fun loadMangaExtensions(context: Context, path: String? = null): List<MangaLoadResult> {
        val pkgManager = context.packageManager

        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        val sharedExtPkgs = installedPkgs.filter { isPackageAnExtension(MediaType.MANGA, it) }

        val privateExtPkgs = try {
            val privateDir = File(context.filesDir, "exts_manga")
            if (!privateDir.exists()) {
                privateDir.mkdirs()
            }

            if (!path.isNullOrBlank()) {
                val externalDir = File(path, "exts_manga")
                if (externalDir.exists() && externalDir.absolutePath != privateDir.absolutePath) {
                    val externalApks = externalDir.listFiles()?.filter { it.isFile && it.extension == "apk" } ?: emptyList()
                    val externalNames = externalApks.map { it.name }.toSet()
                    privateDir.listFiles()?.forEach { if (it.name !in externalNames) it.delete() }
                    for (src in externalApks) {
                        val dst = File(privateDir, src.name)
                        if (dst.exists() && dst.length() == src.length()) continue
                        val tmp = File(privateDir, "${src.name}.tmp")
                        tmp.outputStream().use { out ->
                            src.inputStream().use { it.copyTo(out) }
                        }
                        if (!tmp.renameTo(dst)) {
                            tmp.delete()
                        } else {
                            dst.setReadOnly()
                        }
                    }
                }
            }

            privateDir.listFiles()?.asSequence()?.filter { it.isFile && it.extension == "apk" }?.mapNotNull { apk ->
                pkgManager.getPackageArchiveInfo(apk.absolutePath, PACKAGE_FLAGS)?.apply {
                    applicationInfo?.fixBasePaths(apk.absolutePath)
                }
            }?.filter { isPackageAnExtension(MediaType.MANGA, it) }?.toList() ?: emptyList()
        } catch (e: Exception) {
            Logger.log("Manga private extensions load failed: ${e.message}", LogLevel.ERROR)
            emptyList()
        }

        val sharedPkgsSet = sharedExtPkgs.map { it.packageName }.toSet()
        val allPkgs = (sharedExtPkgs + privateExtPkgs).distinctBy { it.packageName }

        if (allPkgs.isEmpty()) return emptyList()

        val originalPriority = Thread.currentThread().priority
        Thread.currentThread().priority = Thread.MIN_PRIORITY
        return try {
            allPkgs.map {
                val isShared = sharedPkgsSet.contains(it.packageName)
                loadMangaExtension(context, it.packageName, it, isShared)
            }
        } finally {
            Thread.currentThread().priority = originalPriority
        }
    }

    private fun loadAnimeExtension(
        context: Context,
        pkgName: String,
        pkgInfo: PackageInfo
    ): AnimeLoadResult {
        val pkgManager = context.packageManager

        val appInfo = try {
            pkgManager.getApplicationInfo(pkgName, PackageManager.GET_META_DATA)
        } catch (error: PackageManager.NameNotFoundException) {
            pkgInfo.applicationInfo?.apply { fixBasePaths(sourceDir ?: "") } ?: return AnimeLoadResult.Error
        }

        val extName = pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Aniyomi: ")
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        if (versionName.isNullOrEmpty()) {
            Logger.log("Missing versionName for extension $extName", LogLevel.ERROR)
            return AnimeLoadResult.Error
        }

        // Validate lib version
        val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion < ANIME_LIB_VERSION_MIN || libVersion > ANIME_LIB_VERSION_MAX) {
            Logger.log(
                "Lib version is $libVersion, while only versions " +
                        "$ANIME_LIB_VERSION_MIN to $ANIME_LIB_VERSION_MAX are allowed",
                LogLevel.ERROR
            )
            return AnimeLoadResult.Error
        }

        val isNsfw = appInfo.metaData.getInt("${ANIME_PACKAGE}${XX_METADATA_NSFW}") == 1


        val hasReadme = appInfo.metaData.getInt("${ANIME_PACKAGE}${XX_METADATA_HAS_README}", 0) == 1
        val hasChangelog =
            appInfo.metaData.getInt("${ANIME_PACKAGE}${XX_METADATA_HAS_CHANGELOG}", 0) == 1

        val classLoader = try{
            PathClassLoader(appInfo.sourceDir, null, ExtensionLoader::class.java.classLoader!!)
        } catch (e: Throwable) {
            Logger.log("Error creating class loader for $pkgName: ${e.message}", LogLevel.ERROR)
            return AnimeLoadResult.Error
        }
        val sources = appInfo.metaData.getString("$ANIME_PACKAGE$XX_METADATA_SOURCE_CLASS")!!
            .split(";")
            .map {
                val sourceClass = it.trim()
                if (sourceClass.startsWith(".")) {
                    pkgInfo.packageName + sourceClass
                } else {
                    sourceClass
                }
            }
            .flatMap {
                try {
                    when (val obj = Class.forName(it, false, classLoader).getDeclaredConstructor()
                        .newInstance()) {
                        is AnimeSource -> listOf(obj)
                        is AnimeSourceFactory -> obj.createSources()
                        else -> throw Exception("Unknown source class type! ${obj.javaClass}")
                    }
                } catch (e : Throwable) {
                    Logger.log("Error loading $it: ${e.message}", LogLevel.ERROR)
                    return AnimeLoadResult.Error
                }
            }

        val langs = sources.filterIsInstance<AnimeCatalogueSource>()
            .map { it.lang }
            .toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        val extension = AnimeExtension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            hasReadme = hasReadme,
            hasChangelog = hasChangelog,
            sources = sources,
            pkgFactory = appInfo.metaData.getString("${ANIME_PACKAGE}${XX_METADATA_SOURCE_FACTORY}"),
            isUnofficial = true,
            iconUrl = context.getApplicationIcon(pkgInfo),
        )
        return AnimeLoadResult.Success(extension)
    }

    private fun loadMangaExtension(
        context: Context,
        pkgName: String,
        pkgInfo: PackageInfo,
        isShared: Boolean = false,
    ): MangaLoadResult {
        val pkgManager = context.packageManager

        val appInfo = try {
            pkgManager.getApplicationInfo(pkgName, PackageManager.GET_META_DATA)
        } catch (error: PackageManager.NameNotFoundException) {
            pkgInfo.applicationInfo?.apply { fixBasePaths(sourceDir ?: "") } ?: return MangaLoadResult.Error
        }

        val extName =
            pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Tachiyomi: ")
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        if (versionName.isNullOrEmpty()) {
            Logger.log("Missing versionName for extension $extName", LogLevel.ERROR)
            return MangaLoadResult.Error
        }

        // Validate lib version
        val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion < MANGA_LIB_VERSION_MIN || libVersion > MANGA_LIB_VERSION_MAX) {
            Logger.log(
                "Lib version is $libVersion, while only versions " +
                        "$MANGA_LIB_VERSION_MIN to $MANGA_LIB_VERSION_MAX are allowed", LogLevel.ERROR
            )
            return MangaLoadResult.Error
        }

        val isNsfw = appInfo.metaData.getInt("$MANGA_PACKAGE$XX_METADATA_NSFW") == 1

        val hasReadme = appInfo.metaData.getInt("$MANGA_PACKAGE$XX_METADATA_HAS_README", 0) == 1
        val hasChangelog =
            appInfo.metaData.getInt("$MANGA_PACKAGE$XX_METADATA_HAS_CHANGELOG", 0) == 1

        if (appInfo.sourceDir != null) {
            try {
                File(appInfo.sourceDir).setReadOnly()
            } catch (_: Exception) {}
        }

        val classLoader = try{
            ChildFirstPathClassLoader(appInfo.sourceDir, null, ExtensionLoader::class.java.classLoader!!)
        } catch (e: Throwable) {
            Logger.log("Extension load error: $extName - ${e.message}", LogLevel.ERROR)
            return MangaLoadResult.Error
        }

        val sourceClassString = appInfo.metaData.getString("$MANGA_PACKAGE$XX_METADATA_SOURCE_CLASS")
        if (sourceClassString.isNullOrBlank()) {
             Logger.log("Manga extension $pkgName is missing source class metadata", LogLevel.ERROR)
             return MangaLoadResult.Error
        }

        val sources = sourceClassString
            .split(";")
            .map {
                val sourceClass = it.trim()
                if (sourceClass.startsWith(".")) {
                    pkgInfo.packageName + sourceClass
                } else {
                    sourceClass
                }
            }
            .flatMap {
                try {
                    when (val obj = Class.forName(it, false, classLoader)
                        .getDeclaredConstructor().newInstance()) {
                        is MangaSource -> listOf(obj)
                        is SourceFactory -> obj.createSources()
                        else -> throw Exception("Unknown source class type! ${obj.javaClass}")
                    }
                } catch (e: Throwable) {
                    Logger.log("Extension load error: $extName ($it) - ${e.message}", LogLevel.ERROR)
                    return MangaLoadResult.Error
                }
            }

        val langs = sources.filterIsInstance<CatalogueSource>()
            .map { it.lang }
            .toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        val extension = MangaExtension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            hasReadme = hasReadme,
            hasChangelog = hasChangelog,
            sources = sources,
            pkgFactory = appInfo.metaData.getString("$MANGA_PACKAGE$XX_METADATA_SOURCE_FACTORY"),
            isUnofficial = true,
            iconUrl = context.getApplicationIcon(pkgInfo),
            isShared = isShared,
        )
        Logger.log("Loaded Manga extension: $extName", LogLevel.INFO)
        return MangaLoadResult.Success(extension)
    }
    private fun isPackageAnExtension(type: MediaType, pkgInfo: PackageInfo): Boolean {
        return if (type == MediaType.NOVEL) {
            pkgInfo.packageName.startsWith("some.random")
        } else {
            pkgInfo.reqFeatures.orEmpty().any {
                it.name == when (type) {
                    MediaType.ANIME -> ANIME_PACKAGE
                    MediaType.MANGA -> MANGA_PACKAGE
                    else -> ""
                }
            }
        }
    }

    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) {
            sourceDir = apkPath
        }
        if (publicSourceDir == null) {
            publicSourceDir = apkPath
        }
    }
}

fun Context.getApplicationIcon(pkgInfo: PackageInfo): String? {
    return try {
        val appInfo = pkgInfo.applicationInfo ?: return null
        if (appInfo.sourceDir == null) return null

        if (appInfo.publicSourceDir == null) {
            appInfo.publicSourceDir = appInfo.sourceDir
        }

        val drawable = appInfo.loadIcon(packageManager)
        val bitmap = when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            else -> {
                val bmp = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
        }
        val file = File(cacheDir, "${pkgInfo.packageName}_icon.png")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        file.absolutePath
    } catch (e: Exception) {
        Logger.log("Error getting icon for ${pkgInfo.packageName}: ${e.message}", LogLevel.ERROR)
        null
    }
}

interface Type {
    fun asText(): String
}

enum class MediaType : Type {
    ANIME,
    MANGA,
    NOVEL;

    override fun asText(): String {
        return when (this) {
            ANIME -> "Anime"
            MANGA -> "Manga"
            NOVEL -> "Novel"
        }
    }

    companion object {
        fun fromText(string: String): MediaType? {
            return when (string) {
                "Anime" -> ANIME
                "Manga" -> MANGA
                "Novel" -> NOVEL
                else -> {
                    null
                }
            }
        }
    }
}
