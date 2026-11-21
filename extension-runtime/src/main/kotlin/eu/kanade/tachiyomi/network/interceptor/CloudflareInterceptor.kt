package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CloudflareInterceptor(
    context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    private val executor = ContextCompat.getMainExecutor(context)

    override fun shouldIntercept(response: Response): Boolean {
        val server = response.header("Server")
        val isCloudflareServer = server in SERVER_CHECK || (server != null && SERVER_CHECK.any { server.contains(it, ignoreCase = true) })
        val isChallenge = response.header("cf-mitigated") == "challenge" ||
            ((response.code == 403 || response.code == 503) && response.header("cf-ray") != null)
        return isChallenge && isCloudflareServer
    }

    override fun intercept(
        chain: Interceptor.Chain,
        request: Request,
        response: Response,
    ): Response {
        try {
            response.close()
            val hostUrl = "${request.url.scheme}://${request.url.host}"
            val existingCookie = cookieManager.get(request.url)
                .firstOrNull { it.name == "cf_clearance" }
            val webViewCookies = try {
                android.webkit.CookieManager.getInstance().getCookie(request.url.toString())
                    ?: android.webkit.CookieManager.getInstance().getCookie(hostUrl)
            } catch (_: Throwable) {
                null
            }
            val requestCookie = request.header("Cookie")
            val hasClearance = existingCookie != null ||
                (webViewCookies?.contains("cf_clearance=") == true) ||
                (requestCookie?.contains("cf_clearance=") == true)

            if (hasClearance) {
                throw IOException("Cloudflare challenge required. Please solve Cloudflare in WebView")
            }

            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            val oldCookie = cookieManager.get(request.url)
                .firstOrNull { it.name == "cf_clearance" }
            resolveWithWebView(request, oldCookie)
            return chain.proceed(request)
        } catch (e: CloudflareBypassException) {
            throw IOException("Cloudflare bypass failed", e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request, oldCookie: Cookie?) {
        val latch = CountDownLatch(1)
        var webview: WebView? = null
        var challengeFound = false
        var cloudflareBypassed = false
        val origRequestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)
        val activity = com.anymex.runtimehost.RuntimeBridge.resolveAppCompatActivity(context)

        executor.execute {
            val webViewContext = activity ?: context
            val wv = WebView(webViewContext).apply {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    setSupportMultipleWindows(true)
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = false
                    }
                }
                try {
                    android.webkit.CookieManager.getInstance().acceptThirdPartyCookies(this)
                } catch (_: Throwable) {}
                settings.userAgentString = defaultUserAgentProvider()
            }
            webview = wv

            if (activity != null) {
                (activity.window.decorView as ViewGroup).addView(wv, ViewGroup.LayoutParams(1, 1))
            }

            wv.addJavascriptInterface(
                object {
                    @Suppress("unused")
                    @JavascriptInterface
                    fun interactiveDetected() {
                        latch.countDown()
                    }
                },
                "anymex",
            )

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    fun isCloudFlareBypassed(): Boolean {
                        return cookieManager.get(origRequestUrl.toHttpUrl())
                            .firstOrNull { it.name == "cf_clearance" }
                            .let { it != null && it != oldCookie }
                    }

                    if (isCloudFlareBypassed()) {
                        cloudflareBypassed = true
                        latch.countDown()
                    }

                    if (url == origRequestUrl) {
                        if (!challengeFound) {
                            latch.countDown()
                        } else {
                            view.evaluateJavascript(
                                """
                                    addEventListener("message", ({data}) => {
                                        if (data?.source === "cloudflare-challenge" && data?.event === "interactiveBegin") {
                                            anymex.interactiveDetected();
                                        }
                                    })
                                """.trimIndent(),
                                null,
                            )
                        }
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true) {
                        if (errorResponse?.responseHeaders?.get("cf-mitigated") == "challenge") {
                            challengeFound = true
                        } else {
                            latch.countDown()
                        }
                    }
                }
            }

            wv.loadUrl(origRequestUrl, headers)
        }

        latch.await(30, TimeUnit.SECONDS)

        executor.execute {
            webview?.let { wv ->
                wv.stopLoading()
                if (activity != null) {
                    try {
                        (activity.window.decorView as ViewGroup).removeView(wv)
                    } catch (_: Throwable) {}
                }
                wv.destroy()
            }
        }

        if (!cloudflareBypassed) {
            throw CloudflareBypassException()
        }
    }

    companion object {
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        private val COOKIE_NAMES = listOf("cf_clearance")
    }

    private class CloudflareBypassException : Exception()
}
