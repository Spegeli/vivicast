package com.vivicast.tv

import android.app.Application
import android.content.Context
import com.vivicast.tv.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VivicastApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            appContainer.installWorkerRunner()
        }
    }
}
