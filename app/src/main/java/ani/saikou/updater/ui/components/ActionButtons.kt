package ani.saikou.updater.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ani.saikou.compose.SaikouTheme

@Composable
fun UpdateActionButtons(
    isDownloading: Boolean,
    isReadyToInstall: Boolean,
    onPrimaryAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isDownloading) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Button(
                onClick = onPrimaryAction,
                enabled = !isDownloading,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when {
                        isReadyToInstall -> "Install Now"
                        isDownloading -> "Downloading..."
                        else -> "Update"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UpdateActionButtonsPreview() {
    SaikouTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            UpdateActionButtons(
                isDownloading = false,
                isReadyToInstall = false,
                onPrimaryAction = {},
                onDismiss = {}
            )
        }
    }
}