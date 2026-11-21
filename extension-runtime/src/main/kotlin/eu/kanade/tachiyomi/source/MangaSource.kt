package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc.
 */
interface MangaSource {

    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    val lang: String
        get() = ""

    val supportsLatest: Boolean
        get() = false

    fun getFilterList(): FilterList = FilterList()

    suspend fun getPopularManga(page: Int): MangasPage =
        throw UnsupportedOperationException("Popular manga not supported")

    suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        throw UnsupportedOperationException("Search not supported")

    suspend fun getLatestUpdates(page: Int): MangasPage =
        throw UnsupportedOperationException("Latest updates not supported")

    suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        return SMangaUpdate(manga, chapters)
    }

    val supportsRelatedMangas: Boolean get() = false
    val disableRelatedMangasBySearch: Boolean get() = false
    val disableRelatedMangas: Boolean get() = false

    suspend fun getRelatedMangaList(
        manga: SManga,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedManga: Pair<String, List<SManga>>, completed: Boolean) -> Unit,
    ) {
        val handler = CoroutineExceptionHandler { _, e -> exceptionHandler(e) }
        if (!disableRelatedMangas) {
            supervisorScope {
                if (supportsRelatedMangas) launch(handler) { getRelatedMangaListByExtension(manga, pushResults) }
                if (!disableRelatedMangasBySearch) launch(handler) { getRelatedMangaListBySearch(manga, pushResults) }
            }
        }
    }

    suspend fun getRelatedMangaListByExtension(
        manga: SManga,
        pushResults: suspend (relatedManga: Pair<String, List<SManga>>, completed: Boolean) -> Unit,
    ) {
        runCatching { fetchRelatedMangaList(manga) }
            .onSuccess { if (it.isNotEmpty()) pushResults(Pair("", it), false) }
            .onFailure { e ->
                System.err.println("## getRelatedMangaListByExtension: $e")
            }
    }

    suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = throw UnsupportedOperationException("Unsupported!")

    fun String.stripKeywordForRelatedMangas(): List<String> {
        val regexWhitespace = Regex("\\s+")
        val regexSpecialCharacters =
            Regex("([!~#$%^&*+_|/\\\\,?:;'“”‘’\"<>(){}\\[\\]。・～：—！？、―«»《》〘〙【】「」｜]|\\s-|-\\s|\\s\\.|\\.\\s)")
        val regexNumberOnly = Regex("^\\d+$")

        return replace(regexSpecialCharacters, " ")
            .split(regexWhitespace)
            .map {
                it.replace(regexNumberOnly, "")
                    .lowercase()
            }
            .filter { it.length > 1 }
    }

    suspend fun getRelatedMangaListBySearch(
        manga: SManga,
        pushResults: suspend (relatedManga: Pair<String, List<SManga>>, completed: Boolean) -> Unit,
    ) {
        val words = HashSet<String>()
        words.add(manga.title)
        manga.title.stripKeywordForRelatedMangas()
            .filterNot { word -> words.any { it.lowercase() == word } }
            .onEach { words.add(it) }
        if (words.isEmpty()) return

        coroutineScope {
            val filterList = getFilterList()
            words.map { keyword ->
                launch {
                    runCatching {
                        getSearchManga(1, keyword, filterList).mangas
                    }
                        .onSuccess { if (it.isNotEmpty()) pushResults(Pair(keyword, it), false) }
                        .onFailure { e ->
                            System.err.println("## getRelatedMangaListBySearch: $e")
                        }
                }
            }
        }
    }

    /**
     * Get the updated details for a manga.
     *
     * @since extensions-lib 1.5
     * @param manga the manga to update.
     * @return the updated manga.
     */
    @Suppress("DEPRECATION")
    suspend fun getMangaDetails(manga: SManga): SManga {
        return fetchMangaDetails(manga).awaitSingle()
    }

    /**
     * Get all the available chapters for a manga.
     *
     * @since extensions-lib 1.5
     * @param manga the manga to update.
     * @return the chapters for the manga.
     */
    @Suppress("DEPRECATION")
    suspend fun getChapterList(manga: SManga): List<SChapter> {
        return fetchChapterList(manga).awaitSingle()
    }

    /**
     * Get the list of pages a chapter has. Pages should be returned
     * in the expected order; the index is ignored.
     *
     * @since extensions-lib 1.5
     * @param chapter the chapter.
     * @return the pages for the chapter.
     */
    @Suppress("DEPRECATION")
    suspend fun getPageList(chapter: SChapter): List<Page> {
        return fetchPageList(chapter).awaitSingle()
    }

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getMangaDetails"),
    )
    fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getChapterList"),
    )
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getPageList"),
    )
    fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        throw IllegalStateException("Not used")
}
