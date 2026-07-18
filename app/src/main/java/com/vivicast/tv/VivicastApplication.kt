package com.vivicast.tv

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.vivicast.tv.di.AppContainer
import com.vivicast.tv.diagnostics.CrashLogWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VivicastApplication : Application(), SingletonImageLoader.Factory {
    val appContainer: AppContainer by lazy { AppContainer(this) }

    // Every Compose AsyncImage resolves the singleton loader through this factory, so the whole app
    // renders images with the shared OkHttp-backed, disk-cached Coil loader from AppContainer.
    override fun newImageLoader(context: PlatformContext): ImageLoader = appContainer.imageLoader

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Crash capture is always on (independent of the diagnostics toggle).
        runCatching {
            CrashLogWriter(this, appContainer.diagnosticsStore.crashDir, diagnosticsAbout()).install()
        }
        installLifecycleBreadcrumbs()
        installBackgroundPlayerRelease()
        CoroutineScope(Dispatchers.IO).launch {
            // Warm the DB invalidation triggers before any refresh import can hold the writer, so the
            // first Flow subscription (Settings/Home right after a cold start) emits immediately.
            appContainer.warmDatabaseObservers()
            appContainer.installWorkerRunner()
        }
    }

    // Release the ExoPlayer when the whole app backgrounds so weak TV boxes reclaim codecs/threads. Uses
    // ProcessLifecycleOwner (not the per-Activity callbacks above) because it debounces ~700ms — a
    // locale-change recreate() completes well within that window and never trips ON_STOP, so playback isn't
    // torn down across a recreate. The controller rebuilds the engine lazily on the next play(); foreground
    // channel zaps stay warm (they never reach ON_STOP).
    private fun installBackgroundPlayerRelease() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                appContainer.playerController.release()
            }
        })
    }

    // Foreground/background breadcrumbs (gated inside the store: no-op unless logging is enabled).
    private fun installLifecycleBreadcrumbs() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0) appContainer.diagnosticsStore.log("lifecycle", "app_foreground")
            }
            override fun onActivityStopped(activity: Activity) {
                if (--started == 0) appContainer.diagnosticsStore.log("lifecycle", "app_background")
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        appContainer.diagnosticsStore.log("memory", "trim_memory", mapOf("level" to level.toString()))
    }
}
