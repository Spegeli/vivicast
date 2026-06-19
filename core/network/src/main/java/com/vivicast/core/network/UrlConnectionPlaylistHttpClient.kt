package com.vivicast.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

class UrlConnectionPlaylistHttpClient(
    private val connectTimeoutMillis: Int = 12_000,
    private val readTimeoutMillis: Int = 20_000
) : PlaylistHttpClient {
    override suspend fun getText(request: HttpRequest): NetworkResult<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = openConnection(request)

                connection.use {
                    val status = responseCode
                    if (status !in 200..299) {
                        throw IOException("HTTP $status")
                    }
                    responseStream().bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
                }
            }.fold(
                onSuccess = { content ->
                    if (content.isBlank()) {
                        NetworkResult.Failure("Playlist is empty")
                    } else {
                        NetworkResult.Success(content)
                    }
                },
                onFailure = { error ->
                    NetworkResult.Failure(error.message ?: "Could not download playlist", error)
                }
            )
        }
    }

    suspend fun <T> readTextStream(
        request: HttpRequest,
        block: suspend (Reader) -> T
    ): NetworkResult<T> {
        return withContext(Dispatchers.IO) {
            val value = try {
                val connection = openConnection(request)
                connection.use {
                    val status = responseCode
                    if (status !in 200..299) {
                        throw IOException("HTTP $status")
                    }
                    responseStream().bufferedReader(Charsets.UTF_8).use { reader ->
                        block(reader)
                    }
                }
            } catch (error: Throwable) {
                return@withContext NetworkResult.Failure(error.message ?: "Could not download content", error)
            }
            NetworkResult.Success(value)
        }
    }

    private fun openConnection(request: HttpRequest): HttpURLConnection {
        return (URL(request.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/x-mpegURL, application/vnd.apple.mpegurl, application/xml, text/xml, text/plain, */*")
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("User-Agent", "ViviCast/0.1 Android TV")
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
    }

    private fun HttpURLConnection.responseStream(): InputStream {
        val stream = inputStream.bufferedForPeek()
        stream.mark(2)
        val first = stream.read()
        val second = stream.read()
        stream.reset()

        val encoding = contentEncoding.orEmpty().lowercase()
        return when {
            encoding == "gzip" || isGzipHeader(first, second) -> GZIPInputStream(stream)
            encoding == "deflate" || isZlibHeader(first, second) -> InflaterInputStream(stream)
            else -> stream
        }
    }

    private fun InputStream.bufferedForPeek(): BufferedInputStream {
        return this as? BufferedInputStream ?: BufferedInputStream(this)
    }

    private fun isGzipHeader(first: Int, second: Int): Boolean {
        return first == 0x1f && second == 0x8b
    }

    private fun isZlibHeader(first: Int, second: Int): Boolean {
        if (first < 0 || second < 0) return false
        return first and 0x0f == 8 && ((first shl 8) + second) % 31 == 0
    }
}

private inline fun <T> HttpURLConnection.use(block: HttpURLConnection.() -> T): T {
    return try {
        block()
    } finally {
        disconnect()
    }
}
