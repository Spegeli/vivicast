package com.vivicast.tv

import android.content.Context

class TvAppGraph(context: Context) {
    val controller = ViviCastTvController(context.applicationContext)

    fun close() {
        controller.release()
    }
}
