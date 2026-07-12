package com.vivicast.tv

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.vivicast.tv.di.AppContainer
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
        CoroutineScope(Dispatchers.IO).launch {
            // Warm the DB invalidation triggers before any refresh import can hold the writer, so the
            // first Flow subscription (Settings/Home right after a cold start) emits immediately.
            appContainer.warmDatabaseObservers()
            appContainer.installWorkerRunner()
        }
    }
}
