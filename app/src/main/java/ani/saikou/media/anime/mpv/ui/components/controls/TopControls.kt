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
import androidx.compose.ui.unit.dp
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
    showVideoInfo: Boolean,

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
            modifier = Modifier.weight(1f),
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
            Column {
                Text(
                    text = episodeName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
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

            if (videoQualityTracks.size > 1 && showVideoInfo) {
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
    var previousStateText by remember { mutableStateOf("") }
    var isTorrentStatsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(videoScaleModeText) {
        if (videoScaleModeText.isNotEmpty() && videoScaleModeText != previousStateText) {
            if (previousStateText.isNotEmpty()) {
                isStatusVisible = true
                isTorrentStatsVisible = false
            }
            previousStateText = videoScaleModeText
            delay(5.seconds)
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
                    elementTint = Color.White,

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
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
