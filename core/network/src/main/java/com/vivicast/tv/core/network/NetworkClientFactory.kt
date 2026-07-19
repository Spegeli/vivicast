package com.vivicast.tv.core.network

import android.util.Log
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

class NetworkClientFactory {
    /**
     * @param trustAllCertificates DEBUG-ONLY escape hatch. When true, TLS certificate/hostname
     * validation is disabled so the emulator can reach hosts whose (otherwise valid) chain its trust
     * store rejects. Callers MUST gate this on `BuildConfig.DEBUG`; in release builds the flag is a
     * compile-time `false`, so this branch is dead code and can never ship. Never pass `true`
     * unconditionally.
     */
    fun createOkHttpClient(
        userAgentProvider: () -> String = { DEFAULT_USER_AGENT },
        trustAllCertificates: Boolean = false,
        // Called only on a failing request (non-2xx or IOException) with host + code/duration — never a
        // path/query. The sink itself gates on the diagnostics toggle, so the happy path stays untouched.
        networkEventLogger: NetworkEventLogger? = null,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // Explicit timeouts: dead host fails at connect; a stalled but trickling
            // transfer trips the read timeout instead of hanging. No callTimeout on
            // purpose — it would kill legitimate large playlist/EPG downloads.
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                // A caller may set a per-request User-Agent (e.g. a per-provider override); only fall
                // back to the global User-Agent when the request doesn't already carry one.
                val request = if (original.header(USER_AGENT_HEADER) == null) {
                    original.newBuilder()
                        .header(USER_AGENT_HEADER, userAgentProvider().normalizedUserAgent())
                        .build()
                } else {
                    original
                }
                chain.proceed(request)
            }
        if (networkEventLogger != null) {
            builder.addInterceptor { chain ->
                val request = chain.request()
                val startNs = System.nanoTime()
                try {
                    chain.proceed(request).also { response ->
                        if (!response.isSuccessful) {
                            networkEventLogger(request.url.host, response.code, elapsedMs(startNs), null)
                        }
                    }
                } catch (e: IOException) {
                    networkEventLogger(request.url.host, null, elapsedMs(startNs), e.javaClass.simpleName)
                    throw e
                }
            }
        }
        if (trustAllCertificates) {
            builder.applyInsecureTrustManager()
        }
        return builder.build()
    }

    // Debug-only: trust every certificate/hostname. Lint's TLS warnings are suppressed on purpose;
    // this is reachable only when a BuildConfig.DEBUG-gated caller opts in.
    @Suppress("TrustAllX509TrustManager", "BadHostnameVerifier", "CustomX509TrustManager")
    private fun OkHttpClient.Builder.applyInsecureTrustManager() {
        Log.w(
            "NetworkClientFactory",
            "TLS certificate + hostname validation DISABLED (debug build). Do not ship this.",
        )
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        }
        sslSocketFactory(sslContext.socketFactory, trustAll)
        hostnameVerifier { _, _ -> true }
    }
}

/** host, HTTP status (null for a transport/IO failure), elapsed ms, error class name (null for non-2xx). */
typealias NetworkEventLogger = (host: String, statusCode: Int?, durationMs: Long, error: String?) -> Unit

private fun elapsedMs(startNs: Long): Long = (System.nanoTime() - startNs) / 1_000_000

private fun String.normalizedUserAgent(): String =
    trim().takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT

private const val USER_AGENT_HEADER = "User-Agent"
private const val DEFAULT_USER_AGENT = "Vivicast/1.0"
private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val READ_TIMEOUT_SECONDS = 30L
private const val WRITE_TIMEOUT_SECONDS = 30L
