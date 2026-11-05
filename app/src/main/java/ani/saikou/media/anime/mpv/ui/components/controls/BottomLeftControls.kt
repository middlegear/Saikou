package ani.saikou.media.anime.mpv.ui.components.controls

import android.annotation.SuppressLint
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@SuppressLint("DefaultLocale")
private fun Long.formatTimestamp(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun BottomLeftControls(
    isLocked: Boolean,
    onLockToggled: (Boolean) -> Unit,
    currentSpeed: Float,
    onSpeedChanged: () -> Unit,
    hasChapters: Boolean,
    currentChapterTitle: String,
    currentChapterStartMs: Long,
    onChaptersClick: () -> Unit,
    elementTint: Color,
    feedbackColor: Color,
    modifier: Modifier = Modifier
) {
    val uniformHeight = 40.dp
    val uniformBorderThickness = 2.dp
    val pillShape = RoundedCornerShape(50)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Lock / Unlock Button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = feedbackColor, radius = 24.dp),
                    onClick = { onLockToggled(!isLocked) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = "Toggle Screen Lock",
                tint = elementTint
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        val speedLabel = remember(currentSpeed) {
            String.format("%.2fx", currentSpeed)
        }

        // 2. Speed Pill Button
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
                    indication = ripple(bounded = true, color = feedbackColor),
                    onClick = onSpeedChanged
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = speedLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (isLocked) elementTint.copy(alpha = 0.4f) else elementTint,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 3. Chapter Pill Button
        if (hasChapters) {
            Spacer(modifier = Modifier.width(12.dp))

            val timestampLabel = remember(currentChapterStartMs) {
                currentChapterStartMs.formatTimestamp()
            }

            Box(
                modifier = Modifier
                    .height(uniformHeight)
                    .clip(pillShape)
                    .border(
                        uniformBorderThickness,
                        if (isLocked) elementTint.copy(alpha = 0.4f) else elementTint,
                        pillShape
                    )
                    .clickable(
                        enabled = !isLocked,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = feedbackColor),
                        onClick = onChaptersClick
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$currentChapterTitle • $timestampLabel",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLocked) elementTint.copy(alpha = 0.4f) else elementTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Open Chapters Sheet",
                        tint = if (isLocked) elementTint.copy(alpha = 0.4f) else elementTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}