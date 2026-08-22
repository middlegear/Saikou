package ani.saikou.media.anime.mpv.ui.components.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun BottomRightControls(
    isLocked: Boolean,
    isControlsVisible: Boolean,
    skipDurationSecondsSettings: Int?,
    onSkipSegmentClicked: () -> Unit,
    onAspectRatioClicked: () -> Unit,
    elementTint: Color,
    feedbackColor: Color,
    segment: String,
    isSegmentAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    val uniformHeight = 40.dp
    val uniformBorderThickness = 2.dp
    val pillShape = RoundedCornerShape(50)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {

        if (isSegmentAvailable) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .height(uniformHeight)
                        .clip(pillShape)
                        .border(uniformBorderThickness, elementTint, pillShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(bounded = true, color = feedbackColor),
                            onClick = onSkipSegmentClicked
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FastForward,
                            contentDescription = "Skip $segment",
                            tint = elementTint
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Skip $segment",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = elementTint
                        )
                    }
                }
            }
        } else {
            val isSkipButtonVisible = isControlsVisible && (skipDurationSecondsSettings ?: 0) > 0
            AnimatedVisibility(
                visible = isSkipButtonVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val skipLabel = remember(skipDurationSecondsSettings) {
                    "+${skipDurationSecondsSettings}s"
                }

                Box(
                    modifier = Modifier
                        .height(uniformHeight)
                        .width(72.dp)
                        .clip(pillShape)
                        .border(
                            uniformBorderThickness,
                            if (isLocked) elementTint.copy(alpha = 0.4f) else elementTint,
                            pillShape
                        )
                        .clickable(
                            enabled = !isLocked,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(bounded = true, color = feedbackColor),
                            onClick = onSkipSegmentClicked
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = skipLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (isLocked) elementTint.copy(alpha = 0.4f) else elementTint,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = !isLocked,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(
                                bounded = true,
                                color = feedbackColor,
                                radius = 24.dp
                            ),
                            onClick = { onAspectRatioClicked() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AspectRatio,
                        contentDescription = "Toggle Screen Scale Mode",
                        tint = elementTint
                    )
                }
            }
        }
    }
}