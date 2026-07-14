package com.vivicast.tv.feature.settings

import androidx.compose.ui.focus.FocusRequester
import com.vivicast.tv.data.provider.DEFAULT_REFRESH_INTERVAL_HOURS
import com.vivicast.tv.data.provider.LOGO_PRIORITY_PLAYLIST
import com.vivicast.tv.data.provider.REFRESH_INTERVAL_OFF
import com.vivicast.tv.data.provider.M3uSourceMode
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderUpdateRequest
import com.vivicast.tv.data.provider.XTREAM_OUTPUT_HLS
import com.vivicast.tv.data.provider.isAutomaticallyRefreshable
import com.vivicast.tv.data.provider.normalizeLogoPriority
import com.vivicast.tv.data.provider.normalizeXtreamOutputFormat
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderType

internal data class ProviderEditorState(
    val providerId: String?,
    val type: ProviderType,
    val name: String,
    val m3uSourceMode: M3uSourceMode,
    val m3uUrl: String,
    val m3uContent: String,
    val m3uHasExistingSource: Boolean,
    val xtreamServerUrl: String,
    val xtreamUsername: String,
    val xtreamPassword: String,
    val includeLiveTv: Boolean,
    val includeMovies: Boolean,
    val includeSeries: Boolean,
    val refreshIntervalHours: Int,
    val m3uFileName: String = "",
    val isActive: Boolean = true,
    val userAgent: String = "",
    val refreshOnAppStartEnabled: Boolean = true,
    val logoPriority: String = LOGO_PRIORITY_PLAYLIST,
    val xtreamOutputFormat: String = XTREAM_OUTPUT_HLS,
    // Signature of the source (URL/file/Xtream creds) as loaded when editing; blank for a new provider.
    // Lets Save skip the connection test when the source didn't change (see isSourceUnchanged).
    val pristineSource: String = "",
    // The type/mode the provider was loaded with (null in add mode). Editing may switch to another
    // type/mode; comparing against these detects a switch (confirm + wipe) and whether the stored source
    // is back in view.
    val originalType: ProviderType? = null,
    val originalSourceMode: M3uSourceMode? = null,
) {
    val isEditing: Boolean get() = providerId != null
    val isAutomaticallyRefreshable: Boolean
        get() = type == ProviderType.Xtream || (type == ProviderType.M3u && m3uSourceMode.isAutomaticallyRefreshable)

    /** Editing and the chosen source type/mode differs from what was loaded → save must confirm and the
     * repository wipes the old source. */
    val sourceSwitched: Boolean
        get() = isEditing && originalType != null &&
            (type != originalType || (type == ProviderType.M3u && m3uSourceMode != originalSourceMode))

    /** A blank source field means "keep the stored source" only when editing the SAME type/mode. */
    private val keepExistingSource: Boolean get() = isEditing && !sourceSwitched

    /**
     * Switch the editor to a source type (and M3U mode), preserving all drafts (URL/file/Xtream stay
     * filled across toggles). Recomputes whether the ORIGINAL stored source is back in view — only then
     * does a blank field mean "keep it".
     */
    fun selectSource(newType: ProviderType, newMode: M3uSourceMode = m3uSourceMode): ProviderEditorState {
        val base = copy(
            type = newType,
            m3uSourceMode = if (newType == ProviderType.M3u) newMode else m3uSourceMode,
            m3uHasExistingSource = newType == ProviderType.M3u &&
                originalType == ProviderType.M3u &&
                newMode == originalSourceMode,
        )
        // Switching a File source (auto-refresh forced OFF) to a fetchable one re-reveals the interval /
        // app-start options; restore sensible defaults so the user doesn't have to re-enable them. A
        // same-refreshability switch keeps the current values (respects a deliberate "Off").
        return if (!isAutomaticallyRefreshable && base.isAutomaticallyRefreshable &&
            base.refreshIntervalHours <= REFRESH_INTERVAL_OFF
        ) {
            base.copy(refreshIntervalHours = DEFAULT_REFRESH_INTERVAL_HOURS, refreshOnAppStartEnabled = true)
        } else {
            base
        }
    }

    /** A stable signature of the current source fields, compared against [pristineSource]. */
    fun sourceSignature(): String = when (type) {
        ProviderType.M3u -> "M3U|$m3uSourceMode|${m3uUrl.trim()}|${m3uContent.trim()}"
        ProviderType.Xtream -> "XT|${xtreamServerUrl.trim()}|${xtreamUsername.trim()}|${xtreamPassword.trim()}"
    }

    /** Editing an existing provider without having changed its source — Save may skip the connection test. */
    val isSourceUnchanged: Boolean
        get() = isEditing && pristineSource.isNotEmpty() && pristineSource == sourceSignature()

    fun validationMessage(
        msgNameMissing: String,
        msgContentType: String,
        msgXtreamServer: String,
        msgXtreamUser: String,
        msgXtreamPass: String,
        msgM3uUrl: String,
        msgM3uFile: String,
    ): String? {
        if (name.isBlank()) return msgNameMissing
        // Every source type imports selectable content now (M3U classifies too), and the repository
        // requires at least one type for all providers — surface that in the UI up front.
        if (!includeLiveTv && !includeMovies && !includeSeries) return msgContentType
        when (type) {
            // "Keep existing" only for a same-type/mode edit; a switch requires the new source's fields.
            ProviderType.M3u -> m3uSourceValidationMessage(allowExistingSource = keepExistingSource, msgM3uUrl, msgM3uFile)?.let { return it }
            ProviderType.Xtream -> if (!keepExistingSource) {
                if (xtreamServerUrl.isBlank()) return msgXtreamServer
                if (xtreamUsername.isBlank()) return msgXtreamUser
                if (xtreamPassword.isBlank()) return msgXtreamPass
            }
        }
        return null
    }

    // Connection test is independent of the name (blank or duplicate) — it only needs the
    // connection-relevant fields (M3U URL/file content, or Xtream server/user/password).
    fun connectionTestRequestMessage(
        msgXtreamServer: String,
        msgXtreamUser: String,
        msgXtreamPass: String,
        msgM3uUrl: String,
        msgM3uFile: String,
    ): String? =
        when (type) {
            // A File playlist being edited keeps its stored source; its content is resolved from disk for
            // the test, so a blank editor field is allowed. URL still needs the (pre-filled) URL present.
            ProviderType.M3u -> m3uSourceValidationMessage(
                allowExistingSource = m3uSourceMode == M3uSourceMode.File && isEditing && m3uHasExistingSource,
                msgM3uUrl,
                msgM3uFile,
            )
            ProviderType.Xtream -> when {
                xtreamServerUrl.isBlank() -> msgXtreamServer
                xtreamUsername.isBlank() -> msgXtreamUser
                xtreamPassword.isBlank() -> msgXtreamPass
                else -> null
            }
        }

    fun toConnectionTestRequest(): ProviderCreateRequest =
        ProviderCreateRequest(
            name = name,
            type = type,
            m3uSourceMode = m3uSourceMode,
            m3uUrl = m3uUrl,
            m3uContent = m3uContent,
            xtreamServerUrl = xtreamServerUrl,
            xtreamUsername = xtreamUsername,
            xtreamPassword = xtreamPassword,
            includeLiveTv = includeLiveTv,
            includeMovies = includeMovies,
            includeSeries = includeSeries,
            refreshIntervalHours = refreshIntervalHours,
            userAgent = userAgent.ifBlank { null },
            logoPriority = logoPriority,
            xtreamOutputFormat = xtreamOutputFormat,
        )

    fun toCreateRequest(): ProviderCreateRequest =
        ProviderCreateRequest(
            name = name,
            type = type,
            m3uSourceMode = m3uSourceMode,
            m3uUrl = m3uUrl,
            m3uContent = m3uContent,
            xtreamServerUrl = xtreamServerUrl,
            xtreamUsername = xtreamUsername,
            xtreamPassword = xtreamPassword,
            includeLiveTv = includeLiveTv,
            includeMovies = includeMovies,
            includeSeries = includeSeries,
            refreshIntervalHours = refreshIntervalHours,
            userAgent = userAgent.ifBlank { null },
            refreshOnAppStartEnabled = refreshOnAppStartEnabled,
            logoPriority = logoPriority,
            xtreamOutputFormat = xtreamOutputFormat,
        )

    fun toUpdateRequest(): ProviderUpdateRequest =
        ProviderUpdateRequest(
            providerId = requireNotNull(providerId),
            name = name,
            type = type,
            m3uSourceMode = if (type == ProviderType.M3u && shouldReplaceM3uSource) m3uSourceMode else null,
            m3uUrl = m3uUrl.ifBlank { null },
            m3uContent = m3uContent.ifBlank { null },
            xtreamServerUrl = xtreamServerUrl.ifBlank { null },
            xtreamUsername = xtreamUsername.ifBlank { null },
            xtreamPassword = xtreamPassword.ifBlank { null },
            includeLiveTv = includeLiveTv,
            includeMovies = includeMovies,
            includeSeries = includeSeries,
            refreshIntervalHours = refreshIntervalHours,
            userAgent = userAgent.ifBlank { null },
            refreshOnAppStartEnabled = refreshOnAppStartEnabled,
            logoPriority = logoPriority,
            xtreamOutputFormat = xtreamOutputFormat,
        )

    private val shouldReplaceM3uSource: Boolean
        get() = !m3uHasExistingSource || m3uUrl.isNotBlank() || m3uContent.isNotBlank()

    private fun m3uSourceValidationMessage(allowExistingSource: Boolean, msgUrl: String, msgFile: String): String? {
        if (allowExistingSource && m3uHasExistingSource && m3uUrl.isBlank() && m3uContent.isBlank()) return null
        return when (m3uSourceMode) {
            M3uSourceMode.Url -> if (m3uUrl.isBlank()) msgUrl else null
            M3uSourceMode.File -> if (m3uContent.isBlank()) msgFile else null
        }
    }

    companion object {
        fun newProvider(type: ProviderType): ProviderEditorState =
            ProviderEditorState(
                providerId = null,
                type = type,
                name = "",
                m3uSourceMode = M3uSourceMode.Url,
                m3uUrl = "",
                m3uContent = "",
                m3uHasExistingSource = false,
                xtreamServerUrl = "",
                xtreamUsername = "",
                xtreamPassword = "",
                includeLiveTv = true,
                includeMovies = false,
                includeSeries = false,
                refreshIntervalHours = DEFAULT_REFRESH_INTERVAL_HOURS,
            )

        fun from(provider: Provider, credentials: ProviderCredentials? = null): ProviderEditorState {
            // Pre-fill the editor with the stored credentials so editing shows the current values.
            // File-mode content is NOT loaded into the editor (it can be large); we show the stored
            // file name (<providerId>.m3u) and resolve the content on demand for the Test button.
            val m3u = credentials as? ProviderCredentials.M3u
            val xtream = credentials as? ProviderCredentials.Xtream
            val sourceMode = m3u?.sourceMode ?: M3uSourceMode.Url
            return ProviderEditorState(
                providerId = provider.id,
                type = provider.type,
                name = provider.name,
                m3uSourceMode = sourceMode,
                m3uUrl = m3u?.url.orEmpty(),
                m3uContent = "",
                m3uFileName = if (provider.type == ProviderType.M3u && sourceMode == M3uSourceMode.File) {
                    "${provider.id}.m3u"
                } else {
                    ""
                },
                // Tied to the provider type, not the loaded credentials: an M3U provider still HAS a stored
                // source even if the (async, secure) credential load failed — so a blank field means "keep
                // the stored source", not "source missing", and Save isn't blocked on a transient read error.
                m3uHasExistingSource = provider.type == ProviderType.M3u,
                xtreamServerUrl = xtream?.serverUrl.orEmpty(),
                xtreamUsername = xtream?.username.orEmpty(),
                xtreamPassword = xtream?.password.orEmpty(),
                includeLiveTv = provider.includeLiveTv,
                includeMovies = provider.includeMovies,
                includeSeries = provider.includeSeries,
                refreshIntervalHours = provider.refreshIntervalHours,
                isActive = provider.isActive,
                userAgent = provider.userAgent.orEmpty(),
                refreshOnAppStartEnabled = provider.refreshOnAppStartEnabled,
                logoPriority = normalizeLogoPriority(provider.logoPriority),
                xtreamOutputFormat = normalizeXtreamOutputFormat(provider.xtreamOutputFormat),
                originalType = provider.type,
                originalSourceMode = if (provider.type == ProviderType.M3u) sourceMode else null,
            ).let { it.copy(pristineSource = it.sourceSignature()) }
        }
    }
}

