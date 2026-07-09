package com.vivicast.tv.data.playback

import com.vivicast.tv.core.cache.M3uStreamReferenceStore
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.XTREAM_OUTPUT_TS
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

interface PlaybackStreamResolver {
    suspend fun resolve(request: PlaybackStreamRequest): PlaybackStreamResult
}

data class PlaybackStreamRequest(
    val providerId: String,
    val mediaId: String,
    val mediaType: MediaType,
    val remoteId: String,
    val containerExtension: String? = null,
    val catchupStartMillis: Long? = null,
    val catchupEndMillis: Long? = null,
)

sealed interface PlaybackStreamResult {
    data class Resolved(val stream: PlaybackStream) : PlaybackStreamResult
    data class Failed(val reason: PlaybackStreamFailureReason) : PlaybackStreamResult
}

data class PlaybackStream(
    val providerId: String,
    val mediaId: String,
    val mediaType: MediaType,
    val providerStableKey: String = providerId,
    val url: String,
)

enum class PlaybackStreamFailureReason {
    ProviderNotFound,
    ProviderInactive,
    CredentialsMissing,
    CredentialTypeMismatch,
    UnsupportedProvider,
    UnsupportedMediaType,
    MissingRemoteId,
    MissingStreamReference,
    MissingContainerExtension,
    InvalidCatchupWindow,
    InvalidServerUrl,
}

