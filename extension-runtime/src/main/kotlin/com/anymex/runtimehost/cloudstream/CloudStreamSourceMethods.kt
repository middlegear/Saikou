package com.anymex.runtimehost.cloudstream

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

private const val TAG = "CloudStreamMethods"

private fun Any?.toRawMap(): Map<String, Any?>? {
    if (this == null) return null
    return try {
        val mapper = com.lagradost.cloudstream3.APIHolder.mapper
        val json = mapper.writeValueAsString(this)
        mapper.readValue(
            json,
            object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {}
        )
    } catch (e: Exception) {
        null
    }
}

private fun <T : Any> T?.withExtraData(
    topLevel: Map<String, Any?>,
    usedKeys: Set<String>,
    additionalExtra: Map<String, Any?> = emptyMap()
): Map<String, Any?> {
    if (this == null) return topLevel.toRawMap() ?: topLevel
    val raw = this.toRawMap() ?: emptyMap()
    val extra = raw.filterKeys { it !in usedKeys } + additionalExtra
    val composite = topLevel + mapOf("extraData" to extra)
    return composite.toRawMap() ?: composite
}

private fun isInvalidData(data: String): Boolean {
    return data.isEmpty() || data == "[]" || data == "about:blank"
}

class CloudStreamSourceMethods(val provider: MainAPI) {

    suspend fun search(query: String, page: Int): Map<String, Any?> {
        Log.i(TAG, "Searching on '${provider.name}' for '$query' (page $page)...")
        return try {
            val res = provider.search(query, page)
            if (res == null) {
                Log.w(TAG, "'${provider.name}' returned null search results.")
                return mapOf("list" to emptyList<Any>(), "hasNextPage" to false)
            }
            if (res.items.isEmpty()) {
                Log.w(TAG, "'${provider.name}' search returned 0 items.")
            } else {
                Log.i(TAG, "'${provider.name}' search returned ${res.items.size} items (hasNext = ${res.hasNext}).")
            }
            mapOf(
                "list" to res.items.map { it.toMap() },
                "hasNextPage" to res.hasNext  
            )
        } catch (e: Exception) {
            Log.e(TAG, "ERROR: '${provider.name}' search failed for query '$query'", e)
            mapOf("list" to emptyList<Any>(), "hasNextPage" to false)
        }
    }

    suspend fun getDetails(url: String): Map<String, Any?> {
        val res = provider.load(url) ?: return mapOf(
            "title" to null, "url" to url, "cover" to null,
            "description" to null, "episodes" to emptyList<Any>()
        )

        val episodes: List<Map<String, Any?>> = when (res) {
            is TvSeriesLoadResponse -> res.episodes.mapIndexed { i, ep -> episodeToMap(ep, i + 1) }
            is AnimeLoadResponse -> {
                res.episodes.flatMap { (key, epList) ->
                    epList.map { it to key }
                }
                    .distinctBy { it.first.data }
                    .mapIndexed { i, (ep, key) -> 
                        episodeToMap(ep, i + 1, mapOf("episodeGroup" to key)) 
                    }
            }
            is MovieLoadResponse -> listOf(
                mapOf(
                    "name" to res.name,
                    "url" to res.dataUrl,  
                    "episodeNumber" to 1.0,
                    "thumbnail" to res.posterUrl,
                    "description" to null,
                    "dateUpload" to null,
                    "scanlator" to null,
                    "filler" to false,
                ).withExtraData(res.toRawMap()!!, setOf("name", "dataUrl", "posterUrl"))
            )
            else -> emptyList()
        }

        val topLevel = mapOf(
            "title" to res.name,                
            "url" to res.url,
            "cover" to res.posterUrl,           
            "description" to res.plot,          
            "author" to null,
            "artist" to null,
            "genre" to (res.tags ?: emptyList()),
            "episodes" to episodes
        )
        val usedKeys = setOf("name", "url", "posterUrl", "plot", "tags")
        return res.withExtraData(topLevel, usedKeys)
    }

