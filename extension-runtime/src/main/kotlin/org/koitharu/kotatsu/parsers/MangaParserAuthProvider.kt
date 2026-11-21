package org.koitharu.kotatsu.parsers

public interface MangaParserAuthProvider {

	/**
	 * URL for authentication web page, if applicable.
	 * May be null if auth is handled differently.
	 */
	public val authUrl: String

	/**
	 * Check if user is authorized.
	 * Backward-compatible suspend function version.
	 */
	public suspend fun isAuthorized(): Boolean

	/**
	 * Get username of authorized user.
	 * Backward-compatible for parsers that implemented this.
	 */
	public suspend fun getUsername(): String? = null
}
