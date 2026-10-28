package ani.saikou.media.anime.mpv.ui.components.controls

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ripple

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ani.saikou.R

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun CenterControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPauseToggle: () -> Unit,
    onNextEpisode: () -> Unit,
    onPreviousEpisode: () -> Unit,
    modifier: Modifier = Modifier,
    hasNext: Boolean = true,
    hasPrevious: Boolean = true
) {
    val feedbackColor = MaterialTheme.colorScheme.primary

    val playToPauseVector = AnimatedImageVector.animatedVectorResource(R.drawable.anim_play_to_pause)
    val pauseToPlayVector = AnimatedImageVector.animatedVectorResource(R.drawable.anim_pause_to_play)

    val playToPausePainter = rememberAnimatedVectorPainter(playToPauseVector, atEnd = isPlaying)
    val pauseToPlayPainter = rememberAnimatedVectorPainter(pauseToPlayVector, atEnd = !isPlaying)

    val painter = if (isPlaying) playToPausePainter else pauseToPlayPainter


    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {

        //  PREVIOUS  BUTTON
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = hasPrevious,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = feedbackColor, radius = 42.dp),
                    onClick = onPreviousEpisode
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous Episode",
                tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(56.dp)
            )
        }

        //  PLAY / PAUSE
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isLoading) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = feedbackColor, radius = 60.dp),
                            onClick = onPlayPauseToggle
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Icon(
                        painter = painter,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(84.dp)
                    )
                }
            }
        }

        // NEXT  BUTTON
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = hasNext,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = feedbackColor, radius = 42.dp),
                    onClick = onNextEpisode
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next Episode",
                tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(56.dp)
            )
        }
    }
}




@Preview(name = "Playing", device = "spec:width=800dp,height=360dp,dpi=480", showBackground = true)
@Composable
fun CenterControlsPlayingPreview() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        CenterControls(
            isPlaying = true,
            isLoading = false,
            onPlayPauseToggle = {},
            onNextEpisode = {},
            onPreviousEpisode = {}
        )
    }
}

@Preview(name = "Loading", device = "spec:width=800dp,height=360dp,dpi=480", showBackground = true)
@Composable
fun CenterControlsLoadingPreview() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        CenterControls(
            isPlaying = false,
            isLoading = true,
            onPlayPauseToggle = {},
            onNextEpisode = {},
            onPreviousEpisode = {}
        )
    }
}