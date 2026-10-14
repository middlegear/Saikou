package ani.saikou.torrserver.utils

import ani.saikou.loadData
import java.io.Serializable

data class TorrentioConfig(
    var providers: List<String> = DEFAULT_PROVIDERS,
    var excludedQualities: List<String> = emptyList(),
    var languages: List<String> = emptyList(),
    var sort: String = DEFAULT_SORT,
) : Serializable {

 
    fun getNormalizedSort(): String {
        val trimmed = sort.trim()
        val mappedValue = TorrentioOptions.sorts.firstOrNull {
            it.first.equals(trimmed, ignoreCase = true)
        }?.second

        return mappedValue ?: trimmed.lowercase()
    }


    fun toSegments(): List<String> = buildList {
        if (providers.isNotEmpty()) add("providers=${providers.joinToString(",")}")
        if (languages.isNotEmpty()) add("language=${languages.joinToString(",")}")
        if (excludedQualities.isNotEmpty()) add("qualityfilter=${excludedQualities.joinToString(",")}")

        val normalizedSort = getNormalizedSort()
        if (normalizedSort.isNotBlank() && !normalizedSort.equals(DEFAULT_SORT, ignoreCase = true)) {
            add("sort=$normalizedSort")
        }
    }

    companion object {
        const val KEY = "torrentio_config"
        const val DEFAULT_SORT = "quality"

        fun load(): TorrentioConfig =
            loadData<TorrentioConfig>(KEY, toast = false) ?: TorrentioConfig()

        val DEFAULT_PROVIDERS = listOf(
            "eztv", "rarbg", "1337x", "ext", "thepiratebay", "kickasstorrents",
            "torrentgalaxy", "magnetdl", "horriblesubs", "nyaasi", "tokyotosho",
            "anidex", "nekobt", "yts"
        )
    }
}

object TorrentioOptions {
    val providers = listOf(
        "YTS" to "yts", "EZTV" to "eztv", "RARBG" to "rarbg", "1337x" to "1337x",
        "EXT" to "ext", "ThePirateBay" to "thepiratebay", "KickassTorrents" to "kickasstorrents",
        "TorrentGalaxy" to "torrentgalaxy", "MagnetDL" to "magnetdl", "HorribleSubs" to "horriblesubs",
        "NyaaSi" to "nyaasi", "TokyoTosho" to "tokyotosho", "AniDex" to "anidex", "nekoBT" to "nekobt",
        "🇷🇺 Rutor" to "rutor", "🇷🇺 Rutracker" to "rutracker", "🇵🇹 Comando" to "comando",
        "🇵🇹 BluDV" to "bludv", "🇵🇹 MicoLeaoDublado" to "micoleaodublado", "🇫🇷 Torrent9" to "torrent9",
        "🇮🇹 ilCorSaRoNero" to "ilcorsaronero", "🇪🇸 MejorTorrent" to "mejortorrent",
        "🇪🇸 Wolfmax4k" to "wolfmax4k", "🇲🇽 Cinecalidad" to "cinecalidad", "🇵🇱 BestTorrents" to "besttorrents",
    )

    val qualities = listOf(
        "BluRay REMUX" to "brremux", "HDR/HDR10+/Dolby Vision" to "hdrall",
        "Dolby Vision" to "dolbyvision", "Dolby Vision + HDR" to "dolbyvisionwithhdr",
        "3D" to "threed", "Non 3D (DO NOT SELECT IF NOT SURE)" to "nonthreed",
        "4k" to "4k", "1080p" to "1080p", "720p" to "720p", "480p" to "480p",
        "Other (DVDRip/HDRip/BDRip...)" to "other", "Screener" to "scr", "Cam" to "cam", "Unknown" to "unknown",
    )

    val languages = listOf(
        "🇯🇵 Japanese" to "japanese", "🇷🇺 Russian" to "russian", "🇮🇹 Italian" to "italian",
        "🇵🇹 Portuguese" to "portuguese", "🇪🇸 Spanish" to "spanish", "🇲🇽 Latino" to "latino",
        "🇰🇷 Korean" to "korean", "🇨🇳 Chinese" to "chinese", "🇹🇼 Taiwanese" to "taiwanese",
        "🇫🇷 French" to "french", "🇩🇪 German" to "german", "🇳🇱 Dutch" to "dutch",
        "🇮🇳 Hindi" to "hindi", "🇮🇳 Telugu" to "telugu", "🇮🇳 Tamil" to "tamil",
        "🇵🇱 Polish" to "polish", "🇱🇹 Lithuanian" to "lithuanian", "🇱🇻 Latvian" to "latvian",
        "🇪🇪 Estonian" to "estonian", "🇨🇿 Czech" to "czech", "🇸🇰 Slovakian" to "slovakian",
        "🇸🇮 Slovenian" to "slovenian", "🇭🇺 Hungarian" to "hungarian", "🇷🇴 Romanian" to "romanian",
        "🇧🇬 Bulgarian" to "bulgarian", "🇷🇸 Serbian" to "serbian", "🇭🇷 Croatian" to "croatian",
        "🇺🇦 Ukrainian" to "ukrainian", "🇬🇷 Greek" to "greek", "🇩🇰 Danish" to "danish",
        "🇫🇮 Finnish" to "finnish", "🇸🇪 Swedish" to "swedish", "🇳🇴 Norwegian" to "norwegian",
        "🇹🇷 Turkish" to "turkish", "🇸🇦 Arabic" to "arabic", "🇮🇷 Persian" to "persian",
        "🇮🇱 Hebrew" to "hebrew", "🇻🇳 Vietnamese" to "vietnamese", "🇮🇩 Indonesian" to "indonesian",
        "🇲🇾 Malay" to "malay", "🇹🇭 Thai" to "thai",
    )

    val sorts = listOf(
        "By quality then seeders" to "quality",
        "By quality then size" to "qualitysize",
        "By seeders" to "seeders",
        "By size" to "size",
    )
}