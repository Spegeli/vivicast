package com.vivicast.core.network

data class HttpRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap()
)

sealed interface NetworkResult<out T> {
    data class Success<T>(val value: T) : NetworkResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : NetworkResult<Nothing>
}
