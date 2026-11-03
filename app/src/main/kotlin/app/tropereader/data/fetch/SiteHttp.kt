package app.tropereader.data.fetch

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import app.tropereader.BuildConfig
import okhttp3.OkHttpClient

/**
 * What the app sends to the site outside the WebView (images). Cloudflare's clearance cookie is tied
 * to the browser that earned it, so requests reuse the WebView's cookies and exact User-Agent.
 */
object SiteHttp {
    /** The WebView's own User-Agent, identifying the app (sobriety requirement). */
    fun userAgent(context: Context): String = "${WebSettings.getDefaultUserAgent(context)} TropeReader/${BuildConfig.VERSION_NAME}"

    fun client(context: Context): OkHttpClient {
        val userAgent = userAgent(context)
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                val builder = request.newBuilder()
                    .header("User-Agent", userAgent)
                    .header("Referer", "https://tvtropes.org/")
                CookieManager.getInstance().getCookie(url)?.let { builder.header("Cookie", it) }
                chain.proceed(builder.build())
            }
            .build()
    }
}