/**
 * The connection-test request for this editor. For a File playlist being edited without a freshly picked
 * file (blank content field), the stored content is resolved on demand so the test checks the linked file.
 */
internal suspend fun ProviderEditorState.resolveTestRequest(
    getStoredM3uContent: suspend (String) -> String?,
): ProviderCreateRequest {
    val base = toConnectionTestRequest()
    val needsStoredContent = type == ProviderType.M3u &&
        m3uSourceMode == M3uSourceMode.File &&
        m3uContent.isBlank() &&
        providerId != null
    return if (needsStoredContent) base.copy(m3uContent = getStoredM3uContent(providerId)) else base
}

internal enum class ConnectionTestStatus { Idle, Testing, Passed, Failed }

internal enum class ProviderEditorErrorFocus { Name, Url, File, Server, User, Pass, Import }

/** The URL and file field focus requesters, bundled to keep the credential list under the arg limit. */
internal class M3uSourceFocus(val url: FocusRequester, val file: FocusRequester)

/** True when the given M3U source's field is empty and required (not an edit that keeps its source). */
internal fun ProviderEditorState.isSourceBlank(mode: M3uSourceMode): Boolean {
    if (type != ProviderType.M3u || m3uSourceMode != mode) return false
    if (isEditing && m3uHasExistingSource) return false
    return when (mode) {
        M3uSourceMode.Url -> m3uUrl.isBlank()
        M3uSourceMode.File -> m3uContent.isBlank()
    }
}

