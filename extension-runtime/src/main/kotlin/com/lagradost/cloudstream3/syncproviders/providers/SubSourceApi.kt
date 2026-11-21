package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.subtitles.SubtitleAPI
import com.lagradost.cloudstream3.syncproviders.*

class SubSourceApi : SubtitleAPI() {
    override var name = "SubSource"
    override var mainUrl = "https://subsource.net"
}
