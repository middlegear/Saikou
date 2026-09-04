package ani.saikou.media.anime.mpv

import android.util.Log
import ani.saikou.parsers.Subtitle
import `is`.xyz.mpv.MPVNode


enum class PlaybackState {
    IDLE,      // Player init
    BUFFERING, // Playing, but waiting for network/cache to catch up
    PLAYING,   // Actively playing
    PAUSED,    // Media loaded, playback paused by user
    ENDED, // Media playback reached the end

}


enum class AudioChannels(val title: String, val property: String, val value: String) {
    Auto("Auto", "audio-channels", "auto-safe"),
    AutoSafe("Auto Safe", "audio-channels", "auto"),
    Mono("Mono", "audio-channels", "mono"),
    Stereo("Stereo", "audio-channels", "stereo"),
    ReverseStereo("Reverse Stereo", "af", "pan=[stereo|c0=c1|c1=c0]"),


    Surround51("5.1 Surround", "audio-channels", "5.1,stereo"),

    //might have to remove surround 7.1
    Surround71("7.1 Surround", "audio-channels", "7.1,5.1,stereo"),
}

enum class VideoScaleMode {
    FIT,
    STRETCH,
    CROP,
    ZOOM,
    ORIGINAL
}

enum class Decoder(val title: String, val value: String) {
    AutoCopy("AutoCopy", "auto-copy"),
    Auto("Auto", "auto"),
    SW("SW", "no"),
    HW("HW", "mediacodec-copy"),
    HWPlus("HW+", "mediacodec"),
}


data class AudioTrack(
    val id: Int,
    val name: String,
    val language: String? = null,
    val codec: String? = null,
    val channels: Int? = null,
    val isDefault: Boolean = false,
    val isSelected: Boolean = false
)

data class SubtitleTrack(
    val id: Int,
    val name: String,
    val language: String?,
    val codec: String? = null,
    val isDefault: Boolean = false,
    val isSelected: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
)

data class ExternalSubtitle(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val language: String? = null,
    val label: String? = null
)

data class VideoTrack(
    val id: Int,
    val name: String,
    val codec: String? = null,
    val resolution: String? = null,
    val isSelected: Boolean = false
)

data class ExternalAudio(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val label: String? = null,
    val language: String? = null
)

object TrackParser {

