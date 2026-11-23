package com.example.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.MainTab
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassTier
import com.example.ui.theme.LocalArcSettings
import com.example.ui.theme.LocalLowRamDevice
import com.example.ui.theme.arcTextRoles
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure snapping math for the draggable floating navigation indicator.
 * Combines instantaneous fractional slot index with release flick velocity.
 */
fun snapNavDestinationIndex(
    fractionalIndex: Float,
    velocityPxPerSec: Float,
    slotSizePx: Float,
    count: Int
): Int {
    if (count <= 1) return 0
    val safeSlot = slotSizePx.coerceAtLeast(1f)
    val velocitySlots = (velocityPxPerSec / safeSlot) * 0.14f
    val projected = fractionalIndex + velocitySlots.coerceIn(-0.65f, 0.65f)
    return projected.roundToInt().coerceIn(0, count - 1)
}

@Composable
fun MorphingFloatingNavBar(
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = LocalArcSettings.current
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val tabs = MainTab.entries
    val selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0)

    var isDragging by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var lastHoveredSlot by remember { mutableIntStateOf(selectedIndex) }

    val targetIndex = if (isDragging) dragIndex else selectedIndex.toFloat()
    val animatedIndex by animateFloatAsState(
        targetValue = targetIndex,
        animationSpec = if (settings.reducedMotionEnabled || isDragging) {
            snap()
        } else {
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "floating_nav_indicator_index"
    )

    val displayedTab = if (isDragging) {
        tabs[dragIndex.roundToInt().coerceIn(0, tabs.lastIndex)]
    } else {
        currentTab
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .height(78.dp),
            contentAlignment = Alignment.Center
        ) {
            val horizontalInset = 10.dp
            val containerWidthPx = with(density) { (maxWidth - horizontalInset * 2).toPx() }
            val slotWidthPx = (containerWidthPx / tabs.size).coerceAtLeast(1f)
            val indicatorSize = 68.dp
            val indicatorSizePx = with(density) { indicatorSize.toPx() }

            // Underlying frosted-glass pill dock
            GlassSurface(
                tier = GlassTier.FLOATING_CHROME,
                shape = RoundedCornerShape(30.dp),
                elevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.Center)
                    .pointerInput(tabs.size, selectedIndex, slotWidthPx, settings.hapticFeedbackEnabled) {
                        val velocityTracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragIndex = selectedIndex.toFloat()
                                lastHoveredSlot = selectedIndex
                                velocityTracker.resetTracking()
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                dragIndex = (dragIndex + dragAmount / slotWidthPx)
                                    .coerceIn(0f, (tabs.size - 1).toFloat())
                                val hovered = dragIndex.roundToInt().coerceIn(0, tabs.lastIndex)
                                if (hovered != lastHoveredSlot) {
                                    lastHoveredSlot = hovered
                                    if (settings.hapticFeedbackEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onSelectTab(tabs[hovered])
                                }
                            },
                            onDragEnd = {
                                val vx = velocityTracker.calculateVelocity().x
                                val snapped = snapNavDestinationIndex(
                                    fractionalIndex = dragIndex,
                                    velocityPxPerSec = vx,
                                    slotSizePx = slotWidthPx,
                                    count = tabs.size
                                )
                                isDragging = false
                                if (tabs[snapped] != currentTab && settings.hapticFeedbackEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onSelectTab(tabs[snapped])
                            },
                            onDragCancel = {
                                val snapped = dragIndex.roundToInt().coerceIn(0, tabs.lastIndex)
                                isDragging = false
                                onSelectTab(tabs[snapped])
                            }
                        )
                    }
                    .testTag("morphing_floating_nav")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalInset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isCoveredByIndicator = abs(animatedIndex - index.toFloat()) < 0.35f
                        val label = stringResource(tab.labelRes)
                        val slotAlpha by animateFloatAsState(
                            targetValue = if (isCoveredByIndicator) 0f else 1f,
                            animationSpec = if (settings.reducedMotionEnabled) snap() else spring(stiffness = Spring.StiffnessMedium),
                            label = "nav_slot_alpha_${tab.name}"
                        )
                        val interactionSource = remember { MutableInteractionSource() }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(22.dp))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = ripple(bounded = true),
                                    role = Role.Tab,
                                    onClick = {
                                        if (tab != currentTab && settings.hapticFeedbackEnabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        onSelectTab(tab)
                                    }
                                )
                                .graphicsLayer { alpha = slotAlpha }
                                .testTag(tab.testTag),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.unselectedIcon,
                                contentDescription = label,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(21.dp)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.arcTextRoles.navigation,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Independent larger floating rounded-square glass active indicator
            val insetPx = with(density) { horizontalInset.toPx() }
            val centerX = insetPx + slotWidthPx * (animatedIndex + 0.5f)
            val offsetX = (centerX - indicatorSizePx / 2f).roundToInt()

            FloatingRoundedSquareIndicator(
                icon = displayedTab.selectedIcon,
                contentDescription = stringResource(displayedTab.labelRes),
                size = indicatorSize,
                isDragging = isDragging,
                reducedMotion = settings.reducedMotionEnabled,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(x = offsetX, y = 0) }
                    .testTag("floating_nav_active_indicator")
            )
        }
    }
}

