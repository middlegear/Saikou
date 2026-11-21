package eu.kanade.tachiyomi.torrentServer

import eu.kanade.tachiyomi.torrentServer.model.Torrent

object TorrentServerUtils {
    val hostUrl = "http://127.0.0.1:8090"
    
    fun setTrackersList() {}
    
    fun getTorrentPlayLink(torr: Torrent, index: Int): String {
        return "$hostUrl/stream/video?link=${torr.hash}&index=$index&play"
    }
}
