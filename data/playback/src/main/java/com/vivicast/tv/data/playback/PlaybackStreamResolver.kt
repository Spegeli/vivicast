package com.vivicast.tv.data.playback

import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderRepository
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
    MissingContainerExtension,
    InvalidServerUrl,
}

class DefaultPlaybackStreamResolver(
    private val providerRepository: ProviderRepository,
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
                    resolveXtream(request, credentials)
                }
            }
            ProviderType.M3u -> {
                if (credentials !is ProviderCredentials.M3u) {
                    PlaybackStreamResult.Failed(PlaybackStreamFailureReason.CredentialTypeMismatch)
                } else {
                    PlaybackStreamResult.Failed(PlaybackStreamFailureReason.UnsupportedProvider)
                }
            }
        }
    }

    private fun resolveXtream(
        request: PlaybackStreamRequest,
        credentials: ProviderCredentials.Xtream,
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
                if (request.catchupStartMillis != null && request.catchupEndMillis != null) {
                    val durationMinutes = ((request.catchupEndMillis - request.catchupStartMillis) / MILLIS_PER_MINUTE)
                        .coerceAtLeast(1L)
                    val start = request.catchupStartMillis.toXtreamCatchupStart().pathSegment()
                    "$serverUrl/timeshift/$username/$password/$durationMinutes/$start/$encodedRemoteId.ts"
                } else {
                    "$serverUrl/live/$username/$password/$encodedRemoteId.ts"
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
                url = url,
            ),
        )
    }

    private fun ProviderStatus.blocksPlayback(): Boolean =
        this == ProviderStatus.Disabled ||
            this == ProviderStatus.InvalidCredentials ||
            this == ProviderStatus.Expired

    private fun String.pathSegment(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")

    private fun String?.normalizedExtension(): String? {
        val extension = this?.trim()?.trimStart('.') ?: return null
        if (extension.isBlank() || extension.any { it == '/' || it == '\\' }) return null
        return extension
    }

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
