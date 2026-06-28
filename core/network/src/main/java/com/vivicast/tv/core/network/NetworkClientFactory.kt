package com.vivicast.tv.core.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit

class NetworkClientFactory {
    fun createOkHttpClient(
        userAgentProvider: () -> String = { DEFAULT_USER_AGENT },
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
                .newBuilder()
                .header(USER_AGENT_HEADER, userAgentProvider().normalizedUserAgent())
                .build()
            chain.proceed(request)
        }
        .build()

    fun createRetrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .build()
}

private fun String.normalizedUserAgent(): String =
    trim().takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT

private const val USER_AGENT_HEADER = "User-Agent"
private const val DEFAULT_USER_AGENT = "Vivicast/1.0"
