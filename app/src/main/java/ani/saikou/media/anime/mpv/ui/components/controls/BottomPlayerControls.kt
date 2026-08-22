package ani.saikou.media.anime.mpv.ui.components.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ani.saikou.media.anime.mpv.PlayerRepository
import ani.saikou.media.anime.mpv.PlayerViewModel


@Composable
fun BottomPlayerControls(
    isLocked: Boolean,
    isControlsVisible: Boolean,
    onLockToggled: (Boolean) -> Unit,
    onUserInteraction: () -> Unit,
    segmentName: String,
    skipSegmentDuration: Int?,
    onSkipSegmentClicked: () -> Unit,
    onAspectRatioClicked: () -> Unit,
    positionMs: Long,
    durationMs: Long,
    readAheadMs: Long,
    onSeekFinished: (Long) -> Unit, skipStamps: List<PlayerRepository.SkipInterval>? = null,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val elementTint = Color.White
    val feedbackColor = MaterialTheme.colorScheme.primary

    val isSegmentAvailable = skipSegmentDuration != null
    val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    val current = viewModel.playbackSpeed.collectAsState().value
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val leftAlpha by animateFloatAsState(
                targetValue = if (isControlsVisible) 1f else 0f,
                animationSpec = tween(200),
                label = "BottomLeftAlpha"
            )

            BottomLeftControls(
                isLocked = isLocked,
                onLockToggled = onLockToggled,
                currentSpeed = current,
                onSpeedChanged = {
                    onUserInteraction()

                    val nextIndex =
                        (speedOptions.indexOf(current) + 1) % speedOptions.size
                    viewModel.setPlaybackSpeed(speedOptions[nextIndex])
                },
                elementTint = elementTint,
                feedbackColor = feedbackColor,
                modifier = Modifier
                    .wrapContentWidth()
                    .graphicsLayer { alpha = leftAlpha }
                    .then(
                        if (!isControlsVisible)
                            Modifier.pointerInput(Unit) {}
                        else Modifier
                    )
            )

            Spacer(modifier = Modifier.weight(1f))

            BottomRightControls(
                isLocked = isLocked,
                isControlsVisible = isControlsVisible,
                skipDurationSecondsSettings = viewModel.settings.skipTime,
                onSkipSegmentClicked = onSkipSegmentClicked,
                onAspectRatioClicked = onAspectRatioClicked,
                elementTint = elementTint,
                feedbackColor = feedbackColor,
                segment = segmentName,
                isSegmentAvailable = isSegmentAvailable
            )
        }

        Spacer(modifier = Modifier.height(4.dp))


        val seekBarAlpha by animateFloatAsState(
            targetValue = if (isControlsVisible) 1f else 0f,
            animationSpec = tween(200),
            label = "SeekBarAlpha"
        )

        SeekBar(
            positionMs = positionMs,
            durationMs = durationMs,
            readAheadMs = readAheadMs,
            onSeekFinished = onSeekFinished,
            skipStamps = skipStamps,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .graphicsLayer { alpha = seekBarAlpha }
                .then(
                    if (!isControlsVisible)
                        Modifier.pointerInput(Unit) {}
                    else Modifier
                )
        )
    }
}