    fun parseTrackList(node: MPVNode?): Triple<List<AudioTrack>, List<SubtitleTrack>, List<VideoTrack>> {
        val audio = mutableListOf<AudioTrack>()
        val subtitle = mutableListOf<SubtitleTrack>()
        val video = mutableListOf<VideoTrack>()

        val array = node?.asArray() ?: return Triple(emptyList(), emptyList(), emptyList())

        for (trackNode in array) {
            val map = trackNode.asMap() ?: continue
            val type = map["type"]?.asString() ?: continue
            val id = map["id"]?.asInt()?.toInt() ?: continue

            val lang = map["lang"]?.asString()
            val codec = map["codec"]?.asString()
            val isDefault = map["default"]?.asBoolean() ?: false
            val isSelected = map["selected"]?.asBoolean() ?: false
            val isForced = map["forced"]?.asBoolean() ?: false
            val isExternal = map["external"]?.asBoolean() ?: false

            when (type) {
                "audio" -> {
                    val channels = map["audio-channels"]?.asInt()?.toInt()
                        ?: map["demux-channel-count"]?.asInt()?.toInt()
                        ?: map["channels"]?.asInt()?.toInt()

                    val sampleRate = map["demux-samplerate"]?.asInt()?.toInt()
                    val bitrate = map["demux-bitrate"]?.asInt()?.toLong()
                        ?: map["codec-bitrate"]?.asInt()?.toLong()

                    val codecProfile = map["codec-profile"]?.asString()

                    val langName = langCodeToName(lang) ?: run {
                        val metadata = map["metadata"]?.asMap()
                        val comment = metadata?.get("comment")?.asString()
                        if (!comment.isNullOrBlank()) comment.trim()
                        else lang?.uppercase() ?: "Unknown"
                    }

                    val channelStr = when (channels) {
                        1 -> "Mono"
                        2 -> "Stereo"
                        6 -> "5.1"
                        8 -> "7.1"
                        else -> if (channels != null) "${channels}ch" else null
                    }

                    val codecStr = buildString {
                        append(codec?.uppercase() ?: "AAC")
                        if (!codecProfile.isNullOrBlank()) append(" [$codecProfile]")
                    }

                    val sampleRateStr = sampleRate?.let {
                        if (it >= 1000) "${it / 1000}kHz" else "${it}Hz"
                    }

                    val bitrateStr = bitrate?.let {
                        val kbps = it / 1000
                        if (kbps > 0) "${kbps}kbps" else null
                    }


                    val details = listOfNotNull(codecStr, channelStr, sampleRateStr, bitrateStr)
                        .joinToString(" · ")
                    val name = "$langName ($details)"

                    audio.add(
                        AudioTrack(
                            id = id,
                            name = name,
                            language = lang,
                            codec = codec,
                            channels = channels,
                            isDefault = isDefault,
                            isSelected = isSelected
                        )
                    )
                    Log.d("TrackParser", "Parsed AudioTrack → id=$id name=$name")
                }

                "sub" -> {
                    val metadata = map["metadata"]?.asMap()
                    val comment = metadata?.get("comment")?.asString()
                    val externalFilename = map["external-filename"]?.asString()
                    val title = map["title"]?.asString()

                    val langName = langCodeToName(lang)


                    val codecStr = when (codec?.lowercase()) {
                        "webvtt" -> "WebVTT"
                        "ass", "ssa" -> "ASS"
                        "subrip", "srt" -> "SRT"
                        "dvd_subtitle", "dvdsub" -> "DVD Sub"
                        "hdmv_pgs_subtitle", "pgssub" -> "PGS"
                        "mov_text" -> "MOV Text"
                        else -> codec?.uppercase()
                    }

                    val name = when {
                        !title.isNullOrBlank() -> title.trim()
                        !comment.isNullOrBlank() -> comment.trim()
                        langName != null -> langName
                        !lang.isNullOrBlank() -> lang.uppercase()
                        else -> "Subtitle $id"
                    }

                    val tags = listOfNotNull(
                        codecStr,
                        if (isExternal) "External" else null,
                        if (isForced) "Forced" else null,
                        if (isDefault) "Default" else null
                    )

                    val displayName =
                        if (tags.isNotEmpty()) "$name · ${tags.joinToString(" · ")}" else name

                    subtitle.add(
                        SubtitleTrack(
                            id = id,
                            name = displayName,
                            language = lang,
                            codec = codec,
                            isDefault = isDefault,
                            isSelected = isSelected,
                            isForced = isForced,
                            isExternal = isExternal,
                        )
                    )
                    Log.d("TrackParser", "Parsed Subtitle → id=$id name=$displayName")
                }

                "video" -> {
                    val width = map["demux-w"]?.asInt()?.toInt() ?: 0
                    val height = map["demux-h"]?.asInt()?.toInt() ?: 0
                    val fps = map["demux-fps"]?.asDouble()
                    val bitrate = map["demux-bitrate"]?.asInt()?.toLong()
                        ?: map["codec-bitrate"]?.asInt()?.toLong()
                    val codecProfile = map["codec-profile"]?.asString()

                    val resolution = if (width > 0 && height > 0) "${width}x$height" else null

                    val heightLabel = when {
                        height >= 2160 -> "4K"
                        height >= 1080 -> "1080p"
                        height >= 720 -> "720p"
                        height >= 480 -> "480p"
                        height >= 360 -> "360p"
                        height > 0 -> "${height}p"
                        else -> null
                    }

                    val codecStr = buildString {
                        append(codec?.uppercase() ?: "H264")
                        if (!codecProfile.isNullOrBlank()) append(" [$codecProfile]")
                    }

                    val fpsStr = fps?.let {
                        val rounded = Math.round(it * 10) / 10.0
                        if (rounded == rounded.toLong()
                                .toDouble()
                        ) "${rounded.toLong()}fps" else "${rounded}fps"
                    }

                    val bitrateStr = bitrate?.let {
                        val kbps = it / 1000
                        if (kbps > 0) "${kbps}kbps" else null
                    }

                    // e.g. "1080p · H264 [High] · 25fps · 2164kbps"
                    val details = listOfNotNull(codecStr, fpsStr, bitrateStr).joinToString(" · ")
                    val name =
                        if (heightLabel != null) "$heightLabel · $details" else details.ifEmpty { "Video $id" }

                    video.add(
                        VideoTrack(
                            id = id,
                            name = name,
                            codec = codec,
                            resolution = resolution,
                            isSelected = isSelected
                        )
                    )
                    Log.d("TrackParser", "Parsed VideoTrack → id=$id name=$name")
                }
            }
        }

        return Triple(audio, subtitle, video)
    }

