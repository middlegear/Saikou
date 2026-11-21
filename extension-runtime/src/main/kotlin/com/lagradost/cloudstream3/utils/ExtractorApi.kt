package com.lagradost.cloudstream3.utils


import com.lagradost.cloudstream3.AudioFile
import com.lagradost.cloudstream3.IDownloadableMinimum
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT

import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.jsoup.Jsoup
import java.net.URI
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import com.lagradost.cloudstream3.extractors.*
import me.xdrop.fuzzywuzzy.FuzzySearch

/**
 * For use in the ConcatenatingMediaSource.
 * If features are missing (headers), please report and we can add it.
 * @param durationUs use Long.toUs() for easier input
 * */
data class PlayListItem(
    val url: String,
    val durationUs: Long,
)

/**
 * Converts Seconds to MicroSeconds, multiplication by 1_000_000
 * */
fun Long.toUs(): Long {
    return this * 1_000_000
}

/**
 * If your site has an unorthodox m3u8-like system where there are multiple smaller videos concatenated
 * use this.
 * */
@Suppress("DEPRECATION")
data class ExtractorLinkPlayList(
    override val source: String,
    override val name: String,
    val playlist: List<PlayListItem>,
    override var referer: String,
    override var quality: Int,
    override var headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob() */
    override var extractorData: String? = null,
    override var type: ExtractorLinkType,
    override var audioTracks: List<AudioFile> = emptyList(),
) : ExtractorLink(
    source = source,
    name = name,
    url = "",
    referer = referer,
    quality = quality,
    headers = headers,
    extractorData = extractorData,
    type = type,
    audioTracks = audioTracks
) {
    constructor(
        source: String,
        name: String,
        playlist: List<PlayListItem>,
        referer: String,
        quality: Int,
        isM3u8: Boolean = false,
        headers: Map<String, String> = mapOf(),
        extractorData: String? = null,
    ) : this(
        source = source,
        name = name,
        playlist = playlist,
        referer = referer,
        quality = quality,
        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
        headers = headers,
        extractorData = extractorData,
    )
}

/** Metadata about the file type used for downloads and exoplayer hint,
 * if you respond with the wrong one the file will fail to download or be played */
enum class ExtractorLinkType {
    /** Single stream of bytes no matter the actual file type */
    VIDEO,

    /** Split into several .ts files, has support for encrypted m3u8s */
    M3U8,

    /** Like m3u8 but uses xml, currently no download support */
    DASH,

    /** No support at the moment */
    TORRENT,

    /** No support at the moment */
    MAGNET;

    // See https://www.iana.org/assignments/media-types/media-types.xhtml
    fun getMimeType(): String {
        return when (this) {
            VIDEO -> "video/mp4"
            M3U8 -> "application/x-mpegURL"
            DASH -> "application/dash+xml"
            TORRENT -> "application/x-bittorrent"
            MAGNET -> "application/x-bittorrent"
        }
    }
}

private fun inferTypeFromUrl(url: String): ExtractorLinkType {
    val path = try {
        URI(url).path
    } catch (_: Throwable) {
        // don't log magnet links as errors
        null
    }
    return when {
        path?.endsWith(".m3u8") == true -> ExtractorLinkType.M3U8
        path?.endsWith(".mpd") == true -> ExtractorLinkType.DASH
        path?.endsWith(".torrent") == true -> ExtractorLinkType.TORRENT
        url.startsWith("magnet:") -> ExtractorLinkType.MAGNET
        else -> ExtractorLinkType.VIDEO
    }
}

val INFER_TYPE: ExtractorLinkType? = null

/**
 * UUID for the ClearKey DRM scheme.
 *
 *
 * ClearKey is supported on Android devices running Android 5.0 (API Level 21) and up.
 */
val CLEARKEY_UUID = UUID(-0x1d8e62a7567a4c37L, 0x781AB030AF78D30EL)

/**
 * UUID for the Widevine DRM scheme.
 *
 *
 * Widevine is supported on Android devices running Android 4.3 (API Level 18) and up.
 */
val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

/**
 * UUID for the PlayReady DRM scheme.
 *
 *
 * PlayReady is supported on all AndroidTV devices. Note that most other Android devices do not
 * provide PlayReady support.
 */
val PLAYREADY_UUID = UUID(-0x65fb0f8667bfbd7aL, -0x546d19a41f77a06bL)

