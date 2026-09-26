package ani.saikou.torrserver.utils

import org.json.JSONArray
import org.json.JSONObject



fun TorrentSettings.toTorrServerJson(): JSONObject {
    val cacheSizeBytes = bufferSizeMb.toLong() * 1024L * 1024L

    return JSONObject().apply {
        put("cacheSize", cacheSizeBytes)

        put("readerReadAHead",60)

        put("preloadCache", 40)

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
        put("connectionsLimit", maxConnections.coerceIn(20, 100))
        put("peersListenPort", 0)
        put("torrentDisconnectTimeout", 25)
        put("retrackersMode", 1)
        put("enableDebug", false)
        put("enableDLNA", false)
        put("friendlyName", "")
        put("enableRutorSearch", false)
        put("enableTorznabSearch", false)
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