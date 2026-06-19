package com.vivicast.core.model

data class XtreamCredentials(
    val baseUrl: String,
    val username: String,
    val password: String,
    val userAgent: String? = null,
    val outputFormat: XtreamOutputFormat = XtreamOutputFormat.Ts
)

enum class XtreamOutputFormat(val wireValue: String, val streamExtension: String) {
    Ts("ts", "ts"),
    Hls("m3u8", "m3u8")
}