suspend fun newExtractorLink(
    source: String,
    name: String,
    url: String,
    type: ExtractorLinkType? = null,
    initializer: suspend ExtractorLink.() -> Unit = { }
): ExtractorLink {

    @Suppress("DEPRECATION_ERROR")
    val builder =
        ExtractorLink(
            source = source,
            name = name,
            url = url,
            type = type ?: INFER_TYPE
        )

    builder.initializer()
    return builder
}

suspend fun newDrmExtractorLink(
    source: String,
    name: String,
    url: String,
    type: ExtractorLinkType? = null,
    uuid: UUID,
    initializer: suspend DrmExtractorLink.() -> Unit = { }
): DrmExtractorLink {

    @Suppress("DEPRECATION_ERROR")
    val builder =
        DrmExtractorLink(
            source = source,
            name = name,
            url = url,
            uuid = uuid,
            type = type ?: INFER_TYPE
        )

    builder.initializer()
    return builder
}

/** Class holds extracted DRM media info to be passed to the player.
 * @property source Name of the media source, appears on player layout.
 * @property name Title of the media, appears on player layout.
 * @property url Url string of media file
 * @property referer Referer that will be used by network request.
 * @property quality Quality of the media file
 * @property headers Headers <String, String> map that will be used by network request.
 * @property extractorData Used for getExtractorVerifierJob()
 * @property type the type of the media, use [INFER_TYPE] if you want to auto infer the type from the url
 * @property kid  Base64 value of The KID element (Key Id) contains the identifier of the key associated with a license.
 * @property key Base64 value of Key to be used to decrypt the media file.
 * @property uuid Drm UUID [WIDEVINE_UUID], [PLAYREADY_UUID], [CLEARKEY_UUID] (by default) .. etc
 * @property kty Key type "oct" (octet sequence) by default
 * @property keyRequestParameters Parameters that will used to request the key.
 * @see newDrmExtractorLink
 * */
@Suppress("DEPRECATION")
open class DrmExtractorLink private constructor(
    override val source: String,
    override val name: String,
    override val url: String,
    override var referer: String,
    override var quality: Int,
    override var headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob() */
    override var extractorData: String? = null,
    override var type: ExtractorLinkType,
    open var kid: String? = null,
    open var key: String? = null,
    open var uuid: UUID,
    open var kty: String? = null,
    open var keyRequestParameters: HashMap<String, String>,
    open var licenseUrl: String? = null,
    override var audioTracks: List<AudioFile> = emptyList(),
) : ExtractorLink(
    source, name, url, referer, quality, headers, extractorData, type, audioTracks
) {
    @Deprecated("Use newDrmExtractorLink", level = DeprecationLevel.ERROR)
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String? = null,
        quality: Int? = null,
        /** the type of the media, use INFER_TYPE if you want to auto infer the type from the url */
        type: ExtractorLinkType? = INFER_TYPE,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null,
        kid: String? = null,
        key: String? = null,
        uuid: UUID = CLEARKEY_UUID,
        kty: String? = "oct",
        keyRequestParameters: HashMap<String, String> = hashMapOf(),
        licenseUrl: String? = null,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer ?: "",
        quality = quality ?: Qualities.Unknown.value,
        headers = headers,
        extractorData = extractorData,
        type = type ?: inferTypeFromUrl(url),
        kid = kid,
        key = key,
        uuid = uuid,
        keyRequestParameters = keyRequestParameters,
        kty = kty,
        licenseUrl = licenseUrl,
    )

    @Deprecated("Use newDrmExtractorLink", level = DeprecationLevel.ERROR)
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        /** the type of the media, use INFER_TYPE if you want to auto infer the type from the url */
        type: ExtractorLinkType?,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null,
        kid: String? = null,
        key: String? = null,
        uuid: UUID = CLEARKEY_UUID,
        kty: String? = "oct",
        keyRequestParameters: HashMap<String, String> = hashMapOf(),
        licenseUrl: String? = null,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        headers = headers,
        extractorData = extractorData,
        type = type ?: inferTypeFromUrl(url),
        kid = kid,
        key = key,
        uuid = uuid,
        keyRequestParameters = keyRequestParameters,
        kty = kty,
        licenseUrl = licenseUrl,
    )
}

