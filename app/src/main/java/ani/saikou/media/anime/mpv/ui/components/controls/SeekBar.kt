package ani.saikou.media.anime.mpv.ui.components.controls

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ani.saikou.media.anime.mpv.Chapter
import ani.saikou.media.anime.mpv.PlayerRepository
import dev.vivvvek.seeker.Segment
import dev.vivvvek.seeker.Seeker
import dev.vivvvek.seeker.SeekerDefaults
import `is`.xyz.mpv.Utils
import kotlin.math.abs

@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    readAheadMs: Long,
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
    skipStamps: List<PlayerRepository.SkipInterval>? = null,
    chapters: List<Chapter>? = null,
) {
    var dragPositionMs by remember { mutableStateOf<Long?>(null) }
    var seekTargetMs by remember { mutableStateOf<Long?>(null) }
    val currentDisplayPosition = dragPositionMs ?: seekTargetMs ?: positionMs

    LaunchedEffect(positionMs, seekTargetMs) {
        val target = seekTargetMs ?: return@LaunchedEffect
        if (abs(positionMs - target) < 1500L) {
            seekTargetMs = null
        }
    }

    val unifiedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    val seekerSegments = remember(skipStamps, chapters, durationMs) {
        val list = mutableListOf<Segment>()

        if (!chapters.isNullOrEmpty() && durationMs > 0L) {
            val sortedChapters = chapters.sortedBy { it.startMs }

            if (sortedChapters.first().startMs > 0L) {
                list.add(
                    Segment(
                        name = "",
                        start = 0f,
                        color = unifiedTrackColor
                    )
                )
            }

            sortedChapters.forEach { chapter ->
                val startMs = chapter.startMs.toFloat()
                if (startMs < durationMs) {
                    list.add(
                        Segment(
                            name = chapter.title,
                            start = startMs,
                            color = unifiedTrackColor
                        )
                    )
                }
            }
        } else if (!skipStamps.isNullOrEmpty() && durationMs > 0L) {
            val sortedStamps = skipStamps.sortedBy { it.startTimeMs }

            if (sortedStamps.first().startTimeMs > 0L) {
                list.add(
                    Segment(
                        name = "",
                        start = 0f,
                        color = unifiedTrackColor
                    )
                )
            }

            sortedStamps.forEach { stamp ->
                val startMs = stamp.startTimeMs.toFloat()
                if (startMs < durationMs) {
                    list.add(
                        Segment(
                            name = stamp.type,
                            start = startMs,
                            color = unifiedTrackColor
                        )
                    )
                }
            }
        }

        list.distinctBy { it.start }.sortedBy { it.start }
    }

    Row(
        modifier = modifier
            .height(48.dp)
            .pointerInput(Unit) {
                val exclusionHeightPx = 16.dp.toPx()
                awaitEachGesture {
                    val firstDown = awaitFirstDown(pass = PointerEventPass.Initial)
                    val localBottomExclusionLine = size.height - exclusionHeightPx

                    if (firstDown.position.y > localBottomExclusionLine) {
                        firstDown.consume()
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VideoTimer(
            valueMs = currentDisplayPosition,
            modifier = Modifier.width(64.dp),
        )

        val durationFloat = durationMs.toFloat().coerceAtLeast(0f)
        val clampedDisplay = currentDisplayPosition.toFloat().coerceIn(0f, durationFloat)

        Seeker(
            value = clampedDisplay,
            thumbValue = clampedDisplay,
            range = 0f..durationFloat,
            readAheadValue = readAheadMs.toFloat().coerceIn(0f, durationFloat),
            segments = seekerSegments,
            onValueChange = { newValue ->
                seekTargetMs = null
                dragPositionMs = newValue.toLong()
            },
            onValueChangeFinished = {
                val finishedValue = dragPositionMs
                dragPositionMs = null
                if (finishedValue != null) {
                    seekTargetMs = finishedValue
                    onSeekFinished(finishedValue)
                }
            },
            modifier = Modifier.weight(1f),
            dimensions = SeekerDefaults.seekerDimensions(
                trackHeight = 4.dp,
                thumbRadius = 8.dp,
                gap = 2.dp
            ),
            colors = SeekerDefaults.seekerColors(
                progressColor = MaterialTheme.colorScheme.primary,
                thumbColor = MaterialTheme.colorScheme.primary,
                trackColor = unifiedTrackColor,
                readAheadColor = Color.White
            ),
        )

        VideoTimer(
            valueMs = durationMs,
            modifier = Modifier.width(64.dp),
        )
    }
}

@Composable
fun VideoTimer(
    valueMs: Long,
    modifier: Modifier = Modifier,
) {
    val formattedTime = remember(valueMs) {
        val seconds = (valueMs / 1000).toInt()
        Utils.prettyTime(seconds, false)
    }

    Text(
        modifier = modifier
            .fillMaxHeight()
            .wrapContentHeight(Alignment.CenterVertically),
        text = formattedTime,
        color = Color.White,
        textAlign = TextAlign.Center,
    )
}