package ani.saikou.extensions

import ani.saikou.FileUrl
import ani.saikou.parsers.ShowResponse

/**
 * Models that mirror what the AnyMeX extension runtime host returns as `Map<String, Any?>`.
 * The host is shared by every extension ecosystem Saikou supports (Aniyomi/Tachiyomi,
 * CloudStream and Kotatsu), so every result is normalised here once.
 */
data class AniyomiSource(
    val id: String,
    val name: String,
    val extensionName: String,
    val baseUrl: String,
    val lang: String,
    val isNsfw: Boolean,
    val iconUrl: String?,
    val version: String?,
    val pkgName: String?,
    val isAnime: Boolean,
    val hasUpdate: Boolean,
    val isObsolete: Boolean,
    val isShared: Boolean,
    val supportsLatest: Boolean,
    val supportsPopular: Boolean,
) {
    val key: String get() = "${if (isAnime) "aniyomi_anime" else "aniyomi_manga"}_$id"
    val displayName: String get() = if (lang.isBlank()) name else "$name (${lang.uppercase()})"
}

data class AvailableAniyomiExtension(
    val name: String,
    val pkgName: String,
    val version: String,
    val versionCode: Long,
    val lang: String,
    val isNsfw: Boolean,
    val iconUrl: String?,
    val apkUrl: String,
    val repository: String,
    val isAnime: Boolean,
)

data class CloudStreamProvider(
    val name: String,
    val url: String,
    val language: String,
    val plugin: String,
    val iconUrl: String?,
    val internalName: String,
    val hasSettings: Boolean,
) {
    val key: String get() = "cloudstream_$name"
    val id: String get() = name
}

data class KotatsuSource(
    val id: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val iconUrl: String?,
    val isNsfw: Boolean,
    val version: String?,
    val pkgName: String?,
) {
    val key: String get() = "kotatsu_$id"
}

data class ExtensionPreference(
    val key: String,
    val title: String,
    val summary: String?,
    val type: String,
    val value: Any?,
    val entries: List<String>,
    val entryValues: List<String>,
    val enabled: Boolean,
)

internal fun Any?.asMapList(): List<Map<String, Any?>> {
    val raw = this as? List<*> ?: return emptyList()
    return raw.mapNotNull { item ->
        (item as? Map<*, *>)?.entries?.mapNotNull { entry ->
            (entry.key as? String)?.let { key -> key to entry.value }
        }?.toMap()
    }
}

internal fun Map<String, Any?>.str(key: String): String? = this[key] as? String

internal fun Map<String, Any?>.bool(key: String): Boolean = when (val value = this[key]) {
    is Boolean -> value
    is Number -> value.toInt() == 1
    is String -> value.equals("true", ignoreCase = true)
    else -> false
}

internal fun Map<String, Any?>.stringMap(key: String): Map<String, String> {
    val raw = this[key] as? Map<*, *> ?: return emptyMap()
    return raw.entries.mapNotNull { (k, v) ->
        (k as? String)?.let { it to (v?.toString() ?: "") }
    }.toMap()
}

/** Converts any runtime-host search result map into a Saikou [ShowResponse]. */
internal fun Map<String, Any?>.toShowResponse(): ShowResponse? {
    val title = str("title") ?: str("name") ?: return null
    val link = str("url") ?: return null
    val cover = str("cover") ?: str("thumbnail_url") ?: str("posterUrl") ?: ""
    return ShowResponse(title, link, cover)
}

internal fun Map<String, Any?>.coverUrl(): String? =
    str("cover") ?: str("thumbnail_url") ?: str("posterUrl")

internal fun Map<String, Any?>.toEpisode(number: String): ani.saikou.parsers.Episode? {
    val link = str("url") ?: return null
    val thumbnail = (str("thumbnail") ?: str("thumbnail_url"))?.takeIf { it.isNotBlank() }
    return ani.saikou.parsers.Episode(
        number = number,
        link = link,
        title = str("name") ?: str("title"),
        description = str("description"),
        isFiller = bool("filler"),
        thumbnail = thumbnail?.let { FileUrl(it) },
        extra = mapOf(
            "name" to (str("name") ?: "Episode $number"),
            "number" to number,
        ),
    )
}

