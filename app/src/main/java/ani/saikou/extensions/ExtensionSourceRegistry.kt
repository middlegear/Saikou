package ani.saikou.extensions

import ani.saikou.Lazier
import ani.saikou.parsers.BaseParser

/**
 * Builds and caches the lazy source lists that Saikou's source pickers consume.
 *
 * The cache is rebuilt whenever [ExtensionRuntime.extensionVersion] changes, which happens
 * after extensions are installed, removed, enabled or disabled.
 */
object ExtensionSourceRegistry {

    private var builtForVersion: Long = -1L

    private var animeCache: List<Lazier<BaseParser>> = emptyList()
    private var nsfwAnimeCache: List<Lazier<BaseParser>> = emptyList()
    private var mangaCache: List<Lazier<BaseParser>> = emptyList()
    private var nsfwMangaCache: List<Lazier<BaseParser>> = emptyList()

    @Synchronized
    private fun ensureBuilt() {
        val version = ExtensionRuntime.extensionVersion
        if (version == builtForVersion) return

        val (nsfwAnime, safeAnime) = ExtensionRuntime.animeSources().partition { it.isNsfw }
        animeCache = safeAnime.map { source ->
            Lazier<BaseParser>(source.displayName) { AniyomiAnimeParser(source) }
        } + ExtensionRuntime.cloudStreamProviders().map { provider ->
            Lazier<BaseParser>(provider.name) { CloudStreamParser(provider) }
        }
        nsfwAnimeCache = nsfwAnime.map { source ->
            Lazier<BaseParser>(source.displayName) { AniyomiAnimeParser(source) }
        }

        val (nsfwManga, safeManga) = ExtensionRuntime.mangaSources().partition { it.isNsfw }
        mangaCache = safeManga.map { source ->
            Lazier<BaseParser>(source.displayName) { AniyomiMangaParser(source) }
        } + ExtensionRuntime.kotatsuSources().filterNot { it.isNsfw }.map { source ->
            Lazier<BaseParser>(source.name) { KotatsuMangaParser(source) }
        }
        nsfwMangaCache = nsfwManga.map { source ->
            Lazier<BaseParser>(source.displayName) { AniyomiMangaParser(source) }
        } + ExtensionRuntime.kotatsuSources().filter { it.isNsfw }.map { source ->
            Lazier<BaseParser>(source.name) { KotatsuMangaParser(source) }
        }

        builtForVersion = version
    }

    fun animeSources(): List<Lazier<BaseParser>> {
        ensureBuilt()
        return animeCache
    }

    fun nsfwAnimeSources(): List<Lazier<BaseParser>> {
        ensureBuilt()
        return nsfwAnimeCache
    }

    fun mangaSources(): List<Lazier<BaseParser>> {
        ensureBuilt()
        return mangaCache
    }

    fun nsfwMangaSources(): List<Lazier<BaseParser>> {
        ensureBuilt()
        return nsfwMangaCache
    }

    /** Call after installing, removing or toggling an extension. */
    fun invalidate() {
        ExtensionRuntime.extensionVersion = System.currentTimeMillis()
        builtForVersion = -1L
    }
}
