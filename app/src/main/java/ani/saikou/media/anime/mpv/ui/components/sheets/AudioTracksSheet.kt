package ani.saikou.media.anime.mpv.ui.components.sheets

import androidx.compose.runtime.Composable
import ani.saikou.media.anime.mpv.AudioTrack

@Composable

fun AudioTracksSheet(
    title: String = "Audio Language",
    trackList: List<AudioTrack>,
    currentTrack: AudioTrack,
    onTrackSelected: (AudioTrack) -> Unit,
    onDismissRequest: () -> Unit
) {
    GenericTracksSheet(
        title = title,
        trackList = trackList,
        currentTrack = currentTrack,
        trackToText = { track ->
            buildString {
                append(track.name.ifEmpty { "Unknown Audio" })
                if (track.isDefault) append(" [Default]")
            }
        },
        onTrackSelected = onTrackSelected,
        onDismissRequest = onDismissRequest
    )
}