package ani.saikou.updater.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ani.saikou.compose.SaikouTheme


@Composable
fun UpdateActionButtons(
    isDownloading: Boolean,
    isReadyToInstall: Boolean,
    dontShowAgain: Boolean,
    onDontShowAgainChange: (Boolean) -> Unit,
    onPrimaryAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {

    Column(modifier = modifier.fillMaxWidth()) {
        if (!isDownloading && !isReadyToInstall) {
            val interactionSource = remember { MutableInteractionSource() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        onDontShowAgainChange(!dontShowAgain)
                    }
            ) {
                Checkbox(
                    checked = dontShowAgain,
                    onCheckedChange = onDontShowAgainChange
                )
                Text(
                    text = "Don't ask for this version again",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
                dontShowAgain = false,
                onDontShowAgainChange = {},
                onPrimaryAction = {},
                onDismiss = {}
            )
        }
    }
}