package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.syncproviders.providers.*

abstract class AccountManager {
    companion object {
        const val APP_STRING = "cloudstreamapp"
        const val NONE_ID: Int = -1

        var cachedAccounts: MutableMap<String, Array<AuthData>> = mutableMapOf()
        var cachedAccountIds: MutableMap<String, Int> = mutableMapOf()

        @JvmStatic
        val malApi = MALApi()
        @JvmStatic
        val kitsuApi = KitsuApi()
        @JvmStatic
        val aniListApi = AniListApi()
        @JvmStatic
        val simklApi = SimklApi()
        @JvmStatic
        val localListApi = LocalList()

        @JvmStatic
        val openSubtitlesApi = OpenSubtitlesApi()
        @JvmStatic
        val addic7ed = Addic7ed()
        @JvmStatic
        val subDlApi = SubDlApi()
        @JvmStatic
        val subSourceApi = SubSourceApi()

        @JvmStatic
        fun accounts(prefix: String): Array<AuthData> = arrayOf()
    }
}
