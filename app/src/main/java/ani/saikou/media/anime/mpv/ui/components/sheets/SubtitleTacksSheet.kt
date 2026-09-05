package ani.saikou.media.anime.mpv.ui.components.sheets

import androidx.compose.runtime.Composable
import ani.saikou.media.anime.mpv.SubtitleTrack

@Composable
fun SubtitlesTracksSheet(
    title: String = "Select Subtitle",
    trackList: List<SubtitleTrack>,
    currentTrackId: Int?,
    onTrackSelected: (Int?) -> Unit,
    onDismissRequest: () -> Unit
) {
    val items: List<SubtitleTrack?> = listOf(null) + trackList
    val currentTrackObj = trackList.find { it.id == currentTrackId }
    GenericTracksSheet(
        title = title,
        trackList = items,
        currentTrack = currentTrackObj,
        trackToText = { track ->
            track?.name?.ifEmpty { track.language ?: "Unknown Subtitle" } ?: "None"
        },
        onTrackSelected = { selectedTrack ->
            onTrackSelected(selectedTrack?.id)
        },
        onDismissRequest = onDismissRequest
    )
}