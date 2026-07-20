package com.vivicast.tv.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation-Compose routes (replaces the old `selectedRoute` string model).
 *
 * Phase A1 scope: the tab shell + the six top-level destinations. Movie/series **detail** destinations and
 * the **Player** destination are added in their own phases (B / C1) — do not pre-declare them here (keeps the
 * graph in step with what is actually wired, and avoids unused-route noise).
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
