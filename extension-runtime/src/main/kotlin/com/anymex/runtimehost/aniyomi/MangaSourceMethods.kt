package com.anymex.runtimehost.aniyomi

import com.anymex.runtimehost.Logger
import eu.kanade.tachiyomi.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.regex.Matcher
import java.util.regex.Pattern
import eu.kanade.tachiyomi.network.normalizeUrl
import eu.kanade.tachiyomi.network.normalizeMediaUrl
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Suppress("PrivatePropertyName")
class MangaSourceMethods(sourceID: String, langIndex: Int = 0) : AniyomiSourceMethods {

    val source: CatalogueSource

    init {
        val manager = Injekt.get<AniyomiExtensionManager>()

        val src = manager.installedMangaExtensions
            .asSequence()
            .flatMap { it.sources.asSequence() }
            .firstOrNull { it.id.toString() == sourceID }
            ?: throw IllegalArgumentException("Manga source with ID '$sourceID' not found.")

        source = src as? HttpSource
            ?: src as? CatalogueSource
                    ?: throw IllegalArgumentException(
                "Source with ID '$sourceID' is not an HttpSource or CatalogueSource"
            )
    }

    override var baseUrl = (source as? HttpSource)?.baseUrl
    override var parameters: Map<String, Any?>? = null

    override suspend fun getPopular(page: Int): AnimesPage {
        return mangaPageToAnimePage(source.getPopularManga(page))
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        return mangaPageToAnimePage(source.getLatestUpdates(page))
    }

    override suspend fun getSearchResults(query: String, page: Int): AnimesPage {
        val filterList = source.getFilterList()
        @Suppress("UNCHECKED_CAST")
        val filtersData = parameters?.get("filters") as? List<Map<String, Any?>>
        if (filtersData != null) {
            applyMangaFilters(filterList, filtersData)
        }
        return mangaPageToAnimePage(
            source.getSearchManga(
                page = page,
                query = query,
                filters = filterList
            )
        )
    }

    private fun applyMangaFilters(filterList: eu.kanade.tachiyomi.source.model.FilterList, filtersData: List<Map<String, Any?>>) {
        for (i in 0 until minOf(filterList.list.size, filtersData.size)) {
            val filter = filterList.list[i]
            val data = filtersData[i]
            val state = data["state"]
            if (state != null) {
                applyMangaFilterState(filter, state)
            }
        }
    }

    private fun applyMangaFilterState(filter: eu.kanade.tachiyomi.source.model.Filter<*>, state: Any?) {
        when (filter) {
            is eu.kanade.tachiyomi.source.model.Filter.CheckBox -> {
                if (state is Boolean) filter.state = state
            }
            is eu.kanade.tachiyomi.source.model.Filter.TriState -> {
                if (state is Number) filter.state = state.toInt()
            }
            is eu.kanade.tachiyomi.source.model.Filter.Select<*> -> {
                if (state is Number) filter.state = state.toInt()
            }
            is eu.kanade.tachiyomi.source.model.Filter.Text -> {
                if (state is String) filter.state = state
            }
            is eu.kanade.tachiyomi.source.model.Filter.Sort -> {
                if (state is Map<*, *>) {
                    val index = (state["index"] as? Number)?.toInt() ?: filter.state?.index ?: 0
                    val ascending = (state["ascending"] as? Boolean) ?: filter.state?.ascending ?: true
                    filter.state = eu.kanade.tachiyomi.source.model.Filter.Sort.Selection(index, ascending)
                }
            }
            is eu.kanade.tachiyomi.source.model.Filter.Group<*> -> {
                if (state is List<*>) {
                    val subFilters = filter.state
                    for (j in 0 until minOf(subFilters.size, state.size)) {
                        val subFilter = subFilters[j] as? eu.kanade.tachiyomi.source.model.Filter<*>
                        val subState = (state[j] as? Map<*, *>)?.get("state")
                        if (subFilter != null && subState != null) {
                            applyMangaFilterState(subFilter, subState)
                        }
                    }
                }
            }
            else -> {}
        }
    }

