package ani.saikou.updater.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ani.saikou.BuildConfig
import ani.saikou.compose.SaikouTheme
import ani.saikou.updater.UpdateState
import ani.saikou.updater.ui.components.ChangelogSection
import ani.saikou.updater.ui.components.CheckingForUpdatesSection
import ani.saikou.updater.ui.components.DownloadProgressSection
import ani.saikou.updater.ui.components.ErrorStateSection
import ani.saikou.updater.ui.components.IdleStateContent
import ani.saikou.updater.ui.components.UpdateActionButtons
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AppUpdateContent(
    state: UpdateState,
    onCheckForUpdates: () -> Unit,
    onStartDownload: (url: String, version: String) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onDontShowAgain: (version: String, isChecked: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDismissing by remember { mutableStateOf(false) }

    val handleDismiss: () -> Unit = {
        if (!isDismissing) {
            isDismissing = true
            onDismiss()
        }
    }

    BackHandler {
        handleDismiss()
    }

    var dontAskChecked by remember { mutableStateOf(false) }
    var lastDownloadingState by remember { mutableStateOf<UpdateState.Downloading?>(null) }

    val scope = rememberCoroutineScope()

    val onCheckForUpdatesWithDelay: () -> Unit = {
        scope.launch {
            val startTime = System.currentTimeMillis()
            onCheckForUpdates()
            val elapsedTime = System.currentTimeMillis() - startTime
            val minDelay = 2000L
            if (elapsedTime < minDelay) {
                delay((minDelay - elapsedTime).milliseconds)
            }
        }
    }

    LaunchedEffect(state) {
        when (state) {
            is UpdateState.Downloading -> lastDownloadingState = state
            is UpdateState.ReadyToInstall -> onInstall(state.apkFile)
            else -> {}
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {

        // --- BACK BUTTON ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 32.dp)
        ) {
            Surface(
                onClick = handleDismiss,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(64.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // --- TITLE & CHECK FOR UPDATES ICON ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Updater",
                fontSize = 28.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 32.dp, top = 16.dp, bottom = 16.dp, end = 16.dp)
            )


            Box(
                modifier = Modifier
                    .padding(end = 20.dp)
                    .size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        // --- DYNAMIC CONTENT ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (!isDismissing) {
                AnimatedContent(
                    targetState = state,
                    label = "UpdateStateContent",
                    contentAlignment = Alignment.Center,
                    contentKey = {
                        when (it) {
                            is UpdateState.Downloading, is UpdateState.ReadyToInstall -> "download"
                            is UpdateState.Idle -> "idle"
                            is UpdateState.Checking -> "checking"
                            is UpdateState.Available -> "available"
                            is UpdateState.Error -> "error"
                        }
                    },
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150)))
                            .using(SizeTransform(clip = false))
                    },
                    modifier = Modifier.fillMaxSize()
                ) { currentState ->
                    when (currentState) {
                        is UpdateState.Idle -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                IdleStateContent(
                                    currentVersion = BuildConfig.VERSION_NAME,
                                    onCheckForUpdates = onCheckForUpdatesWithDelay
                                )
                            }
                        }

                        is UpdateState.Checking -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CheckingForUpdatesSection(onCancelCheck = handleDismiss)
                            }
                        }

                        is UpdateState.Available -> {

                            Column(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = "New Update Available",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    ChangelogSection(
                                        version = currentState.version,
                                        changelog = currentState.changelog,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))


                                UpdateActionButtons(
                                    isDownloading = false,
                                    isReadyToInstall = false,
                                    dontShowAgain = dontAskChecked,
                                    onDontShowAgainChange = {
                                        dontAskChecked = it
                                        onDontShowAgain(currentState.version, it)
                                    },
                                    onPrimaryAction = {
                                        onStartDownload(currentState.downloadUrl, currentState.version)
                                    },
                                    onDismiss = handleDismiss
                                )
                            }
                        }

                        is UpdateState.Downloading, is UpdateState.ReadyToInstall -> {
                            val total = if (currentState is UpdateState.ReadyToInstall) {
                                lastDownloadingState?.totalBytes
                                    ?: currentState.apkFile.length().takeIf { it > 0 }
                                    ?: 0L
                            } else {
                                (currentState as UpdateState.Downloading).totalBytes
                            }

                            val downloaded = if (currentState is UpdateState.ReadyToInstall) total else (currentState as UpdateState.Downloading).downloadedBytes
                            val progress = if (currentState is UpdateState.ReadyToInstall) 100 else (currentState as UpdateState.Downloading).progressPercentage

                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                DownloadProgressSection(
                                    progressPercentage = progress,
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    onCancelDownload = onCancelDownload
                                )
                            }
                        }

                        is UpdateState.Error -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                ErrorStateSection(
                                    errorMessage = currentState.message,
                                    onRetry = onCheckForUpdatesWithDelay,
                                    onDismiss = handleDismiss
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// PREVIEWS

@Preview(name = "1. Idle State", showBackground = true)
@Composable
private fun AppUpdatePreview_Idle() {
    SaikouTheme {
        Surface {
            AppUpdateContent(
                state = UpdateState.Idle,
                onCheckForUpdates = {},
                onStartDownload = { _, _ -> },
                onCancelDownload = {},
                onInstall = {},
                onDontShowAgain = { _, _ -> },
                onDismiss = {}
            )
        }
    }
}

//@Preview(name = "2. Checking State", showBackground = true)
//@Composable
//private fun AppUpdatePreview_Checking() {
//    SaikouTheme {
//        Surface {
//            AppUpdateContent(
//                state = UpdateState.Checking,
//                onCheckForUpdates = {},
//                onStartDownload = { _, _ -> },
//                onCancelDownload = {},
//                onInstall = {},
//                onDontShowAgain = { _, _ -> },
//                onDismiss = {}
//            )
//        }
//    }
//}
//
//@Preview(name = "3. Available Update", showBackground = true)
//@Composable
//private fun AppUpdatePreview_Available() {
//    SaikouTheme {
//        Surface {
//            AppUpdateContent(
//                state = UpdateState.Available(
//                    version = "1.2.5-beta",
//                    changelog = """
//                        ### Fixes
//                        * **Anizone:** Fixed search errors, missing sources, and incomplete episode counts.
//                        * **Player:** Resolved video frame dropping during high bitrate playback.
//                    """.trimIndent(),
//                    downloadUrl = "https://github.com/supboys/releases"
//                ),
//                onCheckForUpdates = {},
//                onStartDownload = { _, _ -> },
//                onCancelDownload = {},
//                onInstall = {},
//                onDontShowAgain = { _, _ -> },
//                onDismiss = {}
//            )
//        }
//    }
//}
//
//@Preview(name = "4. Downloading State", showBackground = true)
//@Composable
//private fun AppUpdatePreview_Downloading() {
//    SaikouTheme {
//        Surface {
//            AppUpdateContent(
//                state = UpdateState.Downloading(
//                    downloadedBytes = 15_400_000L,
//                    totalBytes = 32_000_000L,
//                    progressPercentage = 48
//                ),
//                onCheckForUpdates = {},
//                onStartDownload = { _, _ -> },
//                onCancelDownload = {},
//                onInstall = {},
//                onDontShowAgain = { _, _ -> },
//                onDismiss = {}
//            )
//        }
//    }
//}

//@Preview(name = "5. Error State", showBackground = true)
//@Composable
//private fun AppUpdatePreview_Error() {
//    SaikouTheme {
//        Surface {
//            AppUpdateContent(
//                state = UpdateState.Error("Unable to connect to GitHub API (HTTP 429 Rate Limit Exceeded)."),
//                onCheckForUpdates = {},
//                onStartDownload = { _, _ -> },
//                onCancelDownload = {},
//                onInstall = {},
//                onDontShowAgain = { _, _ -> },
//                onDismiss = {}
//            )
//        }
//    }
//}