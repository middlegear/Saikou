package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.subtitles.SubtitleAPI
import com.lagradost.cloudstream3.syncproviders.*

class OpenSubtitlesApi : SubtitleAPI() {
    override var name = "OpenSubtitles"
    override var mainUrl = "https://www.opensubtitles.org"
}