@Composable
fun MorphingFloatingNavRail(
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = LocalArcSettings.current
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val tabs = MainTab.entries
    val selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0)

    var isDragging by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var lastHoveredSlot by remember { mutableIntStateOf(selectedIndex) }

    val animatedIndex by animateFloatAsState(
        targetValue = if (isDragging) dragIndex else selectedIndex.toFloat(),
        animationSpec = if (settings.reducedMotionEnabled || isDragging) {
            snap()
        } else {
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "floating_rail_indicator_index"
    )

    val displayedTab = if (isDragging) {
        tabs[dragIndex.roundToInt().coerceIn(0, tabs.lastIndex)]
    } else {
        currentTab
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 12.dp, top = 16.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        val railHeight = 330.dp
        val verticalInset = 10.dp
        val indicatorSize = 68.dp
        val slotHeightPx = with(density) { ((railHeight - verticalInset * 2) / tabs.size).toPx() }.coerceAtLeast(1f)
        val indicatorSizePx = with(density) { indicatorSize.toPx() }
        val insetPx = with(density) { verticalInset.toPx() }

        Box(
            modifier = Modifier
                .width(84.dp)
                .height(railHeight),
            contentAlignment = Alignment.Center
        ) {
            GlassSurface(
                tier = GlassTier.FLOATING_CHROME,
                shape = RoundedCornerShape(30.dp),
                elevation = 10.dp,
                modifier = Modifier
                    .width(70.dp)
                    .height(railHeight)
                    .pointerInput(tabs.size, selectedIndex, slotHeightPx) {
                        val velocityTracker = VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragIndex = selectedIndex.toFloat()
                                lastHoveredSlot = selectedIndex
                                velocityTracker.resetTracking()
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                dragIndex = (dragIndex + dragAmount / slotHeightPx)
                                    .coerceIn(0f, (tabs.size - 1).toFloat())
                                val hovered = dragIndex.roundToInt().coerceIn(0, tabs.lastIndex)
                                if (hovered != lastHoveredSlot) {
                                    lastHoveredSlot = hovered
                                    if (settings.hapticFeedbackEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onSelectTab(tabs[hovered])
                                }
                            },
                            onDragEnd = {
                                val vy = velocityTracker.calculateVelocity().y
                                val snapped = snapNavDestinationIndex(
                                    fractionalIndex = dragIndex,
                                    velocityPxPerSec = vy,
                                    slotSizePx = slotHeightPx,
                                    count = tabs.size
                                )
                                isDragging = false
                                onSelectTab(tabs[snapped])
                            },
                            onDragCancel = {
                                val snapped = dragIndex.roundToInt().coerceIn(0, tabs.lastIndex)
                                isDragging = false
                                onSelectTab(tabs[snapped])
                            }
                        )
                    }
                    .testTag("morphing_floating_nav")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = verticalInset, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isCovered = abs(animatedIndex - index.toFloat()) < 0.35f
                        val label = stringResource(tab.labelRes)
                        val slotAlpha by animateFloatAsState(
                            targetValue = if (isCovered) 0f else 1f,
                            animationSpec = if (settings.reducedMotionEnabled) snap() else spring(stiffness = Spring.StiffnessMedium),
                            label = "rail_slot_alpha_${tab.name}"
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(22.dp))
                                .clickable(role = Role.Tab) {
                                    if (tab != currentTab && settings.hapticFeedbackEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onSelectTab(tab)
                                }
                                .graphicsLayer { alpha = slotAlpha }
                                .testTag(tab.testTag),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.unselectedIcon,
                                contentDescription = label,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(21.dp)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.arcTextRoles.navigation,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            val centerY = insetPx + slotHeightPx * (animatedIndex + 0.5f)
            val offsetY = (centerY - indicatorSizePx / 2f).roundToInt()
            FloatingRoundedSquareIndicator(
                icon = displayedTab.selectedIcon,
                contentDescription = stringResource(displayedTab.labelRes),
                size = indicatorSize,
                isDragging = isDragging,
                reducedMotion = settings.reducedMotionEnabled,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(x = 0, y = offsetY) }
                    .testTag("floating_nav_active_indicator")
            )
        }
    }
}

