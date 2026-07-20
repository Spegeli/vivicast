package com.vivicast.tv.core.logging

import android.util.Log

/** Shared logcat tag for every Vivicast dev trace — filter the whole app with `adb logcat -s VCd`. */
const val VC_TAG: String = "VCd"

/**
 * Debug-only trace. Guarded by [BuildConfig.DEBUG], so release builds emit nothing — this matters
 * because R8/minify is off (CLAUDE.md), so `Log.d` is NOT stripped automatically. The [message]
 * lambda runs only in a debug build, so building the string costs nothing in release.
 *
 * One tag ([VC_TAG]) for the whole app; pass [area] to label the origin so the single tag can be
 * grep-narrowed per screen/feature (prints `[area] …`).
 *
 * Usage:
 * ```
 * vcLog("live-tv") { "focus -> channel=$channelId" }
 * vcLog { "no area label" }
 * ```
 */
inline fun vcLog(area: String? = null, message: () -> String) {
    if (BuildConfig.DEBUG) {
        Log.d(VC_TAG, if (area == null) message() else "[$area] ${message()}")
    }
}
