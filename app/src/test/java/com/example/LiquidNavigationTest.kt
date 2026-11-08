package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.ui.navigation.LiquidGlassBottomNav
import com.example.ui.navigation.navigationTabAt
import com.example.ui.navigation.nearestNavigationTab
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.model.FrameworkMethod
import org.robolectric.RobolectricTestRunner
import org.robolectric.internal.bytecode.InstrumentationConfiguration
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(NavigationTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hitTestingClampsEdgesAndMirrorsRtl() {
        assertEquals(0, navigationTabAt(-20f, 320f, 4, false))
        assertEquals(3, navigationTabAt(400f, 320f, 4, false))
        assertEquals(1, navigationTabAt(100f, 320f, 4, false))
        assertEquals(2, navigationTabAt(100f, 320f, 4, true))
        assertEquals(0, navigationTabAt(10f, 0f, 4, false))
        assertEquals(3, nearestNavigationTab(2.51f, 4))
        assertEquals(2, nearestNavigationTab(2.49f, 4))
        assertEquals(3, nearestNavigationTab(9f, 4))
        assertEquals(0, nearestNavigationTab(-2f, 4))
    }

    @Test fun fallbackTapAndDragCommitOnlyOnRelease() {
        val selected = mutableStateOf(AppTab.TERMINAL)
        val changes = mutableListOf<AppTab>()
        compose.setContent {
            MyApplicationTheme {
                Box(Modifier.width(393.dp)) {
                    LiquidGlassBottomNav(selected.value, { selected.value = it; changes.add(it) }, darkTheme = false)
                }
            }
        }
        compose.onNodeWithTag("tab_files").performClick().assertIsSelected()
        assertEquals(listOf(AppTab.FILES), changes)
        capture("light-fallback")
        val bar = compose.onNodeWithTag("liquid_bottom_nav")
        bar.performTouchInput {
            down(Offset(width * .375f, height * .5f))
            moveTo(Offset(width * .86f, height * .5f), delayMillis = 240)
        }
        compose.onNodeWithTag("tab_files").assertIsSelected()
        assertEquals(1, changes.size)
        val draggedCenter = compose.onNodeWithTag("liquid_tab_indicator").fetchSemanticsNode().boundsInRoot.center.x
        val filesCenter = compose.onNodeWithTag("tab_files").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("Indicator follows the finger before page selection", draggedCenter > filesCenter)
        bar.performTouchInput { up() }
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        assertEquals(listOf(AppTab.FILES, AppTab.SETTINGS), changes)
        assertEquals(compose.onNodeWithTag("tab_settings").fetchSemanticsNode().boundsInRoot.center.x,
            compose.onNodeWithTag("liquid_tab_indicator").fetchSemanticsNode().boundsInRoot.center.x, 2f)
        bar.performTouchInput {
            down(Offset(width * .875f, height * .5f))
            moveTo(Offset(width * .13f, height * .5f), delayMillis = 240)
            up()
        }
        compose.onNodeWithTag("tab_terminal").assertIsSelected()
        bar.performTouchInput {
            down(Offset(width * .125f, height * .5f))
            moveTo(Offset(width * .875f, height * .5f), delayMillis = 200)
        }
        bar.performTouchInput { cancel() }
        compose.onNodeWithTag("tab_terminal").assertIsSelected()
        assertEquals(3, changes.size)
    }

    @Test fun darkFallbackWorksWithAnimationsDisabled() {
        val selected = mutableStateOf(AppTab.TERMINAL)
        compose.setContent {
            MyApplicationTheme {
                Box(Modifier.width(320.dp)) {
                    LiquidGlassBottomNav(selected.value, { selected.value = it }, darkTheme = true, animationsEnabled = false)
                }
            }
        }
        compose.onNodeWithTag("tab_hermes").performClick().assertIsSelected()
        compose.onNodeWithTag("tab_settings").performClick().assertIsSelected()
        capture("dark-fallback")
        compose.onNodeWithTag("liquid_tab_indicator").assertExists()
        compose.onNodeWithTag("liquid_bottom_nav").performTouchInput {
            down(Offset(width * .875f, height * .5f))
            moveTo(Offset(width * .125f, height * .5f), delayMillis = 120)
            up()
        }
        compose.onNodeWithTag("tab_terminal").assertIsSelected()
    }

    @Test
    @Config(sdk = [24])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun android7FallbackSupportsSliding() {
        val selected = mutableStateOf(AppTab.TERMINAL)
        compose.setContent {
            MyApplicationTheme {
                Box(Modifier.width(320.dp)) {
                    LiquidGlassBottomNav(selected.value, { selected.value = it }, animationsEnabled = false)
                }
            }
        }
        compose.onNodeWithTag("liquid_bottom_nav").performTouchInput {
            down(Offset(width * .125f, height * .5f))
            moveTo(Offset(width * .875f, height * .5f), delayMillis = 200)
            up()
        }
        compose.onNodeWithTag("tab_settings").assertIsSelected()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        val output = File("build/outputs/liquid-glass").apply { mkdirs() }
        compose.onNodeWithTag("liquid_bottom_nav").captureRoboImage(
            filePath = File(output, "$name.png").path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record)
        )
    }
}

class NavigationTestRunner(testClass: Class<*>) : RobolectricTestRunner(testClass) {
    override fun createClassLoaderConfig(method: FrameworkMethod): InstrumentationConfiguration =
        InstrumentationConfiguration.Builder(super.createClassLoaderConfig(method))
            // Native font loading discovers MINA's filesystem provider through Java's SPI.
            .doNotAcquirePackage("org.apache.sshd.")
            .build()
}