@Composable
private fun FloatingRoundedSquareIndicator(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    isDragging: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val settings = LocalArcSettings.current
    val lowRam = LocalLowRamDevice.current
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(22.dp)

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.07f else 1.0f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "floating_indicator_scale"
    )

    val useBackdropBlur = settings.frostedBlurEnabled &&
        !lowRam &&
        !settings.highContrastEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val glassBaseColor = if (settings.highContrastEnabled) {
        colorScheme.primaryContainer
    } else {
        colorScheme.primaryContainer.copy(alpha = if (isDark) 0.58f else 0.78f)
    }

    val specularBorder = if (settings.highContrastEnabled) {
        colorScheme.primary
    } else if (settings.redScreenFilterEnabled) {
        colorScheme.primary.copy(alpha = 0.72f)
    } else if (isDark) {
        Color.White.copy(alpha = 0.34f)
    } else {
        Color.White.copy(alpha = 0.90f)
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (settings.highContrastEnabled) 2.dp else 14.dp,
                shape = shape,
                ambientColor = colorScheme.primary.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.50f else 0.18f)
            )
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        // Blurred translucent glass substrate underneath crisp foreground icon
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (useBackdropBlur) Modifier.blur(12.dp) else Modifier)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            glassBaseColor.copy(alpha = (glassBaseColor.alpha + 0.14f).coerceAtMost(0.96f)),
                            glassBaseColor,
                            colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.62f else 0.72f)
                        )
                    )
                )
        )

        // Subtle crisp glass rim highlight + specular bevel
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            if (settings.highContrastEnabled) Color.Transparent
                            else if (settings.redScreenFilterEnabled) colorScheme.primary.copy(alpha = 0.12f)
                            else Color.White.copy(alpha = if (isDark) 0.16f else 0.55f),
                            Color.Transparent
                        )
                    )
                )
                .border(
                    width = 1.25.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            specularBorder,
                            colorScheme.primary.copy(alpha = 0.45f)
                        )
                    ),
                    shape = shape
                )
        )

        // Displays only the icon of the currently selected page
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                if (reducedMotion) {
                    androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "floating_indicator_icon"
        ) { currentIcon ->
            Icon(
                imageVector = currentIcon,
                contentDescription = contentDescription,
                tint = colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
