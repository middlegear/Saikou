package ani.saikou.connections.anilist

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ani.saikou.R
import ani.saikou.loadData
import ani.saikou.connections.mal.MAL
import ani.saikou.media.Media
import ani.saikou.snackString
import ani.saikou.tryWithSuspend
import ani.saikou.updater.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun getUserId(context: Context, block: () -> Unit) {
    val anilist = if (Anilist.userid == null && Anilist.token != null) {
        if (Anilist.query.getUserData()) {
            tryWithSuspend {
                if (MAL.token != null && !MAL.query.getUserData())
                    snackString(context.getString(R.string.error_loading_mal_user_data))
            }
            true
        } else {
            snackString(context.getString(R.string.error_loading_anilist_user_data))
            false
        }
    } else true

    if (anilist) block.invoke()
}

class AnilistHomeViewModel : ViewModel() {
    private val listImages: MutableLiveData<ArrayList<String?>> = MutableLiveData(arrayListOf())
    fun getListImages(): LiveData<ArrayList<String?>> = listImages
    suspend fun setListImages() = listImages.postValue(Anilist.query.getBannerImages())

    private val animeContinue: MutableLiveData<ArrayList<Media>> = MutableLiveData(null)
    fun getAnimeContinue(): LiveData<ArrayList<Media>> = animeContinue
    suspend fun setAnimeContinue() = animeContinue.postValue(Anilist.query.continueMedia("ANIME"))

    private val animeFav: MutableLiveData<ArrayList<Media>> = MutableLiveData(null)
    fun getAnimeFav(): LiveData<ArrayList<Media>> = animeFav
    suspend fun setAnimeFav() = animeFav.postValue(Anilist.query.favMedia(true))

    private val animePlanned: MutableLiveData<ArrayList<Media>> = MutableLiveData(null)
    fun getAnimePlanned(): LiveData<ArrayList<Media>> = animePlanned
    suspend fun setAnimePlanned() = animePlanned.postValue(Anilist.query.continueMedia("ANIME", true))

    private val mangaContinue: MutableLiveData<ArrayList<Media>> = MutableLiveData(null)
    fun getMangaContinue(): LiveData<ArrayList<Media>> = mangaContinue
    suspend fun setMangaContinue() = mangaContinue.postValue(Anilist.query.continueMedia("MANGA"))

    private val mangaFav: MutableLiveData<ArrayList<Media>> = MutableLiveData(null)
    fun getMangaFav(): LiveData<ArrayList<Media>> = mangaFav
    suspend fun setMangaFav() = mangaFav.postValue(Anilist.query.favMedia(false))

    private val mangaPlanned: MutableLiveData<ArrayList<Media>> = MutableLiveData(null)
    fun getMangaPlanned(): LiveData<ArrayList<Media>> = mangaPlanned
    suspend fun setMangaPlanned() = mangaPlanned.postValue(Anilist.query.continueMedia("MANGA", true))

    private val recommendation: MutableLiveData<ArrayList<Media>> = MutableLiveData(null)
    fun getRecommendation(): LiveData<ArrayList<Media>> = recommendation
    suspend fun setRecommendation() = recommendation.postValue(Anilist.query.recommendations())

    suspend fun loadMain(activity: FragmentActivity) = withContext(Dispatchers.IO) {
        Anilist.getSavedToken(activity)
        MAL.getSavedToken(activity)

        val fetchedGenres = Anilist.query.getGenresAndTags(activity)
        genres.postValue(fetchedGenres)

        if (loadData<Boolean>("check_update") != false) {
            AppUpdater.check(activity, force = false)
        }
    }

    val empty = MutableLiveData<Boolean>(null)
    var loaded: Boolean = false
    val genres: MutableLiveData<Boolean?> = MutableLiveData(null)
}

class AnilistAnimeViewModel : ViewModel() {
    var searched = false
    var notSet = true
    lateinit var searchResults: SearchResults
    private val type = "ANIME"
    private val trending: MutableLiveData<MutableList<Media>> = MutableLiveData(null)

    fun getTrending(): LiveData<MutableList<Media>> = trending
    suspend fun loadTrending(i: Int) = withContext(Dispatchers.IO) {
        val (season, year) = Anilist.currentSeasons[i]
        trending.postValue(
            Anilist.query.search(
                type,
                perPage = 20,
                sort = Anilist.sortBy[2],
                season = season,
                seasonYear = year,
                hd = true
            )?.results
        )
    }

    private val updated: MutableLiveData<MutableList<Media>> = MutableLiveData(null)
    fun getUpdated(): LiveData<MutableList<Media>> = updated
    suspend fun loadUpdated() = withContext(Dispatchers.IO) {
        updated.postValue(Anilist.query.recentlyUpdated())
    }

