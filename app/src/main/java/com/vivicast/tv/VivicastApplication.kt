package com.vivicast.tv

import android.app.Application
import com.vivicast.tv.di.AppContainer

class VivicastApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        appContainer.installWorkerRunner()
    }
}
