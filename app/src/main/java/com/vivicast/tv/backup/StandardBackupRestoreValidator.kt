package com.vivicast.tv.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class StandardBackupRestorePreview(
    val exportedAtMillis: Long,
    val providerCount: Int,
    val epgSourceCount: Int,
    val favoriteCount: Int,
    val playbackProgressCount: Int,
    val parentalProtectionWasActive: Boolean,
)

sealed interface StandardBackupRestoreValidation {
    data class Valid(val preview: StandardBackupRestorePreview) : StandardBackupRestoreValidation
    data class SafetyBackupFailed(val preview: StandardBackupRestorePreview) : StandardBackupRestoreValidation
    data class Invalid(val message: String) : StandardBackupRestoreValidation
}

fun validateStandardBackupForRestore(jsonText: String): StandardBackupRestoreValidation =
    runCatching { JSONObject(jsonText).validateBackupForRestore(expectedMode = "STANDARD", allowSecrets = false) }
        .getOrElse { StandardBackupRestoreValidation.Invalid("Backup-Datei ungueltig.") }

fun validateFullBackupPayloadForRestore(jsonText: String): StandardBackupRestoreValidation =
    runCatching { JSONObject(jsonText).validateBackupForRestore(expectedMode = "FULL", allowSecrets = true) }
        .getOrElse { StandardBackupRestoreValidation.Invalid("Backup-Datei ungueltig.") }

private fun JSONObject.validateBackupForRestore(
    expectedMode: String,
    allowSecrets: Boolean,
): StandardBackupRestoreValidation {
    if (optInt("schemaVersion", -1) != STANDARD_BACKUP_SCHEMA_VERSION) {
        return StandardBackupRestoreValidation.Invalid("Backup-Version nicht unterstuetzt.")
    }
    if (optString("exportMode") != expectedMode) {
        return StandardBackupRestoreValidation.Invalid("Backup-Modus nicht unterstuetzt.")
    }
    val exportedAtMillis = optLong("exportedAtMillis", -1L)
    if (exportedAtMillis < 0L) return StandardBackupRestoreValidation.Invalid("Backup-Datei ungueltig.")
    if (!allowSecrets) findStandardBackupSecretKey()?.let {
        return StandardBackupRestoreValidation.Invalid("Standard-Backup enthaelt geheime Werte.")
    }

    val providers = optJSONArray("providers") ?: JSONArray()
    val epgSources = optJSONArray("epgSources") ?: JSONArray()
    val providerKeys = mutableSetOf<String>()
    val epgSourceKeys = mutableSetOf<String>()

    providers.forEachObject { provider ->
        val stableKey = provider.requiredString("stableKey") ?: return invalidBackup()
        providerKeys += stableKey
        val type = provider.requiredString("type") ?: return invalidBackup()
        if (type !in PROVIDER_TYPES) return invalidBackup()
        provider.optJSONObject("source")?.let { source ->
            source.nullableString("m3uUrl")?.let {
                if (!isRestorableSourceUrl(it, allowSecrets)) return invalidBackup()
            }
            source.nullableString("xtreamServerUrl")?.let {
                if (!isRestorableSourceUrl(it, allowSecrets)) return invalidBackup()
            }
        }
    }

    epgSources.forEachObject { source ->
        val stableKey = source.requiredString("stableKey") ?: return invalidBackup()
        epgSourceKeys += stableKey
        source.nullableString("url")?.let {
            if (!isRestorableSourceUrl(it, allowSecrets)) return invalidBackup()
        }
    }

    (optJSONArray("providerEpgSources") ?: JSONArray()).forEachObject {
        if (it.requiredString("providerStableKey") !in providerKeys) return invalidReference()
        if (it.requiredString("epgSourceStableKey") !in epgSourceKeys) return invalidReference()
    }
    (optJSONArray("epgChannelMappings") ?: JSONArray()).forEachObject {
        if (it.requiredString("providerStableKey") !in providerKeys) return invalidReference()
        if (it.requiredString("epgSourceStableKey") !in epgSourceKeys) return invalidReference()
        if (it.requiredString("channelStableKey") == null) return invalidBackup()
        if (it.requiredString("epgChannelStableKey") == null) return invalidBackup()
    }
    (optJSONArray("categories") ?: JSONArray()).forEachObject {
        if (it.requiredString("providerStableKey") !in providerKeys) return invalidReference()
        if (it.requiredString("stableKey") == null) return invalidBackup()
    }
    val favorites = optJSONArray("favorites") ?: JSONArray()
    favorites.forEachObject {
        if (it.requiredString("providerStableKey") !in providerKeys) return invalidReference()
        if (it.requiredString("mediaStableKey") == null) return invalidBackup()
    }
    val playbackProgress = optJSONArray("playbackProgress") ?: JSONArray()
    playbackProgress.forEachObject {
        if (it.requiredString("providerStableKey") !in providerKeys) return invalidReference()
        if (it.requiredString("mediaStableKey") == null) return invalidBackup()
        if (it.optLong("positionMillis", -1L) < 0L) return invalidBackup()
        if (it.optLong("durationMillis", -1L) < 0L) return invalidBackup()
    }
    (optJSONArray("channelHistory") ?: JSONArray()).forEachObject {
        if (it.requiredString("providerStableKey") !in providerKeys) return invalidReference()
        if (it.requiredString("channelStableKey") == null) return invalidBackup()
    }

    return StandardBackupRestoreValidation.Valid(
        StandardBackupRestorePreview(
            exportedAtMillis = exportedAtMillis,
            providerCount = providers.length(),
            epgSourceCount = epgSources.length(),
            favoriteCount = favorites.length(),
            playbackProgressCount = playbackProgress.length(),
            parentalProtectionWasActive = optJSONObject("security")
                ?.optBoolean("parentalProtectionWasActive", false) == true,
        ),
    )
}

