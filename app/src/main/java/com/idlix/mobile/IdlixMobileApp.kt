package com.idlix.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.idlix.data.net.CoreHttp
import com.idlix.data.net.IdlixDomainResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.idlix.mobile.update.UpdateCheckWorker

class IdlixMobileApp : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CoreHttp.init(this, "IdlixMobile-Client/1.0 (Android)")
        IdlixDomainResolver.init(this)
        // Fire-and-forget: HEAD→GET the canonical entry so mirror rotations
        // propagate. Cached-last-known stays live until the redirect answers;
        // failure keeps the cached/fallback URL (resolver is fail-closed).
        appScope.launch {
            try {
                IdlixDomainResolver.resolve()
            } catch (_: Exception) {
            }
        }
        try {
            UpdateCheckWorker.schedule(this)
        } catch (_: Throwable) {
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(CoreHttp.okHttpClient)
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .decoderDispatcher(Dispatchers.IO)
            .fetcherDispatcher(Dispatchers.IO)
            .transformationDispatcher(Dispatchers.IO)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

}
