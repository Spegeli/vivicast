package com.vivicast.tv.core.database

import java.security.MessageDigest

/**
 * SHA-256 hex over the given content parts. Used to detect whether a staged row's content differs from the
 * live row in the delta-merge. Content parts only — never include user-state, createdAt, or updatedAt.
 * See plans/nonblocking-db-imports.md.
 *
 * Parts are length-prefixed ("<len>:<value>;", null -> "N;") before hashing, so field boundaries stay
 * unambiguous regardless of the field contents ("a" + "b" != "ab") and an absent field never collides with
 * an empty-string one — without depending on any separator character that could occur in the data.
 */
fun syncFingerprint(vararg parts: Any?): String {
    val builder = StringBuilder()
    for (part in parts) {
        if (part == null) {
            builder.append("N;")
        } else {
            val value = part.toString()
            builder.append(value.length).append(':').append(value).append(';')
        }
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(builder.toString().toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { "%02x".format(it) }
}
