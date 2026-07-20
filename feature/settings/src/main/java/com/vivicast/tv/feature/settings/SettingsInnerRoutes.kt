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

/** Playlists section: nested graph holding the overview + the provider actions / editor / groups screens. */
@Serializable internal object PlaylistsGraph

/** Playlists overview (provider list + add / refresh-all). */
@Serializable internal object SecPlaylists

/** Per-provider actions menu (edit / test / refresh / manage groups / delete). */
@Serializable internal data class PlaylistActions(val providerId: String)

/** Provider add/edit form. [providerId] null = add a new provider. Self-contained: loads its own draft. */
@Serializable internal data class PlaylistEditor(val providerId: String? = null)

/** Category-group management ("Gruppen verwalten") for a provider. */
@Serializable internal data class PlaylistGroups(val providerId: String)

@Serializable internal object SecEpg

@Serializable internal object SecAppearance

@Serializable internal object SecPlayback

@Serializable internal object SecParental

@Serializable internal object SecCache

@Serializable internal object SecBackup

@Serializable internal object SecAbout