/** Class holds extracted media info to be passed to the player.
 * @property source Name of the media source, appears on player layout.
 * @property name Title of the media, appears on player layout.
 * @property url Url string of media file
 * @property referer Referer that will be used by network request.
 * @property quality Quality of the media file
 * @property headers Headers <String, String> map that will be used by network request.
 * @property extractorData Used for getExtractorVerifierJob()
 * @property type Extracted link type (Video, M3u8, Dash, Torrent or Magnet)
 * @property audioTracks List of separate audio tracks that can be used with this video
 * @see newExtractorLink
 * */
open class ExtractorLink
@Deprecated("Use newExtractorLink", level = DeprecationLevel.WARNING)
constructor(
    open val source: String,
    open val name: String,
    override val url: String,
    override var referer: String,
    open var quality: Int,
    override var headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob() */
    open var extractorData: String? = null,
    open var type: ExtractorLinkType,
    /** List of separate audio tracks that can be merged with this video */
    open var audioTracks: List<AudioFile> = emptyList(),
) : IDownloadableMinimum {
    val isM3u8: Boolean get() = type == ExtractorLinkType.M3U8
    val isDash: Boolean get() = type == ExtractorLinkType.DASH

    // Cached video size
    private var videoSize: Long? = null

    /**
     * Get video size in bytes with one head request. Only available for ExtractorLinkType.Video
     * @param timeoutSeconds timeout of the head request.
     */
    suspend fun getVideoSize(timeoutSeconds: Long = 3L): Long? {
        // Content-Length is not applicable to other types of formats
        if (this.type != ExtractorLinkType.VIDEO) return null

        videoSize = videoSize ?: runCatching {
            // val response = app.head(this.url, headers = headers, referer = referer, timeout = timeoutSeconds)
            // response.headers["Content-Length"]?.toLong()
            null
        }.getOrNull() as Long?

        return videoSize
    }

    fun getAllHeaders(): Map<String, String> {
        if (referer.isBlank()) {
            return headers
        } else if (headers.keys.none { it.equals("referer", ignoreCase = true) }) {
            return headers + mapOf("referer" to referer)
        }
        return headers
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use newExtractorLink", level = DeprecationLevel.ERROR)
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String? = null,
        quality: Int? = null,
        /** the type of the media, use INFER_TYPE if you want to auto infer the type from the url */
        type: ExtractorLinkType? = INFER_TYPE,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer ?: "",
        quality = quality ?: Qualities.Unknown.value,
        headers = headers,
        extractorData = extractorData,
        type = type ?: inferTypeFromUrl(url)
    )

    @Suppress("DEPRECATION")
    @Deprecated("Use newExtractorLink", level = DeprecationLevel.ERROR)
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        /** the type of the media, use INFER_TYPE if you want to auto infer the type from the url */
        type: ExtractorLinkType?,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        headers = headers,
        extractorData = extractorData,
        type = type ?: inferTypeFromUrl(url)
    )

    /**
     * Old constructor without isDash, allows for backwards compatibility with extensions.
     * Should be removed after all extensions have updated their cloudstream.jar
     **/
    @Suppress("DEPRECATION_ERROR")
    @Deprecated("Use newExtractorLink", level = DeprecationLevel.ERROR)
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        isM3u8: Boolean = false,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null
    ) : this(source, name, url, referer, quality, isM3u8, headers, extractorData, false)

    @Suppress("DEPRECATION")
    @Deprecated("Use newExtractorLink", level = DeprecationLevel.ERROR)
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        isM3u8: Boolean = false,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null,
        isDash: Boolean,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        headers = headers,
        extractorData = extractorData,
        type = if (isDash) ExtractorLinkType.DASH else if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
    )

    override fun toString(): String {
        return "ExtractorLink(name=$name, url=$url, referer=$referer, type=$type)"
    }
}

/**
 * Removes https:// and www.
 * To match urls regardless of schema, perhaps Uri() can be used?
 */
val schemaStripRegex = Regex("""^(https:|)//(www\.|)""")

enum class Qualities(var value: Int, val defaultPriority: Int) {
    Unknown(400, 4),
    P144(144, 0), // 144p
    P240(240, 2), // 240p
    P360(360, 3), // 360p
    P480(480, 4), // 480p
    P720(720, 5), // 720p
    P1080(1080, 6), // 1080p
    P1440(1440, 7), // 1440p
    P2160(2160, 8); // 4k or 2160p

