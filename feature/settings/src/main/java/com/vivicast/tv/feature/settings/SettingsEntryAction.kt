package com.vivicast.tv.feature.settings

/**
 * One-shot sub-view to open when Settings is entered via a deep-link (e.g. the Home "add playlist" CTA),
 * on top of landing on the deep-linked section. [None] = land on the section only.
 *
 * Cross-panel extension point: to deep-link into a new section's sub-view (EPG source, appearance detail, …)
 * add a case here and a matching branch in [SettingsRoute]'s entry effect, which owns the section's typed
 * inner-nav routes. The host ([com.vivicast.tv.MainActivity]) only forwards the enum, so it never needs to
 * know a section's internal routes.
 */
enum class SettingsEntryAction {
    None,
    AddPlaylist,
}
