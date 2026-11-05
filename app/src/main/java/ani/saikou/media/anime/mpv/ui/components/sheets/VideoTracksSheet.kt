package ani.saikou.media.anime.mpv.ui.components.sheets

import androidx.compose.runtime.Composable
import ani.saikou.media.anime.mpv.VideoTrack

@Composable
fun VideoTracksSheet(
    title: String = "Video Quality",
    trackList: List<VideoTrack>,
    currentTrack: VideoTrack,
    onTrackSelected: (VideoTrack) -> Unit,
    onDismissRequest: () -> Unit
) {
    GenericTracksSheet(
        title = title,
        trackList = trackList,
        currentTrack = currentTrack,
        trackToText = { track ->
            track.name.ifEmpty { "Auto / Unknown Quality" }
        },
        onTrackSelected = onTrackSelected,
        onDismissRequest = onDismissRequest
    )
}