package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.subtitles.SubtitleAPI
import com.lagradost.cloudstream3.syncproviders.*

class SubDlApi : SubtitleAPI() {
    override var name = "SubDl"
    override var mainUrl = "https://subdl.com"
}
