package app.tropereader

import android.app.Application
import app.tropereader.data.fetch.SiteHttp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TropeReaderApp : Application(), SingletonImageLoader.Factory {
    /** Card images go through the same Cloudflare clearance as the WebView (F3.8). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { SiteHttp.client(this@TropeReaderApp) })) }
            .build()
}
