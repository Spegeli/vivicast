package com.vivicast.tv.domain

fun interface UseCase<in Parameters, out Result> {
    suspend operator fun invoke(parameters: Parameters): Result
}