private fun JSONObject.findStandardBackupSecretKey(): String? {
    keys().forEach { key ->
        if (key in STANDARD_BACKUP_SECRET_KEYS) return key
        val value = opt(key)
        when (value) {
            is JSONObject -> value.findStandardBackupSecretKey()?.let { return it }
            is JSONArray -> value.findStandardBackupSecretKey()?.let { return it }
        }
    }
    return null
}

private fun JSONArray.findStandardBackupSecretKey(): String? {
    for (index in 0 until length()) {
        when (val value = opt(index)) {
            is JSONObject -> value.findStandardBackupSecretKey()?.let { return it }
            is JSONArray -> value.findStandardBackupSecretKey()?.let { return it }
        }
    }
    return null
}

private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: throw JSONException("Expected object.")
        block(item)
    }
}

private fun JSONObject.requiredString(key: String): String? =
    nullableString(key)?.takeIf { it.isNotBlank() }

private fun JSONObject.nullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).trim()

private fun invalidBackup(): StandardBackupRestoreValidation.Invalid =
    StandardBackupRestoreValidation.Invalid("Backup-Datei ungueltig.")

private fun invalidReference(): StandardBackupRestoreValidation.Invalid =
    StandardBackupRestoreValidation.Invalid("Backup-Referenzen ungueltig.")

private fun isRestorableSourceUrl(url: String, allowSecrets: Boolean): Boolean =
    if (allowSecrets) fullBackupHttpUrlOrNull(url) != null else standardBackupM3uUrlOrNull(url) != null

private fun fullBackupHttpUrlOrNull(url: String?): String? {
    val value = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val parsed = runCatching { java.net.URI(value) }.getOrNull() ?: return null
    if (parsed.scheme?.lowercase() !in setOf("http", "https")) return null
    if (parsed.host.isNullOrBlank()) return null
    return value
}

private val PROVIDER_TYPES = setOf("M3U", "XTREAM", "M3u", "Xtream")
private val STANDARD_BACKUP_SECRET_KEYS = setOf(
    "sourceConfigKey",
    "credentialsKey",
    "urlKey",
    "username",
    "password",
    "xtreamUsername",
    "xtreamPassword",
    "cookie",
    "cookies",
    "headers",
)
