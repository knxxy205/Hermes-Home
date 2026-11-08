package com.example.ui.navigation

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import com.example.AppTab
import kotlin.math.roundToInt

@Composable
internal fun rememberNavigationAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun readEnabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var enabled by remember(resolver) { mutableStateOf(readEnabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = readEnabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        enabled = readEnabled()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

@Composable
internal fun Modifier.navigationPage(
    tab: AppTab,
    transition: Transition<AppTab>,
    animationsEnabled: Boolean
): Modifier {
    val duration = if (animationsEnabled) 280 else 0
    val opacity = transition.animateFloat(
        transitionSpec = { tween(duration, easing = FastOutSlowInEasing) }, label = "${tab.tag}-opacity"
    ) { if (it == tab) 1f else 0f }
    val horizontal = transition.animateFloat(
        transitionSpec = { tween(duration, easing = FastOutSlowInEasing) }, label = "${tab.tag}-slide"
    ) { selected -> if (selected == tab) 0f else if (selected.ordinal > tab.ordinal) -18f else 18f }
    val density = LocalDensity.current.density
    val visible = transition.targetState == tab || transition.currentState == tab
    val active = transition.targetState == tab
    return this
        .zIndex(if (active) 1f else 0f)
        .then(if (active) Modifier else Modifier.clearAndSetSemantics {})
        .layout { measurable, constraints ->
            if (visible) {
                val page = measurable.measure(constraints)
                layout(page.width, page.height) { page.placeRelative(0, 0, if (active) 1f else 0f) }
            } else layout(0, 0) {}
        }
        .graphicsLayer {
            alpha = opacity.value
            translationX = horizontal.value * density
        }
}

internal fun navigationTabAt(x: Float, width: Float, count: Int, rtl: Boolean): Int {
    if (width <= 0f || count <= 1) return 0
    val physical = (x / width * count).toInt().coerceIn(0, count - 1)
    return if (rtl) count - 1 - physical else physical
}

internal fun nearestNavigationTab(position: Float, count: Int): Int =
    position.roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))

internal fun Modifier.navigationContentPadding(navigationHeight: Dp, ime: WindowInsets): Modifier =
    windowInsetsPadding(ime.union(WindowInsets(bottom = navigationHeight)))
