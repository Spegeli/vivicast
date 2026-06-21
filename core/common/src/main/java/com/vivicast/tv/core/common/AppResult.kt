package com.vivicast.tv.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

data class AppError(
    val code: String,
    val message: String,
    val cause: Throwable? = null,
)
