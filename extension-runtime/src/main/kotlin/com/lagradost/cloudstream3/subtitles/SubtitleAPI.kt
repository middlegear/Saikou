package com.lagradost.cloudstream3.subtitles

import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.AuthRepo

abstract class SubtitleAPI : AuthAPI()

open class SubtitleRepo(override val api: SubtitleAPI) : AuthRepo(api)