/** First blocking field error on save (name → URL/file → import), or null when the form may be saved. */
internal fun firstSaveError(
    editor: ProviderEditorState,
    duplicateName: Boolean,
    duplicateUrlName: String?,
    nameBlank: Boolean,
    urlBlank: Boolean,
    fileBlank: Boolean,
    serverBlank: Boolean,
    userBlank: Boolean,
    passBlank: Boolean,
): ProviderEditorErrorFocus? {
    // Xtream credentials are required when creating OR when switching an existing provider to Xtream;
    // a same-type Xtream edit keeps its stored credentials.
    val xtreamRequired = !editor.isEditing || editor.sourceSwitched
    return when {
        duplicateName || nameBlank -> ProviderEditorErrorFocus.Name
        duplicateUrlName != null || urlBlank -> ProviderEditorErrorFocus.Url
        fileBlank -> ProviderEditorErrorFocus.File
        xtreamRequired && serverBlank -> ProviderEditorErrorFocus.Server
        xtreamRequired && userBlank -> ProviderEditorErrorFocus.User
        xtreamRequired && passBlank -> ProviderEditorErrorFocus.Pass
        !editor.includeLiveTv && !editor.includeMovies && !editor.includeSeries -> ProviderEditorErrorFocus.Import
        else -> null
    }
}

