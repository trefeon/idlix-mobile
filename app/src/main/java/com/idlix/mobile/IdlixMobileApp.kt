package com.idlix.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.idlix.data.net.CoreHttp
import com.idlix.data.net.IdlixDomainResolver
import com.idlix.mobile.startup.ErrorLog
import com.idlix.mobile.startup.StartupRepair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.idlix.mobile.update.UpdateCheckWorker

class IdlixMobileApp : Application(), ImageLoaderFactory {

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Headless repair first: crash-handler chain, corrupt-prefs quarantine,
        // Coil cache probe, version migrations. Never throws.
        try {
            StartupRepair.run(this)
        } catch (_: Throwable) {
        }
        try {
            CoreHttp.init(this, "IdlixMobile-Client/1.0 (Android)")
        } catch (t: Throwable) {
            try {
                ErrorLog.append(this, t, "CoreHttp.init")
            } catch (_: Throwable) {
            }
        }
        try {
            IdlixDomainResolver.init(this)
        } catch (t: Throwable) {
            try {
                ErrorLog.append(this, t, "IdlixDomainResolver.init")
            } catch (_: Throwable) {
            }
        }
        // Daily update check (GitHub releases). Never throws; no-ops pre-first-run.
        try {
            UpdateCheckWorker.schedule(this)
        } catch (_: Throwable) {
        }
        // Warm the resolver off the main thread; a failed/unsurfaced resolve
        // (still on FALLBACK_URL) schedules a WorkManager retry in 6h when the
        // work runtime is present, silently skipped otherwise.
        startupScope.launch {
            try {
                val resolved = IdlixDomainResolver.resolve()
                if (resolved == IdlixDomainResolver.FALLBACK_URL) {
                    StartupRepair.scheduleResolverRetry(this@IdlixMobileApp)
                }
            } catch (t: Throwable) {
                try {
                    ErrorLog.append(this@IdlixMobileApp, t, "startup-resolve")
                } catch (_: Throwable) {
                }
                try {
                    StartupRepair.scheduleResolverRetry(this@IdlixMobileApp)
                } catch (_: Throwable) {
                }
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val builder = ImageLoader.Builder(this)
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
        if (StartupRepair.isCoilCacheless()) {
            // Cache partition unusable (probed in StartupRepair): run cacheless
            // rather than crashing inside Coil's disk cache.
            builder.diskCache(null)
        } else {
            builder.diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
        }
        return builder.crossfade(true).build()
    }

}
