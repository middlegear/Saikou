package ani.saikou.parsers

import ani.saikou.Lazier
import ani.saikou.lazyList
import ani.saikou.parsers.anime.AniBD
import ani.saikou.parsers.anime.Anikoto
import ani.saikou.parsers.anime.AnimeHeaven
import ani.saikou.parsers.anime.Anizone
import ani.saikou.parsers.anime.Haho
import ani.saikou.parsers.anime.HentaiFF
import ani.saikou.parsers.anime.HentaiMama
import ani.saikou.parsers.anime.HentaiStream
import ani.saikou.parsers.anime.Torrentio

object AnimeSources : WatchSources() {

    override val list: List<Lazier<BaseParser>> = lazyList(
        "Torrentio" to ::Torrentio,
        "Anikoto" to ::Anikoto,
        "AniBD" to ::AniBD,
        "AnimeHeaven" to ::AnimeHeaven,
        "Anizone" to ::Anizone,

    )
}

object HAnimeSources : WatchSources() {

    private val hList: List<Lazier<BaseParser>> = lazyList(
        "HentaiMama" to ::HentaiMama,
        "Haho" to ::Haho,
        "HentaiStream" to ::HentaiStream,
        "HentaiFF" to ::HentaiFF,
    )

    override val list: List<Lazier<BaseParser>>
        get() = hList + AnimeSources.list
}