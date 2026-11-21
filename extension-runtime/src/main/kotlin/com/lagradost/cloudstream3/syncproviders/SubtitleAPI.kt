package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch

abstract class SubtitleAPI : AuthAPI() {
    open suspend fun search(
        auth: AuthData?,
        query: SubtitleSearch
    ): List<SubtitleEntity>? = throw NotImplementedError()

    open suspend fun load(
        auth: AuthData?,
        subtitle: SubtitleEntity
    ): String? = throw NotImplementedError()
}

open class SubtitleRepo(override val api: SubtitleAPI) : AuthRepo(api)
