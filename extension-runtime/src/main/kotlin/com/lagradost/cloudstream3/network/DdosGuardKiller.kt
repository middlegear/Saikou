package com.lagradost.cloudstream3.network

import androidx.annotation.AnyThread
import com.lagradost.cloudstream3.app
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Stub for DdosGuardKiller to prevent NoClassDefFoundError in CloudStream plugins.
 * Original class is an Interceptor that handles ddos-guard protected sites.
 */
@AnyThread
class DdosGuardKiller(private val alwaysBypass: Boolean) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // Just proceed with the original request as a stub.
        return chain.proceed(chain.request())
    }
}
