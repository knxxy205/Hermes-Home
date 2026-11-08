package com.example.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AppTab
import com.example.ui.i18n.tr
import com.example.ui.theme.IsDarkAppearance
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

private class GlassNavMotion(index: Int) {
    val position = Animatable(index.toFloat())
    val press = Animatable(0f)
    var target by mutableFloatStateOf(index.toFloat())
    var pressed by mutableStateOf(false)
    var dragging by mutableStateOf(false)
    var dragPosition by mutableFloatStateOf(index.toFloat())
    var dragVelocity by mutableFloatStateOf(0f)
    var settleFrom: Float? = null
    var lastVelocity = 0f
    val displayPosition get() = if (dragging) dragPosition else settleFrom ?: position.value
}

@Composable
fun LiquidGlassBottomNav(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = IsDarkAppearance || selectedTab == AppTab.TERMINAL,
    animationsEnabled: Boolean = rememberNavigationAnimationsEnabled()
) {
    val tabs = remember { AppTab.entries }
    val motion = remember { GlassNavMotion(selectedTab.ordinal) }
    val currentTab by rememberUpdatedState(selectedTab)
    val onSelect by rememberUpdatedState(onTabSelected)
    val haptic = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shape = RoundedCornerShape(50)
    val accent = if (darkTheme) Color(0xFF99C7FF) else Color(0xFF1452B8)
    val idle = if (darkTheme) Color(0xFFE1E8F3) else Color(0xFF475569)
    val tint = if (darkTheme) Color(0xFF172133) else Color(0xFFF5F8FF)
    val edge = if (darkTheme) Color.White.copy(alpha = .28f) else Color.White.copy(alpha = .85f)
    val density = LocalDensity.current
    val barHeight = (64 + 12 * (density.fontScale - 1f).coerceIn(0f, 1f)).dp

    fun select(index: Int) {
        motion.target = index.toFloat()
        if (index != currentTab.ordinal) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelect(tabs[index])
        }
    }

    LaunchedEffect(selectedTab) {
        if (!motion.dragging) motion.target = selectedTab.ordinal.toFloat()
    }
    LaunchedEffect(motion, animationsEnabled) {
        snapshotFlow { motion.target to motion.dragging }.collectLatest { (target, dragging) ->
            if (dragging) {
                motion.position.stop()
                return@collectLatest
            }
            motion.settleFrom?.let { start ->
                motion.position.snapTo(start)
                motion.settleFrom = null
            }
            if (animationsEnabled) {
                motion.position.animateTo(target, spring(.72f, 580f, .001f), initialVelocity = motion.lastVelocity) {
                    motion.lastVelocity = velocity
                }
            } else motion.position.snapTo(target)
            motion.lastVelocity = 0f
        }
    }
    LaunchedEffect(motion, animationsEnabled) {
        snapshotFlow { motion.pressed }.collectLatest { pressed ->
            if (animationsEnabled) motion.press.animateTo(if (pressed) 1f else 0f, spring(.7f, 520f))
            else motion.press.snapTo(0f)
        }
    }

    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().height(barHeight)
                .testTag("liquid_bottom_nav")
                .graphicsLayer {
                    val scale = 1f - .008f * motion.press.value
                    scaleX = scale; scaleY = scale
                }
                .shadow(8.dp, shape, clip = false)
                .clip(shape)
                .background(remember(tint, darkTheme) {
                    Brush.verticalGradient(listOf(
                        tint.copy(alpha = .97f), tint.copy(alpha = .94f)
                    ))
                })
                .border(.7.dp, edge, shape)
                .selectableGroup()
                .pointerInput(motion, rtl) {
                    val inset = 4.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        down.consume()
                        motion.pressed = true
                        val start = motion.displayPosition
                        val width = (size.width - 2 * inset).coerceAtLeast(1f)
                        val slot = width / tabs.size
                        val sign = if (rtl) -1 else 1
                        val startPhysical = if (rtl) tabs.lastIndex - start else start
                        val onIndicator = abs(down.position.x - inset - (startPhysical + .5f) * slot) <= slot * .6f
                        var dragged = false
                        var released = false
                        var lastMoveTime = down.uptimeMillis
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed) break
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (!dragged && abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx)) {
                                    change.consume()
                                    break
                                }
                                if (!change.pressed) {
                                    change.consume()
                                    released = true
                                    if (dragged) {
                                        motion.settleFrom = motion.dragPosition
                                        motion.lastVelocity = motion.dragVelocity
                                    }
                                    select(if (dragged) nearestNavigationTab(motion.dragPosition, tabs.size)
                                        else navigationTabAt(change.position.x - inset, width, tabs.size, rtl))
                                    break
                                }
                                if (!dragged && abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) {
                                    dragged = true
                                    motion.dragPosition = start
                                    motion.dragging = true
                                }
                                if (dragged) {
                                    val target = if (onIndicator) start + dx / slot * sign
                                        else ((change.position.x - inset) / slot - .5f).let { if (rtl) tabs.lastIndex - it else it }
                                    val next = target.coerceIn(0f, tabs.lastIndex.toFloat())
                                    val elapsed = (change.uptimeMillis - lastMoveTime).coerceAtLeast(1)
                                    motion.dragVelocity = ((next - motion.dragPosition) * 1000f / elapsed).coerceIn(-12f, 12f)
                                    motion.dragPosition = next
                                    lastMoveTime = change.uptimeMillis
                                }
                                change.consume()
                            }
                        } finally {
                            if (dragged && !released) {
                                motion.settleFrom = motion.dragPosition
                                motion.lastVelocity = 0f
                            }
                            if (!released) motion.target = currentTab.ordinal.toFloat()
                            motion.dragging = false
                            motion.pressed = false
                        }
                    }
                }
        ) {
            val slotWidth = (maxWidth - 8.dp) / tabs.size
            val indicatorTint = if (darkTheme) Color(0xFF25374F) else Color(0xFFDDEAFF)
            Box(
                Modifier.padding(4.dp).width(slotWidth).fillMaxHeight()
                    .graphicsLayer {
                        val position = motion.displayPosition
                        translationX = (if (rtl) tabs.lastIndex - position else position) * slotWidth.toPx()
                        val velocity = if (motion.dragging) motion.dragVelocity else motion.position.velocity
                        val stretch = (abs(velocity) * .012f).coerceAtMost(.13f)
                        scaleX = 1f + .065f * motion.press.value + stretch
                        scaleY = 1f + .09f * motion.press.value - stretch * .3f
                    }
                    .clip(shape).background(indicatorTint)
                    .border(.7.dp, edge, shape)
                    .testTag("liquid_tab_indicator")
            )
            Row(Modifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { tab ->
                    GlassTab(tab, selectedTab == tab, accent, idle, animationsEnabled,
                        Modifier.weight(1f).fillMaxHeight()) { select(tab.ordinal) }
                }
            }
        }
    }
}

