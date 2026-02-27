package ani.saikou.parsers

import ani.saikou.Lazier
import ani.saikou.lazyList
import ani.saikou.parsers.manga.*

object MangaSources : MangaReadSources() {
    override val list: List<Lazier<BaseParser>> = lazyList(
        "Comix" to ::Comix,
        "AllManga" to ::AllManga,
        "MangaBuddy" to ::MangaBuddy,
        "MangaPill" to ::MangaPill,
        "MangaDex" to ::MangaDex,
        "Toonily" to ::Toonily, /// will need to check the parsers
//        "MangaHub" to ::MangaHub,
        "MangaKatana" to ::MangaKatana,
//        "MangaKomi" to ::MangaKomi, /// dead source
//        "Manga4Life" to ::Manga4Life, /// moved to weebcentral covered by cloudflare fk htmx
        "MangaRead" to ::MangaRead,
//        "ColoredManga" to ::ColoredManga, /// domain is not resolving probably dead
    )
}

object HMangaSources : MangaReadSources() {
    val aList: List<Lazier<BaseParser>> = lazyList(

        "Manhwa18" to ::Manhwa18,

    )
    override val list = listOf(aList,MangaSources.list).flatten()
}
