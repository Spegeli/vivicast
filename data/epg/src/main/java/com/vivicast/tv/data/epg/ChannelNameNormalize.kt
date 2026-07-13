package com.vivicast.tv.data.epg

import java.text.Normalizer
import java.util.Locale

/**
 * Loose key: trimmed + lowercased. Used where exactness matters (e.g. programme titles), NOT for channel
 * recognition — that uses [smartMatchKey].
 */
fun normalizeChannelMatchKey(value: String): String = value.trim().lowercase(Locale.ROOT)

// Quality / resolution tokens dropped so "ZDF HD" / "ZDF (720p)" reduce like "ZDF".
private val MATCH_NOISE = setOf(
    "hd", "uhd", "fhd", "sd", "4k", "8k", "hq", "hevc", "h264", "h265",
    "720p", "1080p", "2160p", "480p", "576p", "50fps", "60fps",
)

// Country codes dropped from the leading or trailing position — EPG names use a "DE - " prefix, tv-logos
// files a "-de" suffix, tvg-ids a ".de" suffix; none of them identify the channel.
private val COUNTRY_CODES = setOf(
    "de", "at", "ch", "uk", "gb", "us", "ca", "ie", "fr", "es", "pt", "it", "nl", "be", "lu",
    "pl", "cz", "sk", "hu", "ro", "bg", "gr", "tr", "ru", "ua", "se", "no", "dk", "fi", "is",
    "hr", "si", "rs", "ba", "mk", "al", "ee", "lv", "lt", "md",
)

private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")
private val BRACKETS = Regex("[\\[({][^\\]})]*[\\]})]")
private val NON_ALNUM = Regex("[^a-z0-9]+")

/**
 * Provider-tolerant channel recognition key, shared by EPG channel matching and local-logo-folder matching.
 * tvg-ids, EPG `<channel id>`s and logo filenames are defined independently per provider with wildly
 * different formats — `ZDFinfo.de`, `ZDFinfode`, `zdf-info-de`, `ZDFinfo.de@SD`, `DE - ZDFinfo`,
 * `ZDFinfo (720p) [Geo-blocked]` — so an exact/format-preserving match can't work.
 *
 * This collapses them to a comparable slug: lowercase, drop the `@variant` suffix + bracketed tags +
 * diacritics, map `+` to a `plus` token, tokenize on any non-alphanumeric, drop resolution noise and a
 * leading/trailing country code, then join. All the examples above become `zdfinfo`.
 *
 * Deliberately NOT lossy on the parts that distinguish channels: digits and the `+`/`plus` marker survive,
 * so `Sport1` / `Sport1+` → `sport1` / `sport1plus` and `ZDF` / `ZDF2` → `zdf` / `zdf2` stay distinct.
 */
fun smartMatchKey(value: String): String {
    val cleaned = Normalizer.normalize(value.lowercase(Locale.ROOT).substringBefore('@'), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .replace(BRACKETS, " ")
        .replace("+", " plus ")
    var tokens = NON_ALNUM.split(cleaned).filter { it.isNotBlank() && it !in MATCH_NOISE }
    if (tokens.size > 1 && tokens.first() in COUNTRY_CODES) tokens = tokens.drop(1)
    if (tokens.size > 1 && tokens.last() in COUNTRY_CODES) tokens = tokens.dropLast(1)
    return tokens.joinToString(separator = "")
}
