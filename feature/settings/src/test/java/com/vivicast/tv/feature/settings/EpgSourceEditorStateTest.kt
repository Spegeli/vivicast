package com.vivicast.tv.feature.settings

import com.vivicast.tv.domain.model.EpgSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgSourceEditorStateTest {

    private fun EpgSourceEditorState.validate() = validationMessage(msgNameMissing = "name", msgUrlMissing = "url")

    private fun source() = EpgSource(id = "s1", name = "EPG", sourceConfigKey = "key", timeShiftMinutes = 0, isActive = true)

    @Test
    fun addMode_blankUrl_isRequiredMissing() {
        val state = EpgSourceEditorState.newSource().copy(name = "N")
        assertTrue(state.urlRequiredMissing)
        assertEquals("url", state.validate())
    }

    @Test
    fun editMode_failedPrefill_blankUrlNotMissing_keepsStored() {
        // Async pre-fill returned blank (read failure) but the source has a stored URL -> not "missing".
        val state = EpgSourceEditorState.from(source(), url = "").copy(name = "N")
        assertFalse(state.urlRequiredMissing)
        assertNull(state.validate())
    }

    @Test
    fun editMode_userClearsUrl_isRequiredMissing() {
        // Editing the field flips hasExistingUrl off; a now-blank field is a required-field error.
        val state = EpgSourceEditorState.from(source(), url = "http://epg").copy(name = "N", url = "", hasExistingUrl = false)
        assertTrue(state.urlRequiredMissing)
        assertEquals("url", state.validate())
    }
}
