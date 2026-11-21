package org.koitharu.kotatsu.parsers.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.*

public class LinkResolver(
	public val link: HttpUrl,
	public val context: MangaLoaderContext? = null,
	public val parser: MangaParser? = null,
) {

	public suspend fun getSource(): MangaSource? = parser?.source

	public suspend fun getManga(): Manga? {
		val p = parser ?: return null // TODO: implement global link resolution via context
		return p.resolveLink(link) ?: resolveManga(p)
	}

	public suspend fun resolveManga(
		parser: MangaParser,
		url: String = link.toString().toRelativeUrl(link.host),
		id: Long = generateUid(parser.source, url),
		title: String = STUB_TITLE,
	): Manga? = resolveBySeed(
		parser,
		Manga(
			id = id,
			title = title,
			altTitles = emptySet(),
			url = url,
			publicUrl = link.toString(),
			rating = RATING_UNKNOWN,
			contentRating = null,
			coverUrl = "",
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			largeCoverUrl = null,
			description = null,
			chapters = null,
			source = parser.source,
		),
	)

	private suspend fun resolveBySeed(parser: MangaParser, s: Manga): Manga? {
		val seed = parser.getDetails(s)
		if (!parser.filterCapabilities.isSearchSupported) {
			return seed.takeUnless { it.chapters.isNullOrEmpty() }
		}
		val query = when {
			seed.title != STUB_TITLE && seed.title.isNotEmpty() -> seed.title
			seed.altTitles.isNotEmpty() -> seed.altTitles.first()
			seed.authors.isNotEmpty() -> seed.authors.first()
			else -> return seed // unfortunately we do not know a real manga title so unable to find it
		}
		val resolved = runCatching {
			val list = parser.getList(0, parser.bestSortOrder(), MangaListFilter(query = query))
			list.singleOrNull { manga -> isSameUrl(manga.publicUrl) }
		}.getOrNull()
		if (resolved == null) {
			return seed
		}
		return runCatching {
			parser.getDetails(resolved)
		}.getOrElse {
			resolved.copy(
				chapters = seed.chapters ?: resolved.chapters,
				description = seed.description ?: resolved.description,
				authors = seed.authors.ifEmpty { resolved.authors },
				tags = seed.tags + resolved.tags,
				state = seed.state ?: resolved.state,
				coverUrl = seed.coverUrl ?: resolved.coverUrl,
				largeCoverUrl = seed.largeCoverUrl ?: resolved.largeCoverUrl,
				altTitles = seed.altTitles + resolved.altTitles,
			)
		}
	}

	private fun isSameUrl(publicUrl: String): Boolean {
		if (publicUrl == link.toString()) {
			return true
		}
		val httpUrl = publicUrl.toHttpUrlOrNull() ?: return false
		return link.host == httpUrl.host
			&& link.encodedPath == httpUrl.encodedPath
	}

	private fun MangaParser.bestSortOrder(): SortOrder {
		val supported = availableSortOrders
		if (SortOrder.RELEVANCE in supported) {
			return SortOrder.RELEVANCE
		}
		return SortOrder.values().first { it in supported }
	}

	private companion object {
		const val STUB_TITLE = "Unknown manga"
	}
}
