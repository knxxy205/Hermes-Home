package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.data.model.ServerConfig
import com.example.ui.hermes.ModelPickerDialog
import com.example.ui.navigation.LiquidGlassBottomNav
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.imageio.ImageIO

@RunWith(NavigationTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModelPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightPickerKeepsItsWindowAndSearchFixedAtBothScrollLimits() = checkPicker(false)

    @Test fun darkPickerKeepsItsWindowAndSearchFixedAtBothScrollLimits() = checkPicker(true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun checkPicker(dark: Boolean) {
        val models = List(160) { "models/gemini-test-${it.toString().padStart(3, '0')}" }
        val config = ServerConfig(hermesModel = models[100])
        val visible = mutableStateOf(true)
        var selected: String? = null
        var requests = 0
        var result: Result<List<String>> = Result.success(models)
        compose.setContent {
            MyApplicationTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize()) {
                    if (visible.value) ModelPickerDialog(
                        config, onSelect = { selected = it }, onDismiss = { visible.value = false }, onOpenSettings = {},
                        loadModels = { requests++; result })
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        val picker = compose.onNodeWithTag("model_picker")
        val header = compose.onNodeWithTag("model_picker_header")
        val search = compose.onNodeWithTag("model_search")
        val list = compose.onNodeWithTag("model_list")
        val windowBounds = picker.fetchSemanticsNode().boundsInRoot
        val headerBounds = header.fetchSemanticsNode().boundsInRoot
        val searchBounds = search.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("model_option_${models[100]}").assertIsSelected().assertIsDisplayed()
        list.performScrollToIndex(159)
        repeat(4) {
            list.performTouchInput { swipeUp() }
            assertStableBounds(windowBounds, picker.fetchSemanticsNode().boundsInRoot)
            assertStableBounds(headerBounds, header.fetchSemanticsNode().boundsInRoot)
            assertStableBounds(searchBounds, search.fetchSemanticsNode().boundsInRoot)
        }
        list.performScrollToIndex(0)
        repeat(4) {
            list.performTouchInput { swipeDown() }
            assertStableBounds(windowBounds, picker.fetchSemanticsNode().boundsInRoot)
            assertStableBounds(headerBounds, header.fetchSemanticsNode().boundsInRoot)
        }
        val directory = File("build/outputs/model-picker-fix/screenshots").apply { mkdirs() }
        picker.captureRoboImage(File(directory, if (dark) "picker-dark.png" else "picker-light.png").path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        search.performTextInput("gemini 157")
        compose.onNodeWithTag("model_option_${models[157]}").performClick()
        assertEquals(models[157], selected)
        search.performTextReplacement("no-such-model")
        compose.onNodeWithText("Tidak ada nama model yang cocok. Coba kata lain.").assertIsDisplayed()
        compose.onNodeWithTag("clear_model_search").performClick()
        compose.onNodeWithTag("model_option_${models[100]}").assertIsDisplayed()
        compose.onNodeWithTag("reload_models").performClick()
        compose.runOnIdle { assertEquals(2, requests) }
        assertStableBounds(windowBounds, picker.fetchSemanticsNode().boundsInRoot)

        compose.runOnIdle { result = Result.failure(IllegalStateException("Fixture catalog unavailable")) }
        compose.onNodeWithTag("reload_models").performClick()
        compose.onNodeWithText("Daftar model gagal dimuat").assertIsDisplayed()
        assertStableBounds(windowBounds, picker.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { result = Result.success(emptyList()) }
        compose.onNodeWithText("Coba lagi").performClick()
        compose.onNodeWithText("Server belum menyediakan model.").assertIsDisplayed()
        compose.runOnIdle { result = Result.success(models) }
        compose.onNodeWithTag("reload_models").performClick()
        compose.onNodeWithTag("model_option_${models[100]}").assertIsDisplayed()
        compose.runOnIdle { assertEquals(5, requests) }
        compose.onNodeWithTag("close_model_picker").performClick()
        picker.assertDoesNotExist()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun navigationOuterMarginShowsTheContentBehindTheCapsule() {
        val background = Color(0xFF187B59)
        compose.setContent {
            MyApplicationTheme(darkTheme = false) {
                Box(Modifier.size(393.dp, 200.dp).background(background).testTag("nav_background")) {
                    LiquidGlassBottomNav(AppTab.FILES, {}, Modifier.align(androidx.compose.ui.Alignment.BottomCenter), animationsEnabled = false)
                }
            }
        }
        val output = File("build/outputs/model-picker-fix/screenshots/navigation-margin.png")
        output.parentFile?.mkdirs()
        compose.onNodeWithTag("nav_background").captureRoboImage(output.path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val image = ImageIO.read(output)
        assertEquals(background.toArgb(), image.getRGB(0, image.height - 30))
        assertEquals(background.toArgb(), image.getRGB(image.width - 1, image.height - 30))
        compose.onNodeWithTag("tab_files").assertIsSelected()
    }

    private fun assertStableBounds(before: Rect, after: Rect) {
        // Native window inset rounding can change a measured edge by one physical pixel.
        assertEquals(before.left, after.left, 1f)
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.right, after.right, 1f)
        assertEquals(before.bottom, after.bottom, 1f)
    }
}
