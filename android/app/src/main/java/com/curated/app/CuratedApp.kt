package com.curated.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.curated.app.core.data.SupabaseProvider

class CuratedApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        SupabaseProvider.client(this)
    }

    /**
     * The one image loader every AsyncImage uses. The network fetcher is
     * registered explicitly — Coil 3 can't load https URLs without it — and
     * debug builds log every failed request to Logcat under the "Coil" tag.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .apply { if (BuildConfig.DEBUG) logger(DebugLogger()) }
            .build()
}
