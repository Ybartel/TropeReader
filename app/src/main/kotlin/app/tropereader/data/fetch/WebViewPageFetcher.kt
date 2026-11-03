package app.tropereader.data.fetch

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import app.tropereader.BuildConfig
import app.tropereader.domain.PageLoadException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface PageFetcher {
    /** Raw HTML of [url] as served by the site. @throws PageLoadException */
    suspend fun fetch(url: String): String
}

/**
 * Fetches pages through an off-screen WebView. The site sits behind a Cloudflare challenge that
 * plain HTTP clients fail, while a browser engine passes it.
 *
 * Once the article is on screen, a same-origin `fetch(location.href)` reads the page source as
 * served (not the DOM after scripts), so the parser sees the same HTML as in its recorded tests.
 * Requests are serialized, at most one per second (sobriety requirement).
 */
@Singleton
class WebViewPageFetcher @Inject constructor(@ApplicationContext private val context: Context) : PageFetcher {
    private val mutex = Mutex()
    private var lastRequestAt = 0L

    override suspend fun fetch(url: String): String = mutex.withLock {
        val wait = lastRequestAt + MIN_INTERVAL_MS - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        try {
            withTimeout(TIMEOUT_MS) { withContext(Dispatchers.Main) { load(url) } }
        } catch (e: TimeoutCancellationException) {
            throw PageLoadException("The page took too long to load. The site may be asking for a verification; try again later.", e)
        } finally {
            lastRequestAt = SystemClock.elapsedRealtime()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun load(url: String): String = suspendCancellableCoroutine { cont ->
        val main = Handler(Looper.getMainLooper())
        val webView = WebView(context)
        val done = AtomicBoolean(false)
        fun finish(block: () -> Unit) {
            if (!done.compareAndSet(false, true)) return
            block()
            main.post { webView.destroy() }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            blockNetworkImage = true
            userAgentString = "$userAgentString TropeReader/${BuildConfig.VERSION_NAME}"
        }
        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onHtml(html: String) {
                    if (html.contains(ARTICLE_MARKER)) finish { cont.resume(html) }
                }

                @JavascriptInterface
                fun onError(message: String) {
                    finish { cont.resumeWithException(PageLoadException("Could not read the page: $message")) }
                }
            },
            BRIDGE,
        )
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, finishedUrl: String) {
                // A challenge page finishes too; the script waits for the real article.
                view.evaluateJavascript(EXTRACT_SCRIPT, null)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                // Only the site and its Cloudflare check are needed: skip ads, trackers and fonts.
                val host = request.url.host.orEmpty()
                return if (ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }) null else EMPTY_RESPONSE()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                if (request.isForMainFrame) {
                    finish { cont.resumeWithException(PageLoadException("Network error: ${error.description}")) }
                }
            }
        }
        cont.invokeOnCancellation { main.post { if (done.compareAndSet(false, true)) webView.destroy() } }
        webView.loadUrl(url)
    }

    private companion object {
        const val MIN_INTERVAL_MS = 1_000L
        const val TIMEOUT_MS = 30_000L
        const val BRIDGE = "TropeReaderBridge"
        const val ARTICLE_MARKER = "main-article"
        val ALLOWED_HOSTS = listOf("tvtropes.org", "cloudflare.com")
        val EMPTY_RESPONSE = { WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) }
        val EXTRACT_SCRIPT = """
            (function () {
              if (!document.getElementById('$ARTICLE_MARKER')) return;
              fetch(location.href, { cache: 'force-cache', credentials: 'include' })
                .then(function (r) { return r.text(); })
                .then(function (t) { $BRIDGE.onHtml(t); })
                .catch(function (e) { $BRIDGE.onError(String(e)); });
            })();
        """.trimIndent()
    }
}
