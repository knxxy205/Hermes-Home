package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AppPreferences
import com.example.data.sftp.RemoteFileItem
import com.example.ui.files.FileItemsList
import com.example.ui.navigation.LiquidGlassBottomNav
import com.example.ui.navigation.navigationContentPadding
import com.example.ui.settings.appPreferenceItems
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(NavigationTestRunner::class)
@Config(sdk = [28], qualifiers = "w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PerformanceInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun indicatorFollowsTheFingerOnTheNextFrameWithoutWaitingForASpring() {
        val selected = mutableStateOf(AppTab.TERMINAL)
        compose.setContent {
            MyApplicationTheme {
                Box(Modifier.width(393.dp)) {
                    LiquidGlassBottomNav(selected.value, { selected.value = it })
                }
            }
        }
        compose.mainClock.autoAdvance = false
        val indicator = compose.onNodeWithTag("liquid_tab_indicator")
        val before = indicator.fetchSemanticsNode().boundsInRoot.center.x
        val bar = compose.onNodeWithTag("liquid_bottom_nav")
        val barWidth = bar.fetchSemanticsNode().boundsInRoot.width
        bar.performTouchInput {
            down(Offset(width * .125f, height * .5f))
            moveTo(Offset(width * .525f, height * .5f), delayMillis = 8)
        }
        compose.mainClock.advanceTimeByFrame()
        val moved = indicator.fetchSemanticsNode().boundsInRoot.center.x - before
        assertEquals(barWidth * .4f, moved, 3f)
        compose.onNodeWithTag("tab_terminal").assertIsSelected()
        bar.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("tab_hermes").assertIsSelected()
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test fun keyboardInsetsDoNotRecomposeContentOrDropNavigationClearanceAtTheFirstFrame() {
        val ime = MutableWindowInsets(WindowInsets(0, 0, 0, 0))
        var contentCompositions = 0
        compose.setContent {
            Box(Modifier.size(320.dp, 600.dp).navigationContentPadding(80.dp, ime)) {
                SideEffect { contentCompositions++ }
                Box(Modifier.fillMaxSize().testTag("keyboard_content"))
            }
        }
        fun height() = compose.onNodeWithTag("keyboard_content").fetchSemanticsNode().boundsInRoot.height
        val original = height()
        val initialCompositions = contentCompositions
        compose.runOnIdle { ime.insets = WindowInsets(bottom = 8.dp) }
        assertEquals(original, height(), .1f)
        compose.runOnIdle { ime.insets = WindowInsets(bottom = 200.dp) }
        val keyboardOpen = height()
        assertTrue(keyboardOpen < original)
        compose.runOnIdle { ime.insets = WindowInsets(bottom = 204.dp) }
        assertEquals(original / 520f * 4f, keyboardOpen - height(), .5f)
        compose.runOnIdle { ime.insets = WindowInsets(0, 0, 0, 0) }
        assertEquals(original, height(), .1f)
        assertEquals(initialCompositions, contentCompositions)
    }

    @Test fun largeFileListStaysLazyAndSelectionAndActionsSurviveScrolling() {
        val entries = List(1500) { index ->
            RemoteFileItem("file-$index.txt", "/files/file-$index.txt", false, 1024L * index,
                "-rw-r--r--", 1735689600L)
        }
        val selected = mutableStateOf(emptySet<String>())
        var inspected: RemoteFileItem? = null
        compose.setContent {
            MyApplicationTheme(darkTheme = true) {
                Box(Modifier.size(393.dp, 700.dp)) {
                    FileItemsList(entries, selected.value, false, 0.dp,
                        onOpen = { selected.value = selected.value + it.path },
                        onLongClick = { selected.value = selected.value + it.path },
                        onRename = {}, onDelete = {}, onDownload = {}, onInfo = { inspected = it })
                }
            }
        }
        val rows = hasTestTagPrefix("file_item_")
        assertTrue(compose.onAllNodes(rows).fetchSemanticsNodes().size < 30)
        compose.onNodeWithTag("files_list").performScrollToIndex(1201)
        compose.onNodeWithContentDescription("Tindakan untuk file-1200.txt").performClick()
        compose.onNodeWithText("Informasi file").performClick()
        assertEquals(entries[1200], inspected)
        compose.onNodeWithTag("file_item_file-1200.txt").performTouchInput { longClick() }
        assertEquals(setOf(entries[1200].path), selected.value)
        compose.onNodeWithTag("files_list").performScrollToIndex(1)
        compose.onNodeWithTag("file_item_file-0.txt").performClick()
        assertEquals(setOf(entries[0].path, entries[1200].path), selected.value)
    }

    @Test fun lazyPreferencesKeepChangesWhenSectionsLeaveTheViewport() {
        val preferences = mutableStateOf(AppPreferences())
        compose.setContent {
            MyApplicationTheme {
                LazyColumn(Modifier.size(393.dp, 650.dp).testTag("preferences_list")) {
                    appPreferenceItems(preferences.value, { preferences.value = it })
                }
            }
        }
        compose.onNodeWithTag("app_theme_picker").performClick()
        compose.onNodeWithTag("app_theme_dark").performClick()
        compose.onNodeWithTag("preferences_list").performScrollToIndex(3)
        compose.onNodeWithTag("hermes_instructions").performScrollTo().performTextInput("Jelaskan sebelum menjalankan")
        compose.onNodeWithTag("preferences_list").performScrollToIndex(0)
        compose.onNodeWithTag("app_theme_picker").assertTextContains("Gelap")
        assertEquals("Jelaskan sebelum menjalankan", preferences.value.hermesInstructions)
        compose.onNodeWithTag("preferences_list").performScrollToIndex(4)
        compose.onNodeWithTag("reset_app_preferences").performClick()
        assertEquals(AppPreferences(), preferences.value)
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("test tag starts with $prefix") {
        it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith(prefix)
    }
}
