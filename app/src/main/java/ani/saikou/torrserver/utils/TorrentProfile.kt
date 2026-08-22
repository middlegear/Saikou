package ani.saikou.torrserver.utils

import java.io.Serializable

enum class TorrentProfile(val displayName: String) : Serializable {
    BATTERY("Battery"),
    BALANCED("Balanced"),
    PERFORMANCE("Performance");

    fun applyTo(settings: TorrentSettings): TorrentSettings {
        return when (this) {
            BATTERY -> settings.copy(
                profile = BATTERY,
                bufferSizeMb = 64,
                maxConnections = 25,
                downloadRateLimitKb = 0,
                uploadRateLimitKb = 0,
                enableDHT = false,
                enablePEX = true,
                enableUpload = false,
                enableEncryption = false
            )

            BALANCED -> settings.copy(
                profile = BALANCED,
                bufferSizeMb = 128,
                maxConnections = 60,
                downloadRateLimitKb = 0,
                uploadRateLimitKb = 0,
                enableDHT = true,
                enablePEX = true,
                enableUpload = true,
                enableEncryption = false
            )

            PERFORMANCE -> settings.copy(
                profile = PERFORMANCE,
                bufferSizeMb = 256,
                maxConnections = 100,
                downloadRateLimitKb = 0,
                uploadRateLimitKb = 0,
                enableDHT = true,
                enablePEX = true,
                enableUpload = true,
                enableEncryption = false
            )
        }
    }
}