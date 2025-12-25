package ani.saikou.parsers

import ani.saikou.FileUrl
import java.io.Serializable

/**
 * Used to extract videos from a specific video host,
 *
 * A new instance is created for every embeds/iframes of that Episode
 * **/
abstract class VideoExtractor : Serializable {
    abstract val server: VideoServer
    var videos: List<Video> = listOf()
    var subtitles: List<Subtitle> = listOf()

    /** Separate audio tracks shared by all videos from this extractor (e.g. Bilibili .m4s) */
    var audioTracks: List<AudioTrack> = listOf()

    /**
     * Extracts videos, subtitles & audio tracks from the embed
     *
     * returns a container containing everything
     * **/
    abstract suspend fun extract(): VideoContainer

    /**
     * Loads videos, subtitles & audio tracks from a given Url
     *
     * & returns itself with the data loaded
     * **/
    open suspend fun load(): VideoExtractor {
        extract().also {
            videos = it.videos
            subtitles = it.subtitles
            audioTracks = it.audioTracks
            return this
        }
    }

    /**
     * Gets called when a Video from this extractor starts playing
     *
     * Useful for Extractor that require Polling
     * **/
    open suspend fun onVideoPlayed(video: Video?) {}

    /**
     * Called when a particular video has been stopped playing
     **/
    open suspend fun onVideoStopped(video: Video?) {}
}

/**
 * Represents a separate audio track that can be merged with any video stream
 * Perfect for sources that provide video-only + audio-only segments
 * */
data class AudioTrack(
    /** Direct URL to the audio file/segment */
    val url: String,

    /** Audio bitrate in kbps (e.g. 184, 128, 64) */
    val bitrate: String? = null,

    /** Language of the audio (e.g. "Japanese", "English") */
    val language: String? = null,

    /** Optional note (e.g. "Highest", "Commentary") */
    val note: String? = null,

    /** Optional headers required for this audio URL */
    val headers: Map<String, String>? = null
) : Serializable {
    constructor(url: String, bitrate:String? = null, language: String? = null, note: String? = null)
            : this(url, bitrate, language, note, null)
}

/**
 * A simple class containing name, link & extraData of the embed
 * */
data class VideoServer(
    val name: String,
    val embed: FileUrl,
    val extraData: Map<String, String>? = null,
) : Serializable {
    constructor(name: String, embedUrl: String, extraData: Map<String, String>? = null)
            : this(name, FileUrl(embedUrl), extraData)
}

/**
 * A Container for keeping videos, subtitles and shared audio tracks
 * */
data class VideoContainer(
    val videos: List<Video>,
    val subtitles: List<Subtitle> = listOf(),
    val audioTracks: List<AudioTrack> = listOf()
) : Serializable

/**
 * The Class which contains all the information about a Video
 * */
data class Video(
    /**
     * Will represent quality to user in form of "${quality}p" (1080p)
     *
     * If quality is null → "Unknown Quality"
     * If format is M3U8 and quality is null → "Multi Quality"
     * */
    val quality: Int?,

    /**
     * Mime type / Format of the video
     *
     * CONTAINER → Mp4 & Mkv (shows download button)
     * M3U8 / DASH → streaming playlists
     * */
    val format: VideoType,

    /**
     * The direct url to the Video
     *
     * Supports mp4, mkv, dash & m3u8
     * */
    val file: FileUrl,

    /**
     * File size in MB, use getSize(url) to calculate
     *
     * No need to set it on M3U8 links
     * */
    val size: Double? = null,

    /**
     * In case you want to show extra notes to the User
     *
     * Ex: "Backup", "Akamai", "Fast"
     * */
    val extraNote: String? = null,
) : Serializable {

    constructor(quality: Int? = null, videoType: VideoType, url: String, size: Double?, extraNote: String? = null)
            : this(quality, videoType, FileUrl(url), size, extraNote)

    constructor(quality: Int? = null, videoType: VideoType, url: String, size: Double?)
            : this(quality, videoType, FileUrl(url), size)

    constructor(quality: Int? = null, videoType: VideoType, url: String)
            : this(quality, videoType, FileUrl(url))
}

/**
 * The Class which contains the link to a subtitle file of a specific language
 * */
data class Subtitle(
    val language: String,
    val file: FileUrl,
    val type: SubtitleType = SubtitleType.VTT,
) : Serializable {
    constructor(language: String, url: String, type: SubtitleType = SubtitleType.VTT)
            : this(language, FileUrl(url), type)
}

enum class VideoType {
    CONTAINER, M3U8, DASH
}

enum class SubtitleType {
    VTT, ASS, SRT
}