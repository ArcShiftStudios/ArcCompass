package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainTab
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassTier
import com.example.ui.theme.LocalArcSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Computes how strongly the floating navigation indicator morphs into a rounded triangle
 * near the navigation edges (1.0f at outer edges, 0.0f when centrally positioned).
 */
fun computeNavIndicatorEdgeMorph(animatedIndex: Float, count: Int): Float {
    if (count <= 1) return 0f
    val center = (count - 1) / 2f
    val dist = abs(animatedIndex - center) / center
    // Keep inner central tabs (1 & 2 out of 0..3 -> dist ~0.33) purely circular;
    // smoothly ramp toward rounded triangular geometry near the outer edges.
    return ((dist - 0.36f) / 0.64f).coerceIn(0f, 1f)
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
    val tabs = MainTab.entries
    val selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0)

    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = if (settings.reducedMotionEnabled) {
            snap()
        } else {
            spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "floating_nav_indicator_index"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            tier = GlassTier.FLOATING_CHROME,
            shape = RoundedCornerShape(32.dp),
            elevation = 12.dp,
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .height(66.dp)
                .testTag("morphing_floating_nav")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Fluid morphing background indicator: circular in center, rounded triangle near edges
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val slotWidth = size.width / tabs.size
                    val cx = slotWidth * (animatedIndex + 0.5f)
                    val cy = size.height / 2f
                    val baseR = (minOf(slotWidth * 0.43f, size.height * 0.45f))

                    val edgeMorph = computeNavIndicatorEdgeMorph(animatedIndex, tabs.size)
                    val isLeftSide = animatedIndex < (tabs.size - 1) / 2f
                    val tipAngleRad = if (isLeftSide) PI.toFloat() else 0f

                    val path = buildMorphingIndicatorPath(
                        center = Offset(cx, cy),
                        baseRadius = baseR,
                        horizontalScale = 1.18f - 0.06f * edgeMorph,
                        edgeMorph = edgeMorph,
                        tipAngleRad = tipAngleRad
                    )

                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colorScheme.primary.copy(alpha = 0.28f),
                                colorScheme.primaryContainer.copy(alpha = 0.58f)
                            )
                        )
                    )
                    drawPath(
                        path = path,
                        color = colorScheme.primary.copy(alpha = 0.55f),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val selected = tab == currentTab
                        val label = stringResource(tab.labelRes)
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.08f else 0.96f,
                            animationSpec = if (settings.reducedMotionEnabled) snap() else spring(stiffness = Spring.StiffnessMedium),
                            label = "nav_icon_scale_${tab.name}"
                        )
                        val interactionSource = remember { MutableInteractionSource() }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(24.dp))
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
                                .testTag(tab.testTag),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = label,
                                tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(21.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
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
    val tabs = MainTab.entries
    val selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0)

    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = if (settings.reducedMotionEnabled) {
            snap()
        } else {
            spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "floating_rail_indicator_index"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 12.dp, top = 16.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            tier = GlassTier.FLOATING_CHROME,
            shape = RoundedCornerShape(32.dp),
            elevation = 12.dp,
            modifier = Modifier
                .width(78.dp)
                .height(330.dp)
                .testTag("morphing_floating_nav")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp, horizontal = 6.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val slotHeight = size.height / tabs.size
                    val cx = size.width / 2f
                    val cy = slotHeight * (animatedIndex + 0.5f)
                    val baseR = minOf(size.width * 0.42f, slotHeight * 0.42f)
                    val edgeMorph = computeNavIndicatorEdgeMorph(animatedIndex, tabs.size)
                    val isTopSide = animatedIndex < (tabs.size - 1) / 2f
                    val tipAngleRad = if (isTopSide) (-PI / 2.0).toFloat() else (PI / 2.0).toFloat()

                    val path = buildMorphingIndicatorPath(
                        center = Offset(cx, cy),
                        baseRadius = baseR,
                        horizontalScale = 1.0f,
                        edgeMorph = edgeMorph,
                        tipAngleRad = tipAngleRad
                    )
                    drawPath(
                        path = path,
                        color = colorScheme.primaryContainer.copy(alpha = 0.58f)
                    )
                    drawPath(
                        path = path,
                        color = colorScheme.primary.copy(alpha = 0.55f),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    tabs.forEach { tab ->
                        val selected = tab == currentTab
                        val label = stringResource(tab.labelRes)
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
                                .testTag(tab.testTag),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = label,
                                tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildMorphingIndicatorPath(
    center: Offset,
    baseRadius: Float,
    horizontalScale: Float,
    edgeMorph: Float,
    tipAngleRad: Float
): Path {
    val steps = 48
    return Path().apply {
        for (i in 0..steps) {
            val theta = ((2.0 * PI * i) / steps).toFloat()
            // Smoothly morph between circular (edgeMorph = 0) and rounded triangular geometry (edgeMorph = 1)
            val triHarmonic = cos(3f * (theta - tipAngleRad))
            val r = baseRadius * (1f + edgeMorph * 0.16f * triHarmonic)
            val x = center.x + r * horizontalScale * cos(theta)
            val y = center.y + r * sin(theta)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
