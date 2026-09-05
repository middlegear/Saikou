package ani.saikou.media.anime.mpv.ui

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.SurfaceHolder
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ani.saikou.media.MediaDetailsViewModel
import ani.saikou.media.anime.mpv.PlayerEpisodeUiState
import ani.saikou.media.anime.mpv.PlayerScreenActions
import ani.saikou.media.anime.mpv.PlayerViewModel
import ani.saikou.media.anime.mpv.ui.components.PlayerControlsLayout
import ani.saikou.media.anime.mpv.ui.components.backdrops.LoadingScreen
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "mpv_ui"

private tailrec fun Context.findActivity(): AppCompatActivity? = when (this) {
    is AppCompatActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    episodeUiState: PlayerEpisodeUiState,
    mediaDetailsModel: MediaDetailsViewModel,
    actions: PlayerScreenActions
) {
    var isControlsLocked by remember { mutableStateOf(false) }
    val isDialogShowing by viewModel.isDialogShowing.collectAsState()
    val attachedPlayer by viewModel.attachedPlayerView.collectAsState()
    val bufferProgress by viewModel.bufferingProgress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val torrentStats by viewModel.torrentStats.collectAsState()
    val isMediaLoaded by viewModel.mediaLoaded.collectAsState()

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val showCustomLoadingScreen = viewModel.settings.customLoadingScreen

    val isPlayerReady = duration > 0L || bufferProgress >= 1f || isMediaLoaded

    val hasArtwork = !episodeUiState.backdropUrl.isNullOrEmpty() &&
            !episodeUiState.logo.isNullOrEmpty()

    val showArtworkLoadingScreen = showCustomLoadingScreen && hasArtwork && !isPlayerReady

    Box(
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(WindowInsets.navigationBars)
            .consumeWindowInsets(WindowInsets.statusBars)
    ) {

        val playerView = attachedPlayer
        if (playerView != null) {
            DisposableEffect(playerView, activity) {
                val callback = object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        activity?.let { act ->
                            if (playerView.surfaceReady && playerView.isInitialized) {
                                viewModel.onSurfaceReady(act, mediaDetailsModel)
                            }
                        } ?: Log.w(TAG, "SKIPPED onSurfaceReady — activity is null")
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {}
                }

                playerView.holder.addCallback(callback)

                if (playerView.surfaceReady && playerView.isInitialized && activity != null) {
                    viewModel.onSurfaceReady(activity, mediaDetailsModel)
                }

                onDispose {
                    playerView.holder.removeCallback(callback)
                }
            }

            AndroidView(
                factory = {
                    (playerView.parent as? ViewGroup)?.removeView(playerView)
                    playerView
                },
                update = { _ -> },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 1. Artwork Backdrop Overlay Layer – hides automatically when ready
        AnimatedVisibility(
            visible = showArtworkLoadingScreen,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            LoadingScreen(
                backdropUrl = episodeUiState.backdropUrl,
                progress = bufferProgress,
                stats = torrentStats,
                logoUrl = episodeUiState.logo,
                isTorrentEnabled = viewModel.torrentSettings.enableTorrentServer
            )
        }

        // 2. UI Controls Layer – shows once ready or if custom loading screen is disabled
        if (!isDialogShowing) {
            AnimatedVisibility(
                visible = isPlayerReady || !showCustomLoadingScreen || !hasArtwork,
                enter = fadeIn(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(250))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {

                    PlayerControlsLayout(
                        viewModel = viewModel,
                        episodeUi = episodeUiState,
                        isControlsLocked = isControlsLocked,
                        onLockChanged = { isControlsLocked = it },
                        actions = actions,
                        activity = activity
                    )

                    AnimatedVisibility(
                        visible = isControlsLocked,
                        enter = fadeIn(animationSpec = tween(durationMillis = 350)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 350))
                    ) {
                        var isUnlockButtonVisible by remember { mutableStateOf(true) }

                        LaunchedEffect(isUnlockButtonVisible) {
                            if (isUnlockButtonVisible) {
                                delay(4000.milliseconds)
                                isUnlockButtonVisible = false
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    isUnlockButtonVisible = !isUnlockButtonVisible
                                }
                        ) {
                            AnimatedVisibility(
                                visible = isUnlockButtonVisible,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 24.dp, end = 24.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.5f),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    IconButton(
                                        onClick = { isControlsLocked = false },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = "Toggle Screen Lock",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}