package ani.saikou.media.anime.mpv

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.connections.anilist.Anilist
import ani.saikou.connections.discord.WebSocketRPC
import ani.saikou.connections.updateProgress
import ani.saikou.loadData
import ani.saikou.media.Media
import ani.saikou.media.MediaDetailsViewModel
import ani.saikou.media.anime.Episode
import ani.saikou.others.TheMovieDatabase
import ani.saikou.parsers.AnimeSources
import ani.saikou.parsers.HAnimeSources
import ani.saikou.parsers.Subtitle
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoExtractor
import ani.saikou.saveData
import ani.saikou.settings.player.PlayerSettings
import ani.saikou.snackString
import ani.saikou.toast
import ani.saikou.torrserver.TorrServerService
import ani.saikou.torrserver.models.TorrentStats
import ani.saikou.torrserver.utils.TorrentSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var strongPlayer: MpvVideoPlayer? = null
    private var _playerRef: WeakReference<MpvVideoPlayer>? = null
    val player: MpvVideoPlayer?
        get() = _playerRef?.get() ?: strongPlayer

    val isPlayerAttached: Boolean
        get() {
            val p = player ?: return false
            return p.isInitialized && p.surfaceReady
        }

    private val repository = PlayerRepository()
    private val stateJobs = mutableListOf<Job>()
    private var timestampCollectionJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _volumeChangeEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    val volumeChangeEvent: SharedFlow<Int> = _volumeChangeEvent.asSharedFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrack>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrack>> = _subtitleTracks.asStateFlow()

    private val _videoTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val videoTracks: StateFlow<List<VideoTrack>> = _videoTracks.asStateFlow()

    private val loadingAudioTrack =
        AudioTrack(id = 0, name = "Loading Audio...", language = "unknown")
    private val loadingVideoTrack = VideoTrack(id = 0, name = "Loading Video", resolution = null)

    private val _currentAudioTrack = MutableStateFlow(loadingAudioTrack)
    val currentAudioTrack: StateFlow<AudioTrack> = _currentAudioTrack.asStateFlow()

    private val defaultSubtitleTrack = SubtitleTrack(id = -1, name = "None/Off", language = "")
    private val _currentSubtitleTrack = MutableStateFlow(defaultSubtitleTrack)
    val currentSubtitleTrack: StateFlow<SubtitleTrack> = _currentSubtitleTrack.asStateFlow()

    private val _currentVideoTrack = MutableStateFlow(loadingVideoTrack)
    val currentVideoTrack: StateFlow<VideoTrack> = _currentVideoTrack.asStateFlow()

    private val _videoScaleMode = MutableStateFlow(VideoScaleMode.FIT)
    val videoScaleMode: StateFlow<VideoScaleMode> = _videoScaleMode.asStateFlow()

    private val _currentDecoder = MutableStateFlow(Decoder.Auto)
    val currentDecoder: StateFlow<Decoder> = _currentDecoder.asStateFlow()

    private val _audioChannel = MutableStateFlow(AudioChannels.Auto)
    val audioChannel: StateFlow<AudioChannels> = _audioChannel.asStateFlow()

    private val _bufferCacheDuration = MutableStateFlow(0L)
    val bufferCacheDuration: StateFlow<Long> = _bufferCacheDuration.asStateFlow()
    private val _mediaLoaded = MutableStateFlow(false)
    val mediaLoaded: StateFlow<Boolean> = _mediaLoaded.asStateFlow()
    private val _uiState = MutableStateFlow(PlayerEpisodeUiState())
    val uiState: StateFlow<PlayerEpisodeUiState> = _uiState.asStateFlow()

    private val _skipStamps = MutableStateFlow<List<PlayerRepository.SkipInterval>>(emptyList())
    val skipStamps: StateFlow<List<PlayerRepository.SkipInterval>> = _skipStamps.asStateFlow()

    private val _torrentStats = MutableStateFlow<TorrentStats?>(null)
    val torrentStats: StateFlow<TorrentStats?> = _torrentStats.asStateFlow()

    private var torrentStatsJob: Job? = null

    val torrentSettings: TorrentSettings =
        loadData<TorrentSettings>("torrent_settings") ?: TorrentSettings()

    var isTimeStampsLoaded = false
        private set

    var currentEpisodeIndex = 0
        private set

    private var media: Media? = null
    private var currentEpisode: Episode? = null

    private var episodeArr: List<String> = emptyList()
    private var episodes: Map<String, Episode> = emptyMap()

    private var extractor: VideoExtractor? = null
    private var video: Video? = null

    private var subtitleOverride: Subtitle? = null
    private var loadedMediaKey: String? = null

    var settings = PlayerSettings()
        private set

    private var pendingStartPositionMs: Long = 0L

    private val _isDialogShowing = MutableStateFlow(false)

    val isDialogShowing: StateFlow<Boolean> = _isDialogShowing.asStateFlow()

    private val discordRPC: WebSocketRPC by lazy {
        WebSocketRPC(getApplication())
    }

    private val _attachedPlayerView = MutableStateFlow<MpvVideoPlayer?>(null)

    val attachedPlayerView: StateFlow<MpvVideoPlayer?> = _attachedPlayerView.asStateFlow()

    private var _playbackServiceRef: WeakReference<PlaybackService>? = null

    private val playbackService: PlaybackService?
        get() = _playbackServiceRef?.get()

    private val _bufferingProgress = MutableStateFlow(0f)

    val bufferingProgress: StateFlow<Float> = _bufferingProgress.asStateFlow()

    private var _torrServerServiceRef: WeakReference<TorrServerService>? = null

    private val torrServerService: TorrServerService?
        get() = _torrServerServiceRef?.get()

    private var boundMediaDetailsModel: WeakReference<MediaDetailsViewModel>? = null

    private val loadGuard = AtomicBoolean(false)

    private var pendingEpisode: Episode? = null
    private var currentLoadJob: Job? = null
    private var loadingEpisodeKey: String? = null

    private var pendingMediaSessionActive: Boolean? = null

    private var volumeContentObserver: ContentObserver? = null
    private var volumeObserverThread: HandlerThread? = null

    @Volatile
    private var cachedMaxVolumeSteps = 15

    @Volatile
    private var lastKnownVolumeStep = -1
    private var volumeUpdateJob: Job? = null

    private val playbackConnection = object : ServiceConnection {

        override fun onServiceConnected(
            name: ComponentName?, binder: IBinder?
        ) {
            val service = (binder as PlaybackService.LocalBinder).service
            _playbackServiceRef = WeakReference(service)
            cachedMaxVolumeSteps = service.getMaxVolumeStep().coerceAtLeast(1)

            val mediaDetailsModel = boundMediaDetailsModel?.get()
            if (mediaDetailsModel == null) {
                Log.w(
                    "mpv", "Service requested mediaDetailsModel is null"
                )
                return
            }
            setPlayerInstance(service.player)
            service.onNext = {
                Log.d(
                    "mpv", "Service requested next episode"
                )
            }
            service.onPrev = {
                Log.d(
                    "mpv", "Service requested previous episode"
                )
            }

            pendingMediaSessionActive?.let { active ->
                pendingMediaSessionActive = null
                setMediaSessionActive(active)
            }
        }

        override fun onServiceDisconnected(
            name: ComponentName?
        ) {
            _playbackServiceRef?.clear()
            _playbackServiceRef = null
        }
    }

    private val torrServerConnection = object : ServiceConnection {

        override fun onServiceConnected(
            name: ComponentName?, binder: IBinder?
        ) {
            val service = (binder as TorrServerService.LocalBinder).service

            _torrServerServiceRef = WeakReference(service)

            Log.d(
                "mpv", "Bound to TorrServerService in ViewModel"
            )
        }

        override fun onServiceDisconnected(
            name: ComponentName?
        ) {
            _torrServerServiceRef?.clear()
            _torrServerServiceRef = null

            Log.d(
                "mpv", "TorrServerService disconnected in ViewModel"
            )
        }
    }

    fun setMediaSessionActive(active: Boolean) {
        val service = playbackService

        if (service != null) {
            Log.d(
                "mpv", "Setting media session active state to: $active"
            )

            service.setSessionActive(active)
        } else {
            Log.w(
                "mpv", "Cannot toggle media session state; service not bound yet. Queuing request."
            )

            pendingMediaSessionActive = active
        }
    }

    fun bindService(activity: AppCompatActivity, mediaDetailsModel: MediaDetailsViewModel) {
        boundMediaDetailsModel = WeakReference(mediaDetailsModel)

        val playbackIntent = Intent(activity, PlaybackService::class.java)
        activity.bindService(playbackIntent, playbackConnection, Context.BIND_AUTO_CREATE)
        TorrServerService.startOrStop(activity, torrentSettings)
        if (torrentSettings.enableTorrentServer) {
            val torrentIntent = Intent(activity, TorrServerService::class.java)
            activity.bindService(torrentIntent, torrServerConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService(context: Context) {
        try {
            context.unbindService(playbackConnection)
        } catch (_: IllegalArgumentException) {
        }

        try {
            if (torrentSettings.enableTorrentServer) {
                context.unbindService(torrServerConnection)
            }
        } catch (_: IllegalArgumentException) {
        }

        boundMediaDetailsModel?.clear()
        boundMediaDetailsModel = null
    }

    private fun buildRPCConfig(): WebSocketRPC.RPCConfig {
        val currentMedia = media
        val ep = currentEpisode

        if (currentMedia == null || ep == null) {
            return WebSocketRPC.RPCConfig(
                title = "Saikou",
                episode = "?",
                episodeTitle = "loading peak",
                totalEpisodes = "searching",
                coverUrl = null,
                shareLink = "https://anilist.co/anime/116674"
            )
        }

        return WebSocketRPC.RPCConfig(
            title = currentMedia.userPreferredName ?: currentMedia.nameRomaji ?: currentMedia.name
            ?: "Unknown Anime",
            episode = ep.number,
            episodeTitle = ep.title?.takeIf { it.isNotBlank() && it != "null" },
            totalEpisodes = currentMedia.anime?.totalEpisodes?.toString(),
            coverUrl = currentMedia.banner ?: currentMedia.cover,
            shareLink = currentMedia.shareLink ?: "https://anilist.co/anime/${currentMedia.id}",
            episodeThumbnail = ep.thumb?.url
        )
    }

    fun setDialogShowing(showing: Boolean) {
        _isDialogShowing.value = showing
    }

    fun setPlayerInstance(playerInstance: MpvVideoPlayer) {
        clearStateJobs()
        strongPlayer = playerInstance
        _playerRef = WeakReference(playerInstance)
        _attachedPlayerView.value = playerInstance

        discordRPC.connect()

        setupSystemVolumeSync(playerInstance)

        stateJobs += viewModelScope.launch {
            playerInstance.playbackState.collect { state ->
                _playbackState.value = state

                if (state == PlaybackState.BUFFERING || state == PlaybackState.IDLE || state == PlaybackState.ENDED) {
                    return@collect
                }
                val playingNow = state == PlaybackState.PLAYING
                discordRPC.onPlaybackChanged(playingNow, playerInstance.currentPosition.value)
            }
        }

        stateJobs += viewModelScope.launch {
            playerInstance.isPlaying.collect { isPlaying ->

                _isPlaying.value = isPlaying

                playbackService?.publishState(
                    if (isPlaying) {
                        android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
                    } else {
                        android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED
                    }
                )
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.currentPosition.collect {
                _currentPosition.value = it
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.mediaLoaded.collect {
                _mediaLoaded.value = it
            }
        }

        stateJobs += viewModelScope.launch {
            playerInstance.duration.collect {
                _duration.value = it
            }
        }

        stateJobs += viewModelScope.launch {
            playerInstance.playbackSpeed.collect {
                _playbackSpeed.value = it
            }
        }

        stateJobs += viewModelScope.launch {
            playerInstance.audioTracks.collect {
                _audioTracks.value = it
            }
        }

        stateJobs += viewModelScope.launch {
            playerInstance.currentAudioTrack.collect {
                _currentAudioTrack.value = it
            }
        }

        stateJobs += viewModelScope.launch {
            playerInstance.subtitleTracks.collect {
                _subtitleTracks.value = it
            }
        }

        stateJobs += viewModelScope.launch {
            playerInstance.currentSubtitleTrack.collect {
                _currentSubtitleTrack.value = it
            }
        }

        stateJobs += viewModelScope.launch {
            playerInstance.videoTracks.collect {
                _videoTracks.value = it
            }
        }

        stateJobs += viewModelScope.launch {
            playerInstance.currentVideoTrack.collect {
                _currentVideoTrack.value = it
            }
        }

        stateJobs += viewModelScope.launch {
            playerInstance.videoScaleMode.collect {
                _videoScaleMode.value = it
            }
        }

        stateJobs += viewModelScope.launch {
            playerInstance.currentDecoder.collect {
                _currentDecoder.value = it
            }
        }

        stateJobs += viewModelScope.launch {
            playerInstance.audioChannel.collect {
                _audioChannel.value = it
            }
        }

        stateJobs += viewModelScope.launch {
            playerInstance.bufferCacheDuration.collect {
                _bufferCacheDuration.value = it
            }
        }

        stateJobs += viewModelScope.launch {
            playerInstance.bufferingProgress.collect {
                _bufferingProgress.value = it
            }
        }

        stateJobs += viewModelScope.launch {
            playerInstance.duration.collect { dur ->

                if (dur <= 0L) return@collect

                var resolvedPosition = currentPosition.value

                if (pendingStartPositionMs > 0L) {

                    val safeTarget = pendingStartPositionMs

                    pendingStartPositionMs = 0L

                    val remainingMs = dur - safeTarget

                    if (remainingMs >= 60_000L) {
                        resolvedPosition = safeTarget
                        seekTo(safeTarget)
                    } else {
                        seekTo(0L)
                        resolvedPosition = 0L
                    }
                }

                withContext(Dispatchers.Main) {
                    publishUi()
                    playbackService?.updateMetadata(
                        title = media?.userPreferredName ?: media?.nameRomaji ?: media?.name
                        ?: "Unknown",
                        subtitle = "Episode ${currentEpisode?.number ?: ""}",
                        durationMs = dur
                    )
                }

                if (!isTimeStampsLoaded && settings.timeStampsEnabled) {
                    val ep = currentEpisode
                    val currentMedia = media

                    if (ep != null && currentMedia != null) {
                        isTimeStampsLoaded = true

                        loadSkipTimes(
                            currentMedia, ep, dur
                        )
                    }
                }

                discordRPC.onDurationReady(
                    buildRPCConfig(), dur, resolvedPosition
                )
            }
        }
    }


    private fun maxVolumeSteps(): Int {
        return cachedMaxVolumeSteps.coerceAtLeast(1)
    }


    private fun setupSystemVolumeSync(playerInstance: MpvVideoPlayer) {
        val service = playbackService ?: return

        removeVolumeObserver()

        cachedMaxVolumeSteps = service.getMaxVolumeStep().coerceAtLeast(1)

        val thread = HandlerThread("VolumeObserver").apply {
            start()
        }

        volumeObserverThread = thread

        val observer = object : ContentObserver(Handler(thread.looper)) {
            override fun onChange(selfChange: Boolean) {
                syncVolumeFromSystem()
            }
        }

        volumeContentObserver = observer
        service.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)

        playerInstance.setVolume(100)

        syncVolumeFromSystem()
    }

    private fun removeVolumeObserver() {
        volumeContentObserver?.let { observer ->

            try {
                playbackService?.contentResolver?.unregisterContentObserver(observer)
            } catch (_: IllegalArgumentException) {
                // Observer wasn't registered.
            }
        }

        volumeContentObserver = null

        volumeObserverThread?.let { thread ->
            thread.quitSafely()
        }

        volumeObserverThread = null
    }

    private fun syncVolumeFromSystem() {
        val service = playbackService ?: return

        val maxSteps = cachedMaxVolumeSteps.coerceAtLeast(1)

        val currentStep = service.getCurrentVolumeStep()

        if (currentStep == lastKnownVolumeStep) {
            return
        }

        lastKnownVolumeStep = currentStep

        val percent = ((currentStep.toFloat() / maxSteps) * 100f).roundToInt()
        _volume.value = percent
    }


    private fun applySystemVolumeStep(step: Int) {
        volumeUpdateJob?.cancel()

        volumeUpdateJob = viewModelScope.launch(Dispatchers.Default) {

            playbackService?.setVolumeStep(step)
        }
    }


    fun setVolume(level: Int) {
        val maxSteps = maxVolumeSteps()
        val step = ((level.coerceIn(0, 100) / 100f) * maxSteps).roundToInt().coerceIn(0, maxSteps)
        if (step == lastKnownVolumeStep) {
            return
        }
        lastKnownVolumeStep = step

        val quantizedPercent = ((step.toFloat() / maxSteps) * 100f).roundToInt()

        _volume.value = quantizedPercent
        _volumeChangeEvent.tryEmit(
            quantizedPercent
        )
        applySystemVolumeStep(step)
    }


    fun adjustVolumeStep(deltaSteps: Int) {
        val maxSteps = maxVolumeSteps()
        val current = if (lastKnownVolumeStep >= 0) {
            lastKnownVolumeStep
        } else {
            ((volume.value / 100f) * maxSteps).roundToInt()
        }
        val newStep = (current + deltaSteps).coerceIn(0, maxSteps)

        if (newStep == lastKnownVolumeStep) {
            return
        }

        lastKnownVolumeStep = newStep

        val quantizedPercent = ((newStep.toFloat() / maxSteps) * 100f).roundToInt()
        _volume.value = quantizedPercent
        _volumeChangeEvent.tryEmit(quantizedPercent)
        applySystemVolumeStep(newStep)
    }

    fun onSurfaceReady(activity: AppCompatActivity, mediaDetailsModel: MediaDetailsViewModel) {
        if (isDialogShowing.value) {
            Log.d("mpv", "onSurfaceReady: dialog is showing, deferring episode load")
            return
        }

        val targetEpisode =
            mediaDetailsModel.getEpisode().value ?: episodeArr.getOrNull(currentEpisodeIndex)
                ?.let { episodes[it] }

        if (targetEpisode != null) {
            loadResolvedEpisode(
                targetEpisode, activity, mediaDetailsModel
            )
        }
    }

    fun initializeManager(
        extractedMedia: Media,
        initialSubtitle: Subtitle? = null,
        mediaDetailsModel: MediaDetailsViewModel
    ) {
        media = extractedMedia
        subtitleOverride = initialSubtitle

        settings = loadData("player_settings") ?: PlayerSettings()

        mediaDetailsModel.watchSources = if (extractedMedia.isAdult) {
            HAnimeSources
        } else {
            AnimeSources
        }

        episodes = extractedMedia.anime?.episodes ?: emptyMap()
        episodeArr = episodes.keys.toList()
        currentEpisodeIndex = episodeArr.indexOf(extractedMedia.anime?.selectedEpisode).coerceAtLeast(0)

        mediaDetailsModel.setMedia(
            extractedMedia
        )

        publishUi()
    }

    fun setInitialEpisode(mediaDetailsModel: MediaDetailsViewModel) {
        episodeArr.getOrNull(currentEpisodeIndex)?.let { episodes[it] }?.let {
            mediaDetailsModel.setEpisode(
                it, "mpv-init"
            )
        }
    }

    fun handleNextEpisodeClick(activity: AppCompatActivity, mediaDetailsModel: MediaDetailsViewModel) {
        if (currentEpisodeIndex + 1 < episodeArr.size) {

            updateAnimeProgress()

            openEpisodeSelector(
                currentEpisodeIndex + 1, activity, mediaDetailsModel
            )
        } else {
            toast("This is the last Episode!")
        }
    }

    fun previousEpisode(activity: AppCompatActivity, mediaDetailsModel: MediaDetailsViewModel) {
        if (currentEpisodeIndex > 0) {
            openEpisodeSelector(currentEpisodeIndex - 1, activity, mediaDetailsModel)
        } else {
            toast("This is the first Episode!")
        }
    }

    fun handleSourceClick(activity: AppCompatActivity, mediaDetailsModel: MediaDetailsViewModel) {
        val currentMedia = media ?: return
        val selected = currentMedia.selected ?: return
        val episodeNumber = episodeArr.getOrNull(currentEpisodeIndex) ?: return

        saveCurrentPosition()
        selected.server = null
        mediaDetailsModel.saveSelected(currentMedia.id, selected, activity)
        mediaDetailsModel.onEpisodeClick(currentMedia, episodeNumber, activity.supportFragmentManager, launch = false)
    }

    fun loadResolvedEpisode(
        ep: Episode,
        activity: AppCompatActivity,
        mediaDetailsModel: MediaDetailsViewModel
    ) {
        val currentMedia = media ?: return

        val targetIndex = episodeArr.indexOf(ep.number)

        if (targetIndex == -1) {
            return
        }

        val preferredServer = mediaDetailsModel.loadSelected(currentMedia).server

        val resolvedExtractor = ep.extractors?.find {
            it.server.name == ep.selectedExtractor
        } ?: ep.extractors?.find {
            it.server.name == preferredServer
        }

        if (resolvedExtractor == null) {
            return
        }

        val resolvedVideo = ep.selectedVideo.let {
            resolvedExtractor.videos.getOrNull(it)
        } ?: return

        val newMediaKey =
            "${ep.number}|${resolvedExtractor.server.name}|" + "${ep.selectedVideo}|${resolvedVideo.file.url}|" + "${ep.selectedSubtitle}"

        if (newMediaKey == loadedMediaKey) {
            publishUi()
            return
        }

        if (newMediaKey == loadingEpisodeKey) {
            Log.d(
                "mpv", "[PlayerViewModel] Already loading episode ${ep.number}, ignoring duplicate"
            )
            return
        }

        torrServerService?.releaseStream()
        stopTorrentStatsMonitoring()

        player?.stop()

        _playbackState.value = PlaybackState.BUFFERING

        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _bufferingProgress.value = 0f

        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        _videoTracks.value = emptyList()
        _currentAudioTrack.value = loadingAudioTrack
        _currentSubtitleTrack.value = defaultSubtitleTrack
        _currentVideoTrack.value = loadingVideoTrack
        _skipStamps.value = emptyList()
        isTimeStampsLoaded = false
        _mediaLoaded.value = false

        if (!loadGuard.compareAndSet(false, true)) {
            Log.d("mpv", "[PlayerViewModel] Load in progress – queuing episode ${ep.number}")
            pendingEpisode = ep
            loadingEpisodeKey = newMediaKey
            return
        }

        loadingEpisodeKey = newMediaKey

        val isNewTorrent = resolvedVideo.file.url.startsWith(
            "magnet:", ignoreCase = true
        )

        currentEpisodeIndex = targetIndex
        currentEpisode = ep

        currentMedia.anime?.selectedEpisode = ep.number

        mediaDetailsModel.setMedia(currentMedia)

        currentLoadJob?.cancel()

        currentLoadJob = viewModelScope.launch(Dispatchers.IO) {

            try {

                extractor?.onVideoStopped(video)

                extractor = resolvedExtractor
                video = resolvedVideo

                resolvedExtractor.onVideoPlayed(resolvedVideo)

                saveContinueState(currentMedia, ep)

                val startPosition = loadData<Long>(
                    "${currentMedia.id}_${ep.number}", activity
                ) ?: 0L

                val headers = resolvedVideo.file.headers

                val externalAudio = resolvedExtractor.audioTracks.map {
                    ExternalAudio(
                        it.url, language = it.language, headers = headers
                    )
                }

                val externalSubs = resolvedExtractor.subtitles.map { sub ->

                    ExternalSubtitle(
                        url = sub.file.url,
                        headers = sub.headers ?: sub.file.headers ?: emptyMap(),
                        language = sub.language ?: "und"
                    )
                }

                val playbackUrl: String

                if (isNewTorrent) {

                    delay(500)

                    var service = torrServerService

                    var waitAttempts = 0

                    while (service == null && waitAttempts < 30) {
                        delay(100)

                        service = torrServerService

                        waitAttempts++
                    }

                    if (service == null) {
                        Log.e(
                            "TorrServer", "TorrServerService not bound after waiting"
                        )
                        withContext(Dispatchers.Main) {
                            snackString(
                                "Service not available (force stop the app and retry)"
                            )
                        }

                        return@launch
                    }

                    startTorrentStatsMonitoring()

                    Log.d(
                        "TorrServer", "Resolving stream for episode ${ep.number}"
                    )

                    val streamUrl = service.resolveStreamUrl(
                        resolvedVideo.file.url
                    )

                    if (streamUrl == null) {

                        Log.e(
                            "TorrServer",
                            "Failed to resolve torrent stream for episode ${ep.number}"
                        )
                        stopTorrentStatsMonitoring()
                        withContext(Dispatchers.Main) {
                            snackString(
                                "Failed to resolve stream for this episode(Force stop App)"
                            )
                        }

                        return@launch
                    }

                    Log.d(
                        "TorrServer", "Stream resolved: $streamUrl"
                    )

                    playbackUrl = streamUrl

                } else {
                    playbackUrl = resolvedVideo.file.url
                }

                pendingStartPositionMs = startPosition

                val loadSucceeded = loadMedia(
                    playbackUrl, headers ?: emptyMap(), startPosition, externalAudio, externalSubs
                )

                if (loadSucceeded) {

                    loadedMediaKey = newMediaKey

                } else {

                    pendingStartPositionMs = 0L

                    Log.e(
                        "mpv", "[PlayerViewModel] Failed to load media for episode ${ep.number}"
                    )
                }

                withContext(Dispatchers.Main) {

                    publishUi()

                    if (loadSucceeded) {

                        play()

                        discordRPC.updateEpisode(
                            buildRPCConfig(), isCurrentlyPlaying = true
                        )

                        playbackService?.updateMetadata(
                            title = currentMedia.userPreferredName ?: currentMedia.nameRomaji
                            ?: currentMedia.name ?: "Unknown",

                            subtitle = "Episode ${ep.number}",

                            durationMs = _duration.value
                        )
                    }
                }

                if (loadSucceeded) {

                    var waited = 0L

                    while (waited < 5000L) {

                        val state = _playbackState.value

                        if (state == PlaybackState.BUFFERING || state == PlaybackState.PLAYING) {
                            Log.d(
                                "mpv", "[PlayerViewModel] MPV State $state after ${waited}ms"
                            )
                            break
                        }

                        delay(100)
                        waited += 100
                    }
                }

            } catch (e: Exception) {

                Log.e("mpv", "Error loading episode", e)
                withContext(Dispatchers.Main) {
                    snackString(
                        "Error loading episode: ${e.message}"
                    )
                }

            } finally {

                loadGuard.set(false)

                val nextEp = pendingEpisode

                val nextKey = loadingEpisodeKey

                pendingEpisode = null
                loadingEpisodeKey = null

                if (nextEp != null && nextKey != newMediaKey) {

                    Log.d(
                        "mpv",
                        "[PlayerViewModel] Processing queued episode after current load completed"
                    )

                    loadResolvedEpisode(
                        nextEp, activity, mediaDetailsModel
                    )
                }
            }
        }
    }

    fun loadSkipTimes(
        media: Media, episode: Episode, durationMs: Long
    ) {
        viewModelScope.launch(Dispatchers.IO) {

            val episodeNumber = episode.number.trim().toIntOrNull()
            val isMovie = media.format == "MOVIE"

            var result: List<PlayerRepository.SkipInterval>? = media.idMAL?.let { malId ->

                episodeNumber?.let { epNum ->

                    Log.d(
                        "SkipTimes",
                        "Fetching from AniSkip: malId=$malId, episode=$epNum, durationSec=${durationMs / 1000}"
                    )

                    repository.fetchAniSkipTimes(
                        malId, epNum, durationMs / 1000
                    ).also { res ->

                        Log.d(
                            "SkipTimes", "AniSkip result: $res"
                        )
                    }
                }
            }

            if (result.isNullOrEmpty() && settings.useAlternativeTimestampProvider) {

                val tmdbId = media.idTMDB?.toIntOrNull()

                val absoluteEp = episode.absoluteEpisodeNumber

                result = if (tmdbId != null && (isMovie || absoluteEp != null)) {

                    val seasonParam = if (isMovie) {
                        null
                    } else {
                        episode.seasonNumber
                    }

                    val episodeParam = if (isMovie) {
                        null
                    } else {
                        absoluteEp
                    }

                    Log.d(
                        "SkipTimes",
                        "Fetching from TheIntroDB (fallback): tmdbId=$tmdbId, isMovie=$isMovie, season=$seasonParam, episode=$episodeParam, durationMs=$durationMs"
                    )

                    TheMovieDatabase.fetchSkipTimes(
                        tmdbId = tmdbId,
                        season = seasonParam,
                        episode = episodeParam,
                        durationMs = durationMs
                    ).also {

                        Log.d(
                            "SkipTimes", "TheIntroDB fallback result: $it"
                        )
                    }

                } else {
                    null
                }
            }

            if (!result.isNullOrEmpty()) {

                Log.d(
                    "SkipTimes", "Final skip stamps applied: $result"
                )

                _skipStamps.value = result

            } else {

                Log.d(
                    "SkipTimes", "No skip times found from any source, resetting isTimeStampsLoaded"
                )

                isTimeStampsLoaded = false
            }
        }
    }

    fun startTorrentStatsMonitoring() {
        stopTorrentStatsMonitoring()
        val service = torrServerService ?: return
        _torrentStats.value = service.getStats()
        torrentStatsJob = viewModelScope.launch {
            service.controller.stats.collect { stats ->
                _torrentStats.value = stats
            }
        }
    }

    fun stopTorrentStatsMonitoring() {
        torrentStatsJob?.cancel()
        torrentStatsJob = null
        _torrentStats.value = null
    }

    fun saveCurrentPosition() {
        val currentMedia = media ?: return
        val episodeNumber = currentEpisode?.number ?: currentMedia.anime?.selectedEpisode ?: return

        saveData(
            "${currentMedia.id}_${episodeNumber}", currentPosition.value, getApplication()
        )
    }

    fun release(mediaDetailsModel: MediaDetailsViewModel) {
        stopTorrentStatsMonitoring()
        val currentMedia = media
        val fallbackEpisode = currentEpisode?.number ?: currentMedia?.anime?.selectedEpisode
        if (currentMedia != null && fallbackEpisode != null) {
            currentMedia.anime?.selectedEpisode = fallbackEpisode

            mediaDetailsModel.setMedia(
                currentMedia
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            extractor?.onVideoStopped(video)
        }
    }

    private fun openEpisodeSelector(
        index: Int, activity: AppCompatActivity, mediaDetailsModel: MediaDetailsViewModel
    ) {
        val currentMedia = media ?: return
        if (index !in episodeArr.indices) {
            return
        }

        saveCurrentPosition()
        val targetEpisodeNumber = episodeArr[index]

        mediaDetailsModel.epChanged.postValue(false)

        mediaDetailsModel.onEpisodeClick(
            currentMedia, targetEpisodeNumber, activity.supportFragmentManager, launch = false
        )

        pause()
    }

    private fun saveContinueState(currentMedia: Media, ep: Episode) {
        saveData("${currentMedia.id}_current_ep", ep.number, getApplication())
        val continueSet =
            loadData<MutableSet<Int>>("continue_ANIME", getApplication()) ?: mutableSetOf()

        continueSet.remove(currentMedia.id)
        continueSet.add(currentMedia.id)
        saveData(
            "continue_ANIME", continueSet
        )
    }

    private fun publishUi() {
        val current =
            episodeArr.getOrNull(currentEpisodeIndex)?.let { episodes[it] } ?: currentEpisode

        val builtEpisodeTitle = current?.let { ep ->

            val title = ep.title?.takeIf {
                it.isNotBlank() && it != "null"
            }
            val number = ep.number
            when {
                title == null -> "Episode $number"
                title.contains(
                    "episode", ignoreCase = true
                ) -> title

                else -> "Episode $number: $title"
            }
        } ?: ""

        val state = PlayerEpisodeUiState(
            mainTitle = media?.let { it.userPreferredName ?: it.nameRomaji ?: it.name }
                ?: "Unknown",
            episodeTitle = builtEpisodeTitle,
            episodeTitles = episodeArr.mapNotNull { episodes[it]?.number },
            currentEpisodeIndex = currentEpisodeIndex,
            hasNextEpisode = currentEpisodeIndex + 1 < episodeArr.size,
            hasPreviousEpisode = currentEpisodeIndex > 0,
            backdropUrl = media?.anime?.tmdbBackdrop,
            logo = media?.anime?.tmdbLogo
        )
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _uiState.value = state
        } else {
            viewModelScope.launch(Dispatchers.Main) {
                _uiState.value = state
            }
        }
    }

    fun loadMedia(
        videoUrl: String,
        headers: Map<String, String> = emptyMap(),
        startPositionMs: Long = 0L,
        audioTracks: List<ExternalAudio> = emptyList(),
        subtitles: List<ExternalSubtitle> = emptyList()
    ): Boolean {
        val playerInstance = player
        if (playerInstance == null) {
            Log.w("mpv", "[PlayerViewModel] loadMedia failed – player is null")
            return false
        }
        playerInstance.loadMedia(videoUrl, headers, startPositionMs, audioTracks, subtitles)
        return true
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        saveCurrentPosition()
        player?.pause()
    }

    fun stop() {
        saveCurrentPosition()
        player?.stop()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun selectSubtitleTrack(trackId: Int) {
        player?.selectSubtitleTrack(trackId)
    }

    fun selectAudioTrack(trackId: Int) {
        player?.selectAudioTrack(trackId)
    }

    fun selectVideoTrack(trackId: Int) {
        player?.selectVideoTrack(trackId)
    }

    fun setDecoder(decoder: Decoder) {
        player?.setDecoder(decoder)
    }

    fun setAudioChannel(channel: AudioChannels) {
        player?.setAudioChannel(channel)
    }

    fun setVideoScaleMode(mode: VideoScaleMode) {
        player?.setVideoScaleMode(mode)
        _videoScaleMode.value = mode
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
    }

    fun setExternalSubtitles(subs: List<ExternalSubtitle>) {
        player?.setExternalSubtitles(subs)
    }

    fun detachPlayerState() {
        clearStateJobs()
        timestampCollectionJob?.cancel()
        removeVolumeObserver()
        volumeUpdateJob?.cancel()
        volumeUpdateJob = null
        strongPlayer = null
        _playerRef?.clear()
        _playerRef = null
        _attachedPlayerView.value = null
    }

    fun exitPlayback() {
        saveCurrentPosition()
        updateAnimeProgress()
        torrServerService?.releaseStream()
        stopTorrentStatsMonitoring()
        playbackService?.teardown()
        _playbackServiceRef?.clear()
        _playbackServiceRef = null
        _torrServerServiceRef?.clear()
        _torrServerServiceRef = null
    }

    fun releasePlayer(mediaDetailsModel: MediaDetailsViewModel) {
        clearStateJobs()
        timestampCollectionJob?.cancel()
        removeVolumeObserver()
        volumeUpdateJob?.cancel()
        volumeUpdateJob = null
        release(mediaDetailsModel)
        discordRPC.close()
        torrServerService?.releaseStream()
        stopTorrentStatsMonitoring()
        _playbackState.value = PlaybackState.ENDED
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        _videoTracks.value = emptyList()
        _currentAudioTrack.value = loadingAudioTrack
        _currentVideoTrack.value = loadingVideoTrack
        _currentSubtitleTrack.value = defaultSubtitleTrack
        _skipStamps.value = emptyList()
        isTimeStampsLoaded = false
        pendingStartPositionMs = 0L
        strongPlayer = null
        _playerRef?.clear()
        _playerRef = null
        _attachedPlayerView.value = null
        _mediaLoaded.value = false
    }

    override fun onCleared() {
        volumeUpdateJob?.cancel()
        volumeUpdateJob = null
        torrServerService?.releaseStream()
        removeVolumeObserver()
        super.onCleared()
    }

    private fun clearStateJobs() {
        stateJobs.forEach { it.cancel() }
        stateJobs.clear()
    }

    private fun updateAnimeProgress() {
        val dur = duration.value
        if (dur <= 0L) {
            return
        }

        val ratio = currentPosition.value.toDouble() / dur.toDouble()
        if (ratio <= settings.watchPercentage || Anilist.userid == null) {
            return
        }

        val currentMedia = media ?: return
        val epNumber = (currentEpisode ?: episodeArr.getOrNull(currentEpisodeIndex)
            ?.let { episodes[it] })?.number ?: return

        if (loadData<Boolean>("${media?.id}_save_progress") != false && if (media?.isAdult == true) {
                settings.updateForH
            } else {
                true
            }
        ) {
            updateProgress(currentMedia, epNumber)
        }
    }

    fun destroyViewModel(mediaDetailsModel: MediaDetailsViewModel) {
        release(mediaDetailsModel)
        detachPlayerState()
    }
}