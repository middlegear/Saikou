package ani.saikou.media.anime.mpv.ui.components.controls


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ani.saikou.compose.SaikouTheme
import ani.saikou.torrserver.models.TorrentStats
import java.util.Locale

@Composable
fun TorrentStatsPill(
    stats: TorrentStats,
    isEnabled: Boolean,
    elementTint: Color,
) {
    val uniformHeight = 40.dp
    val uniformBorderThickness = 1.dp
    val pillShape = RoundedCornerShape(50)

    if (isEnabled) {
        Surface(
            color = Color(0xFF121212).copy(alpha = 0.55f),
            shape = pillShape,
            modifier = Modifier
                .height(uniformHeight)
                .wrapContentWidth()
                .border(uniformBorderThickness, elementTint.copy(alpha = 0.4f), pillShape)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {


                StatItem(
                    icon = Icons.Rounded.ArrowDownward,
                    text = formatSpeed(stats.downloadSpeed),
                    tint = elementTint
                )

                StatItem(
                    icon = Icons.Rounded.ArrowUpward,
                    text = formatSpeed(stats.uploadSpeed),
                    tint = elementTint,

                    )


                StatItem(
                    icon = Icons.Rounded.Group,
                    text = "${stats.activePeers}(${stats.totalPeers})",
                    tint = elementTint
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    text: String,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            color = tint,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "0.00 MB/s"
    val megabytesPerSecond = bytesPerSecond / (1024.0 * 1024.0)
    return String.format(Locale.ROOT, "%.2f MB/s", megabytesPerSecond)
}

@Preview(name = "Themed Overlay Display", widthDp = 500, heightDp = 100)
@Composable
fun TorrentStatsPillPreview() {
    val sampleStats = TorrentStats(
        activePeers = 8,
        totalPeers = 32,
        downloadSpeed = 4 * 1024 * 1024,
        uploadSpeed = 620 * 1024,
        progress = 0.12f,

        )
    SaikouTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1E)),
            contentAlignment = Alignment.Center
        ) {
            TorrentStatsPill(
                isEnabled = true,
                stats = sampleStats,
                elementTint = Color.White
            )
        }
    }
}