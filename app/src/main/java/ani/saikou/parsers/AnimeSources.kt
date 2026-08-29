package ani.saikou.parsers

import ani.saikou.Lazier
import ani.saikou.lazyList
import ani.saikou.loadData
import ani.saikou.parsers.anime.Haho
import ani.saikou.parsers.anime.HentaiFF
import ani.saikou.parsers.anime.HentaiMama
import ani.saikou.parsers.anime.HentaiStream
import ani.saikou.parsers.anime.Anizone
import ani.saikou.parsers.anime.AniDB
import ani.saikou.parsers.anime.AnimeHeaven
import ani.saikou.parsers.anime.AniBD
import ani.saikou.parsers.anime.Anikoto
import ani.saikou.parsers.anime.Torrentio
import ani.saikou.torrserver.utils.TorrentSettings


object AnimeSources : WatchSources() {

    private val torrentOnlyList: List<Lazier<BaseParser>> = lazyList(
        "Torrentio" to ::Torrentio,

    )

    private val fullList: List<Lazier<BaseParser>> = lazyList(
//        "AllAnime" to ::AllAnime,
        "Anikoto" to ::Anikoto,
        "AniDB" to ::AniDB,
        "AnimeHeaven" to ::AnimeHeaven,
        "AniBD" to ::AniBD,
//        "AnimePahe" to ::AnimePahe,
        "Anizone" to ::Anizone,

        )

    override val list: List<Lazier<BaseParser>>
        get() {
            val settings = loadData<TorrentSettings>("torrent_settings") ?: TorrentSettings()
            return if (settings.enableTorrentServer) torrentOnlyList else fullList
        }
}

object HAnimeSources : WatchSources() {
    private val aList: List<Lazier<BaseParser>> = lazyList(
        "HentaiMama" to ::HentaiMama,
        "Haho" to ::Haho,
        "HentaiStream" to ::HentaiStream,
        "HentaiFF" to ::HentaiFF,
    )

    override val list = listOf(aList, AnimeSources.list).flatten()
}