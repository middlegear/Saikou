@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

class SMangaImpl : SManga {

    override var url: String = ""

    override var title: String = ""

    override var artist: String? = null

    override var author: String? = null

    override var description: String? = null

    override var genre: String? = null

    override var status: Int = 0

    override var thumbnail_url: String? = null

    override var update_strategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE

    override var initialized: Boolean = false

    override var memo: kotlinx.serialization.json.JsonObject? = kotlinx.serialization.json.JsonObject(emptyMap())
}