@Composable
private fun GlassTab(
    tab: AppTab, selected: Boolean, accent: Color, idle: Color, animationsEnabled: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val color by animateColorAsState(if (selected) accent else idle, tween(if (animationsEnabled) 180 else 0), label = "${tab.tag}-color")
    val filled = androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f,
        tween(if (animationsEnabled) 160 else 0), label = "${tab.tag}-filled")
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(selected, animationsEnabled) {
        if (selected && animationsEnabled) {
            bounce.animateTo(1.13f, spring(.58f, 900f))
            bounce.animateTo(1f, spring(.65f, 650f))
        } else bounce.snapTo(1f)
    }
    val label = when (tab) {
        AppTab.FILES -> tr("Berkas", "Files")
        AppTab.SETTINGS -> tr("Pengaturan", "Settings")
        else -> tab.title
    }
    val outline = when (tab) {
        AppTab.TERMINAL -> Icons.Outlined.Terminal
        AppTab.FILES -> Icons.Outlined.FolderOpen
        AppTab.HERMES -> Icons.Outlined.Forum
        AppTab.SETTINGS -> Icons.Outlined.Settings
    }
    val fill = when (tab) {
        AppTab.TERMINAL -> Icons.Filled.Terminal
        AppTab.FILES -> Icons.Filled.FolderOpen
        AppTab.HERMES -> Icons.Filled.Forum
        AppTab.SETTINGS -> Icons.Filled.Settings
    }
    Column(modifier.clip(RoundedCornerShape(50))
        .then(if (focused) Modifier.border(2.dp, accent, RoundedCornerShape(50)) else Modifier)
        .selectable(selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
        .testTag(tab.tag).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(23.dp).graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }) {
            Icon(outline, contentDescription = null, tint = color,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - filled.value })
            Icon(fill, contentDescription = null, tint = color,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = filled.value })
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
