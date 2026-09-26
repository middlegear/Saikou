package ani.saikou.torrserver.utils

import java.io.Serializable

enum class TorrentProfile(val displayName: String) : Serializable {
    BATTERY("Battery"),
    BALANCED("Balanced"),
    PERFORMANCE("Performance");


    private data class Preset(val bufferSizeMb: Int, val maxConnections: Int)

    private val localPreset: Preset
        get() = when (this) {
            BATTERY -> Preset(bufferSizeMb = 64, maxConnections = 25)
            BALANCED -> Preset(bufferSizeMb = 128, maxConnections = 50)
            PERFORMANCE -> Preset(bufferSizeMb = 180, maxConnections = 70)
        }

    /**
     * Values used when settings.proxyUrl points at a deployed/remote server instance
     */
    private val remotePreset: Preset
        get() = Preset(bufferSizeMb = 128, maxConnections = 40)

    fun applyTo(settings: TorrentSettings): TorrentSettings {
        val isRemote = settings.proxyUrl.isNotBlank() && settings.proxyUrl.startsWith(
            "https://",
            ignoreCase = true
        )
        val preset = if (isRemote) remotePreset else localPreset

        return settings.copy(
            profile = this,
            bufferSizeMb = preset.bufferSizeMb,
            maxConnections = preset.maxConnections,
            downloadRateLimitKb = 0,
            uploadRateLimitKb = 0,
            enableDHT = false,
            enablePEX = true,
            enableUpload = true,
            enableEncryption = false
        )
    }
}