package ani.saikou.parsers

import ani.saikou.Lazier
import ani.saikou.lazyList
import ani.saikou.parsers.novel.AnnaArchive

object NovelSources : NovelReadSources() {
    override val list: List<Lazier<BaseParser>> = lazyList(
        "Anna's Archive" to ::AnnaArchive,
    )
}