package ani.saikou.media.anime.mpv.ui.components.sheets


import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ani.saikou.compose.SaikouTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun <T> GenericTracksSheet(
    title: String = "Select Track",
    trackList: List<T>,
    currentTrack: T,
    trackToText: (T) -> String,
    onTrackSelected: (T) -> Unit,
    onDismissRequest: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val isPreview = LocalInspectionMode.current
    var isAnimatedVisible by remember { mutableStateOf(isPreview) }

    var selectedTrackInternal by remember(currentTrack) { mutableStateOf(currentTrack) }


    val configuration = LocalConfiguration.current

    val screenHeightDp = configuration.screenHeightDp.dp
    val minSheetHeight = screenHeightDp * 0.80f
    val maxSheetWidth = configuration.screenWidthDp.dp * 0.70f


    LaunchedEffect(Unit) {
        if (!isPreview) {
            isAnimatedVisible = true
        }
    }

    val animateAndDismiss: () -> Unit = {
        coroutineScope.launch {
            isAnimatedVisible = false
            delay(300.milliseconds)
            onDismissRequest()
        }
    }

    BackHandler(enabled = isAnimatedVisible && !isPreview) {
        animateAndDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { animateAndDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = isAnimatedVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 300)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 300)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = maxSheetWidth)
                    .fillMaxWidth()
                    .heightIn(min = minSheetHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                shape = RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp,
                    bottomStart = 0.dp,
                    bottomEnd = 0.dp
                ),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                shape = MaterialTheme.shapes.extraLarge
                            )
                    )

                    Text(
                        text = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp, start = 24.dp, end = 24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .selectableGroup()
                            .padding(horizontal = 20.dp)
                    ) {
                        items(trackList) { track ->
                            val isSelected = track == selectedTrackInternal

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTrackInternal = track
                                        onTrackSelected(track)
                                    }
                                    .padding(vertical = 4.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTrackInternal = track
                                        onTrackSelected(track)
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = trackToText(track),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Preview(
    showSystemUi = true,
    showBackground = true,
    device = "spec:width=800dp, height=360dp, dpi=480, orientation=landscape"
)
@Composable
fun AudioTrackSelectorSheetPreview() {
    val languageTracks = listOf(
        "English  - AAC Stereo",
        "Spanish  - 5.1 Surround",
        "French  - Stereo",
        "German  - Stereo",
        "Japanese  - AAC Stereo",
        "Italiano - Stereo",
    )

    var selectedLanguage by remember { mutableStateOf("English  - AAC Stereo") }
    var showSheet by remember { mutableStateOf(true) }

    SaikouTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (showSheet) {
                    GenericTracksSheet(
                        trackList = languageTracks,
                        currentTrack = selectedLanguage,
                        title = "Select Audio Track",
                        trackToText = { it },
                        onTrackSelected = { choice ->
                            selectedLanguage = choice
                        },
                        onDismissRequest = {
                            showSheet = false
                        }
                    )
                }
            }
        }
    }
}