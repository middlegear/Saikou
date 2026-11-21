package eu.kanade.tachiyomi.torrentServer

import eu.kanade.tachiyomi.torrentServer.model.Torrent
import java.io.InputStream

object TorrentServerApi {
    fun echo(): String = ""
    fun shutdown(): String = ""
    
    fun addTorrent(
        link: String,
        title: String,
        poster: String = "",
        data: String = "",
        save: Boolean,
    ): Torrent {
        return Torrent(
            title = title,
            hash = link.hashCode().toString(16),
            torrent_size = 0L,
            file_stats = emptyList(),
            trackers = emptyList()
        )
    }

    fun getTorrent(hash: String): Torrent {
        return Torrent(
            title = "Torrent",
            hash = hash,
            torrent_size = 0L,
            file_stats = emptyList(),
            trackers = emptyList()
        )
    }

    fun remTorrent(hash: String) {}

    fun listTorrent(): List<Torrent> = emptyList()

    fun uploadTorrent(
        file: InputStream,
        title: String,
        poster: String,
        data: String,
        save: Boolean,
    ): Torrent {
        return Torrent(
            title = title,
            hash = title.hashCode().toString(16),
            torrent_size = 0L,
            file_stats = emptyList(),
            trackers = emptyList()
        )
    }
}
