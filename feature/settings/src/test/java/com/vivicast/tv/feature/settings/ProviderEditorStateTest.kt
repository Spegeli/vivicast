package com.vivicast.tv.feature.settings

import com.vivicast.tv.data.provider.M3uSourceMode
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the "at least one content type" rule that the inline add/edit form now enforces for every
 * source type (previously Xtream-only). This mirrors the repository's `validateProviderOptions`, so
 * an M3U provider with no content type selected is caught in the UI instead of failing the save.
 */
class ProviderEditorStateTest {

    private fun ProviderEditorState.validate() = validationMessage(
        requireConnectionTest = false,
        msgNameMissing = "name",
        msgContentType = "content",
        msgXtreamServer = "server",
        msgXtreamUser = "user",
        msgXtreamPass = "pass",
        msgConnTest = "conntest",
        msgM3uUrl = "m3uurl",
        msgM3uFile = "m3ufile",
    )

    @Test
    fun m3uUrl_withNoContentTypeSelected_requiresContentType() {
        val state = ProviderEditorState.newProvider(ProviderType.M3u).copy(
            name = "List",
            m3uSourceMode = M3uSourceMode.Url,
            m3uUrl = "http://x",
            includeLiveTv = false,
            includeMovies = false,
            includeSeries = false,
        )

        assertEquals("content", state.validate())
    }

    @Test
    fun m3uUrl_withOneContentType_passesValidation() {
        val state = ProviderEditorState.newProvider(ProviderType.M3u).copy(
            name = "List",
            m3uSourceMode = M3uSourceMode.Url,
            m3uUrl = "http://x",
            includeLiveTv = true,
            includeMovies = false,
            includeSeries = false,
        )

        assertNull(state.validate())
    }

    @Test
    fun m3uEdit_unchangedSource_isSourceUnchanged_thenFalseAfterEdit() {
        val state = ProviderEditorState.from(providerFixture(ProviderType.M3u), ProviderCredentials.M3u(url = "http://list"))
        assertTrue(state.isSourceUnchanged)
        // Editing the URL (which flips m3uHasExistingSource off) changes the source signature.
        assertFalse(state.copy(m3uUrl = "http://other", m3uHasExistingSource = false).isSourceUnchanged)
    }

    @Test
    fun xtreamEdit_changedPassword_isSourceUnchangedFalse() {
        val state = ProviderEditorState.from(
            providerFixture(ProviderType.Xtream),
            ProviderCredentials.Xtream(serverUrl = "http://s", username = "u", password = "p"),
        )
        assertTrue(state.isSourceUnchanged)
        assertFalse(state.copy(xtreamPassword = "p2").isSourceUnchanged)
    }

    @Test
    fun newProvider_isSourceUnchangedFalse() {
        assertFalse(ProviderEditorState.newProvider(ProviderType.M3u).isSourceUnchanged)
    }

    @Test
    fun xtream_withNoContentTypeSelected_requiresContentType() {
        val state = ProviderEditorState.newProvider(ProviderType.Xtream).copy(
            name = "X",
            xtreamServerUrl = "http://s",
            xtreamUsername = "u",
            xtreamPassword = "p",
            includeLiveTv = false,
            includeMovies = false,
            includeSeries = false,
        )

        assertEquals("content", state.validate())
    }

    private fun providerFixture(type: ProviderType) = Provider(
        id = "p1", name = "P", type = type, sourceConfigKey = "key", isActive = true,
        status = ProviderStatus.Active, includeLiveTv = true, includeMovies = false, includeSeries = false,
        refreshIntervalHours = 0, logoPriority = "provider", createdAt = 1L, updatedAt = 1L,
    )
}
