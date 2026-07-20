package com.vivicast.tv.feature.settings

import kotlinx.serialization.Serializable

/**
 * Inner Settings navigation — **feature-owned**. The app graph (`:app`/`VivicastRoutes`) stays the sole owner
 * of TOP-LEVEL navigation; this inner NavHost drives Settings' own master-detail: the section rail picks a
 * section, and the detail pane's sub-views (provider editor / actions / groups) are real destinations with
 * proper BACK — replacing the old local-state + focus-park + `collapseSubViewSignal` machinery.
 *
 * This is a deliberate, documented exception to "feature modules never see a route type" (see
 * `VivicastRoutes.kt`): that rule is about the *app* graph. A feature owning its own internal nav keeps
 * Settings' structure encapsulated in the feature instead of leaking it into `:app`.
 *
 * Playlists is modelled as a nested graph now (with only `SecPlaylists` as its start) so the sub-editors can
 * be added under it in a follow-up without re-shaping the section-level graph — mirroring how `MoviesGraph`
 * held only `MoviesList` before the detail destination landed.
 */

@Serializable internal object SecGeneral

/** Playlists section: nested graph holding the overview + (later) the provider editor/actions/groups. */
@Serializable internal object PlaylistsGraph

@Serializable internal object SecPlaylists

@Serializable internal object SecEpg

@Serializable internal object SecAppearance

@Serializable internal object SecPlayback

@Serializable internal object SecParental

@Serializable internal object SecCache

@Serializable internal object SecBackup

@Serializable internal object SecAbout
