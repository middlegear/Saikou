package ani.saikou.media.anime.mpv

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import ani.saikou.R
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class MpvVideoPlayer(
    context: Context,
    attrs: AttributeSet? = null
) : BaseMPVView(context, attrs) {

    private val TAG = "mpv"

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _bufferingProgress = MutableStateFlow(0f)
    val bufferingProgress: StateFlow<Float> = _bufferingProgress.asStateFlow()


    private val _mediaLoaded = MutableStateFlow(false)
    val mediaLoaded: StateFlow<Boolean> = _mediaLoaded.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrack>> = _audioTracks.asStateFlow()
    private val defaultAudioTrack = AudioTrack(id = 0, name = "Default Audio", language = "")
    private val _currentAudioTrack = MutableStateFlow(defaultAudioTrack)
    val currentAudioTrack: StateFlow<AudioTrack> = _currentAudioTrack.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrack>> = _subtitleTracks.asStateFlow()
    private val defaultSubtitleTrack = SubtitleTrack(id = -1, name = "None/Off", language = "")
    private val _currentSubtitleTrack = MutableStateFlow(defaultSubtitleTrack)
    val currentSubtitleTrack: StateFlow<SubtitleTrack> = _currentSubtitleTrack.asStateFlow()

    private val _videoTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val videoTracks: StateFlow<List<VideoTrack>> = _videoTracks.asStateFlow()
    private val defaultVideoTrack =
        VideoTrack(id = 0, name = "Default Video", codec = null, resolution = null)
    private val _currentVideoTrack = MutableStateFlow(defaultVideoTrack)
    val currentVideoTrack: StateFlow<VideoTrack> = _currentVideoTrack.asStateFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _audioChannel = MutableStateFlow(AudioChannels.Auto)
    val audioChannel: StateFlow<AudioChannels> = _audioChannel.asStateFlow()

    private val _videoScaleMode = MutableStateFlow(VideoScaleMode.FIT)
    val videoScaleMode: StateFlow<VideoScaleMode> = _videoScaleMode.asStateFlow()

    private val _currentDecoder = MutableStateFlow(Decoder.Auto)
    val currentDecoder: StateFlow<Decoder> = _currentDecoder.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _bufferCacheDuration = MutableStateFlow(0L)
    val bufferCacheDuration: StateFlow<Long> = _bufferCacheDuration.asStateFlow()

    var isInitialized = false
        private set

    var surfaceReady = false
        private set

    private var isFileLoaded = false
    private var initialBufferingDone = false
    private var pendingMediaState: PendingMediaState? = null

    private val isShutdown = AtomicBoolean(false)
    private var configDir: String = ""
    private var cacheDir: String = ""
    private var currentSurfaceHolder: SurfaceHolder? = null

    private val targetBufferSeconds = 5.0

    private fun copyFontsForMpv(): String {
        val fontsDir = File(context.filesDir, "mpv_fonts")
        fontsDir.mkdirs()

        val fontResources = mapOf(
            "poppins_thin.ttf" to R.font.poppins_thin,
            "poppins.ttf" to R.font.poppins,
            "poppins_semi_bold.ttf" to R.font.poppins_semi_bold,
            "poppins_bold.ttf" to R.font.poppins_bold
        )

        fontResources.forEach { (filename, resId) ->
            val outFile = File(fontsDir, filename)
            if (!outFile.exists()) {
                context.resources.openRawResource(resId).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }

        return fontsDir.absolutePath
    }

    override fun initOptions() {
        mpv.setOptionString("profile", "fast")
        mpv.setOptionString("msg-level", "all=error")
        mpv.setOptionString("ytdl", "no")

        mpv.setOptionString("video-sync", "audio")
        mpv.setOptionString("initial-audio-sync", "yes")

        mpv.setOptionString("demuxer-thread", "yes")
        mpv.setOptionString("demuxer-readahead-secs", "240")

        mpv.setOptionString("cache", "yes")
        mpv.setOptionString("cache-pause", "yes")
        mpv.setOptionString("cache-pause-initial", "yes")
        mpv.setOptionString("cache-pause-wait", "5")

        val cache =
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 150 else 64) * 1024 * 1024
        mpv.setOptionString("demuxer-max-bytes", "$cache")
        mpv.setOptionString("demuxer-max-back-bytes", "$cache")

        mpv.setOptionString("framedrop", "vo")
        mpv.setOptionString("vd-lavc-framedrop", "nonkey")

        val targetDecoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Decoder.HWPlus
        } else {
            Decoder.Auto
        }
        mpv.setOptionString("hwdec", targetDecoder.value)
        _currentDecoder.value = targetDecoder

        mpv.setOptionString("keep-open", "always")
        mpv.setOptionString("volume-max", "100")
        mpv.setOptionString("volume", "100")
        mpv.setOptionString("speed", "1.0")

        val fontsDir = copyFontsForMpv()
        mpv.setOptionString("sub-fonts-dir", fontsDir)
        mpv.setOptionString("sub-font", "Poppins")
        mpv.setOptionString("sub-visibility", "yes")
        mpv.setOptionString("sub-ass-override", "force")
        mpv.setOptionString("sub-font-size", "60")
    }

    override fun postInitOptions() {
        Log.d(TAG, "postInitOptions()")
    }

    override fun observeProperties() {
        Log.d(TAG, "observeProperties()")
        mpv.observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("volume", MPV.mpvFormat.MPV_FORMAT_INT64)
        mpv.observeProperty("speed", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("track-list", MPV.mpvFormat.MPV_FORMAT_NODE)
        mpv.observeProperty("sid", MPV.mpvFormat.MPV_FORMAT_STRING)
        mpv.observeProperty("aid", MPV.mpvFormat.MPV_FORMAT_STRING)
        mpv.observeProperty("eof-reached", MPV.mpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("demuxer-cache-duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("paused-for-cache", MPV.mpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("seeking", MPV.mpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("hwdec", MPV.mpvFormat.MPV_FORMAT_STRING)
        mpv.observeProperty("hwdec-current", MPV.mpvFormat.MPV_FORMAT_STRING)
        mpv.observeProperty("cache-buffering-state", MPV.mpvFormat.MPV_FORMAT_INT64)
        mpv.observeProperty("core-idle", MPV.mpvFormat.MPV_FORMAT_FLAG)

        setupObservers()
    }

    private fun setupObservers() {
        mpv.addObserver(object : MPV.EventObserver {
            override fun eventProperty(property: String) = handlePropertyChange(property)
            override fun eventProperty(property: String, value: Long) =
                handlePropertyChange(property)
            override fun eventProperty(property: String, value: Boolean) =
                handlePropertyChange(property)
            override fun eventProperty(property: String, value: String) =
                handlePropertyChange(property)
            override fun eventProperty(property: String, value: Double) =
                handlePropertyChange(property)
            override fun eventProperty(property: String, value: MPVNode) =
                handlePropertyChange(property)

            override fun event(eventId: Int, data: MPVNode) {
                Log.d(TAG, "MPV Event: $eventId")
                when (eventId) {
                    MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                        Log.d(TAG, "EVENT_FILE_LOADED")
                        isShutdown.set(false)
                        isFileLoaded = true
                        initialBufferingDone = false
                        _bufferingProgress.value = 0f

                        _mediaLoaded.value = true

                        _playbackState.value = PlaybackState.BUFFERING

                        pendingMediaState?.audioTracks?.forEach { audio ->
                            if (audio.headers.isNotEmpty()) {
                                val headerStr =
                                    audio.headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" } + "\r\n"
                                mpv.setPropertyString("http-header-fields", headerStr)
                            }
                            val cmd = mutableListOf("audio-add", audio.url, "auto")
                            if (!audio.label.isNullOrBlank()) cmd.add(audio.label)
                            if (!audio.language.isNullOrBlank()) cmd.add(audio.language)
                            mpv.command(*cmd.toTypedArray())
                        }

                        pendingMediaState?.subtitles?.forEach { sub ->
                            if (sub.headers.isNotEmpty()) {
                                val headerStr =
                                    sub.headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" } + "\r\n"
                                mpv.setPropertyString("http-header-fields", headerStr)
                            }
                            val cmd = mutableListOf("sub-add", sub.url, "auto")
                            if (!sub.label.isNullOrBlank()) cmd.add(sub.label)
                            if (!sub.language.isNullOrBlank()) cmd.add(sub.language)
                            mpv.command(*cmd.toTypedArray())
                        }
                        refreshTracks()
                        forceUpdateDurationAndPosition()
                        if ((pendingMediaState?.startPositionMs ?: 0) > 0) {
                            seekTo(pendingMediaState!!.startPositionMs)
                        }

                        pendingMediaState = null
                        updateBufferingProgress()
                        refreshPlayerState()
                    }

                    MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                        Log.d(TAG, "EVENT_END_FILE")
                        if (pendingMediaState != null || !isFileLoaded) {
                            Log.d(TAG, "Ignoring END_FILE because new media load is pending")
                            return
                        }
                        if (_duration.value > 0L) {
                            _playbackState.value = PlaybackState.ENDED
                            _isPlaying.value = false
                        }
                        return
                    }

                    MPV.mpvEvent.MPV_EVENT_SHUTDOWN -> {
                        Log.d(TAG, "EVENT_SHUTDOWN")
                        _playbackState.value = PlaybackState.IDLE
                        _isPlaying.value = false
                        isFileLoaded = false
                        initialBufferingDone = false
                        _bufferingProgress.value = 0f

                        _mediaLoaded.value = false

                        isShutdown.set(true)
                        return
                    }
                }

                refreshPlayerState()
            }
        })
    }

    private fun forceUpdateDurationAndPosition() {
        val durSeconds = mpv.getPropertyDouble("duration") ?: 0.0
        if (durSeconds > 0.0) {
            _duration.value = (durSeconds * 1000).toLong()
        }
        val posSeconds = mpv.getPropertyDouble("time-pos") ?: 0.0
        _currentPosition.value = (posSeconds * 1000).toLong()
    }

    private fun handlePropertyChange(property: String) {
        when (property) {
            "time-pos" -> {
                val posSeconds = mpv.getPropertyDouble("time-pos") ?: 0.0
                _currentPosition.value = (posSeconds * 1000).toLong()
                if (_playbackState.value != PlaybackState.PLAYING) refreshPlayerState()
            }

            "duration" -> {
                val durSeconds = mpv.getPropertyDouble("duration") ?: 0.0
                _duration.value = (durSeconds * 1000).toLong()
            }

            "volume" -> _volume.value = mpv.getPropertyInt("volume") ?: 100
            "speed" -> _playbackSpeed.value = (mpv.getPropertyDouble("speed") ?: 1.0).toFloat()
            "track-list" -> refreshTracks()
            "vid" -> {
                val activeId = mpv.getPropertyInt("vid") ?: 0
                _currentVideoTrack.value =
                    _videoTracks.value.firstOrNull { it.id == activeId } ?: defaultVideoTrack
            }

            "aid" -> {
                val activeId = mpv.getPropertyString("aid")?.toIntOrNull() ?: 0
                _currentAudioTrack.value =
                    _audioTracks.value.firstOrNull { it.id == activeId } ?: defaultAudioTrack
            }

            "sid" -> {
                val activeId = mpv.getPropertyString("sid")?.toIntOrNull() ?: -1
                _currentSubtitleTrack.value =
                    _subtitleTracks.value.firstOrNull { it.id == activeId } ?: defaultSubtitleTrack
            }

            "demuxer-cache-duration" -> {
                val cacheSeconds = mpv.getPropertyDouble("demuxer-cache-duration") ?: 0.0
                _bufferCacheDuration.value = (cacheSeconds * 1000).toLong()
                if (!initialBufferingDone) {
                    updateBufferingProgress()
                }
            }

            "hwdec-current", "hwdec" -> {
                val currentMpvValue = mpv.getPropertyString(property) ?: "no"
                val matchedDecoder =
                    Decoder.entries.firstOrNull { it.value == currentMpvValue } ?: Decoder.Auto
                _currentDecoder.value = matchedDecoder
                Log.d(TAG, "Decoder changed ($property): $currentMpvValue -> $matchedDecoder")
            }

            "cache-buffering-state" -> {
                if (!initialBufferingDone) {
                    updateBufferingProgress()
                }
            }

            "pause" -> {
                if (!initialBufferingDone) {
                    updateBufferingProgress()
                }
                refreshPlayerState()
            }

            "paused-for-cache" -> {
                if (!initialBufferingDone) {
                    updateBufferingProgress()
                }
                refreshPlayerState()
            }

            "core-idle" -> {
                if (!initialBufferingDone) {
                    updateBufferingProgress()
                }
                refreshPlayerState()
            }

            "seeking", "eof-reached" -> refreshPlayerState()
        }
    }

    private fun updateBufferingProgress() {
        if (initialBufferingDone || !isFileLoaded) return

        val isCoreIdle = mpv.getPropertyBoolean("core-idle") ?: true
        val isPaused = mpv.getPropertyBoolean("pause") ?: true
        val cacheBufferingState = mpv.getPropertyInt("cache-buffering-state") ?: 0
        val cacheDur = mpv.getPropertyDouble("demuxer-cache-duration") ?: 0.0

        if (!isCoreIdle || cacheDur >= targetBufferSeconds || cacheBufferingState >= 100) {
            initialBufferingDone = true
            _bufferingProgress.value = 1f
            refreshPlayerState()
            return
        }

        if (isCoreIdle) {
            val progress = (cacheBufferingState / 100f).coerceIn(0f, 1f)
            if (progress > _bufferingProgress.value) {
                _bufferingProgress.value = progress
            }
        }
    }

    private fun refreshPlayerState() {
        if (isShutdown.get()) return

        val isPaused = mpv.getPropertyBoolean("pause") ?: true
        val isCoreIdle = mpv.getPropertyBoolean("core-idle") ?: true
        val filename = mpv.getPropertyString("filename")
        val eof = mpv.getPropertyBoolean("eof-reached") ?: false
        val seeking = mpv.getPropertyBoolean("seeking") ?: false
        val pausedForCache = mpv.getPropertyBoolean("paused-for-cache") ?: false

        if (isFileLoaded) {
            val posSeconds = mpv.getPropertyDouble("time-pos") ?: 0.0
            _currentPosition.value = (posSeconds * 1000).toLong()
        }

        val isActuallyPlaying = !isPaused && !isCoreIdle && !eof && filename != null

        _playbackState.value = when {
            eof -> PlaybackState.ENDED
            filename == null || !isFileLoaded -> {
                if (pendingMediaState != null) PlaybackState.BUFFERING else PlaybackState.IDLE
            }
            !initialBufferingDone -> PlaybackState.BUFFERING
            seeking || pausedForCache -> PlaybackState.BUFFERING
            isPaused -> PlaybackState.PAUSED
            isCoreIdle -> PlaybackState.BUFFERING
            else -> PlaybackState.PLAYING
        }

        _isPlaying.value = isActuallyPlaying
    }

    private fun refreshTracks() {
        val trackListNode = mpv.getPropertyNode("track-list") ?: return
        val (audio, subtitle, video) = TrackParser.parseTrackList(trackListNode)

        _audioTracks.value = audio
        _subtitleTracks.value = subtitle
        _videoTracks.value = video

        if (audio.isNotEmpty()) {
            val aidStr = mpv.getPropertyString("aid") ?: "no"
            val currentAid = aidStr.toIntOrNull() ?: 0

            if (currentAid == 0) {
                val selected = audio.firstOrNull { it.language?.lowercase() == "en" }
                    ?: audio.firstOrNull { it.language?.lowercase() == "ja" }
                    ?: audio.first()

                mpv.setPropertyInt("aid", selected.id)
                _currentAudioTrack.value = selected
            } else {
                _currentAudioTrack.value =
                    audio.firstOrNull { it.id == currentAid } ?: audio.first()
            }
        } else {
            _currentAudioTrack.value = defaultAudioTrack
        }

        if (subtitle.isNotEmpty()) {
            val sidStr = mpv.getPropertyString("sid") ?: "no"
            val currentSid = sidStr.toIntOrNull() ?: -1
            _currentSubtitleTrack.value =
                subtitle.firstOrNull { it.id == currentSid } ?: defaultSubtitleTrack
        } else {
            _currentSubtitleTrack.value = defaultSubtitleTrack
        }

        if (video.isNotEmpty()) {
            val currentVid = mpv.getPropertyInt("vid") ?: 0
            _currentVideoTrack.value = video.firstOrNull { it.id == currentVid } ?: video.first()
        } else {
            _currentVideoTrack.value = defaultVideoTrack
        }
    }

    fun init(surfaceHolder: Any) {
        if (isInitialized && !isShutdown.get()) {
            Log.w(TAG, "Player already initialized")
            return
        }
        Log.d(TAG, "init() called, isShutdown=${isShutdown.get()}")

        configDir = File(context.filesDir, "mpv_config").absolutePath
        cacheDir = context.cacheDir.absolutePath
        File(configDir).mkdirs()

        if (isShutdown.get()) {
            Log.d(TAG, "Destroying before reinit due to shutdown state")
            destroy()
            isShutdown.set(false)
        }

        initialize(configDir, cacheDir)
        initOptions()
        isInitialized = true

        currentSurfaceHolder?.let { holder ->
            if (holder.surface.isValid) {
                Log.d(TAG, "Manually re-attaching surface after reinit")
                surfaceCreated(holder)
            }
        }

        Log.d(TAG, "Player initialized successfully")
    }

    fun release() {
        if (!isInitialized) return

        Log.d(TAG, "release() called")
        destroy()

        isFileLoaded = false
        initialBufferingDone = false
        pendingMediaState = null

        _isPlaying.value = false
        _playbackState.value = PlaybackState.IDLE
        _currentPosition.value = 0L
        _duration.value = 0L
        _bufferCacheDuration.value = 0L
        _bufferingProgress.value = 0f

        _mediaLoaded.value = false

        isInitialized = false
        surfaceReady = false
        isShutdown.set(false)
        currentSurfaceHolder = null
    }

    fun play() {
        if (!isInitialized || isShutdown.get()) return
        mpv.command("set", "pause", "no")
    }

    fun pause() {
        if (!isInitialized || isShutdown.get()) return
        mpv.command("set", "pause", "yes")
    }

    fun seekTo(positionMs: Long) {
        if (!isInitialized || isShutdown.get()) return
        if (positionMs < 0) return
        val seconds = positionMs / 1000.0
        mpv.command("seek", seconds.toString(), "absolute")
    }

    fun stop() {
        if (!isInitialized || isShutdown.get()) {
            _playbackState.value = PlaybackState.IDLE
            _isPlaying.value = false
            isFileLoaded = false
            initialBufferingDone = false
            _bufferingProgress.value = 0f
            pendingMediaState = null

            _mediaLoaded.value = false

            _currentPosition.value = 0L
            _duration.value = 0L
            return
        }

        mpv.command("stop")

        _playbackState.value = PlaybackState.IDLE
        _isPlaying.value = false
        isFileLoaded = false
        initialBufferingDone = false
        _bufferingProgress.value = 0f

        _mediaLoaded.value = false

        _currentPosition.value = 0L
        _duration.value = 0L

        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        _videoTracks.value = emptyList()
    }

    fun loadMedia(
        videoUrl: String,
        headers: Map<String, String> = emptyMap(),
        startPositionMs: Long = 0L,
        audioTracks: List<ExternalAudio> = emptyList(),
        subtitles: List<ExternalSubtitle> = emptyList()
    ) {
        val mediaState =
            PendingMediaState(videoUrl, headers, startPositionMs, audioTracks, subtitles)
        pendingMediaState = mediaState

        if (!isInitialized || isShutdown.get() || !surfaceReady) {
            Log.d(
                TAG,
                "Deferring media load - init:$isInitialized shutdown:${isShutdown.get()} surface:$surfaceReady"
            )

            if (isShutdown.get() && surfaceReady && currentSurfaceHolder != null) {
                init(currentSurfaceHolder!!)
                if (isInitialized && !isShutdown.get()) {
                    loadMediaInternal(mediaState)
                }
            }
            return
        }

        loadMediaInternal(mediaState)
    }

    private fun loadMediaInternal(mediaState: PendingMediaState) {

        _mediaLoaded.value = false

        isFileLoaded = false
        initialBufferingDone = false
        _bufferingProgress.value = 0f
        _playbackState.value = PlaybackState.BUFFERING

        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        _videoTracks.value = emptyList()
        _currentAudioTrack.value = defaultAudioTrack
        _currentSubtitleTrack.value = defaultSubtitleTrack
        _currentVideoTrack.value = defaultVideoTrack
        _duration.value = 0L
        _currentPosition.value = 0L

        mpv.setPropertyString("http-header-fields", "")

        if (mediaState.headers.isNotEmpty() &&
            (mediaState.videoUrl.startsWith("http") || mediaState.videoUrl.startsWith("https"))
        ) {
            val headerStr =
                mediaState.headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" } + "\r\n"
            mpv.setPropertyString("http-header-fields", headerStr)
        }

        mpv.command("loadfile", mediaState.videoUrl, "replace")
    }

    fun selectAudioTrack(trackId: Int) {
        if (!isInitialized || isShutdown.get()) return
        if (trackId == -1) mpv.setPropertyString("aid", "no") else mpv.setPropertyInt("aid", trackId)
    }

    fun setExternalAudioTrack(
        audioTracks: List<ExternalAudio> = emptyList(),
        headers: Map<String, String> = emptyMap()
    ) {
        if (!isInitialized || isShutdown.get() || mpv.getPropertyString("filename").isNullOrBlank()) {
            return
        }
        audioTracks.forEach { audio ->
            if (audio.url.startsWith("http")) {
                val targetHeaders = audio.headers.ifEmpty { headers }
                val headerStr = if (targetHeaders.isNotEmpty()) {
                    targetHeaders.entries.joinToString("\r\n") { "${it.key}: ${it.value}" } + "\r\n"
                } else ""
                mpv.setPropertyString("http-header-fields", headerStr)
            }
            val cmd = mutableListOf("audio-add", audio.url, "select")
            if (!audio.label.isNullOrBlank()) cmd.add(audio.label)
            if (!audio.language.isNullOrBlank()) cmd.add(audio.language)
            mpv.command(*cmd.toTypedArray())
        }
        mpv.command("seek", "0", "relative", "exact")
    }

    fun selectSubtitleTrack(trackId: Int) {
        if (!isInitialized || isShutdown.get()) return
        if (trackId == -1) mpv.setPropertyString("sid", "no") else mpv.setPropertyInt("sid", trackId)
    }

    fun setExternalSubtitles(subtitles: List<ExternalSubtitle>) {
        if (!isInitialized || isShutdown.get()) return
        subtitles.forEach { sub ->
            if (sub.headers.isNotEmpty()) {
                val headerStr =
                    sub.headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" } + "\r\n"
                mpv.setPropertyString("http-header-fields", headerStr)
            }
            val cmd = mutableListOf("sub-add", sub.url, "select")
            if (!sub.label.isNullOrBlank()) cmd.add(sub.label)
            if (!sub.language.isNullOrBlank()) cmd.add(sub.language)
            mpv.command(*cmd.toTypedArray())
        }
    }

    fun selectVideoTrack(trackId: Int) {
        if (!isInitialized || isShutdown.get()) return
        if (trackId == -1) mpv.setPropertyString("vid", "no") else mpv.setPropertyInt("vid", trackId)
    }

    fun setVolume(level: Int) {
        if (!isInitialized || isShutdown.get()) return
        val clamped = level.coerceIn(0, 200)
        mpv.setPropertyInt("volume", clamped)
        _volume.value = clamped
    }

    fun setAudioChannel(channel: AudioChannels) {
        if (!isInitialized || isShutdown.get()) return
        if (channel.property == "af") {
            mpv.setPropertyString("af", channel.value)
            mpv.setPropertyString("audio-channels", "auto-safe")
        } else {
            mpv.setPropertyString("af", "")
            mpv.setPropertyString("audio-channels", channel.value)
        }
        _audioChannel.value = channel
    }

    fun setVideoScaleMode(mode: VideoScaleMode) {
        if (!isInitialized || isShutdown.get()) return

        when (mode) {
            VideoScaleMode.FIT -> {
                mpv.setPropertyBoolean("keepaspect", true)
                mpv.setPropertyString("video-unscaled", "no")
                mpv.setPropertyDouble("panscan", 0.0)
            }
            VideoScaleMode.STRETCH -> {
                mpv.setPropertyBoolean("keepaspect", false)
                mpv.setPropertyString("video-unscaled", "no")
                mpv.setPropertyDouble("panscan", 0.0)
            }
            VideoScaleMode.CROP -> {
                mpv.setPropertyBoolean("keepaspect", true)
                mpv.setPropertyString("video-unscaled", "no")
                mpv.setPropertyDouble("panscan", 1.0)
            }
            VideoScaleMode.ZOOM -> {
                mpv.setPropertyBoolean("keepaspect", true)
                mpv.setPropertyString("video-unscaled", "no")
                mpv.setPropertyDouble("panscan", 0.2)
            }
            VideoScaleMode.ORIGINAL -> {
                mpv.setPropertyBoolean("keepaspect", true)
                mpv.setPropertyString("video-unscaled", "yes")
                mpv.setPropertyDouble("panscan", 0.0)
            }
        }

        _videoScaleMode.value = mode
    }

    fun setDecoder(decoder: Decoder) {
        if (!isInitialized || isShutdown.get()) return
        mpv.setPropertyString("hwdec", decoder.value)
        _currentDecoder.value = decoder
    }

    fun setPlaybackSpeed(speed: Float) {
        if (!isInitialized || isShutdown.get()) return
        val clamped = speed.coerceIn(0.25f, 4.0f)
        mpv.setPropertyDouble("speed", clamped.toDouble())
        _playbackSpeed.value = clamped
    }

    fun attachVideoOutput() {
        if (!isInitialized || isShutdown.get() || !isFileLoaded) return
        val vid = _currentVideoTrack.value.id
        if (vid != 0) {
            Log.d(TAG, "attachVideoOutput() - reconnecting vid=$vid")
            mpv.setPropertyInt("vid", vid)
        }
    }

    fun detachVideoOutput() {
        if (!isInitialized || isShutdown.get()) return
        Log.d(TAG, "detachVideoOutput() - disabling video rendering")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated() called")
        super.surfaceCreated(holder)

        currentSurfaceHolder = holder

        if (!isInitialized || isShutdown.get()) {
            init(holder)
        }

        surfaceReady = true

        when {
            isFileLoaded -> {
                Log.d(TAG, "surfaceCreated: file already loaded — reattaching video output only")
                attachVideoOutput()
            }

            pendingMediaState != null -> {
                Log.d(TAG, "surfaceCreated: loading pending media: ${pendingMediaState!!.videoUrl}")
                if (isShutdown.get()) {
                    init(holder)
                }
                if (isInitialized && !isShutdown.get()) {
                    loadMediaInternal(pendingMediaState!!)
                }
            }

            else -> {
                Log.d(TAG, "surfaceCreated: no pending media and no file loaded, nothing to do")
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed() called")
        detachVideoOutput()
        surfaceReady = false
        super.surfaceDestroyed(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceReady = true
        super.surfaceChanged(holder, format, width, height)
    }
}

