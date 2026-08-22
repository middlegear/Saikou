package ani.saikou.media.anime.mpv

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ani.saikou.R
import ani.saikou.connections.anilist.Anilist
import ani.saikou.loadData
import ani.saikou.media.MediaDetailsViewModel
import ani.saikou.media.anime.mpv.ui.PlayerScreen
import ani.saikou.media.anime.mpv.ui.theme.PlayerTheme
import ani.saikou.saveData
import ani.saikou.toast
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    private val playerModel: PlayerViewModel by viewModels()
    private val mediaDetailsModel: MediaDetailsViewModel by viewModels()
    private val TAG = "mpv"

    private var isExiting = false
    private var resumeOnFocusGain = false

    override fun finish() {
        isExiting = true
        super.finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val media = MediaBridge.getMedia() ?: return finish()

        playerModel.initializeManager(extractedMedia = media, mediaDetailsModel = mediaDetailsModel)

        playerModel.bindService(this, mediaDetailsModel)

        val showProgressDialog = loadData<Boolean>("${media.id}_progressDialog") ?: true

        if (showProgressDialog && Anilist.userid != null &&
            if (media.isAdult) playerModel.settings.updateForH else true
        ) {
            playerModel.setDialogShowing(true)

            AlertDialog.Builder(this, R.style.DialogTheme)
                .setTitle(getString(R.string.auto_update, media.userPreferredName))
                .apply {
                    setOnCancelListener {
                        playerModel.setDialogShowing(false)
                        hideSystemUi()
                    }
                    setCancelable(false)
                    setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                        saveData("${media.id}_progressDialog", false)
                        saveData("${media.id}_save_progress", true)
                        dialog.dismiss()
                        playerModel.setDialogShowing(false)
                        playerModel.setInitialEpisode(mediaDetailsModel)
                    }
                    setNegativeButton(getString(R.string.no)) { dialog, _ ->
                        saveData("${media.id}_progressDialog", false)
                        saveData("${media.id}_save_progress", false)
                        toast(getString(R.string.reset_auto_update))
                        dialog.dismiss()
                        playerModel.setDialogShowing(false)
                        playerModel.setInitialEpisode(mediaDetailsModel)
                    }
                }
                .show()
        } else {
            playerModel.setInitialEpisode(mediaDetailsModel)
        }

        mediaDetailsModel.getEpisode().observe(this) { episode ->
            if (episode != null && playerModel.isPlayerAttached) {
                playerModel.loadResolvedEpisode(episode, this@PlayerActivity, mediaDetailsModel)
            } else if (episode != null) {
                Log.d(
                    TAG,
                    "Episode observed via LiveData: ${episode.number} — player not attached yet, deferring to onSurfaceReady."
                )
            }
        }

        var autoNextFired = false
        var wasNearEnd = false

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    playerModel.playbackState,
                    playerModel.duration,
                    playerModel.currentPosition,
                    playerModel.uiState
                ) { state, duration, position, uiState ->
                    TrackEpisode(state, duration, position, uiState.hasNextEpisode)
                }.collect { (state, duration, currentPosition, hasNextEpisode) ->
                    val remainingTime = if (duration > 0L) duration - currentPosition else -1L


                    if (state == PlaybackState.PLAYING && duration > 0L) {
                        if (remainingTime in 0L..3000L) {
                            wasNearEnd = true
                        }
                    }


                    if (state == PlaybackState.PLAYING || state == PlaybackState.BUFFERING) {
                        if (remainingTime > 5000L || currentPosition < 2000L) {
                            if (autoNextFired || wasNearEnd) {
                                Log.d(TAG, "auto-next: resetting gate (pos=$currentPosition)")
                            }
                            autoNextFired = false
                            wasNearEnd = false
                        }
                    }


                    val isAtTrackEnd = (wasNearEnd && (state == PlaybackState.ENDED || state == PlaybackState.IDLE)) ||
                            (state == PlaybackState.PLAYING && remainingTime in 0L..300L)

//                    Log.d(
//                        TAG, "auto-next check: state=$state, duration=$duration, pos=$currentPosition, " +
//                                "remaining=$remainingTime, wasNearEnd=$wasNearEnd, isAtTrackEnd=$isAtTrackEnd, " +
//                                "autoNextFired=$autoNextFired, autoPlay=${playerModel.settings.autoPlay}, " +
//                                "hasNext=$hasNextEpisode"
//                    )

                    if (isAtTrackEnd && !autoNextFired) {
                        autoNextFired = true
                        wasNearEnd = false
                        Log.d(TAG, "auto-next: triggered!")
                        if (playerModel.settings.autoPlay && hasNextEpisode) {
                            playerModel.handleNextEpisodeClick(this@PlayerActivity, mediaDetailsModel)
                        } else {
                            playerModel.pause()
                        }
                    }
                }
            }
        }

        setContent {
            PlayerTheme {
                val uiState by playerModel.uiState.collectAsState()

                PlayerScreen(
                    viewModel = playerModel,
                    mediaDetailsModel = mediaDetailsModel,
                    episodeUiState = uiState,
                    actions = PlayerScreenActions(
                        onClose = {
                            finish()
                        },
                        onNextEpisode = {
                            playerModel.handleNextEpisodeClick(
                                this@PlayerActivity,
                                mediaDetailsModel
                            )
                        },
                        onPreviousEpisode = {
                            playerModel.previousEpisode(this@PlayerActivity, mediaDetailsModel)
                        },
                        onSourceClick = {
                            playerModel.handleSourceClick(this@PlayerActivity, mediaDetailsModel)
                        }
                    )
                )
            }
        }
        hideSystemUi()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = event.action
        val keyCode = event.keyCode

        if (action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    val currentVol = playerModel.volume.value
                    playerModel.setVolume((currentVol + 5).coerceIn(0, 100))
                    return true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    val currentVol = playerModel.volume.value
                    playerModel.setVolume((currentVol - 5).coerceIn(0, 100))
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        playerModel.setMediaSessionActive(true)
    }

    override fun onPause() {
        super.onPause()
        playerModel.pause()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        playerModel.play()
    }

    override fun onStop() {
        super.onStop()
        playerModel.setMediaSessionActive(false)
    }

    override fun onDestroy() {
        MediaBridge.clear()

        if (isExiting) {
            playerModel.exitPlayback()
            playerModel.releasePlayer(mediaDetailsModel)
            playerModel.setMediaSessionActive(false)
        }
        playerModel.destroyViewModel(mediaDetailsModel)
        playerModel.unbindService(this)

        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        playerModel.setMediaSessionActive(hasFocus)

        if (playerModel.settings.focusPause) {
            if (!hasFocus) {
                if (playerModel.isPlaying.value) {
                    resumeOnFocusGain = true
                    playerModel.pause()
                }
            } else {
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    playerModel.play()
                    hideSystemUi()
                }
            }
        }
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
        }
    }
}