    override suspend fun getDetails(media: SAnime): SAnime {
        val smanga = media.toSManga()
        try {
            val update = try {
                source.getMangaUpdate(smanga, emptyList(), fetchDetails = true, fetchChapters = true)
            } catch (e: Throwable) {
                if (e is UnsupportedOperationException || e is IllegalStateException || e is AbstractMethodError || e is NoSuchMethodError) {
                    val d = source.getMangaDetails(smanga)
                    val c = try { source.getChapterList(smanga) } catch (_: Throwable) { emptyList() }
                    eu.kanade.tachiyomi.source.model.SMangaUpdate(d, c)
                } else {
                    throw e
                }
            }
            if (update.chapters.isNotEmpty()) {
                cachedChapters[smanga.url] = update.chapters
                update.chapters.forEach { ch ->
                    val memo = ch.memo
                    if (memo != null && memo.isNotEmpty()) {
                        chapterMemoCache[ch.url] = memo
                    }
                }
            }
            return update.manga.toSAnime(smanga.url)
        } catch (e: Throwable) {
            Logger.log("getDetails failed: message=${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override suspend fun getChapterList(media: SAnime): List<SEpisode> {
        val smanga = media.toSManga()
        val cached = cachedChapters.remove(smanga.url)
        val chapters = if (!cached.isNullOrEmpty()) {
            cached
        } else {
            try {
                val update = source.getMangaUpdate(smanga, emptyList(), fetchDetails = false, fetchChapters = true)
                if (update.chapters.isNotEmpty()) {
                    update.chapters
                } else {
                    source.getChapterList(smanga)
                }
            } catch (e: Throwable) {
                if (e is UnsupportedOperationException || e is IllegalStateException || e is AbstractMethodError || e is NoSuchMethodError) {
                    source.getChapterList(smanga)
                } else {
                    throw e
                }
            }
        }
        chapters.forEach { ch ->
            val memo = ch.memo
            if (memo != null && memo.isNotEmpty()) {
                chapterMemoCache[ch.url] = memo
            }
        }
        return chapters.map { it.toSEpisode() }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.memo == null || chapter.memo?.isEmpty() == true) {
            chapterMemoCache[chapter.url]?.let { cached ->
                chapter.memo = cached
            }
        }
        return try {
            source.getPageList(chapter)
        } catch (e: Throwable) {
            val isRefreshNeeded = e.message?.contains("Refresh Chapter List", ignoreCase = true) == true
            if (isRefreshNeeded) {
                val seriesSlug = if (chapter.url.contains("/series/")) {
                    chapter.url.substringAfter("/series/").substringBefore("/chapter/")
                } else if (chapter.url.contains("/comics/")) {
                    chapter.url.substringAfter("/comics/").substringBefore("/chapter/")
                } else null

                if (!seriesSlug.isNullOrBlank()) {
                    chapter.memo = buildJsonObject {
                        put("mangaSlug", seriesSlug)
                    }
                    try {
                        return source.getPageList(chapter)
                    } catch (_: Throwable) {}
                }
            }
            throw e
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        if (source is ConfigurableSource) {
            source.setupPreferenceScreen(screen)
        } else {
            throw NoPreferenceScreenException("This source does not support preferences.")
        }
    }

    override suspend fun getEpisodeList(media: SAnime): List<SEpisode> {
        throw UnsupportedOperationException()
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        throw UnsupportedOperationException()
    }

    private fun mangaPageToAnimePage(mangaPage: MangasPage): AnimesPage {
        return AnimesPage(
            mangaPage.mangas.map { it.toSAnime() },
            mangaPage.hasNextPage
        )
    }

    fun SChapter.toSEpisode(): SEpisode {
        val chapter = this
        return SEpisode.create().apply {
            url = normalizeMediaUrl(chapter.url, baseUrl)
            name = chapter.name
            date_upload = chapter.date_upload
            episode_number = if (chapter.chapter_number >= 0f) chapter.chapter_number else findChapterNumber(chapter.name) ?: chapter.chapter_number
            fillermark = false
            scanlator = chapter.scanlator
            summary = chapter.memo?.takeIf { it.isNotEmpty() }?.toString()
            preview_url = null
        }
    }

    fun SAnime.toSManga(): SManga {
        val anime = this
        return SManga.create().apply {
            url = normalizeMediaUrl(anime.url, baseUrl)
            title = anime.title
            artist = anime.artist
            author = anime.author
            description = anime.description
            genre = anime.genre
            status = anime.status
            thumbnail_url = anime.thumbnail_url?.takeIf { it.isNotBlank() }?.normalizeUrl()
            update_strategy = UpdateStrategy.ALWAYS_UPDATE
            initialized = anime.initialized
        }
    }

    fun SManga.toSAnime(fallbackUrl: String? = null): SAnime {
        val manga = this

        return SAnime.create().apply {
            url = runCatching { manga.url }.getOrNull()?.takeIf { it.isNotBlank() }?.let { normalizeMediaUrl(it, baseUrl) }
                ?: fallbackUrl?.let { normalizeMediaUrl(it, baseUrl) }
                ?: ""
            title = runCatching { manga.title }.getOrElse { "" }
            artist = runCatching { manga.artist }.getOrNull()
            author = runCatching { manga.author }.getOrNull()
            description = runCatching { manga.description }.getOrNull()
            genre = runCatching { manga.genre }.getOrNull()
            status = runCatching { manga.status }.getOrDefault(SAnime.UNKNOWN)
            thumbnail_url = runCatching { manga.thumbnail_url }.getOrNull()?.takeIf { it.isNotBlank() }?.normalizeUrl()
            background_url = null
            update_strategy =
                runCatching { AnimeUpdateStrategy.ALWAYS_UPDATE }.getOrDefault(AnimeUpdateStrategy.ALWAYS_UPDATE)
            fetch_type = runCatching { FetchType.Episodes }.getOrDefault(FetchType.Episodes)
            season_number = runCatching { 1.0 }.getOrDefault(0.0)
            initialized = runCatching { manga.initialized }.getOrDefault(false)
        }
    }
    private fun safeTitle(manga: SManga): String =
        runCatching { manga.title }.getOrElse { "[UNINITIALIZED_TITLE]" }

    private fun safeUrl(manga: SManga): String =
        runCatching { manga.url }.getOrElse { "[UNINITIALIZED_URL]" }
    private val REGEX_ITEM = "[\\s:.\\-]*(\\d+\\.?\\d*)[\\s:.\\-]*"
    private val REGEX_PART_NUMBER = "(?<!part\\s)\\b(\\d+)\\b"
    private val REGEX_CHAPTER = "(chapter|chap|ch|c)${REGEX_ITEM}"
    fun findChapterNumber(text: String): Float? {
        val pattern: Pattern = Pattern.compile(REGEX_CHAPTER, Pattern.CASE_INSENSITIVE)
        val matcher: Matcher = pattern.matcher(text)

        return if (matcher.find()) {
            matcher.group(2)?.toFloat()
        } else {
            val failedChapterNumberPattern: Pattern =
                Pattern.compile(REGEX_PART_NUMBER, Pattern.CASE_INSENSITIVE)
            val failedChapterNumberMatcher: Matcher =
                failedChapterNumberPattern.matcher(text)
            if (failedChapterNumberMatcher.find()) {
                failedChapterNumberMatcher.group(1)?.toFloat()
            } else {
                text.toFloatOrNull()
            }
        }
    }

    override fun getHttpSource(): Any? = source as? HttpSource

    companion object {
        val chapterMemoCache = ConcurrentHashMap<String, JsonObject>()
        val cachedChapters = ConcurrentHashMap<String, List<SChapter>>()
    }
}