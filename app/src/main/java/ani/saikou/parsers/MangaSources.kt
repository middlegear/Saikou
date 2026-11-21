package ani.saikou.parsers

import ani.saikou.Lazier
import ani.saikou.extensions.ExtensionSourceRegistry
import ani.saikou.lazyList
import ani.saikou.parsers.manga.*

object MangaSources : MangaReadSources() {
    private val builtIn: List<Lazier<BaseParser>> = lazyList(

//        "AllManga" to ::AllManga,
        "MangaK" to ::MangaK,
        "WeebCentral" to ::WeebCentral,
        "MangaHub" to ::MangaHub,
        "MangaRead" to ::MangaRead,
        "MangaPill" to ::MangaPill,
//        "MangaDex" to ::MangaDex,
//        "Toonily" to ::Toonily,  //stubborn ass provider returning wrong results idk why
//        "MangaKatana" to ::MangaKatana,  // host url isnot resolving
    )

    override val list: List<Lazier<BaseParser>>
        get() = builtIn + ExtensionSourceRegistry.mangaSources()
}

object HMangaSources : MangaReadSources() {
    val aList: List<Lazier<BaseParser>> = lazyList(

        "Manhwa18" to ::Manhwa18,

    )
    override val list: List<Lazier<BaseParser>>
        get() = listOf(aList, ExtensionSourceRegistry.nsfwMangaSources(), MangaSources.list).flatten()
}
