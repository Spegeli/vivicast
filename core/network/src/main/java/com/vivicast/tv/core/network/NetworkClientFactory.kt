package com.vivicast.tv.core.network

import android.util.Log
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit

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
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .header(USER_AGENT_HEADER, userAgentProvider().normalizedUserAgent())
                    .build()
                chain.proceed(request)
            }
        if (trustAllCertificates) {
            builder.applyInsecureTrustManager()
        }
        return builder.build()
    }

    fun createRetrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .build()

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

private fun String.normalizedUserAgent(): String =
    trim().takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT

private const val USER_AGENT_HEADER = "User-Agent"
private const val DEFAULT_USER_AGENT = "Vivicast/1.0"