class DefaultPlaybackStreamResolver(
    private val providerRepository: ProviderRepository,
    private val m3uStreamReferenceStore: M3uStreamReferenceStore,
) : PlaybackStreamResolver {
    override suspend fun resolve(request: PlaybackStreamRequest): PlaybackStreamResult {
        val provider = providerRepository.getProvider(request.providerId)
            ?: return PlaybackStreamResult.Failed(PlaybackStreamFailureReason.ProviderNotFound)

        if (!provider.isActive || provider.status.blocksPlayback()) {
            return PlaybackStreamResult.Failed(PlaybackStreamFailureReason.ProviderInactive)
        }

        val credentials = providerRepository.getCredentials(provider.id)
            ?: return PlaybackStreamResult.Failed(PlaybackStreamFailureReason.CredentialsMissing)

        return when (provider.type) {
            ProviderType.Xtream -> {
                if (credentials !is ProviderCredentials.Xtream) {
                    PlaybackStreamResult.Failed(PlaybackStreamFailureReason.CredentialTypeMismatch)
                } else {
                    resolveXtream(request, credentials, provider.stableKey, provider.xtreamOutputFormat)
                }
            }
            ProviderType.M3u -> {
                if (credentials !is ProviderCredentials.M3u) {
                    PlaybackStreamResult.Failed(PlaybackStreamFailureReason.CredentialTypeMismatch)
                } else {
                    resolveM3u(request, provider.stableKey)
                }
            }
        }
    }

    private suspend fun resolveM3u(request: PlaybackStreamRequest, providerStableKey: String): PlaybackStreamResult {
        // M3U resolves Channel, Movie and Episode from the stored direct stream reference, keyed by the
        // entity remoteId exactly as imported (e.g. "movie:…"/"episode:…"). A Series has no playable
        // stream of its own. Movie/Episode carry no catch-up window, so they take the direct-URL path.
        if (request.mediaType == MediaType.Series) {
            return PlaybackStreamResult.Failed(PlaybackStreamFailureReason.UnsupportedMediaType)
        }
        val remoteId = request.remoteId.trim()
        if (remoteId.isBlank()) {
            return PlaybackStreamResult.Failed(PlaybackStreamFailureReason.MissingRemoteId)
        }
        val reference = m3uStreamReferenceStore.getReference(request.providerId, remoteId)
            ?: return PlaybackStreamResult.Failed(PlaybackStreamFailureReason.MissingStreamReference)
        val streamUrl = if (request.isCatchupRequest) {
            request.resolveM3uCatchupUrl(reference.streamUrl, reference.catchupMode, reference.catchupSource)
                ?: return PlaybackStreamResult.Failed(PlaybackStreamFailureReason.MissingStreamReference)
        } else {
            reference.streamUrl
        }
        if (!streamUrl.isHttpUrl()) {
            return PlaybackStreamResult.Failed(PlaybackStreamFailureReason.InvalidServerUrl)
        }

        return PlaybackStreamResult.Resolved(
            PlaybackStream(
                providerId = request.providerId,
                mediaId = request.mediaId,
                mediaType = request.mediaType,
                providerStableKey = providerStableKey,
                url = streamUrl,
            ),
        )
    }

    private fun resolveXtream(
        request: PlaybackStreamRequest,
        credentials: ProviderCredentials.Xtream,
        providerStableKey: String,
        outputFormat: String,
    ): PlaybackStreamResult {
        val serverUrl = credentials.serverUrl.trim().trimEnd('/')
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            return PlaybackStreamResult.Failed(PlaybackStreamFailureReason.InvalidServerUrl)
        }

        val remoteId = request.remoteId.trim()
        if (remoteId.isBlank()) {
            return PlaybackStreamResult.Failed(PlaybackStreamFailureReason.MissingRemoteId)
        }

        val username = credentials.username.pathSegment()
        val password = credentials.password.pathSegment()
        val encodedRemoteId = remoteId.pathSegment()

        val url = when (request.mediaType) {
            MediaType.Channel -> {
                if (request.isCatchupRequest) {
                    val startMillis = request.catchupStartMillis
                    val endMillis = request.catchupEndMillis
                    if (startMillis == null || endMillis == null || endMillis <= startMillis) {
                        return PlaybackStreamResult.Failed(PlaybackStreamFailureReason.InvalidCatchupWindow)
                    }
                    val durationMinutes = ((endMillis - startMillis) / MILLIS_PER_MINUTE)
                        .coerceAtLeast(1L)
                    val start = startMillis.toXtreamCatchupStart().pathSegment()
                    "$serverUrl/timeshift/$username/$password/$durationMinutes/$start/$encodedRemoteId.ts"
                } else {
                    // HLS output (default) → .m3u8 so ExoPlayer can use the server DVR window for native
                    // timeshift/seek; MPEG-TS → .ts (progressive, no native seek).
                    val extension = if (outputFormat == XTREAM_OUTPUT_TS) "ts" else "m3u8"
                    "$serverUrl/live/$username/$password/$encodedRemoteId.$extension"
                }
            }
            MediaType.Movie -> {
                val extension = request.containerExtension.normalizedExtension()
                    ?: return PlaybackStreamResult.Failed(PlaybackStreamFailureReason.MissingContainerExtension)
                "$serverUrl/movie/$username/$password/$encodedRemoteId.$extension"
            }
            MediaType.Episode -> {
                val extension = request.containerExtension.normalizedExtension()
                    ?: return PlaybackStreamResult.Failed(PlaybackStreamFailureReason.MissingContainerExtension)
                "$serverUrl/series/$username/$password/$encodedRemoteId.$extension"
            }
            MediaType.Series -> return PlaybackStreamResult.Failed(PlaybackStreamFailureReason.UnsupportedMediaType)
        }

        return PlaybackStreamResult.Resolved(
            PlaybackStream(
                providerId = request.providerId,
                mediaId = request.mediaId,
                mediaType = request.mediaType,
                providerStableKey = providerStableKey,
                url = url,
            ),
        )
    }

    private fun ProviderStatus.blocksPlayback(): Boolean =
        this == ProviderStatus.Disabled ||
            this == ProviderStatus.InvalidCredentials ||
            this == ProviderStatus.CredentialsRequired ||
            this == ProviderStatus.Expired

    private val PlaybackStreamRequest.isCatchupRequest: Boolean
        get() = catchupStartMillis != null || catchupEndMillis != null

    private fun String.pathSegment(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")

    private fun String?.normalizedExtension(): String? {
        val extension = this?.trim()?.trimStart('.') ?: return null
        if (extension.isBlank() || extension.any { it == '/' || it == '\\' }) return null
        return extension
    }

    private fun PlaybackStreamRequest.resolveM3uCatchupUrl(
        liveUrl: String,
        catchupMode: String?,
        catchupSource: String?,
    ): String? {
        val startMillis = catchupStartMillis
        val endMillis = catchupEndMillis
        if (startMillis == null || endMillis == null || endMillis <= startMillis) return null
        val rendered = catchupSource
            ?.takeIf { it.isNotBlank() }
            ?.replaceM3uCatchupPlaceholders(startMillis, endMillis)
            ?: return null
        return when (catchupMode?.lowercase(Locale.US)) {
            "default" -> rendered
            "append" -> liveUrl + rendered
            else -> null
        }
    }

    private fun String.replaceM3uCatchupPlaceholders(startMillis: Long, endMillis: Long): String {
        val startSeconds = startMillis / 1_000L
        val endSeconds = endMillis / 1_000L
        val durationSeconds = ((endMillis - startMillis) / 1_000L).coerceAtLeast(1L)
        val durationMinutes = (durationSeconds / 60L).coerceAtLeast(1L)
        return mapOf(
            "start" to startSeconds,
            "utc" to startSeconds,
            "end" to endSeconds,
            "timestamp" to endSeconds,
            "lutc" to endSeconds,
            "duration" to durationSeconds,
            "duration_minutes" to durationMinutes,
        ).entries.fold(this) { text, (key, value) ->
            text.replace("\${$key}", value.toString()).replace("{$key}", value.toString())
        }
    }

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://") || startsWith("https://")

    private fun Long.toXtreamCatchupStart(): String =
        XtreamCatchupDateFormat.get()!!.format(Date(this))

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        val XtreamCatchupDateFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }
}