    private val animePopular = MutableLiveData<SearchResults?>(null)
    fun getPopular(): LiveData<SearchResults?> = animePopular
    suspend fun loadPopular(
        type: String,
        search_val: String? = null,
        genres: ArrayList<String>? = null,
        sort: String = Anilist.sortBy[1],
        onList: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        animePopular.postValue(
            Anilist.query.search(
                type,
                search = search_val,
                onList = if (onList) null else false,
                sort = sort,
                genres = genres
            )
        )
    }

    suspend fun loadNextPage(r: SearchResults) = withContext(Dispatchers.IO) {
        animePopular.postValue(
            Anilist.query.search(
                r.type,
                r.page + 1,
                r.perPage,
                r.search,
                r.sort,
                r.genres,
                r.tags,
                r.format,
                r.isAdult,
                r.onList
            )
        )
    }

    var loaded: Boolean = false
}

class AnilistMangaViewModel : ViewModel() {
    var searched = false
    var notSet = true
    lateinit var searchResults: SearchResults
    private val type = "MANGA"

    private val trending: MutableLiveData<MutableList<Media>> = MutableLiveData(null)
    fun getTrending(): LiveData<MutableList<Media>> = trending
    suspend fun loadTrending() = withContext(Dispatchers.IO) {
        trending.postValue(
            Anilist.query.search(
                type,
                perPage = 10,
                sort = Anilist.sortBy[2],
                hd = true
            )?.results
        )
    }

    private val topRated: MutableLiveData<MutableList<Media>> = MutableLiveData(null)
    fun getTopRatedManga(): LiveData<MutableList<Media>> = topRated
    suspend fun loadTopRatedManga() = withContext(Dispatchers.IO) {
        topRated.postValue(
            Anilist.query.search(
                type,
                perPage = 10,
                sort = Anilist.sortBy[0],
                format = "MANGA"
            )?.results
        )
    }

    private val mangaPopular = MutableLiveData<SearchResults?>(null)
    fun getPopular(): LiveData<SearchResults?> = mangaPopular
    suspend fun loadPopular(
        type: String,
        search_val: String? = null,
        genres: ArrayList<String>? = null,
        sort: String = Anilist.sortBy[1],
        onList: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        mangaPopular.postValue(
            Anilist.query.search(
                type,
                search = search_val,
                onList = if (onList) null else false,
                sort = sort,
                genres = genres
            )
        )
    }

    suspend fun loadNextPage(r: SearchResults) = withContext(Dispatchers.IO) {
        mangaPopular.postValue(
            Anilist.query.search(
                r.type,
                r.page + 1,
                r.perPage,
                r.search,
                r.sort,
                r.genres,
                r.tags,
                r.format,
                r.isAdult,
                r.onList,
                r.excludedGenres,
                r.excludedTags,
                r.seasonYear,
                r.season
            )
        )
    }

    var loaded: Boolean = false
}

class AnilistSearch : ViewModel() {
    var searched = false
    var notSet = true
    lateinit var searchResults: SearchResults
    private val result: MutableLiveData<SearchResults?> = MutableLiveData(null)

    fun getSearch(): LiveData<SearchResults?> = result

    suspend fun loadSearch(r: SearchResults) = withContext(Dispatchers.IO) {
        val enforcedFormat = if (r.type == "MANGA") "MANGA" else r.format
        result.postValue(
            Anilist.query.search(
                r.type,
                r.page,
                r.perPage,
                r.search,
                r.sort,
                r.genres,
                r.tags,
                enforcedFormat,
                r.isAdult,
                r.onList,
                r.excludedGenres,
                r.excludedTags,
                r.seasonYear,
                r.season
            )
        )
    }

    suspend fun loadNextPage(r: SearchResults) = withContext(Dispatchers.IO) {
        val enforcedFormat = if (r.type == "MANGA") "MANGA" else r.format
        result.postValue(
            Anilist.query.search(
                r.type,
                r.page + 1,
                r.perPage,
                r.search,
                r.sort,
                r.genres,
                r.tags,
                enforcedFormat,
                r.isAdult,
                r.onList,
                r.excludedGenres,
                r.excludedTags,
                r.seasonYear,
                r.season
            )
        )
    }
}

class GenresViewModel : ViewModel() {
    var genres: MutableMap<String, String>? = null
    var done = false
    var doneListener: (() -> Unit)? = null
    suspend fun loadGenres(genre: ArrayList<String>, listener: (Pair<String, String>) -> Unit) = withContext(Dispatchers.IO) {
        if (genres == null) {
            genres = mutableMapOf()
            Anilist.query.getGenres(genre) {
                genres!![it.first] = it.second
                listener.invoke(it)
                if (genres!!.size == genre.size) {
                    done = true
                    doneListener?.invoke()
                }
            }
        }
    }
}