    companion object {
        fun getStringByInt(qual: Int?): String {
            return when (qual) {
                0 -> "Auto"
                Unknown.value -> ""
                P2160.value -> "4K"
                null -> ""
                else -> "${qual}p"
            }
        }

        fun getStringByIntFull(quality: Int): String {
            return when (quality) {
                0 -> "Auto"
                Unknown.value -> "Unknown"
                P2160.value -> "4K"
                else -> "${quality}p"
            }
        }
    }
}

fun getQualityFromName(qualityName: String?): Int {
    if (qualityName == null)
        return Qualities.Unknown.value

    val match = qualityName.lowercase().replace("p", "").trim()
    return when (match) {
        "4k" -> Qualities.P2160
        else -> null
    }?.value ?: match.toIntOrNull() ?: Qualities.Unknown.value
}

private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,.*\)\)""")
fun getPacked(string: String): String? {
    return packedRegex.find(string)?.value
}

fun getAndUnpack(string: String): String {
    val packedText = getPacked(string)
    return JsUnpacker(packedText).unpack() ?: string
}

suspend fun unshortenLinkSafe(url: String): String {
    return try {
        if (ShortLink.isShortLink(url))
            ShortLink.unshorten(url)
        else url
    } catch (e: Exception) {
        logError(e)
        url
    }
}

suspend fun loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    return loadExtractor(
        url = url,
        referer = null,
        subtitleCallback = subtitleCallback,
        callback = callback
    )
}


/**
 * Tries to load the appropriate extractor based on link, returns true if any extractor is loaded.
 * */
@Throws(CancellationException::class)
suspend fun loadExtractor(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    // Ensure this coroutine has not timed out
    coroutineScope { ensureActive() }

    val currentUrl = unshortenLinkSafe(url)
    val compareUrl = currentUrl.lowercase().replace(schemaStripRegex, "")

    // Iterate in reverse order so the new registered ExtractorApi takes priority
    for (index in extractorApis.lastIndex downTo 0) {
        val extractor = extractorApis[index]
        if (compareUrl.startsWith(extractor.mainUrl.replace(schemaStripRegex, ""))) {
            try {
                extractor.getUrl(currentUrl, referer, subtitleCallback, callback)
            } catch (e: Exception) {
                logError(e)
                // Rethrow if we have timed out
                if (e is CancellationException) {
                    throw e
                }
            }
            return true
        }
    }

    // this is to match mirror domains - like example.com, example.net
    for (index in extractorApis.lastIndex downTo 0) {
        val extractor = extractorApis[index]
        if (FuzzySearch.partialRatio(
                extractor.mainUrl,
                currentUrl
            ) > 80
        ) {
            try {
                extractor.getUrl(currentUrl, referer, subtitleCallback, callback)
            } catch (e: Exception) {
                logError(e)
                // Rethrow if we have timed out
                if (e is CancellationException) {
                    throw e
                }
            }
            return true
        }
    }

    return false
}

val extractorApis: MutableList<ExtractorApi> = arrayListOf(
    //AllProvider(),
    Mp4Upload(),
    StreamTape(),
    StreamTapeNet(),
    ShaveTape(),
    StreamTapeXyz(),
    Watchadsontape(),

    //mixdrop extractors
    MixDropBz(),
    MixDropCh(),
    MixDropTo(),
    MixDropAg(),
    MixDrop(),
    MixDropPs(),
    Mdy(),
    MxDropTo(),
    MixDropSi(),

    XStreamCdn(),

    StreamSB(),
    Sblona(),
    Vidgomunimesb(),
    StreamSilk(),
    StreamSB1(),
    StreamSB2(),
    StreamSB3(),
    StreamSB4(),
    StreamSB5(),
    StreamSB6(),
    StreamSB7(),
    StreamSB8(),
    StreamSB9(),
    StreamSB10(),
    StreamSB11(),
    SBfull(),
    // Streamhub(), cause Streamhub2() works
    Streamhub2(),
    Ssbstream(),
    Sbthe(),
    Vidgomunime(),
    Sbflix(),
    Streamsss(),
    Sbspeed(),
    Sbsonic(),
    Sbface(),
    Sbrapid(),
    Lvturbo(),

    Fastream(),
    Videa(),
    FEmbed(),
    FeHD(),
    Fplayer(),
    DBfilm(),
    Luxubu(),
    LayarKaca(),
    Rasacintaku(),
    FEnet(),
    Kotakajair(),
    Cdnplayer(),
    //  WatchSB(), 'cause StreamSB.kt works
    Uqload(),
    Uqload1(),
    Uqload2(),
    Uqloadcx(),
    Uqloadbz(),
    Evoload(),
    Evoload1(),
    UpstreamExtractor(),

    Odnoklassniki(),
    TauVideo(),
    SibNet(),
    ContentX(),
    Hotlinger(),
    FourCX(),
    PlayRu(),
    FourPlayRu(),
    Pichive(),
    FourPichive(),
    HDMomPlayer(),
    HDPlayerSystem(),
    VideoSeyred(),
    PeaceMakerst(),
    HDStreamAble(),
    RapidVid(),
    TRsTX(),
    VidMoxy(),
    Sobreatsesuyp(),
    PixelDrain(),
    PixelDrainDev(),
    MailRu(),

    OkRuSSL(),
    OkRuSSLMobile(),
    OkRuHTTP(),
    OkRuHTTPMobile(),
    Sendvid(),

    // dood extractors
    DoodCxExtractor(),
    DoodPmExtractor(),
    DoodToExtractor(),
    DoodSoExtractor(),
    DoodLaExtractor(),
    Dooood(),
    D0000d(),
    D000dCom(),
    DoodstreamCom(),
    DoodWsExtractor(),
    DoodShExtractor(),
    DoodWatchExtractor(),
    DoodWfExtractor(),
    DoodYtExtractor(),
    Doodspro(),
    Dsvplay(),

    // GenericM3U8(),
    Zplayer(),
    ZplayerV2(),
    Upstream(),

    Maxstream(),
    Tantifilm(),
    Userload(),
    Supervideo(),

    // StreamSB.kt works
    //  SBPlay(),
    //  SBPlay1(),
    //  SBPlay2(),

    PlayerVoxzer(),

    Blogger(),
    YourUpload(),

    Hxfile(),
    KotakAnimeid(),
    Neonime8n(),
    Neonime7n(),
    Yufiles(),
    Aico(),

    JWPlayer(),
    Meownime(),
    DesuArcg(),
    DesuOdchan(),
    DesuOdvip(),
    DesuDrive(),


    Keephealth(),
    Sbnet(),
    Sbasian(),
    Sblongvu(),
    Fembed9hd(),
    StreamM4u(),
    Krakenfiles(),
    Gofile(),
    Vicloud(),
    Uservideo(),
    Userscloud(),

    Movhide(),
    StreamhideCom(),
    StreamhideTo(),
    Wibufile(),
    FileMoonIn(),
    Moviesm4u(),
    Filesim(),
    Multimoviesshg(),
    Ahvsh(),
    Guccihide(),
    FileMoon(),
    FileMoonSx(),
    FilemoonV2(),

    Vido(),
    Linkbox(),
    Acefile(),
    Minoplres(), // formerly SpeedoStream
    Embedgram(),
    Mvidoo(),
    Streamplay(),
    Vidmoly(),
    Vidmolyme(),
    Vidmolyto(),
    Vidmolybiz(),
    Voe(),
    Voe1(),
    Tubeless(),
    Moviehab(),
    MoviehabNet(),
    Jeniusplay(),
    StreamoUpload(),
    Streamup(),
    Streamix(),
    Vidara(),

    GamoVideo(),
    Gdriveplayerapi(),
    Gdriveplayerapp(),
    Gdriveplayerfun(),
    Gdriveplayerio(),
    Gdriveplayerme(),
    Gdriveplayerbiz(),
    Gdriveplayerorg(),
    Gdriveplayerus(),
    Gdriveplayerco(),
    GoodstreamExtractor(),
    Gdriveplayer(),
    DatabaseGdrive(),
    DatabaseGdrive2(),
    Mediafire(),

    YoutubeExtractor(),
    YoutubeShortLinkExtractor(),
    YoutubeMobileExtractor(),
    YoutubeNoCookieExtractor(),
    Streamlare(),
    PlayLtXyz(),

    Cda(),
    Dailymotion(),
    Ztreamhub(),
    Rabbitstream(),
    Dokicloud(),
    Megacloud(),
    VidhideExtractor(),
    VidHidePro(),
    VidHidePro1(),
    VidHidePro2(),
    VidHidePro3(),
    VidHidePro4(),
    VidHidePro5(),
    VidHidePro6(),
    VidHideHub(),
    Ryderjet(),
    VidNest(),
    Dhtpre(),

    // CineMM Redirects
    Dhcplay(),
    HglinkTo(),

    // CineMM mirrors
    HgplayCDN(),
    Habetar(),
    Yuguaab(),
    Guxhag(),
    Auvexiug(),
    Xenolyzb(),
    Haxloppd(),
    Cavanhabg(),
    Dumbalag(),
    Uasopt(),

    Smoothpre(),
    Peytonepre(),
    LuluStream(),
    Lulustream1(),
    Lulustream2(),
    Luluvdoo(),
    StreamWishExtractor(),
    StreamHLS(),
    BigwarpIO(),
    BigwarpArt(),
    BgwpCC(),
    WishembedPro(),
    CdnwishCom(),
    FlaswishCom(),
    SfastwishCom(),
    Playerwish(),
    StreamEmbed(),
    EmturbovidExtractor(),
    Vtbe(),
    SecvideoOnline(),
    FsstOnline(),
    CsstOnline(),
    DsstOnline(),
    Simpulumlamerop(),
    Urochsunloath(),
    NathanFromSubject(),
    Yipsu(),
    MetaGnathTuggers(),
    Geodailymotion(),
    Mwish(),
    Dwish(),
    Ewish(),
    Kswplayer(),
    Wishfast(),
    Streamwish2(),
    Strwish(),
    Strwish2(),
    Awish(),
    Obeywish(),
    Jodwish(),
    Swhoi(),
    Multimovies(),
    UqloadsXyz(),
    Doodporn(),
    Asnwish(),
    Nekowish(),
    Nekostream(),
    Swdyu(),
    Wishonly(),
    Ds2play(),
    Ds2video(),
    Vidsonic(),
    InternetArchive(),
    VidStack(),
    GDMirrorbot(),
    Techinmind(),
    Server1uns(),
    VinovoSi(),
    VinovoTo(),
    Vidoza(),
    Videzz(),
    CloudMailRu(),
    HubCloud(),
    VkExtractor(),
    Bysezejataos(),
    ByseSX(),
    ByseVepoin(),
    ByseBuho(),
    MyVidPlay(),
    Vide0Net(),
    Up4Stream(),
    Up4FunTop(),
    GUpload(),
    HlsWish(),
    ByseQekaho(),
)


fun getExtractorApiFromName(name: String): ExtractorApi {
    for (api in extractorApis) {
        if (api.name == name) return api
    }
    return extractorApis[0]
}

fun requireReferer(name: String): Boolean {
    return getExtractorApiFromName(name).requiresReferer
}

fun httpsify(url: String): String {
    return if (url.startsWith("//")) "https:$url" else url
}

suspend fun getPostForm(requestUrl: String, html: String): String? {
    val document = Jsoup.parse(html)
    val inputs = document.select("Form > input")
    if (inputs.size < 4) return null
    var op: String? = null
    var id: String? = null
    var mode: String? = null
    var hash: String? = null

    for (input in inputs) {
        val value = input.attr("value")
        when (input.attr("name")) {
            "op" -> op = value
            "id" -> id = value
            "mode" -> mode = value
            "hash" -> hash = value
            else -> Unit
        }
    }
    if (op == null || id == null || mode == null || hash == null) {
        return null
    }
    delay(5000) // ye this is needed, wont work with 0 delay

    return null // app.post(...)
}

fun ExtractorApi.fixUrl(url: String): String {
    if (url.startsWith("http") ||
        // Do not fix JSON objects when passed as urls.
        url.startsWith("{\"")
    ) {
        return url
    }
    if (url.isEmpty()) {
        return ""
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return mainUrl + url
        }
        return "$mainUrl/$url"
    }
}

abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    /** Determines which plugin a given provider is from. This is the full path to the plugin. */
    var sourcePlugin: String? = null

    //suspend fun getSafeUrl(url: String, referer: String? = null): List<ExtractorLink>? {
    //    return safeAsync { getUrl(url, referer) }
    //}

    // this is the new extractorapi, override to add subtitles and stuff
    @Throws
    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        getUrl(url, referer)?.forEach(callback)
    }

    suspend fun getSafeUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            getUrl(url, referer, subtitleCallback, callback)
        } catch (e: Exception) {
            logError(e)
        }
    }

    /**
     * Will throw errors, use getSafeUrl if you don't want to handle the exception yourself
     */
    @Throws
    open suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? {
        return emptyList()
    }

    open fun getExtractorUrl(id: String): String {
        return id
    }
}
