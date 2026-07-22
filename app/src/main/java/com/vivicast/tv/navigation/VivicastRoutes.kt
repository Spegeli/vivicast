package com.vivicast.tv.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation-Compose routes (replaces the old `selectedRoute` string model).
 *
 * Phase A1 scope: the tab shell + the six top-level destinations. Movie/series **detail** destinations (B) and
 * the **Player** destination (C1) are added in their own phases as they are wired.
 *
 * Movies and Series are modelled as **nested graphs** already now, each with only its `*List` child, so their
 * ViewModels can be scoped to the graph entry (`getBackStackEntry(MoviesGraph)`) from A1 on. Phase B then only
 * *adds* the detail destination under the existing graph — no VM re-scoping.
 *
 * Routes are `internal`: they exist only inside `:app` (the sole module that owns the NavHost). Feature modules
 * keep exposing plain Composables + callbacks and never see a route type.
 */

/** The tab shell graph — its destinations show the persistent top navigation. */
@Serializable
internal object ShellGraph

@Serializable
internal object Home

@Serializable
internal object LiveTv

@Serializable
internal object Search

@Serializable
internal object Settings

/** Movies tab: nested graph holding the grid + the self-contained detail destination. */
@Serializable
internal object MoviesGraph

@Serializable
internal object MoviesList

/**
 * Movie detail = a self-contained destination that loads its movie by stable keys (works on direct entry /
 * deep-link, no loaded grid required). Args are STABLE keys, never Room ids (survive process death / re-import).
 */
@Serializable
internal data class MovieDetail(val providerStableKey: String, val movieStableKey: String)

/** Series tab: nested graph holding the grid + the self-contained detail destination. */
@Serializable
internal object SeriesGraph

@Serializable
internal object SeriesList

/**
 * Series detail = a self-contained destination that loads its series by stable keys. Season/episode stay
 * in-page (there is no separate episode screen); [episodeStableKey], when set (episode deep-link / continue),
 * pre-selects the season + episode that contains it. Args are STABLE keys, never Room ids.
 */
@Serializable
internal data class SeriesDetail(
    val providerStableKey: String,
    val seriesStableKey: String,
    val episodeStableKey: String? = null,
)

/**
 * Player = a full-screen top-level destination (the persistent top nav is suppressed for it, §nav.md). Args are
 * **decomposed primitives** (no custom NavType) carrying everything needed to rebuild the immutable
 * `PlaybackRequest` on entry — so a process-death-restored Player re-resolves + re-plays from its args (stream
 * URLs are just-in-time per ADR-013, never persisted). Enums are carried as their `.name` string
 * (`PlaybackMediaType` / `PlaybackOrigin` / `PlaybackReturnTarget`) to keep the route a pure-primitive type.
 *
 * On a NORMAL launch the App has already built the request + called `playerController.play(...)` (App-hoisted),
 * then navigates here; the destination only renders the running player — it never calls `play()` on entry
 * ("adopt, don't re-connect" — a second connection would violate ADR-013's single-playback invariant). Return
 * is by back-stack pop (the origin detail / Live-TV sits beneath); the Live-TV committed-channel focus is the
 * one explicit carve-out (§3.4-7).
 */
@Serializable
internal data class Player(
    val mediaType: String,
    val providerStableKey: String,
    val mediaStableKey: String,
    val origin: String,
    val returnTarget: String,
    val startPositionMillis: Long = 0L,
    val epgProgramStableKey: String? = null,
)
