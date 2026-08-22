package ani.saikou.media.anime.mpv.ui

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import ani.saikou.media.MediaDetailsViewModel
import ani.saikou.media.anime.mpv.PlayerEpisodeUiState
import ani.saikou.media.anime.mpv.PlayerScreenActions
import ani.saikou.media.anime.mpv.PlayerViewModel
import ani.saikou.media.anime.mpv.ui.components.PlayerControlsLayout
import ani.saikou.media.anime.mpv.ui.components.backdrops.LoadingScreen
//import ani.saikou.media.anime.mpv.ui.components.backdrops.LoadingScreen
//import ani.saikou.torrent.TorrentPlaybackController
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    episodeUiState: PlayerEpisodeUiState,
    mediaDetailsModel: MediaDetailsViewModel,
    actions: PlayerScreenActions
) {
    var isControlsLocked by remember { mutableStateOf(false) }
    val isDialogShowing by viewModel.isDialogShowing.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context as? AppCompatActivity }


    val attachedPlayer by viewModel.attachedPlayerView.collectAsState()
    val bufferProgress by viewModel.bufferingProgress.collectAsState()
    val torrentStats by viewModel.torrentStats.collectAsState()

    val isBuffering = bufferProgress < 1f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(WindowInsets.navigationBars)
            .consumeWindowInsets(WindowInsets.statusBars)
    ) {
        // 1. Base Video Surface Layer
        val playerView = attachedPlayer
        if (playerView != null) {
            AndroidView(
                factory = { _ ->
                    val TAG = "mpv"

                    (playerView.parent as? ViewGroup)?.removeView(playerView)

                    (playerView.tag as? SurfaceHolder.Callback)?.let { previousCallback ->
                        playerView.holder.removeCallback(previousCallback)
                    }

                    val callback = object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            activity?.let { act ->
                                if (playerView.surfaceReady && playerView.isInitialized) {
                                    viewModel.onSurfaceReady(act, mediaDetailsModel)
                                } else {
                                    Log.w(
                                        TAG,
                                        "SKIPPED onSurfaceReady — surfaceReady=${playerView.surfaceReady}, isInitialized=${playerView.isInitialized} from Android View"
                                    )
                                }
                            } ?: Log.w(
                                TAG,
                                "SKIPPED onSurfaceReady — activity is null from Android View"
                            )
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
                    playerView.tag = callback

                    if (playerView.surfaceReady && playerView.isInitialized) {
                        activity?.let { act ->
                            viewModel.onSurfaceReady(act, mediaDetailsModel)
                        }
                    }

                    playerView
                },
                update = { _ -> },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Artwork Backdrop Overlay Layer (Only shown during buffering)
        AnimatedVisibility(
            visible = isBuffering &&
                    !episodeUiState.backdropUrl.isNullOrEmpty() &&
                    !episodeUiState.logo.isNullOrEmpty(),
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
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

        // 3. UI Controls Layer (Hidden during buffering, shown when playback is ready)
        if (!isDialogShowing) {
            AnimatedVisibility(
                visible = !isBuffering,
                enter = fadeIn(animationSpec = tween(350)),
                exit = fadeOut(animationSpec = tween(350))
            ) {
                AnimatedContent(
                    targetState = isControlsLocked,
                    label = "ControlLockTransition",
                    transitionSpec = {
                        fadeIn(animationSpec = tween(durationMillis = 350))
                            .togetherWith(fadeOut(animationSpec = tween(durationMillis = 350)))
                    }
                ) { targetLockedState ->
                    if (targetLockedState) {
                        var isUnlockButtonVisible by remember { mutableStateOf(true) }

                        LaunchedEffect(isUnlockButtonVisible) {
                            if (isUnlockButtonVisible) {
                                delay(4000)
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
                    } else {
                        PlayerControlsLayout(
                            viewModel = viewModel,
                            episodeUi = episodeUiState,
                            isControlsLocked = isControlsLocked,
                            onLockChanged = { isControlsLocked = it },
                            actions = actions,
                            activity = activity
                        )
                    }
                }
            }
        }
    }
}