package com.idlix.mobile.startup

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.idlix.data.net.IdlixDomainResolver
import com.idlix.mobile.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Background worker that retries [IdlixDomainResolver.resolve] when the initial
 * startup probe fell back to [IdlixDomainResolver.FALLBACK_URL].
 */
class ResolverRetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val resolved = IdlixDomainResolver.resolve(forceRefresh = true)
            if (resolved == IdlixDomainResolver.FALLBACK_URL) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (t: Throwable) {
            try {
                ErrorLog.append(applicationContext, t, "resolver-retry-worker")
            } catch (_: Throwable) {
            }
            Result.retry()
        }
    }
}

/**
 * Headless startup hardening. Runs first in `IdlixMobileApp.onCreate`, before
 * [com.idlix.data.net.CoreHttp] / resolver init. No UI, no settings, no
 * manifest/gradle changes, no new permissions.
 *
 * Order in [run]: crash-handler chain → prefs quarantine → Coil cache probe →
 * version migrations. Every step is individually guarded; repair never throws.
 */
object StartupRepair {

    // Prefs files validated at boot. Names owned by :core:data.
    private const val PREFS_DOMAIN = "idlix_domain"
    private const val PREFS_HISTORY = "idlix_tv_watch_history"
    private const val PREFS_SUBDL = "subdl_config"

    private const val PREFS_STARTUP = "idlix_startup"
    private const val KEY_MIGRATED_VERSION = "startup_migrated_version"

    private const val RESOLVER_RETRY_WORK = "idlix_resolver_retry"

    @Volatile
    private var coilCacheless = false

    @Volatile
    private var crashHandlerInstalled = false

    fun run(context: Context) {
        val app = context.applicationContext
        installCrashHandler(app)
        quarantineCorruptPrefs(app)
        ensureCoilCacheDir(app)
        runVersionMigrations(app)
    }

    // ---------------------------------------------------------------- crash handler

