@file:Suppress("DEPRECATION")

package org.koitharu.kotatsu.parsers.core

import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities
import org.koitharu.kotatsu.parsers.util.LinkResolver

public class MangaParserWrapper(
	private val delegate: MangaParser,
) : MangaParser, MangaParserAuthProvider by (delegate.authorizationProvider ?: EmptyAuthProvider) {

	override val source: MangaSource get() = delegate.source

	override val availableSortOrders: Set<SortOrder> get() = delegate.availableSortOrders

	@Deprecated("Too complex. Use filterCapabilities instead")
	override val searchQueryCapabilities: MangaSearchQueryCapabilities get() = delegate.searchQueryCapabilities

	override val filterCapabilities: MangaListFilterCapabilities get() = delegate.filterCapabilities

	override val config: MangaSourceConfig get() = delegate.config

	override val configKeyDomain: ConfigKey.Domain get() = delegate.configKeyDomain

	override val domain: String get() = delegate.domain

	@Deprecated("Too complex. Use getList with filter instead")
	override suspend fun getList(query: MangaSearchQuery): List<Manga> = delegate.getList(query)

	override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> =
		delegate.getList(offset, order, filter)

	override suspend fun getDetails(manga: Manga): Manga = delegate.getDetails(manga)

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> = delegate.getPages(chapter)

	override suspend fun getPageUrl(page: MangaPage): String = delegate.getPageUrl(page)

	override suspend fun getFilterOptions(): MangaListFilterOptions = delegate.getFilterOptions()

	override suspend fun getFavicons(): Favicons = delegate.getFavicons()

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>): Unit = delegate.onCreateConfig(keys)

	override suspend fun getRelatedManga(seed: Manga): List<Manga> = delegate.getRelatedManga(seed)

	override fun getRequestHeaders(): Headers = delegate.getRequestHeaders()

	override suspend fun resolveLink(link: HttpUrl): Manga? = delegate.resolveLink(link)

	@Deprecated("Use resolveLink(HttpUrl) instead")
    override suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Manga? = delegate.resolveLink(resolver, link)

	override fun intercept(chain: Interceptor.Chain): Response = delegate.intercept(chain)

	private object EmptyAuthProvider : MangaParserAuthProvider {
		override val authUrl: String = ""
		override suspend fun isAuthorized(): Boolean = true
	}
}
