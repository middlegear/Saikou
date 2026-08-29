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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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

@SuppressLint("DefaultLocale")
@Composable
fun BottomLeftControls(
    isLocked: Boolean,
    onLockToggled: (Boolean) -> Unit,
    currentSpeed: Float,
    onSpeedChanged: () -> Unit,
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

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = feedbackColor, radius = 24.dp),
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

        Box(
            modifier = Modifier
                .height(uniformHeight)
                .width(72.dp)
                .clip(pillShape)
                .border(uniformBorderThickness, if (isLocked) elementTint.copy(alpha = 0.4f) else elementTint, pillShape)
                .clickable(
                    enabled = !isLocked,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = feedbackColor),
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
    }
}