package com.vivicast.tv.search

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import com.vivicast.tv.VivicastApplication
import com.vivicast.tv.data.media.AndroidTvSearchSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class AndroidTvSearchSuggestionProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val query = selectionArgs?.firstOrNull()
            ?: uri.pathSegments.lastOrNull()
                ?.takeUnless { it == SearchManager.SUGGEST_URI_PATH_QUERY }
            ?: ""
        val limit = uri.getQueryParameter("limit")?.toIntOrNull()?.coerceIn(1, MAX_RESULTS)
            ?: DEFAULT_RESULTS
        val entries = runBlocking(Dispatchers.IO) {
            val appContainer = (context?.applicationContext as VivicastApplication).appContainer
            // Fail-closed: if the current PIN protection state can't be read, hide everything protectable.
            val protection = runCatching { appContainer.pinSecurityStateStore.read() }.getOrNull()
            appContainer.mediaRepository.searchAndroidTvSuggestions(
                query = query,
                limit = limit,
                protectMovies = protection?.protectMovies ?: true,
                protectSeries = protection?.protectSeries ?: true,
                protectAdultContent = protection?.protectAdultContent ?: true,
            )
        }

        return MatrixCursor(SUGGESTION_COLUMNS).apply {
            entries.forEachIndexed { index, entry -> addRow(entry.toSuggestionRow(index)) }
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private companion object {
        const val DEFAULT_RESULTS = 20
        const val MAX_RESULTS = 50

        val SUGGESTION_COLUMNS = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_IS_LIVE,
        )
    }
}

private fun AndroidTvSearchSuggestion.toSuggestionRow(index: Int): Array<Any?> =
    arrayOf(
        index,
        title,
        subtitle,
        imageUrl,
        Intent.ACTION_VIEW,
        deepLink,
        if (mediaType == "CHANNEL") 1 else 0,
    )