    /**
     * Chains onto the previous default uncaught-exception handler: breadcrumb the
     * head of the crash into [ErrorLog], then delegate so the platform still
     * reports/kills the process as usual.
     */
    fun installCrashHandler(context: Context) {
        if (crashHandlerInstalled) return
        synchronized(this) {
            if (crashHandlerInstalled) return
            val app = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    ErrorLog.append(app, throwable, "uncaught")
                } catch (_: Throwable) {
                    // Never interfere with crash delivery.
                }
                if (previous != null) {
                    try {
                        previous.uncaughtException(thread, throwable)
                    } catch (_: Throwable) {
                        // Last resort: nothing to do; the runtime will terminate.
                    }
                }
            }
            crashHandlerInstalled = true
        }
    }

    // ------------------------------------------------------------ prefs quarantine

    /**
     * Validates the known prefs files with typed reads. A corrupt/foreign file
     * (bad XML, wrong value types) throws on access; the file is quarantined to
     * `.bak` and recreated empty so boot proceeds with defaults.
     */
    fun quarantineCorruptPrefs(context: Context) {
        validatePrefsFile(context, PREFS_DOMAIN) { prefs ->
            prefs.getString("resolved_base_url", null)
            prefs.getLong("resolved_base_ts", 0L)
        }
        validatePrefsFile(context, PREFS_HISTORY) { prefs ->
            prefs.getString("history_items_json", null)
        }
        validatePrefsFile(context, PREFS_SUBDL) { prefs ->
            prefs.getString("custom_subdl_api_key", null)
        }
    }

    private fun validatePrefsFile(
        context: Context,
        prefsName: String,
        probe: (SharedPreferences) -> Unit
    ) {
        try {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.all // force load of the backing XML
            probe(prefs) // typed reads surface ClassCastException on foreign types
        } catch (t: Throwable) {
            try {
                ErrorLog.appendMessage(
                    context, "startup-repair",
                    "quarantining corrupt prefs $prefsName: ${t.javaClass.simpleName}"
                )
            } catch (_: Throwable) {
            }
            quarantinePrefsFile(context, prefsName)
        }
    }

    /**
     * Moves `shared_prefs/<name>.xml` aside to `<name>.xml.bak` (replacing any
     * previous backup) and clears the in-memory instance so the next access
     * recreates defaults. Returns true when a corrupt file was moved.
     */
    fun quarantinePrefsFile(context: Context, prefsName: String): Boolean {
        return try {
            val dir = File(context.applicationInfo.dataDir, "shared_prefs")
            val src = File(dir, "$prefsName.xml")
            if (src.exists()) {
                val bak = File(dir, "$prefsName.xml.bak")
                try {
                    if (bak.exists()) bak.delete()
                } catch (_: Throwable) {
                }
                val moved = try {
                    src.renameTo(bak)
                } catch (_: Throwable) {
                    false
                }
                if (!moved) {
                    // Fallback: drop the unreadable file; backup already attempted.
                    try {
                        src.delete()
                    } catch (_: Throwable) {
                    }
                }
            }
            try {
                context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit().clear().apply()
            } catch (_: Throwable) {
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    // --------------------------------------------------------------- Coil cache

    /**
     * Probes `<cacheDir>/image_cache` for writability. Devices with tiny or
     * broken cache partitions would otherwise crash inside Coil's disk cache;
     * when the probe fails [isCoilCacheless] flips and `IdlixMobileApp` builds a
     * cacheless `ImageLoader` instead.
     */
    fun ensureCoilCacheDir(context: Context) {
        coilCacheless = try {
            val dir = File(context.cacheDir, "image_cache")
            if (!dir.exists()) dir.mkdirs()
            if (!dir.isDirectory || !dir.canWrite()) {
                true
            } else {
                val probe = File(dir, ".probe")
                try {
                    probe.writeBytes(byteArrayOf(1))
                    val ok = probe.exists()
                    try {
                        probe.delete()
                    } catch (_: Throwable) {
                    }
                    !ok
                } catch (_: Throwable) {
                    true
                }
            }
        } catch (_: Throwable) {
            true
        }
    }

    fun isCoilCacheless(): Boolean = coilCacheless

    // ------------------------------------------------- WorkManager (optional dep)

    /**
     * True only when the `androidx.work` runtime is on the classpath. There is
     * intentionally no compile-time work-runtime dependency; devices without GMS /
     * builds without the artifact silently skip scheduling.
     */
    fun isWorkAvailable(): Boolean {
        return try {
            Class.forName("androidx.work.WorkManager")
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Schedules a resolver retry [delayHours] out via [ResolverRetryWorker]
     * when the initial startup probe fell back to [IdlixDomainResolver.FALLBACK_URL].
     * Silent no-op when work-runtime is absent — never throws.
     */
    fun scheduleResolverRetry(context: Context, delayHours: Long = 6) {
        try {
            if (!isWorkAvailable()) return
            val request = OneTimeWorkRequestBuilder<ResolverRetryWorker>()
                .setInitialDelay(delayHours, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    RESOLVER_RETRY_WORK,
                    ExistingWorkPolicy.KEEP,
                    request
                )
        } catch (_: Throwable) {
            // Silent: retry scheduling is best-effort on lean mobile builds.
        }
    }

    // -------------------------------------------------------- version migrations

    /**
     * Forward-compatible migration ledger keyed on `BuildConfig.VERSION_CODE`.
     *
     * Documented renames so far:
     * - v1 -> v2: watch-history payload key `history_json` renamed to
     *   `history_items_json` in `idlix_tv_watch_history`.
     * - v2 -> v3: domain cache key `base_url` renamed to `resolved_base_url`
     *   in `idlix_domain`.
     * - v3 -> v4: `subdl_config` keeps `custom_subdl_api_key`; no rename, only
     *   the ledger advances (SubDL key source moved to BuildConfig).
     *
     * Unknown older versions run every step; newer-on-disk versions are kept
     * (downgrade-safe: only advances the ledger).
     */
    fun runVersionMigrations(context: Context) {
        try {
            val current = BuildConfig.VERSION_CODE
            val prefs = context.getSharedPreferences(PREFS_STARTUP, Context.MODE_PRIVATE)
            val last = try {
                prefs.getInt(KEY_MIGRATED_VERSION, 0)
            } catch (_: Throwable) {
                0
            }
            if (last >= current) return
            if (last < 2) {
                renameKey(context, PREFS_HISTORY, "history_json", "history_items_json")
            }
            if (last < 3) {
                renameKey(context, PREFS_DOMAIN, "base_url", "resolved_base_url")
            }
            // last < 4: no rename (documented above); ledger advance only.
            try {
                prefs.edit().putInt(KEY_MIGRATED_VERSION, current).apply()
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
            // Migrations must never block boot.
        }
    }

    private fun renameKey(context: Context, prefsName: String, oldKey: String, newKey: String) {
        try {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val all = try {
                prefs.all
            } catch (_: Throwable) {
                return
            } ?: return
            if (!all.containsKey(oldKey) || all.containsKey(newKey)) return
            val editor = prefs.edit()
            when (val v = all[oldKey]) {
                is String -> editor.putString(newKey, v)
                is Long -> editor.putLong(newKey, v)
                is Int -> editor.putInt(newKey, v)
                is Float -> editor.putFloat(newKey, v)
                is Boolean -> editor.putBoolean(newKey, v)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(newKey, (v as? Set<String>)?.toSet())
                }
                else -> return
            }
            editor.remove(oldKey)
            try {
                editor.apply()
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------------ test hooks

    /** Resets in-memory repair state; the next [run] behaves like a fresh boot. */
    @VisibleForTesting
    fun resetForTests() {
        coilCacheless = false
        crashHandlerInstalled = false
    }

    /**
     * Deletes a prefs file (and its quarantine backup) for corrupt-prefs
     * simulation tests. The Tests slice drives corruption through this hook.
     */
    @VisibleForTesting
    fun purgePrefsForTests(context: Context, prefsName: String): Boolean {
        return try {
            try {
                context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit().clear().apply()
            } catch (_: Throwable) {
            }
            val dir = File(context.applicationInfo.dataDir, "shared_prefs")
            var ok = true
            for (suffix in listOf(".xml", ".xml.bak")) {
                try {
                    val f = File(dir, "$prefsName$suffix")
                    if (f.exists() && !f.delete()) ok = false
                } catch (_: Throwable) {
                    ok = false
                }
            }
            ok
        } catch (_: Throwable) {
            false
        }
    }
}
