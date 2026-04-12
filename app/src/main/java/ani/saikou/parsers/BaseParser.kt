package ani.saikou.parsers

import ani.saikou.*
import ani.saikou.media.Media
import java.net.URLDecoder
import java.net.URLEncoder

abstract class BaseParser {

    /**
     * Name that will be shown in Source Selection
     * **/
    open val name: String = ""

    /**
     * Name used to save the ShowResponse selected by user or by autoSearch
     * **/
    open val saveName: String = ""

    /** * Set to false for sites with rotating/temporary IDs to skip caching
     **/
    open val useCache = true

    /**
     * The main URL of the Site
     * **/
    open val hostUrl: String = ""

    /**
     * override as `true` if the site **only** has NSFW media
     * **/
    open val isNSFW = false

    /**
     * mostly redundant for official app, But override if you want to add different languages
     * **/
    open val language = "English"

    /**
     *  Search for Anime/Manga/Novel, returns a List of Responses
     *
     *  use `encode(query)` to encode the query for making requests
     * **/
    abstract suspend fun search(query: String): List<ShowResponse>

    /**
     * The function app uses to auto find the anime/manga using Media data provided by anilist
     *
     * Isn't necessary to override, but recommended, if you want to improve auto search results
     * **/
    open suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        var response = if (useCache) loadSavedShowResponse(mediaObj.id) else null

        if (response != null) {
            saveShowResponse(mediaObj.id, response, selected = true)
            return response
        }

        val title = mediaObj.name ?: mediaObj.nameRomaji
        setUserText("Searching : $title")

        response = search(title).firstOrNull()

        if (response != null) {
            setUserText("Found : ${response.name}")
            if (useCache) {
                saveShowResponse(mediaObj.id, response)
            }
        } else {
            setUserText("No results found")
        }

        return response
    }

    /**
     * Used to get an existing Search Response which was selected by the user.
     * **/
    open suspend fun loadSavedShowResponse(mediaId: Int): ShowResponse? {
        if (!useCache) return null
        checkIfVariablesAreEmpty()
        return loadData("${saveName}_$mediaId")
    }

    /**
     * Used to save Shows Response using `saveName`.
     * **/
    open fun saveShowResponse(mediaId: Int, response: ShowResponse?, selected: Boolean = false) {
        if (useCache && response != null) {
            checkIfVariablesAreEmpty()
            val prefix = if (selected) "Selected" else "Found"
            setUserText("$prefix : ${response.name}")
            saveData("${saveName}_$mediaId", response)
        }
    }

    fun checkIfVariablesAreEmpty() {
        if (hostUrl.isEmpty()) throw UninitializedPropertyAccessException("Please provide a `hostUrl` for the Parser")
        if (name.isEmpty()) throw UninitializedPropertyAccessException("Please provide a `name` for the Parser")
        if (saveName.isEmpty()) throw UninitializedPropertyAccessException("Please provide a `saveName` for the Parser")
    }

    open var showUserText = ""
    open var showUserTextListener: ((String) -> Unit)? = null

    /**
     * Used to show messages & errors to the User, a useful way to convey what's currently happening or what was done.
     * **/
    fun setUserText(string: String) {
        showUserText = string
        showUserTextListener?.invoke(showUserText)
    }

    fun encode(input: String): String = URLEncoder.encode(input, "utf-8").replace("+", "%20")
    fun decode(input: String): String = URLDecoder.decode(input, "utf-8")

    val defaultImage = "https://s4.anilist.co/file/anilistcdn/media/manga/cover/medium/default.jpg"
}


/**
 * A single show which contains some episodes/chapters which is sent by the site using their search function.
 *
 * You might wanna include `otherNames` & `total` too, to further improve user experience.
 *
 * You can also store a Map of Strings if you want to save some extra data.
 * **/
/**
 * A single show which contains some episodes/chapters which is sent by the site using their search function.
 *
 * You might wanna include `otherNames` & `total` too, to further improve user experience.
 *
 * You can also store a Map of Strings if you want to save some extra data.
 *
 * `episodes` field allows parsers (especially direct-API ones) to return preloaded episodes
 * during search, skipping the extra loadEpisodes() call when possible.
 **/

data class ShowResponse(
    val name: String,
    val link: String,
    val coverUrl: FileUrl,

    // Optional preloaded episodes – very useful for direct-mapped API parsers
    val episodes: List<Episode>? = emptyList(),

    // Alternative titles/synonyms – improves search matching and display
    val otherNames: List<String> = emptyList(),

    // Total number of episodes/chapters (if known from search)
    val total: Int? = null,

    // Extra arbitrary data (e.g. season, year, dub/sub flag, etc.)
    val extra: Map<String, String>? = null
) : java.io.Serializable {

    // Convenience constructors for backward compatibility and ease of use

    constructor(
        name: String,
        link: String,
        coverUrl: String,
        episodes: List<Episode>? = null,
        otherNames: List<String> = emptyList(),
        total: Int? = null,
        extra: Map<String, String>? = null
    ) : this(name, link, FileUrl(coverUrl), episodes, otherNames, total, extra)

    constructor(
        name: String,
        link: String,
        coverUrl: String,
        otherNames: List<String> = emptyList(),
        total: Int? = null,
        extra: Map<String, String>? = null
    ) : this(name, link, FileUrl(coverUrl), null, otherNames, total, extra)

    constructor(
        name: String,
        link: String,
        coverUrl: String,
        otherNames: List<String> = emptyList()
    ) : this(name, link, FileUrl(coverUrl), null, otherNames, null, null)

    constructor(
        name: String,
        link: String,
        coverUrl: String
    ) : this(name, link, FileUrl(coverUrl), null, emptyList(), null, null)
}


