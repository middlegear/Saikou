package ani.saikou.torrserver.utils

import java.io.Serializable

data class TorrentSettings(
    var profile: TorrentProfile = TorrentProfile.BALANCED,
    var enableTorrentServer: Boolean = false,
    var bufferSizeMb: Int = 128,
    var maxConnections: Int = 60,
    var downloadRateLimitKb: Int = 0,
    var uploadRateLimitKb: Int = 0,
    var enableDHT: Boolean = true,
    var enablePEX: Boolean = true,
    var enableUpload: Boolean = true,
    var enableEncryption: Boolean = false,
    var enableStatics: Boolean = false,
) : Serializable


