package ani.saikou.torrserver.utils



import android.util.Log
import ani.saikou.torrserver.TorrServerApiClient
import org.json.JSONArray
import org.json.JSONObject



fun TorrentSettings.toTorrServerJson(): JSONObject {
    val cacheSizeBytes = bufferSizeMb.toLong() * 1024L * 1024L

    return JSONObject().apply {
        put("cacheSize", cacheSizeBytes)
        put("readerReadAHead", when (profile) {
            TorrentProfile.PERFORMANCE -> 98
            TorrentProfile.BALANCED -> 95
            TorrentProfile.BATTERY -> 90
        })
        put("preloadCache", when (profile) {
            TorrentProfile.PERFORMANCE -> 70
            TorrentProfile.BALANCED -> 50
            TorrentProfile.BATTERY -> 45
        })
        put("useDisk", false)
        put("removeCacheOnDrop", true)
        put("responsiveMode", true)

        put("forceEncrypt", enableEncryption)
        put("disableDHT", !enableDHT)
        put("disablePEX", !enablePEX)
        put("disableUpload", !enableUpload)
        put("disableTCP", false)
        put("disableUTP", false)
        put("disableUPNP", true)
        put("enableIPv6", false)
        put("enableLPD", false)
        put("lpdipv6", false)

        put("downloadRateLimit", downloadRateLimitKb)
        put("uploadRateLimit", uploadRateLimitKb)
        put("connectionsLimit", maxConnections.coerceIn(20, 200))
        put("peersListenPort", 0)
        put("torrentDisconnectTimeout", when (profile) {
            TorrentProfile.PERFORMANCE -> 15
            TorrentProfile.BALANCED -> 25
            TorrentProfile.BATTERY -> 30
        })

        put("retrackersMode", 1)
        put("enableDebug", false)
        put("enableDLNA", false)
        put("friendlyName", "")
        put("enableRutorSearch", false)
        put("enableTorznabSearch", false)
        put("torznabUrls", JSONArray())
        put("tmdbsettings", JSONObject().apply {
            put("apikey", "")
            put("apiurl", "https://api.themoviedb.org")
            put("imageURL", "https://image.tmdb.org")
            put("imageURLRu", "https://imagetmdb.com")
        })
        put("sslPort", 0)
        put("sslCert", "")
        put("sslKey", "")
        put("showFSActiveTorr", false)
        put("storeSettingsInJson", true)
        put("storeViewedInJson", false)
        put("trackTimecode", false)
        put("torrentsSavePath", "")
    }
}

/// just a util for testing
//suspend fun TorrentSettings.applyAndVerify(client: TorrServerApiClient): Boolean {
//    val jsonPayload = this.toTorrServerJson()
//    val updated = client.updateSettings(jsonPayload)
//    if (!updated) return false
//
//    val activeSets = client.getSettings() ?: return false
//    val registeredCache = activeSets.optLong("cacheSize")
//    val expectedCache = this.bufferSizeMb.toLong() * 1024L * 1024L
//
//    val isSuccess = registeredCache == expectedCache
//    Log.d("TorrServer", "Settings registered check: $isSuccess ($registeredCache vs $expectedCache)")
//    return isSuccess
//}