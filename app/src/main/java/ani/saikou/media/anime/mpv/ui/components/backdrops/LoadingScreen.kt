package ani.saikou.media.anime.mpv.ui.components.backdrops

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ani.saikou.torrserver.models.TorrentStats
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import java.util.Locale

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun LoadingScreen(
    backdropUrl: String?,
    logoUrl: String?,
    progress: Float,
    stats: TorrentStats?,
    isTorrentEnabled: Boolean
) {
    val showTorrentStats = stats != null && isTorrentEnabled

    val isComplete = if (showTorrentStats && stats != null) {
        maxOf(progress, stats.prebufferProgress) >= 0.99f
    } else {
        progress >= 0.99f
    }

    val currentLogoProgress = if (showTorrentStats && stats != null) {
        maxOf(progress, stats.prebufferProgress).coerceIn(0f, 1f)
    } else {
        progress.coerceIn(0f, 1f)
    }

    val isInitialTorrentStage = stats?.stage == TorrentStats.Stage.RESOLVING_MAGNET ||
            stats?.stage == TorrentStats.Stage.METADATA_DOWNLOAD

    var maxProgress by remember(logoUrl, backdropUrl, isInitialTorrentStage) { mutableFloatStateOf(0f) }

    if (isInitialTorrentStage && currentLogoProgress < 0.05f) {
        maxProgress = currentLogoProgress
    } else {
        maxProgress = maxOf(maxProgress, currentLogoProgress)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = maxProgress,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "LogoProgressFill"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "LoadingAnimations")

    val backdropScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BackdropScale"
    )

    // Pulse alpha for torrent mode logo background
    val logoBaseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoPulseAlpha"
    )


    val nonTorrentLogoScale by infiniteTransition.animateFloat(
        initialValue = 0.99f,
        targetValue = 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "NonTorrentLogoScale"
    )

    val statusText = when {
        !showTorrentStats || stats == null -> null
        stats.stage == TorrentStats.Stage.RESOLVING_MAGNET -> "Adding torrent..."
        stats.stage == TorrentStats.Stage.METADATA_DOWNLOAD -> "Fetching metadata..."
        stats.stage == TorrentStats.Stage.HTTP_HANDOFF -> "Preparing stream..."
        stats.stage == TorrentStats.Stage.ERROR -> "Failed to load torrent"
        else -> "Initializing..."
    }

    val showDetailedStats = showTorrentStats &&
            stats != null &&
            stats.stage == TorrentStats.Stage.READY

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Fullscreen Backdrop Banner
        GlideImage(
            model = backdropUrl,
            contentDescription = "Episode Backdrop Banner",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = backdropScale
                    scaleY = backdropScale
                },
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter
        )

        // 2. Dark Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
        )

        // 3. Center UI
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.5f)
                .wrapContentHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (showTorrentStats) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!logoUrl.isNullOrBlank()) {
                        GlideImage(
                            model = logoUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = logoBaseAlpha },
                            contentScale = ContentScale.Fit
                        )
                        GlideImage(
                            model = logoUrl,
                            contentDescription = "Movie/Show Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    clipRect(
                                        left = 0f,
                                        top = 0f,
                                        right = size.width * animatedProgress,
                                        bottom = size.height
                                    ) {
                                        this@drawWithContent.drawContent()
                                    }
                                },
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.graphicsLayer { alpha = (logoBaseAlpha + 0.65f).coerceAtMost(1f) }
                        ) {
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Buffering Stream",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .graphicsLayer {
                                scaleX = nonTorrentLogoScale
                                scaleY = nonTorrentLogoScale
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!logoUrl.isNullOrBlank()) {
                            GlideImage(
                                model = logoUrl,
                                contentDescription = "Loading Logo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = "Loading",
                                color = Color.White,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Loading stream...",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 4. Bottom Torrent Stats
        if (showTorrentStats && stats != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showDetailedStats) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Downloaded:",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = String.format(Locale.ROOT, "%.2f%%", stats.progress.coerceIn(0f, 1f) * 100f),
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(text = "•", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                        Text(
                            text = "Speed:",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = formatSpeed(stats.downloadSpeed),
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(text = "•", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                        Text(
                            text = "Seeds:",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${stats.connectedSeeders}",
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(text = "•", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                        Text(
                            text = "Peers:",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${stats.totalPeers}",
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }
                } else if (statusText != null) {
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "0.00 MB/s"
    val megabytesPerSecond = bytesPerSecond / (1024.0 * 1024.0)
    return String.format(Locale.ROOT, "%.2f MB/s", megabytesPerSecond)
}