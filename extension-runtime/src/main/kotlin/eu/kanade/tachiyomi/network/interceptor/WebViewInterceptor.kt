package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.Locale

abstract class WebViewInterceptor(
    protected val context: Context,
    protected val defaultUserAgentProvider: () -> String
) : Interceptor {

    private val initWebView by lazy {
        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Exception) {}
    }

    abstract fun shouldIntercept(response: Response): Boolean

    abstract fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!shouldIntercept(response)) {
            return response
        }

        if (!supportsWebView(context)) {
            return response
        }
        initWebView

        return intercept(chain, request, response)
    }

    fun parseHeaders(headers: Headers): Map<String, String> {
        return headers
            .filter { (name, value) ->
                isRequestHeaderSafe(name, value)
            }
            .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
            .mapValues { it.value.getOrNull(0).orEmpty() }
    }

    fun createWebView(request: Request): WebView {
        val webViewContext = com.anymex.runtimehost.RuntimeBridge.resolveAppCompatActivity(context) ?: context
        return WebView(webViewContext).apply {
            setDefaultSettings()
            settings.userAgentString = request.header("User-Agent") ?: defaultUserAgentProvider()
        }
    }

    private fun WebView.setDefaultSettings() {
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(true)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        try {
            CookieManager.getInstance().acceptThirdPartyCookies(this)
        } catch (_: Throwable) {}
    }

    private fun supportsWebView(context: Context): Boolean {
        try {
            CookieManager.getInstance()
        } catch (e: Throwable) {
            return false
        }
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
    }

    private fun isRequestHeaderSafe(_name: String, _value: String): Boolean {
        val name = _name.lowercase(Locale.ENGLISH)
        val value = _value.lowercase(Locale.ENGLISH)
        if (name in unsafeHeaderNames || name.startsWith("proxy-")) return false
        if (name == "connection" && value == "upgrade") return false
        return true
    }

    companion object {
        private val unsafeHeaderNames = listOf(
            "content-length", "host", "trailer", "te", "upgrade", "cookie2", "keep-alive", "transfer-encoding", "set-cookie"
        )
    }
}