/** Pulls a readable episode/chapter number out of the runtime payload. */
internal fun episodeNumberOf(entry: Map<String, Any?>): String {
    val direct = (entry["episode_number"] as? Number)
        ?: (entry["chapter_number"] as? Number)
        ?: (entry["episodeNumber"] as? Number)
    if (direct != null && direct.toFloat() >= 0f) {
        val asFloat = direct.toFloat()
        return if (asFloat == asFloat.toInt().toFloat()) asFloat.toInt().toString() else asFloat.toString()
    }
    val name = entry.str("name") ?: ""
    val match = Regex("""(\d+(?:\.\d+)?)""").find(name)
    return match?.groupValues?.get(1) ?: name.ifBlank { "0" }
}

internal fun extensionNameOf(source: Map<String, Any?>): String =
    source.str("sourceName") ?: source.str("name") ?: source.str("title") ?: "Unknown source"

internal fun Map<String, Any?>.toAniyomiSource(isAnime: Boolean): AniyomiSource = AniyomiSource(
    id = str("id") ?: "",
    name = extensionNameOf(this),
    extensionName = str("name") ?: extensionNameOf(this),
    baseUrl = str("baseUrl") ?: "",
    lang = str("lang") ?: "",
    isNsfw = bool("isNsfw"),
    iconUrl = str("iconUrl")?.takeIf { it.isNotBlank() },
    version = str("version"),
    pkgName = str("pkgName"),
    isAnime = isAnime,
    hasUpdate = bool("hasUpdate"),
    isObsolete = bool("isObsolete"),
    isShared = bool("isShared"),
    supportsLatest = bool("supportsLatest"),
    supportsPopular = bool("supportsPopular"),
)

internal fun Map<String, Any?>.toAvailableExtension(isAnime: Boolean) = AvailableAniyomiExtension(
    name = str("name") ?: str("pkgName") ?: "Unknown extension",
    pkgName = str("pkgName") ?: "",
    version = str("version") ?: "",
    versionCode = (this["code"] as? Number)?.toLong() ?: 0L,
    lang = str("lang") ?: "",
    isNsfw = bool("isNsfw"),
    iconUrl = str("iconUrl")?.takeIf { it.isNotBlank() },
    apkUrl = str("apkUrl") ?: "",
    repository = str("repository") ?: "",
    isAnime = isAnime,
)

internal fun Map<String, Any?>.toCloudStreamProvider() = CloudStreamProvider(
    name = str("name") ?: str("id") ?: "Provider",
    url = str("url") ?: "",
    language = str("language") ?: "",
    plugin = str("plugin") ?: "",
    iconUrl = str("iconUrl")?.takeIf { it.isNotBlank() },
    internalName = str("internalName") ?: str("name") ?: "Provider",
    hasSettings = bool("hasSettings"),
)

internal fun Map<String, Any?>.toKotatsuSource() = KotatsuSource(
    id = str("id") ?: "",
    name = str("name") ?: "Unknown source",
    lang = str("lang") ?: "",
    baseUrl = str("baseUrl") ?: "",
    iconUrl = str("iconUrl")?.takeIf { it.isNotBlank() },
    isNsfw = bool("isNsfw"),
    version = str("version"),
    pkgName = str("pkgName"),
)

internal fun Map<String, Any?>.toExtensionPreference() = ExtensionPreference(
    key = str("key") ?: "",
    title = str("title") ?: str("key") ?: "",
    summary = str("summary"),
    type = str("type") ?: "other",
    value = this["value"],
    entries = (this["entries"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
    entryValues = (this["entryValues"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
    enabled = this["enabled"] as? Boolean ?: true,
)
