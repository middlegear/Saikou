package ani.saikou.parsers

import ani.saikou.Lazier
import ani.saikou.lazyList
import ani.saikou.parsers.anime.AllAnime
import ani.saikou.parsers.anime.Animekai
import ani.saikou.parsers.anime.AnimePahe
import ani.saikou.parsers.anime.Haho
import ani.saikou.parsers.anime.HentaiFF
import ani.saikou.parsers.anime.HentaiMama
import ani.saikou.parsers.anime.HentaiStream

//import ani.saikou.parsers.anime.Anizone
import ani.saikou.parsers.anime.Hianime
import ani.saikou.parsers.anime.Kaido

object AnimeSources : WatchSources() {
    override val list: List<Lazier<BaseParser>> = lazyList(
        "H!Anime" to ::Hianime,
        "Kaido" to ::Kaido,
        "AllAnime" to ::AllAnime,
        "AnimePahe" to ::AnimePahe,
//        "Anizone" to ::Anizone,
//        "Animekai" to ::Animekai,
    )
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
