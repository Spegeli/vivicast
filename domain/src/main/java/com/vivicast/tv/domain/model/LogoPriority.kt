package com.vivicast.tv.domain.model

/**
 * A source a channel logo can come from, per playlist. The user orders these three freely (see
 * [parseLogoPriorityOrder]); the logo resolver walks the order and takes the first source that yields
 * something. [token] is the stable storage string (do not change — it is what a playlist persists and
 * what backups round-trip).
 */
enum class LogoSource(val token: String) {
    Playlist("playlist"),
    Epg("epg"),
    Local("local"),
    ;

    companion object {
        fun fromToken(token: String): LogoSource? = entries.firstOrNull { it.token == token }
    }
}

/** Canonical order when a playlist has no stored preference: playlist logo, then EPG icon, then local file. */
val DEFAULT_LOGO_PRIORITY_ORDER: List<LogoSource> =
    listOf(LogoSource.Playlist, LogoSource.Epg, LogoSource.Local)

// Legacy single-token values (from before ordering was user-configurable) expand to the fixed 3-source
// order the app applied for that choice: the chosen source first, then the historical fallbacks. "provider"
// and anything unrecognized fall through to the default order (playlist-first).
private val LEGACY_SINGLE_TOKEN_ORDER: Map<String, List<LogoSource>> = mapOf(
    LogoSource.Playlist.token to listOf(LogoSource.Playlist, LogoSource.Epg, LogoSource.Local),
    LogoSource.Epg.token to listOf(LogoSource.Epg, LogoSource.Playlist, LogoSource.Local),
    LogoSource.Local.token to listOf(LogoSource.Local, LogoSource.Playlist, LogoSource.Epg),
)

/**
 * Parses a stored `logoPriority` value into a complete, deduped source order. Accepts the CSV form
 * ("playlist,epg,local"), any legacy single token (incl. "provider" → playlist-first), or blank. The
 * result always contains all three sources exactly once: recognized tokens keep their order, then any
 * source not named is appended in the default order — so a value that predates a newly added source (or a
 * corrupted one) still yields a usable full order.
 */
fun parseLogoPriorityOrder(value: String?): List<LogoSource> {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return DEFAULT_LOGO_PRIORITY_ORDER
    if (!raw.contains(',')) LEGACY_SINGLE_TOKEN_ORDER[raw]?.let { return it }
    val parsed = raw.split(',').mapNotNull { LogoSource.fromToken(it.trim()) }.distinct()
    if (parsed.isEmpty()) return DEFAULT_LOGO_PRIORITY_ORDER
    return parsed + DEFAULT_LOGO_PRIORITY_ORDER.filter { it !in parsed }
}

/** Serializes a source order to the stored CSV form ("playlist,epg,local"). */
fun serializeLogoPriorityOrder(order: List<LogoSource>): String = order.joinToString(",") { it.token }