    suspend fun loadLinks(data: String): List<Map<String, Any?>> {
        Log.d(TAG, "loadLinks called with data: $data")
        if (isInvalidData(data)) {
            Log.w(TAG, "isInvalidData returned true for: $data")
            return emptyList()
        }
        val links = java.util.concurrent.CopyOnWriteArrayList<Map<String, Any?>>()
        val subtitles = java.util.concurrent.CopyOnWriteArrayList<Map<String, Any?>>()

        try {
            provider.loadLinks(
                data,
                false,
                { subtitle ->
                    Log.d(TAG, "Subtitle found: ${subtitle.url}")
                    subtitles.add(
                        subtitle.withExtraData(
                            mapOf(
                                "file" to subtitle.url,
                                "label" to subtitle.lang
                            ),
                            setOf("url", "lang")
                        )
                    )
                },
                { link ->
                    Log.d(TAG, "Link found: ${link.url}")
                    links.add(linkToMap(link, subtitles.toList()))
                }
            )
        } catch (e: Exception) {
            val isJsonError = e.javaClass.name.contains("JsonParseException") || 
                             e.message?.contains("Unrecognized token") == true ||
                             e.message?.contains("was expecting") == true
            
            if (isJsonError && data.startsWith("http")) {
                val wrapped = "[{\"source\":\"$data\"}]"
                Log.i(TAG, "Retrying loadLinks with wrapped JSON: $wrapped")
                try {
                    provider.loadLinks(
                        wrapped,
                        false,
                        { subtitle ->
                            Log.d(TAG, "Subtitle found (wrapped): ${subtitle.url}")
                            subtitles.add(
                                subtitle.withExtraData(
                                    mapOf(
                                        "file" to subtitle.url,
                                        "label" to subtitle.lang
                                    ),
                                    setOf("url", "lang")
                                )
                            )
                        },
                        { link ->
                            Log.d(TAG, "Link found (wrapped): ${link.url}")
                            links.add(linkToMap(link, subtitles.toList()))
                        }
                    )
                } catch (e2: Exception) {
                    Log.e(TAG, "Wrapped retry failed", e2)
                }
            } else {
                Log.e(TAG, "loadLinks failed for $data", e)
            }
        }

        if (links.isEmpty() && data.startsWith("http") && !data.contains("[{") && !data.contains("{\"")) {
            Log.i(TAG, "Smart Fallback: Calling provider.load($data)")
            var smartFallbackSucceeded = false
            try {
                val res = provider.load(data)
                val extractedData = when (res) {
                    is MovieLoadResponse -> res.dataUrl
                    is TvSeriesLoadResponse -> res.episodes.firstOrNull()?.data
                    is AnimeLoadResponse -> res.episodes.values.firstOrNull()?.firstOrNull()?.data
                    else -> null
                }

                if (extractedData != null && !isInvalidData(extractedData) && extractedData != data) {
                    Log.i(TAG, "Smart Fallback successful, found dataUrl: $extractedData. Retrying loadLinks...")
                    provider.loadLinks(
                        extractedData,
                        false,
                        { subtitle ->
                            subtitles.add(
                                subtitle.withExtraData(
                                    mapOf("file" to subtitle.url, "label" to subtitle.lang),
                                    setOf("url", "lang")
                                )
                            )
                        },
                        { link ->
                            links.add(linkToMap(link, subtitles.toList()))
                            smartFallbackSucceeded = true
                        }
                    )
                    smartFallbackSucceeded = links.isNotEmpty()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Smart Fallback failed for $data — falling through to loadExtractor", e)
            }

            if (!smartFallbackSucceeded && links.isEmpty()) {
                Log.i(TAG, "Final resort: direct loadExtractor for: $data")
                try {
                    loadExtractor(data, "", { }, { link ->
                        links.add(linkToMap(link, emptyList()))
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "Final resort loadExtractor also failed for $data", e)
                }
            }
        }

        Log.d(TAG, "loadLinks returning ${links.size} links")
        return links
    }

    suspend fun loadLinksStream(data: String, onLinkFound: (Map<String, Any?>) -> Unit) {
        Log.d(TAG, "loadLinksStream called with data: $data")
        if (isInvalidData(data)) {
            Log.w(TAG, "isInvalidData returned true for: $data")
            return
        }
        val subtitles = java.util.concurrent.CopyOnWriteArrayList<Map<String, Any?>>()
        val linksFound = java.util.concurrent.atomic.AtomicBoolean(false)

        try {
            provider.loadLinks(
                data,
                false,
                { subtitle ->
                    Log.d(TAG, "Subtitle found (stream): ${subtitle.url}")
                    subtitles.add(
                        subtitle.withExtraData(
                            mapOf(
                                "file" to subtitle.url,
                                "label" to subtitle.lang
                            ),
                            setOf("url", "lang")
                        )
                    )
                },
                { link ->
                    Log.d(TAG, "Link found (stream): ${link.url}")
                    linksFound.set(true)
                    onLinkFound(linkToMap(link, subtitles.toList()))
                }
            )
        } catch (e: Exception) {
            val isJsonError = e.javaClass.name.contains("JsonParseException") || 
                             e.message?.contains("Unrecognized token") == true ||
                             e.message?.contains("was expecting") == true

            if (isJsonError && data.startsWith("http")) {
                val wrapped = "[{\"source\":\"$data\"}]"
                Log.i(TAG, "Retrying loadLinksStream with wrapped JSON: $wrapped")
                try {
                    provider.loadLinks(
                        wrapped,
                        false,
                        { subtitle ->
                            Log.d(TAG, "Subtitle found (stream-wrapped): ${subtitle.url}")
                            subtitles.add(
                                subtitle.withExtraData(
                                    mapOf(
                                        "file" to subtitle.url,
                                        "label" to subtitle.lang
                                    ),
                                    setOf("url", "lang")
                                )
                            )
                        },
                        { link ->
                            Log.d(TAG, "Link found (stream-wrapped): ${link.url}")
                            linksFound.set(true)
                            onLinkFound(linkToMap(link, subtitles.toList()))
                        }
                    )
                } catch (e2: Exception) {
                    Log.e(TAG, "Wrapped retry (stream) failed", e2)
                }
            } else {
                Log.e(TAG, "loadLinksStream failed for $data", e)
            }
        }

        if (!linksFound.get() && data.startsWith("http") && !data.contains("[{") && !data.contains("{\"")) {
            Log.i(TAG, "Smart Fallback (stream): Calling provider.load($data)")
            var smartFallbackSucceeded = false
            try {
                val res = provider.load(data)
                val extractedData = when (res) {
                    is MovieLoadResponse -> res.dataUrl
                    is TvSeriesLoadResponse -> res.episodes.firstOrNull()?.data
                    is AnimeLoadResponse -> res.episodes.values.firstOrNull()?.firstOrNull()?.data
                    else -> null
                }

                if (extractedData != null && !isInvalidData(extractedData) && extractedData != data) {
                    Log.i(TAG, "Smart Fallback (stream) successful, found dataUrl: $extractedData. Retrying loadLinks...")
                    provider.loadLinks(
                        extractedData,
                        false,
                        { subtitle ->
                            subtitles.add(
                                subtitle.withExtraData(
                                    mapOf("file" to subtitle.url, "label" to subtitle.lang),
                                    setOf("url", "lang")
                                )
                            )
                        },
                        { link ->
                            linksFound.set(true)
                            smartFallbackSucceeded = true
                            onLinkFound(linkToMap(link, subtitles.toList()))
                        }
                    )
                    smartFallbackSucceeded = linksFound.get()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Smart Fallback (stream) failed for $data — falling through to loadExtractor", e)
            }

            if (!smartFallbackSucceeded && !linksFound.get()) {
                Log.i(TAG, "Final resort (stream): direct loadExtractor for: $data")
                try {
                    loadExtractor(data, "", { }, { link ->
                        linksFound.set(true)
                        onLinkFound(linkToMap(link, emptyList()))
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "Final resort (stream) loadExtractor also failed for $data", e)
                }
            }
        }

        Log.d(TAG, "loadLinksStream finished for $data")
    }

    private fun linkToMap(link: ExtractorLink, subtitles: List<Map<String, Any?>>): Map<String, Any?> {
        val finalHeaders = fixHeaders(link.headers, link.referer)
        val qLabel = qualityLabel(link.quality)
        val baseMap = mutableMapOf<String, Any?>(
            "url" to link.url,
            "title" to if (qLabel.isEmpty()) link.name else "${link.name} ($qLabel)",
            "quality" to qLabel,
            "headers" to finalHeaders,
            "isM3u8" to (link.type == ExtractorLinkType.M3U8),
            "subtitles" to subtitles,
            "name" to link.name,
            "referer" to link.referer,
            "qualityInt" to link.quality,
            "extractorData" to link.extractorData,
            "type" to link.type.ordinal,
            "source" to link.source,
            "isDash" to (link.type == ExtractorLinkType.DASH),
            "audioTracks" to link.audioTracks.map { mapOf("file" to it.url) }
        )

        val usedKeys = mutableSetOf(
            "url", "name", "quality", "headers", "type", "referer", 
            "extractorData", "source", "audioTracks"
        )

        if (link is DrmExtractorLink) {
            baseMap["kid"] = link.kid
            baseMap["key"] = link.key
            baseMap["uuid"] = link.uuid.toString()
            baseMap["kty"] = link.kty
            baseMap["keyRequestParameters"] = link.keyRequestParameters
            baseMap["licenseUrl"] = link.licenseUrl
            usedKeys.addAll(listOf("kid", "key", "uuid", "kty", "keyRequestParameters", "licenseUrl"))
        }

        return link.withExtraData(baseMap, usedKeys)
    }

    private fun fixHeaders(headers: Map<String, String>?, linkReferer: String?): Map<String, String> {
        val res = headers?.toMutableMap() ?: mutableMapOf()

        if (res.none { it.key.equals("Referer", ignoreCase = true) } && !linkReferer.isNullOrBlank()) {
            res["Referer"] = linkReferer
        }

        val refererKey = res.keys.find { it.equals("Referer", ignoreCase = true) }
        val referer = refererKey?.let { res[it] }

        if (!referer.isNullOrBlank() && res.none { it.key.equals("Origin", ignoreCase = true) }) {
            try {
                val u = java.net.URI(referer)
                res["Origin"] = "${u.scheme}://${u.host}${if (u.port != -1) ":${u.port}" else ""}"
            } catch (e: Exception) {
            }
        }

        return res
    }

    private fun qualityLabel(quality: Int): String = when {
        quality <= 0 || quality == 400 -> ""
        quality >= 2160 -> "4K"
        quality >= 1080 -> "1080p"
        quality >= 720 -> "720p"
        quality >= 480 -> "480p"
        quality >= 360 -> "360p"
        else -> "${quality}p"
    }
}


fun SearchResponse.toMap(): Map<String, Any?> {
    val topLevel = mapOf(
        "title" to name,          
        "url" to url,
        "apiName" to apiName,
        "cover" to posterUrl,     
        "type" to type?.ordinal,
        "id" to id,
        "quality" to quality?.ordinal,
        "score" to score?.toInt(100)
    )
    val usedKeys = setOf("name", "url", "apiName", "posterUrl", "type", "id", "quality", "score")
    return this.withExtraData(topLevel, usedKeys)
}

fun episodeToMap(
    ep: Episode, 
    fallbackNumber: Int, 
    additionalExtra: Map<String, Any?> = emptyMap()
): Map<String, Any?> {
    val rawNum = ep.episode?.toDouble() ?: fallbackNumber.toDouble()
    val topLevel = mapOf(
        "name" to ep.name,
        "url" to ep.data,              
        "episodeNumber" to rawNum,     
        "thumbnail" to ep.posterUrl,
        "description" to ep.description,
        "dateUpload" to ep.date?.toString(),
        "scanlator" to null,
        "filler" to false,
    )
    val usedKeys = setOf("name", "data", "episode", "posterUrl", "description", "date")
    return ep.withExtraData(topLevel, usedKeys, additionalExtra)
}
