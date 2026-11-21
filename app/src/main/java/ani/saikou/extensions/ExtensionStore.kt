package ani.saikou.extensions

import android.content.Context
import java.io.File

/** Persists which extension sources are enabled plus the configured repositories. */
object ExtensionStore {

    private const val PREFS = "saikou_extensions"
    private const val KEY_ENABLED_PREFIX = "enabled_"
    private const val KEY_ANIME_REPOS = "aniyomi_anime_repos"
    private const val KEY_MANGA_REPOS = "aniyomi_manga_repos"
    private const val KEY_CLOUDSTREAM_PLUGINS = "cloudstream_plugins"

    val DEFAULT_ANIME_REPOSITORIES = listOf(
        "https://raw.githubusercontent.com/aniyomiorg/aniyomi-anime-repo/main/index.min.json",
    )

    val DEFAULT_MANGA_REPOSITORIES = listOf(
        "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ensureDefaults(context: Context) {
        val p = prefs(context)
        if (!p.contains(KEY_ANIME_REPOS)) {
            p.edit().putStringSet(KEY_ANIME_REPOS, DEFAULT_ANIME_REPOSITORIES.toSet()).apply()
        }
        if (!p.contains(KEY_MANGA_REPOS)) {
            p.edit().putStringSet(KEY_MANGA_REPOS, DEFAULT_MANGA_REPOSITORIES.toSet()).apply()
        }
    }

    fun isSourceEnabled(context: Context, key: String): Boolean =
        prefs(context).getBoolean(KEY_ENABLED_PREFIX + key, true)

    fun setSourceEnabled(context: Context, key: String, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED_PREFIX + key, enabled).apply()
    }

    fun repositories(context: Context, isAnime: Boolean): List<String> {
        ensureDefaults(context)
        val key = if (isAnime) KEY_ANIME_REPOS else KEY_MANGA_REPOS
        return prefs(context).getStringSet(key, emptySet())?.toList()?.sorted().orEmpty()
    }

    fun addRepository(context: Context, isAnime: Boolean, url: String) {
        val key = if (isAnime) KEY_ANIME_REPOS else KEY_MANGA_REPOS
        val current = repositories(context, isAnime).toMutableSet()
        current.add(url.trim())
        prefs(context).edit().putStringSet(key, current).apply()
    }

    fun removeRepository(context: Context, isAnime: Boolean, url: String) {
        val key = if (isAnime) KEY_ANIME_REPOS else KEY_MANGA_REPOS
        val current = repositories(context, isAnime).toMutableSet()
        current.remove(url)
        prefs(context).edit().putStringSet(key, current).apply()
    }

    fun markCloudStreamPlugin(context: Context, path: String) {
        val current = prefs(context).getStringSet(KEY_CLOUDSTREAM_PLUGINS, emptySet()).orEmpty().toMutableSet()
        current.add(path)
        prefs(context).edit().putStringSet(KEY_CLOUDSTREAM_PLUGINS, current).apply()
    }

    fun cloudStreamPlugins(context: Context): List<String> =
        prefs(context).getStringSet(KEY_CLOUDSTREAM_PLUGINS, emptySet()).orEmpty().sorted()

    fun kotatsuFolder(context: Context): File =
        File(context.filesDir, "kotatsu_extensions").apply { if (!exists()) mkdirs() }

    fun cloudStreamFolder(context: Context): File =
        File(context.filesDir, "cloudstream_plugins").apply { if (!exists()) mkdirs() }

    fun animeExtensionFolder(context: Context): File =
        File(context.filesDir, "exts").apply { if (!exists()) mkdirs() }

    fun mangaExtensionFolder(context: Context): File =
        File(context.filesDir, "exts_manga").apply { if (!exists()) mkdirs() }
}
