package ani.saikou.parsers

import android.content.Context
import ani.saikou.*
import ani.saikou.media.Media
import ani.saikou.others.AnilistTitles
import ani.saikou.others.findBestMatch
import org.json.JSONArray
import org.json.JSONObject
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
     * Finds the best matching ShowResponse using fuzzy title comparison
     * against AniList media metadata. Reads saved entries first.
     **/
    open suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        var response = loadSavedShowResponse(mediaObj.id)

        if (response != null) {
            setUserText("Selected : ${response.name}")
            return response
        }

        val searchTitles = listOfNotNull(
            mediaObj.name,
            mediaObj.userPreferredName,
            mediaObj.nameRomaji
        ).filter { it.isNotBlank() }.distinct()

        if (searchTitles.isEmpty()) return null

        val targetTitles = AnilistTitles(
            (searchTitles + mediaObj.synonyms).filter { it.isNotBlank() }.distinct()
        )


        var searchResults: List<ShowResponse> = emptyList()
        // ddos who cares
        for (title in searchTitles) {
            setUserText("Searching : $title")
            searchResults = search(title)
            if (searchResults.isNotEmpty()) break
        }

        if (searchResults.isNotEmpty()) {
            response = findBestMatch(target = targetTitles, candidates = searchResults)
                ?: searchResults.firstOrNull()
        }

        if (response != null) {
            setUserText("Found : ${response.name}")
            saveShowResponse(mediaObj.id, response)
        } else {
            setUserText("No results found")
        }

        return response
    }

    /**
     * Reads a saved ShowResponse previously chosen by autoSearch or manually selected by the
     * user.
     **/
    open suspend fun loadSavedShowResponse(mediaId: Int): ShowResponse? {
        checkIfVariablesAreEmpty()
        return loadShowResponseJson("${saveName}_$mediaId")
    }

    open fun saveShowResponse(mediaId: Int, response: ShowResponse?, selected: Boolean = false) {
        if (response != null) {
            checkIfVariablesAreEmpty()
            val prefix = if (selected) "Selected" else "Found"
            setUserText("$prefix : ${response.name}")
            saveShowResponseJson("${saveName}_$mediaId", response)
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

private fun showResponseFileName(key: String) = "$key.showresponse.json"

private fun ShowResponse.toJson(): JSONObject = JSONObject().apply {
    put("name", name)
    put("link", link)
    put("coverUrl", coverUrl.url)
    put("coverHeaders", JSONObject(coverUrl.headers))
    put("otherNames", JSONArray(otherNames))
    if (total != null) put("total", total)
    if (extra != null) put("extra", JSONObject(extra))
}

private fun JSONObject.toShowResponse(): ShowResponse? {
    return try {
        val name = getString("name")
        val link = getString("link")
        val coverUrl = getString("coverUrl")
        val headersJson = optJSONObject("coverHeaders")
        val headers = mutableMapOf<String, String>()
        headersJson?.keys()?.forEach { k -> headers[k] = headersJson.getString(k) }

        val otherNamesJson = optJSONArray("otherNames")
        val otherNames = mutableListOf<String>()
        if (otherNamesJson != null) {
            for (i in 0 until otherNamesJson.length()) otherNames.add(otherNamesJson.getString(i))
        }

        val total = if (has("total")) getInt("total") else null

        val extraJson = optJSONObject("extra")
        val extra = if (extraJson != null) {
            mutableMapOf<String, String>().apply {
                extraJson.keys().forEach { k -> put(k, extraJson.getString(k)) }
            }
        } else null

        ShowResponse(
            name = name,
            link = link,
            coverUrl = FileUrl(coverUrl, headers),
            episodes = null,
            otherNames = otherNames,
            total = total,
            extra = extra
        )
    } catch (e: Exception) {
        null
    }
}

fun saveShowResponseJson(key: String, response: ShowResponse, context: Context? = null) {
    tryWith {
        val ctx = context ?: currContext() ?: return@tryWith
        val fileName = showResponseFileName(key)
        ctx.openFileOutput(fileName, Context.MODE_PRIVATE).use { fos ->
            fos.write(response.toJson().toString().toByteArray(Charsets.UTF_8))
        }
    }
}

fun loadShowResponseJson(key: String, context: Context? = null): ShowResponse? {
    val ctx = context ?: currContext() ?: return null
    val fileName = showResponseFileName(key)
    return try {
        if (fileName !in (ctx.fileList() ?: emptyArray())) return null
        val text = ctx.openFileInput(fileName).use { it.readBytes().toString(Charsets.UTF_8) }
        JSONObject(text).toShowResponse().also {
            if (it == null) ctx.deleteFile(fileName)
        }
    } catch (e: Exception) {
        try {
            ctx.deleteFile(fileName)
        } catch (_: Exception) {
        }
        null
    }
}