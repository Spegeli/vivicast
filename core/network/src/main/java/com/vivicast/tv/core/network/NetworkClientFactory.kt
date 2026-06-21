package com.vivicast.tv.core.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit

class NetworkClientFactory {
    fun createOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    fun createRetrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .build()
}
