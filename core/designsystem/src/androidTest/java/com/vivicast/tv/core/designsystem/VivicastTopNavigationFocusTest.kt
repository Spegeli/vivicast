package com.vivicast.tv.core.designsystem

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Rule
import org.junit.Test

class VivicastTopNavigationFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun focusedNavItemBecomesSelectedMainArea() {
        var selectedIndex by mutableIntStateOf(0)

        compose.setContent {
            VivicastTopNavigation(
                brand = "VIVICAST",
                items = TEST_ITEMS,
                selectedIndex = selectedIndex,
                onSelected = { selectedIndex = it },
                onFocused = { selectedIndex = it },
            )
        }

        compose.onNodeWithTag(topNavItemTag("Suche")).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(timeoutMillis = 5_000) { selectedIndex == TEST_ITEMS.indexOf("Suche") }
        compose.onNodeWithTag(topNavItemTag("Suche")).assertIsFocused()

        compose.onNodeWithTag(topNavItemTag("Filme")).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(timeoutMillis = 5_000) { selectedIndex == TEST_ITEMS.indexOf("Filme") }
    }
}

private val TEST_ITEMS = listOf("Live-TV", "Filme", "Serien", "Suche", "Einstellungen")
