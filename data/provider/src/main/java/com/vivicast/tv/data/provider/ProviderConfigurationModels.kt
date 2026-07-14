package com.vivicast.tv.data.provider

import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderType

data class ProviderCreateRequest(
    val name: String,
    val type: ProviderType,
    val m3uSourceMode: M3uSourceMode = M3uSourceMode.Url,
    val m3uUrl: String? = null,
    val m3uContent: String? = null,
    val xtreamServerUrl: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    val includeLiveTv: Boolean = true,
    val includeMovies: Boolean = true,
    val includeSeries: Boolean = true,
    val refreshIntervalHours: Int = DEFAULT_REFRESH_INTERVAL_HOURS,
    val userAgent: String? = null,
    val refreshOnAppStartEnabled: Boolean = true,
    val logoPriority: String = LOGO_PRIORITY_PLAYLIST,
    val xtreamOutputFormat: String = XTREAM_OUTPUT_HLS,
)

data class ProviderUpdateRequest(
    val providerId: String,
    val name: String,
    // The (possibly changed) provider type. Editing may switch between M3U and Xtream; the repository
    // wipes the old source and rewrites for this type.
    val type: ProviderType,
    val m3uSourceMode: M3uSourceMode? = null,
    val m3uUrl: String? = null,
    val m3uContent: String? = null,
    val xtreamServerUrl: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    val includeLiveTv: Boolean = true,
    val includeMovies: Boolean = true,
    val includeSeries: Boolean = true,
    val refreshIntervalHours: Int = DEFAULT_REFRESH_INTERVAL_HOURS,
    val userAgent: String? = null,
    val refreshOnAppStartEnabled: Boolean = true,
    val logoPriority: String = LOGO_PRIORITY_PLAYLIST,
    val xtreamOutputFormat: String = XTREAM_OUTPUT_HLS,
)

data class ProviderSaveResult(
    val provider: Provider,
    val hasDuplicateName: Boolean,
    // Set to the previous type when an edit switched the provider type (M3U↔Xtream); null otherwise.
    // Purely informational for App-side diagnostics logging.
    val switchedFromType: ProviderType? = null,
)

/**
 * Outcome of a connection test. [errorMessage] non-null means it failed (already localized by the App
 * layer). On success [summary] carries the M3U content breakdown to preview (null for Xtream).
 */
data class ProviderConnectionTestResult(
    val errorMessage: String?,
    val summary: ContentSummary?,
)

sealed interface ProviderCredentials {
    // File-mode M3U carries no content here — it is large and read on demand via
    // ProviderRepository.getProviderM3uInlineContent, so opening the editor stays cheap.
    data class M3u(
        val url: String? = null,
        val sourceMode: M3uSourceMode = M3uSourceMode.Url,
    ) : ProviderCredentials
    data class Xtream(
        val serverUrl: String,
        val username: String,
        val password: String,
    ) : ProviderCredentials
}

enum class M3uSourceMode {
    Url,
    File,
}

val M3uSourceMode.isAutomaticallyRefreshable: Boolean
    get() = this == M3uSourceMode.Url

// ~32 MB (aligned with the URL M3U byte cap). Content is held in RAM as a String during add/import,
// so ~2x this in heap; raise only alongside a streaming import path. 1 char ≈ 1 byte for ASCII M3U.
const val MAX_M3U_INLINE_SOURCE_CHARS = 32 * 1024 * 1024
const val DEFAULT_REFRESH_INTERVAL_HOURS = 24

// 0 = automatic hourly refresh disabled for this playlist (the new default).
const val REFRESH_INTERVAL_OFF = 0

// Selectable auto-refresh intervals in the editor popup ("Aus" = REFRESH_INTERVAL_OFF, then hours).
val REFRESH_INTERVAL_OPTIONS_HOURS = listOf(REFRESH_INTERVAL_OFF, 2, 4, 8, 16, 24, 48, 72, 96, 120, 144, 168)

// Per-provider logo source preference. PLAYLIST (default) prefers the playlist's own channel logo and
// falls back to a mapped EPG source's <icon>; EPG reverses that order (both resolved in the CatalogDao
// effective-logo projection). LOCAL prefers a matching file from the user's local logos folder (resolved
// App-side in resolveChannelLogoModel), falling back to the playlist order on no match.
const val LOGO_PRIORITY_PLAYLIST = "playlist"
const val LOGO_PRIORITY_EPG = "epg"
const val LOGO_PRIORITY_LOCAL = "local"

/** Normalizes any stored value (including the legacy "provider") to the supported priorities. */
fun normalizeLogoPriority(value: String?): String =
    when (value) {
        LOGO_PRIORITY_EPG -> LOGO_PRIORITY_EPG
        LOGO_PRIORITY_LOCAL -> LOGO_PRIORITY_LOCAL
        else -> LOGO_PRIORITY_PLAYLIST
    }

// Per-Xtream-provider live output format (mirrors TVMate's per-playlist switch). HLS (default) requests
// `…/live/…/id.m3u8` → a server DVR window ExoPlayer can seek natively where the panel provides one; MPEG-TS
// requests `…/id.ts` → progressive, no native seek. Only meaningful for Xtream; catch-up keeps the .ts endpoint.
const val XTREAM_OUTPUT_HLS = "hls"
const val XTREAM_OUTPUT_TS = "ts"

fun normalizeXtreamOutputFormat(value: String?): String =
    if (value == XTREAM_OUTPUT_TS) XTREAM_OUTPUT_TS else XTREAM_OUTPUT_HLS
