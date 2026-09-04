package ani.saikou.media.anime.mpv.ui.components.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ani.saikou.compose.SaikouTheme
import ani.saikou.media.anime.mpv.AudioTrack
import ani.saikou.media.anime.mpv.SubtitleTrack
import ani.saikou.media.anime.mpv.VideoTrack
import ani.saikou.torrserver.models.TorrentStats
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun TopControlsBar(
    mainTitle: String,
    episodeName: String,
    onBackPressed: () -> Unit,

    subtitleTracks: List<SubtitleTrack>,
    onSubtitleTracksButtonClicked: () -> Unit,

    audioTracks: List<AudioTrack>,
    onAudioTrackButtonClicked: () -> Unit,

    videoQualityTracks: List<VideoTrack>,
    onVideoTrackButtonClicked: () -> Unit,

    onMoreSettingsClicked: () -> Unit,
    onSourcesClicked: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val feedbackColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(0.65f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        enabled = enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(
                            bounded = true,
                            color = feedbackColor,
                            radius = 24.dp
                        ),
                        onClick = onBackPressed
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Close Player",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = episodeName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = mainTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // RIGHT SIDE Action Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(
                        enabled = enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(
                            bounded = true,
                            color = feedbackColor,
                            radius = 22.dp
                        ),
                        onClick = onSourcesClicked
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Dns,
                    contentDescription = "Streaming Sources",
                    tint = Color.White
                )
            }

            if (subtitleTracks.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(
                                bounded = true,
                                color = feedbackColor,
                                radius = 22.dp
                            ),
                            onClick = onSubtitleTracksButtonClicked
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ClosedCaption,
                        contentDescription = "Subtitles",
                        tint = Color.White
                    )
                }
            }

            if (audioTracks.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(
                                bounded = true,
                                color = feedbackColor,
                                radius = 22.dp
                            ),
                            onClick = onAudioTrackButtonClicked
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RecordVoiceOver,
                        contentDescription = "Audio tracks",
                        tint = Color.White
                    )
                }
            }

            if (videoQualityTracks.size > 1) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(
                                bounded = true,
                                color = feedbackColor,
                                radius = 22.dp
                            ),
                            onClick = onVideoTrackButtonClicked
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HighQuality,
                        contentDescription = "Quality options",
                        tint = Color.White
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(
                        enabled = enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(
                            bounded = true,
                            color = feedbackColor,
                            radius = 22.dp
                        ),
                        onClick = onMoreSettingsClicked
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "More configurations",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun TopControlsPopups(
    torrentStatVisible: Boolean,
    torrentStats: TorrentStats?,
    isEnabled: Boolean,
    videoScaleModeText: String,
    modifier: Modifier = Modifier
) {
    var isStatusVisible by remember { mutableStateOf(false) }
    var isTorrentStatsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(videoScaleModeText) {
        if (videoScaleModeText.isNotEmpty()) {
            isStatusVisible = true
            isTorrentStatsVisible = false
            delay(5.seconds)
            isStatusVisible = false
        } else {
            isStatusVisible = false
        }
    }

    LaunchedEffect(torrentStatVisible, isStatusVisible, torrentStats == null) {
        if (isStatusVisible || torrentStats == null) {
            isTorrentStatsVisible = false
        } else if (torrentStatVisible) {
            isTorrentStatsVisible = true
        } else {
            delay(5.seconds)
            isTorrentStatsVisible = false
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isTorrentStatsVisible && !isStatusVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (torrentStats != null) {
                TorrentStatsPill(
                    isEnabled = isEnabled,
                    stats = torrentStats,
                    elementTint = Color.White
                )
            }
        }

        AnimatedVisibility(
            visible = isStatusVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = videoScaleModeText,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Preview(
    name = "Top Controls Bar - Expanded",
    showBackground = true,
    backgroundColor = 0xFF000000,
    widthDp = 800
)
@Composable
private fun TopControlsBarPreview() {
    SaikouTheme {
        TopControlsBar(
            mainTitle = "Attack on Titan",
            episodeName = "Episode 1 - To You, 2000 Years Later",
            onBackPressed = {},
            subtitleTracks = listOf(
                SubtitleTrack(
                    id = 1,
                    name = "English · ASS",
                    language = "en",
                    codec = "ass",
                    isDefault = true,
                    isSelected = true,
                    isForced = false,
                    isExternal = false
                ),
                SubtitleTrack(
                    id = 2,
                    name = "Japanese · ASS",
                    language = "ja",
                    codec = "ass",
                    isDefault = false,
                    isSelected = false,
                    isForced = false,
                    isExternal = false
                )
            ),
            onSubtitleTracksButtonClicked = {},
            audioTracks = listOf(
                AudioTrack(
                    id = 1,
                    name = "Japanese (AAC · Stereo · 48kHz)",
                    language = "ja",
                    codec = "aac",
                    channels = 2,
                    isDefault = true,
                    isSelected = true
                ),
                AudioTrack(
                    id = 2,
                    name = "English (AAC · Stereo · 48kHz)",
                    language = "en",
                    codec = "aac",
                    channels = 2,
                    isDefault = false,
                    isSelected = false
                )
            ),
            onAudioTrackButtonClicked = {},
            videoQualityTracks = listOf(
                VideoTrack(
                    id = 1,
                    name = "1080p · H264 · 24fps · 5000kbps",
                    codec = "h264",
                    resolution = "1920x1080",
                    isSelected = true
                ),
                VideoTrack(
                    id = 2,
                    name = "720p · H264 · 24fps · 2500kbps",
                    codec = "h264",
                    resolution = "1280x720",
                    isSelected = false
                )
            ),
            onVideoTrackButtonClicked = {},
            onMoreSettingsClicked = {},
            onSourcesClicked = {}
        )
    }
}

@Preview(
    name = "Top Controls Popups - Scale Mode",
    showBackground = true,
    backgroundColor = 0xFF000000,
    widthDp = 600,
    heightDp = 100
)
@Composable
private fun TopControlsVideoScalePreview() {
    SaikouTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            TopControlsPopups(
                torrentStatVisible = false,
                torrentStats = null,
                isEnabled = false,
                videoScaleModeText = "Aspect Ratio: Fit Screen"
            )
        }
    }
}

@Preview(
    name = "Top Controls Popups - Torrent Stats",
    showBackground = true,
    backgroundColor = 0xFF000000,
    widthDp = 600
)
@Composable
private fun TopControlsTorrentStatsPreview() {
    SaikouTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            TopControlsPopups(
                torrentStatVisible = true,
                torrentStats = TorrentStats(
                    downloadSpeed = 2_500_000,
                    uploadSpeed = 500_000,
                    activePeers = 42,
                    connectedSeeders = 120
                ),
                isEnabled = true,
                videoScaleModeText = ""
            )
        }
    }
}