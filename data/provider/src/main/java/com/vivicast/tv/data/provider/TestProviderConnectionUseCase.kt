package com.vivicast.tv.data.provider

import com.vivicast.tv.domain.model.ProviderType
import com.vivicast.tv.iptv.m3u.M3uParser
import com.vivicast.tv.iptv.xtream.XtreamClient
import com.vivicast.tv.iptv.xtream.XtreamCredentials
import com.vivicast.tv.iptv.xtream.XtreamParser

/**
 * Verifies that a provider's connection details actually resolve to a usable playlist / API before
 * the provider is saved. Pure data layer: it takes the M3U parser, the Xtream client and a text
 * fetcher abstraction (so it needs no OkHttp / worker / Android dependency). Network HTTP errors from
 * [fetchText] / [xtreamClient] propagate unchanged, and validation/format problems are signalled via
 * [IllegalArgumentException] / [ProviderConnectionResponseException]. The App layer maps those
 * throwables to the localized user message (behaviour unchanged from the previous AppContainer code).
 */
class TestProviderConnectionUseCase(
    private val m3uParser: M3uParser,
    private val xtreamClient: XtreamClient,
    private val xtreamParser: XtreamParser,
    private val fetchText: suspend (url: String) -> String,
    private val m3uContentSummarizer: M3uContentSummarizer = M3uContentSummarizer(),
) {
    /**
     * Throws on any failure; on success returns the M3U content breakdown (channels/movies/series) so
     * the caller can preview it — same counts as the file check. Xtream has no such preview: returns null.
     */
    suspend fun test(request: ProviderCreateRequest): M3uContentSummary? =
        when (request.type) {
            ProviderType.M3u -> testM3u(request)
            ProviderType.Xtream -> {
                testXtream(request)
                null
            }
        }

    private suspend fun testM3u(request: ProviderCreateRequest): M3uContentSummary {
        val source = if (request.m3uSourceMode.isAutomaticallyRefreshable) {
            val url = request.m3uUrl?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException()
            fetchText(url)
        } else {
            request.m3uContent?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException()
        }
        val channels = m3uParser.parse(source).channels
        if (channels.isEmpty()) {
            throw ProviderConnectionResponseException()
        }
        return m3uContentSummarizer.summarizeChannels(channels)
    }

    private suspend fun testXtream(request: ProviderCreateRequest) {
        val credentials = XtreamCredentials(
            serverUrl = request.xtreamServerUrl?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException(),
            username = request.xtreamUsername?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException(),
            password = request.xtreamPassword?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException(),
        )
        if (!(request.includeLiveTv || request.includeMovies || request.includeSeries)) {
            throw IllegalArgumentException()
        }
        // Canonical Xtream login check: player_api.php (no action) returns user_info.auth.
        val userInfo = xtreamParser.parseUserInfo(xtreamClient.getUserInfo(credentials))
        if (!userInfo.authenticated) {
            throw ProviderInvalidCredentialsException()
        }
    }
}

/** Signals that the source responded but the payload was empty / not in a usable format. */
class ProviderConnectionResponseException : RuntimeException()

/** Xtream server accepted the request but reported the credentials as invalid (user_info.auth != 1). */
class ProviderInvalidCredentialsException : RuntimeException()
