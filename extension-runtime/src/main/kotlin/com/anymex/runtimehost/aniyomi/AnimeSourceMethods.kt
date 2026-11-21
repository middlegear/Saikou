package com.anymex.runtimehost.aniyomi
import android.util.Log
import eu.kanade.tachiyomi.PreferenceScreen
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.network.normalizeMediaUrl
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeSourceMethods(sourceID: String, langIndex: Int = 0) : AniyomiSourceMethods {

    val source: AnimeCatalogueSource
    init {
        val manager = Injekt.get<AniyomiExtensionManager>()

        val src = manager.installedAnimeExtensions
            .asSequence()
            .flatMap { it.sources.asSequence() }
            .firstOrNull { it.id.toString() == sourceID }
            ?: throw IllegalArgumentException("Anime source with ID '$sourceID' not found.")

        source = src as? AnimeHttpSource
            ?: src as? AnimeCatalogueSource
                    ?: throw IllegalArgumentException(
                "Source with ID '$sourceID' is not an AnimeHttpSource or AnimeCatalogueSource"
            )
    }


    override var baseUrl = (source as? AnimeHttpSource)?.baseUrl
    override var parameters: Map<String, Any?>? = null

    override suspend fun getPopular(page: Int): AnimesPage {
        val result = source.getPopularAnime(page)
        result.animes.forEach { it.url = normalizeMediaUrl(it.url, baseUrl) }
        return result
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val result = source.getLatestUpdates(page)
        result.animes.forEach { it.url = normalizeMediaUrl(it.url, baseUrl) }
        return result
    }

    override suspend fun getSearchResults(query: String, page: Int): AnimesPage {
        val filterList = source.getFilterList()
        @Suppress("UNCHECKED_CAST")
        val filtersData = parameters?.get("filters") as? List<Map<String, Any?>>
        if (filtersData != null) {
            applyAnimeFilters(filterList, filtersData)
        }
        val result = source.getSearchAnime(
            page = page,
            query = query,
            filters = filterList
        )
        result.animes.forEach { it.url = normalizeMediaUrl(it.url, baseUrl) }
        return result
    }

    private fun applyAnimeFilters(filterList: eu.kanade.tachiyomi.animesource.model.AnimeFilterList, filtersData: List<Map<String, Any?>>) {
        for (i in 0 until minOf(filterList.list.size, filtersData.size)) {
            val filter = filterList.list[i]
            val data = filtersData[i]
            val state = data["state"]
            if (state != null) {
                applyAnimeFilterState(filter, state)
            }
        }
    }

    private fun applyAnimeFilterState(filter: eu.kanade.tachiyomi.animesource.model.AnimeFilter<*>, state: Any?) {
        when (filter) {
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.CheckBox -> {
                if (state is Boolean) filter.state = state
            }
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.TriState -> {
                if (state is Number) filter.state = state.toInt()
            }
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.Select<*> -> {
                if (state is Number) filter.state = state.toInt()
            }
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.Text -> {
                if (state is String) filter.state = state
            }
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.Sort -> {
                if (state is Map<*, *>) {
                    val index = (state["index"] as? Number)?.toInt() ?: filter.state?.index ?: 0
                    val ascending = (state["ascending"] as? Boolean) ?: filter.state?.ascending ?: true
                    filter.state = eu.kanade.tachiyomi.animesource.model.AnimeFilter.Sort.Selection(index, ascending)
                }
            }
            is eu.kanade.tachiyomi.animesource.model.AnimeFilter.Group<*> -> {
                if (state is List<*>) {
                    val subFilters = filter.state
                    for (j in 0 until minOf(subFilters.size, state.size)) {
                        val subFilter = subFilters[j] as? eu.kanade.tachiyomi.animesource.model.AnimeFilter<*>
                        val subState = (state[j] as? Map<*, *>)?.get("state")
                        if (subFilter != null && subState != null) {
                            applyAnimeFilterState(subFilter, subState)
                        }
                    }
                }
            }
            else -> {}
        }
    }

    override suspend fun getDetails(media: SAnime): SAnime {
        media.url = normalizeMediaUrl(media.url, baseUrl)
        val details = try {
            source.getAnimeDetails(media)
        } catch (e: UnsupportedOperationException) {
            source.getAnimeEpisodeUpdate(media, emptyList(), fetchDetails = true, fetchEpisodes = false).anime
        }
        details.url = normalizeMediaUrl(details.url, baseUrl)
        return details
    }

    override suspend fun getEpisodeList(media: SAnime): List<SEpisode> {
        media.url = normalizeMediaUrl(media.url, baseUrl)
        val episodes = try {
            source.getEpisodeList(media)
        } catch (e: UnsupportedOperationException) {
            source.getAnimeEpisodeUpdate(media, emptyList(), fetchDetails = false, fetchEpisodes = true).episodes
        }
        episodes.forEach { it.url = normalizeMediaUrl(it.url, baseUrl) }
        return episodes
    }

    private fun checkHasHosters(src: AnimeSource): Boolean {
        var current: Class<*>? = src::class.java
        while (current != null) {
            if (
                current.name == "eu.kanade.tachiyomi.animesource.online.AnimeHttpSource" ||
                current.name == "eu.kanade.tachiyomi.animesource.AnimeCatalogueSource" ||
                current.name == "eu.kanade.tachiyomi.animesource.AnimeSource"
            ) {
                return false
            }
            if (current.declaredMethods.any {
                    it.name in listOf("getHosterList", "hosterListRequest", "hosterListParse")
                }
            ) {
                return true
            }
            current = current.superclass ?: return false
        }
        return false
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        episode.url = normalizeMediaUrl(episode.url, baseUrl)
        val hasHosters = checkHasHosters(source)

        if (hasHosters) {
            val hosters = try {
                source.getHosterList(episode)
            } catch (e: Exception) {
                emptyList()
            }

            Log.d("AnymeX_Hoster", "Source '${source.name}' hasHosters=true. Found ${hosters.size} hosters for episode ${episode.name}")
            hosters.forEachIndexed { i, h ->
                Log.d("AnymeX_Hoster", "  Hoster #$i: name='${h.hosterName}', url='${h.hosterUrl}', videoListSize=${h.videoList?.size ?: 0}")
            }

            if (hosters.isNotEmpty()) {
                return hosters.flatMap { hoster ->
                    try {
                        val hosterVideos = source.getVideoList(hoster)
                        Log.d("AnymeX_Hoster", "  -> Fetched ${hosterVideos.size} videos from hoster '${hoster.hosterName}'")
                        hosterVideos.map { v ->
                            Log.d("AnymeX_Hoster", "     Video: title='${v.videoTitle}', quality='${v.quality}', url='${v.videoUrl}'")
                            val combinedTitle = if (hoster.hosterName.isNotBlank() && !v.videoTitle.contains(hoster.hosterName, ignoreCase = true)) {
                                "${hoster.hosterName} - ${v.videoTitle.ifBlank { "Default" }}"
                            } else {
                                v.videoTitle.ifBlank { hoster.hosterName.ifBlank { "Default" } }
                            }
                            v.copy(videoTitle = combinedTitle)
                        }
                    } catch (e: Exception) {
                        Log.w("AnymeX_Hoster", "  -> Failed fetching videos from hoster '${hoster.hosterName}': ${e.message}")
                        emptyList()
                    }
                }
            }
        }

        return try {
            val vids = source.getVideoList(episode)
            Log.d("AnymeX_Hoster", "source.getVideoList(episode) returned ${vids.size} videos (direct)")
            vids.forEach { v ->
                Log.d("AnymeX_Hoster", "   Direct Video: title='${v.videoTitle}', quality='${v.quality}', url='${v.videoUrl}'")
            }
            vids
        } catch (e: Exception) {
            Log.d("AnymeX_Hoster", "source.getVideoList(episode) failed: ${e.message}, falling back to getHosterList(episode)")
            try {
                val hosters = source.getHosterList(episode)
                Log.d("AnymeX_Hoster", "Fallback hosters count: ${hosters.size}")
                if (hosters.isNotEmpty()) {
                    hosters.flatMap { hoster ->
                        try {
                            Log.d("AnymeX_Hoster", "  Fallback hoster: name='${hoster.hosterName}', url='${hoster.hosterUrl}'")
                            val hosterVideos = source.getVideoList(hoster)
                            Log.d("AnymeX_Hoster", "  -> Fetched ${hosterVideos.size} videos from fallback hoster '${hoster.hosterName}'")
                            hosterVideos.map { v ->
                                Log.d("AnymeX_Hoster", "     Video: title='${v.videoTitle}', quality='${v.quality}', url='${v.videoUrl}'")
                                val combinedTitle = if (hoster.hosterName.isNotBlank() && !v.videoTitle.contains(hoster.hosterName, ignoreCase = true)) {
                                    "${hoster.hosterName} - ${v.videoTitle.ifBlank { "Default" }}"
                                } else {
                                    v.videoTitle.ifBlank { hoster.hosterName.ifBlank { "Default" } }
                                }
                                v.copy(videoTitle = combinedTitle)
                            }
                        } catch (err: Exception) {
                            Log.w("AnymeX_Hoster", "  -> Failed fetching videos from fallback hoster '${hoster.hosterName}': ${err.message}")
                            emptyList()
                        }
                    }
                } else {
                    throw e
                }
            } catch (_: Exception) {
                throw e
            }
        }
    }

    override suspend fun getChapterList(media: SAnime): List<SEpisode> =
        throw UnsupportedOperationException("Chapters are not supported in anime sources.")

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        throw UnsupportedOperationException("Pages are not supported in anime sources.")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        if (source is ConfigurableAnimeSource) {
            source.setupPreferenceScreen(screen)
        } else {
            throw NoPreferenceScreenException("This source does not support preferences.")
        }
    }

    override fun getHttpSource(): Any? = source as? AnimeHttpSource
}
class NoPreferenceScreenException(message: String) : Exception(message)