    private fun langCodeToName(lang: String?): String? = when (lang?.lowercase()) {
        "ja", "jpn" -> "Japanese"
        "en", "eng" -> "English"
        "zh", "zho", "chi" -> "Chinese"
        "ko", "kor" -> "Korean"
        "fr", "fra", "fre" -> "French"
        "es", "spa" -> "Spanish"
        "de", "deu", "ger" -> "German"
        "ru", "rus" -> "Russian"
        "it", "ita" -> "Italian"
        "pt", "por" -> "Portuguese"
        "ar", "ara" -> "Arabic"
        "hi", "hin" -> "Hindi"
        "th", "tha" -> "Thai"
        "vi", "vie" -> "Vietnamese"
        "ms", "msa", "may" -> "Malay"
        "id", "ind" -> "Indonesian"
        "tr", "tur" -> "Turkish"
        "pl", "pol" -> "Polish"
        "cs", "ces", "cze" -> "Czech"
        "hu", "hun" -> "Hungarian"
        "nl", "nld", "dut" -> "Dutch"
        "sv", "swe" -> "Swedish"
        "no", "nor" -> "Norwegian"
        "da", "dan" -> "Danish"
        "fi", "fin" -> "Finnish"
        "he", "heb" -> "Hebrew"
        "uk", "ukr" -> "Ukrainian"
        "ro", "ron", "rum" -> "Romanian"
        "bg", "bul" -> "Bulgarian"
        "hr", "hrv" -> "Croatian"
        "sk", "slk", "slo" -> "Slovak"
        "el", "ell", "gre" -> "Greek"
        else -> null
    }
}

data class PendingMediaState(
    val videoUrl: String,
    val headers: Map<String, String>,
    val startPositionMs: Long,
    val audioTracks: List<ExternalAudio>,
    val subtitles: List<ExternalSubtitle>
)

data class PlayerEpisodeUiState(
    val mainTitle: String = "",
    val episodeTitle: String = "",
    val episodeTitles: List<String> = emptyList(),
    val currentEpisodeIndex: Int = 0,
    val subtitleTracks: List<Subtitle> = listOf(),
    val audioTracks: List<AudioTrack> = listOf(),
    var hasNextEpisode: Boolean = false,
    var hasPreviousEpisode: Boolean = false,
    val backdropUrl: String? = null,
    val logo:String?= null
)

data class PlayerScreenActions(
    val onClose: () -> Unit,
    val onNextEpisode: () -> Unit,
    val onPreviousEpisode: () -> Unit,
    val onSourceClick: () -> Unit,
)

data class TrackEpisode(
    val state: PlaybackState,
    val duration: Long,
    val position: Long,
    val hasNextEpisode: Boolean
)