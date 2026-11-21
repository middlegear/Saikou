package org.koitharu.kotatsu.parsers

import org.koitharu.kotatsu.parsers.model.ContentType

/**
 * Annotate a Tachiyomi [HttpSource] class to register it as a Kotatsu parser source.
 * The KSP processor will generate factory code that wraps the Tachiyomi source with [TachiyomiSourceAdapter].
 *
 * Simple usage (auto-detect all values):
 * ```
 * @TachiyomiSource
 * class BaoTangTruyen : HttpSource() {
 *     override val name = "BaoTangTruyen"
 *     override val lang = "vi"
 *     ...
 * }
 * ```
 *
 * Or with explicit parameters:
 * ```
 * @TachiyomiSource(
 *     name = "BAOTANGTRUYEN",
 *     title = "BaoTangTruyen",
 *     locale = "vi",
 *     type = ContentType.MANGA
 * )
 * class BaoTangTruyen : HttpSource() { ... }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class TachiyomiSource(
    /**
     * Name of manga source. Used as an Enum value, must be UPPER_CASE and unique.
     * If empty, will be auto-generated from class name.
     */
    val name: String = "",
    /**
     * User-friendly title of manga source.
     * If empty, will use the class simple name.
     */
    val title: String = "",
    /**
     * Language code (for example "en" or "vi") or blank if multi-language.
     * If empty, will try to detect from package name or use empty string.
     */
    val locale: String = "",
    /**
     * Type of content provided by the source.
     */
    val type: ContentType = ContentType.MANGA,
)
