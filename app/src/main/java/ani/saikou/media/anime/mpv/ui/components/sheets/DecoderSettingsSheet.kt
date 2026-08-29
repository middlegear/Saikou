package ani.saikou.media.anime.mpv.ui.components.sheets

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ani.saikou.compose.SaikouTheme
import ani.saikou.media.anime.mpv.AudioChannels
import ani.saikou.media.anime.mpv.Decoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DecoderSettingsSheet(
    selectedDecoder: Decoder,
    onDecoderSelected: (Decoder) -> Unit,
    selectedAudioChannel: AudioChannels,
    onAudioChannelSelected: (AudioChannels) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    availableDecoders: List<Decoder> = Decoder.entries,
    availableAudioChannels: List<AudioChannels> = AudioChannels.entries
) {
    val coroutineScope = rememberCoroutineScope()
    val isPreview = LocalInspectionMode.current
    var isAnimatedVisible by remember { mutableStateOf(isPreview) }

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
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = isAnimatedVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = fadeOut(animationSpec = tween(durationMillis = 300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { animateAndDismiss() }
            )
        }

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
                val primaryColor = MaterialTheme.colorScheme.primary
                val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                val unselectedBg = onSurfaceColor.copy(alpha = 0.06f)
                val selectedBg = primaryColor.copy(alpha = 0.15f)
                val selectedBorder = primaryColor.copy(alpha = 0.4f)

                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp)
                        .padding(
                            bottom = WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding() + 16.dp
                        )
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .size(width = 36.dp, height = 4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    shape = MaterialTheme.shapes.extraLarge
                                )
                        )

                        Text(
                            text = "Decoder Settings",
                            style = MaterialTheme.typography.titleMedium,
                            color = onSurfaceColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Hardware decoding mode",
                            style = MaterialTheme.typography.labelLarge,
                            color = onSurfaceColor.copy(alpha = 0.5f),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableDecoders.forEach { decoder ->
                                val isSelected = decoder == selectedDecoder

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .widthIn(min = 96.dp)
                                        .height(38.dp)
                                        .background(
                                            color = if (isSelected) selectedBg else unselectedBg,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.dp,
                                            color = if (isSelected) selectedBorder else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onDecoderSelected(decoder) }
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = decoder.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) primaryColor else onSurfaceColor.copy(
                                            alpha = 0.8f
                                        ),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Audio Channels",
                            style = MaterialTheme.typography.labelLarge,
                            color = onSurfaceColor.copy(alpha = 0.5f),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableAudioChannels.forEach { channel ->
                                val isSelected = channel == selectedAudioChannel

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .widthIn(min = 96.dp)
                                        .height(38.dp)
                                        .background(
                                            color = if (isSelected) selectedBg else unselectedBg,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.dp,
                                            color = if (isSelected) selectedBorder else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onAudioChannelSelected(channel) }
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = channel.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) primaryColor else onSurfaceColor.copy(
                                            alpha = 0.8f
                                        ),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center
                                    )
                                }
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
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    device = "spec:width=800dp,height=360dp,dpi=480,orientation=landscape"
)
@Composable
fun HeavyDecoderSettingsSheetPreview() {
    var currentDecoder by remember { mutableStateOf(Decoder.HW) }
    var currentAudio by remember { mutableStateOf(AudioChannels.Stereo) }

    SaikouTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DecoderSettingsSheet(
                selectedDecoder = currentDecoder,
                onDecoderSelected = { currentDecoder = it },
                selectedAudioChannel = currentAudio,
                onAudioChannelSelected = { currentAudio = it },
                onDismissRequest = {},
                availableDecoders = Decoder.entries,
                availableAudioChannels = AudioChannels.entries
            )
        }
    }
}