/** Error targets that live at the top of the list, so a jump to them scrolls to item 0 first. */
internal val topFocusTargets = setOf(ProviderEditorErrorFocus.Name, ProviderEditorErrorFocus.Url)

/** The source field to focus when a test fails (M3U URL/file, or the Xtream server). */
internal fun ProviderEditorState.sourceFocusTarget(): ProviderEditorErrorFocus = when {
    type == ProviderType.Xtream -> ProviderEditorErrorFocus.Server
    m3uSourceMode == M3uSourceMode.File -> ProviderEditorErrorFocus.File
    else -> ProviderEditorErrorFocus.Url
}

/** The blank required source field to jump to when testing, or null when the test may run. */
internal fun testBlankSourceFocus(
    editor: ProviderEditorState,
    serverBlank: Boolean,
    userBlank: Boolean,
    passBlank: Boolean,
    urlBlank: Boolean,
    fileBlank: Boolean,
): ProviderEditorErrorFocus? =
    if (editor.type == ProviderType.Xtream) {
        firstBlankXtreamFocus(serverBlank, userBlank, passBlank)
    } else {
        sourceBlankFocus(urlBlank, fileBlank)
    }

/** Blank source field to jump to when testing (URL or file), or null when the test may run. */
private fun sourceBlankFocus(urlBlank: Boolean, fileBlank: Boolean): ProviderEditorErrorFocus? = when {
    urlBlank -> ProviderEditorErrorFocus.Url
    fileBlank -> ProviderEditorErrorFocus.File
    else -> null
}

/** Xtream fields for the credential list, bundled to keep its arg list under the limit. */
internal class XtreamFieldState(
    val serverError: Boolean,
    val userError: Boolean,
    val passError: Boolean,
    val serverFocus: FocusRequester,
    val userFocus: FocusRequester,
    val passFocus: FocusRequester,
)

internal enum class XtreamField { Server, User, Pass }

internal fun xtreamFieldState(
    showSourceBlankError: Boolean,
    serverBlank: Boolean,
    userBlank: Boolean,
    passBlank: Boolean,
    serverFocus: FocusRequester,
    userFocus: FocusRequester,
    passFocus: FocusRequester,
): XtreamFieldState = XtreamFieldState(
    serverError = showSourceBlankError && serverBlank,
    userError = showSourceBlankError && userBlank,
    passError = showSourceBlankError && passBlank,
    serverFocus = serverFocus,
    userFocus = userFocus,
    passFocus = passFocus,
)

internal fun ProviderEditorState.isXtreamFieldBlank(field: XtreamField): Boolean {
    if (type != ProviderType.Xtream) return false
    return when (field) {
        XtreamField.Server -> xtreamServerUrl.isBlank()
        XtreamField.User -> xtreamUsername.isBlank()
        XtreamField.Pass -> xtreamPassword.isBlank()
    }
}

/** First blank Xtream credential to jump to on test (server → user → password), or null. */
private fun firstBlankXtreamFocus(
    serverBlank: Boolean,
    userBlank: Boolean,
    passBlank: Boolean,
): ProviderEditorErrorFocus? = when {
    serverBlank -> ProviderEditorErrorFocus.Server
    userBlank -> ProviderEditorErrorFocus.User
    passBlank -> ProviderEditorErrorFocus.Pass
    else -> null
}
