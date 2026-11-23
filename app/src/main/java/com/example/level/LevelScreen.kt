package com.example.level

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ArcCompassSettings
import com.example.sensor.LevelTiltState
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassPill
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassTier
import com.example.ui.theme.arcSemanticColors
import com.example.ui.theme.arcTextRoles
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Computes progressive circular -> rounded-triangle morph factor [0f..1f]
 * from normalized radial distance [0f..1f+] inside the Level inclinometer lens.
 */
fun computeLevelBubbleMorphFraction(normDistance: Float): Float {
    val t = ((normDistance - 0.10f) / 0.78f).coerceIn(0f, 1f)
    // Smoothstep curve for organic easing near both center and outer rim
    return t * t * (3f - 2f * t)
}

/**
 * Pure radial profile for the morphing Level orientation bubble at [thetaRad].
 */
fun levelBubbleRadiusAt(
    baseRadius: Float,
    morphFraction: Float,
    thetaRad: Float,
    tiltDirectionRad: Float
): Float {
    val clampedMorph = morphFraction.coerceIn(0f, 1f)
    val delta = thetaRad - tiltDirectionRad
    val roundedTriangle = cos(3f * delta)
    val directionalBias = cos(delta)
    return baseRadius * (
        1f +
            clampedMorph * 0.23f * roundedTriangle +
            clampedMorph * 0.05f * directionalBias
        )
}

/**
 * Builds the dynamically morphing Level orientation bubble path:
 * - At center (morphFraction = 0): exact circle.
 * - Approaching circumference (morphFraction -> 1): smoothly rounded triangle pointing along [tiltDirectionRad].
 */
fun buildMorphingLevelBubblePath(
    center: Offset,
    baseRadius: Float,
    morphFraction: Float,
    tiltDirectionRad: Float
): Path {
    val steps = 64
    return Path().apply {
        for (i in 0..steps) {
            val theta = ((2.0 * PI * i) / steps).toFloat()
            val r = levelBubbleRadiusAt(
                baseRadius = baseRadius,
                morphFraction = morphFraction,
                thetaRad = theta,
                tiltDirectionRad = tiltDirectionRad
            )
            val x = center.x + r * cos(theta)
            val y = center.y + r * sin(theta)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

@Composable
fun LevelScreen(
    levelState: LevelTiltState,
    settings: ArcCompassSettings,
    modifier: Modifier = Modifier,
    onCaptureReference: () -> Unit = {},
    onClearReference: () -> Unit = {},
    bottomNavClearance: Dp = 16.dp
) {
    val haptic = LocalHapticFeedback.current
    var wasLevel by remember { mutableStateOf(false) }

    LaunchedEffect(levelState.isFlatLevel, settings.hapticFeedbackEnabled) {
        if (settings.hapticFeedbackEnabled && levelState.isSupported && levelState.isFlatLevel && !wasLevel) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasLevel = levelState.isFlatLevel
    }

    val roll = levelState.rollDegrees ?: 0f
    val pitch = levelState.pitchDegrees ?: 0f
    val totalTilt = levelState.totalTiltDegrees ?: 0f
    val verticalPlumb = levelState.verticalPlumbDegrees ?: 0f

    val animSpec = if (settings.reducedMotionEnabled) {
        snap<Float>()
    } else {
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    }

    val animRoll by animateFloatAsState(targetValue = roll, animationSpec = animSpec, label = "level_roll")
    val animPitch by animateFloatAsState(targetValue = pitch, animationSpec = animSpec, label = "level_pitch")
    val animTotal by animateFloatAsState(targetValue = totalTilt, animationSpec = animSpec, label = "level_total")
    val animPlumb by animateFloatAsState(targetValue = verticalPlumb, animationSpec = animSpec, label = "level_plumb")

    val onDiscTap = {
        if (settings.hapticFeedbackEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (levelState.hasCustomReference) {
            onClearReference()
        } else {
            onCaptureReference()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("level_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        val isLandscape = maxWidth > maxHeight && maxWidth >= 560.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 920.dp)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = 10.dp,
                    bottom = bottomNavClearance + 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LevelHeaderBar(
                levelState = levelState,
                nightFilterActive = settings.redScreenFilterEnabled
            )

            if (!levelState.isSupported) {
                UnsupportedLevelCard()
                return@Column
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PrecisionGlassInclinometerInstrument(
                            rollDegrees = animRoll,
                            pitchDegrees = animPitch,
                            totalTiltDegrees = animTotal,
                            isFlatLevel = levelState.isFlatLevel,
                            hasCustomReference = levelState.hasCustomReference,
                            showDecimals = settings.showLevelDecimals,
                            largeReadouts = settings.largeReadoutsEnabled,
                            reducedMotion = settings.reducedMotionEnabled,
                            nightFilterActive = settings.redScreenFilterEnabled,
                            onTapReset = onDiscTap
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FloatingReferenceControlDeck(
                            hasCustomReference = levelState.hasCustomReference,
                            onCaptureReference = {
                                if (settings.hapticFeedbackEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onCaptureReference()
                            },
                            onClearReference = {
                                if (settings.hapticFeedbackEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onClearReference()
                            }
                        )
                        PrecisionLevelTelemetryConsole(
                            animRoll = animRoll,
                            roll = roll,
                            animPlumb = animPlumb,
                            levelState = levelState,
                            totalTilt = levelState.totalTiltDegrees,
                            settings = settings
                        )
                    }
                }
            } else {
                PrecisionGlassInclinometerInstrument(
                    rollDegrees = animRoll,
                    pitchDegrees = animPitch,
                    totalTiltDegrees = animTotal,
                    isFlatLevel = levelState.isFlatLevel,
                    hasCustomReference = levelState.hasCustomReference,
                    showDecimals = settings.showLevelDecimals,
                    largeReadouts = settings.largeReadoutsEnabled,
                    reducedMotion = settings.reducedMotionEnabled,
                    nightFilterActive = settings.redScreenFilterEnabled,
                    onTapReset = onDiscTap
                )

                FloatingReferenceControlDeck(
                    hasCustomReference = levelState.hasCustomReference,
                    onCaptureReference = {
                        if (settings.hapticFeedbackEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onCaptureReference()
                    },
                    onClearReference = {
                        if (settings.hapticFeedbackEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onClearReference()
                    }
                )

                PrecisionLevelTelemetryConsole(
                    animRoll = animRoll,
                    roll = roll,
                    animPlumb = animPlumb,
                    levelState = levelState,
                    totalTilt = levelState.totalTiltDegrees,
                    settings = settings
                )
            }
        }
    }
}

@Composable
private fun LevelHeaderBar(
    levelState: LevelTiltState,
    nightFilterActive: Boolean
) {
    val levelColor = if (nightFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.arcSemanticColors.success
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Surface Level",
                style = MaterialTheme.arcTextRoles.heading,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (levelState.isSupported) {
                    "${levelState.source.label} • ${levelState.accuracy.label} accuracy • No magnetometer"
                } else {
                    "Orientation sensor unavailable"
                },
                style = MaterialTheme.arcTextRoles.technical,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("level_sensor_status")
            )
        }

        if (levelState.isSupported && levelState.isFlatLevel) {
            GlassPill(
                tintColor = levelColor.copy(alpha = 0.22f),
                borderColor = levelColor.copy(alpha = 0.60f),
                modifier = Modifier.testTag("level_flat_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Surface is level",
                        tint = levelColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "LEVEL 0°",
                        style = MaterialTheme.arcTextRoles.technical,
                        color = levelColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PrecisionGlassInclinometerInstrument(
    rollDegrees: Float,
    pitchDegrees: Float,
    totalTiltDegrees: Float,
    isFlatLevel: Boolean,
    hasCustomReference: Boolean,
    showDecimals: Boolean,
    largeReadouts: Boolean,
    reducedMotion: Boolean,
    nightFilterActive: Boolean,
    onTapReset: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val levelColor = if (nightFilterActive) colorScheme.primary else MaterialTheme.arcSemanticColors.success
    val activeAccent = if (isFlatLevel) levelColor else colorScheme.primary
    val primaryInk = colorScheme.onSurface
    val mutedInk = colorScheme.onSurfaceVariant
    val outlineColor = colorScheme.outline
    val textMeasurer = rememberTextMeasurer()
    val techStyle = MaterialTheme.arcTextRoles.technical

    val ringDegreeStyle = remember(mutedInk, techStyle) {
        techStyle.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            color = mutedInk.copy(alpha = 0.85f)
        )
    }

    // Visual tilt vector direction (degrees, 0 = top 12 o'clock)
    val tiltMagnitude = hypot(rollDegrees.toDouble(), pitchDegrees.toDouble()).toFloat()
    val vectorAngleRad = atan2(-pitchDegrees.toDouble(), rollDegrees.toDouble())
    val vectorClockDeg = Math.toDegrees(vectorAngleRad).toFloat() + 90f

    GlassSurface(
        tier = GlassTier.FLOATING_CHROME,
        shape = CircleShape,
        elevation = 16.dp,
        borderColor = if (isFlatLevel) activeAccent.copy(alpha = 0.72f) else null,
        modifier = Modifier
            .widthIn(max = 396.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClickLabel = "Tap to capture or reset level reference orientation",
                onClick = onTapReset
            )
            .testTag("level_bullseye_canvas")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxR = size.minDimension / 2f * 0.96f
                val center = Offset(cx, cy)

                // Deep frosted lens interior with radial depth
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colorScheme.surface.copy(alpha = if (isDark) 0.50f else 0.78f),
                            activeAccent.copy(alpha = if (isFlatLevel) 0.14f else 0.06f),
                            colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.62f else 0.85f)
                        ),
                        center = center,
                        radius = maxR * 0.84f
                    ),
                    radius = maxR * 0.84f,
                    center = center
                )

                // Subtle diagonal glass specular reflection
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            if (nightFilterActive) colorScheme.primary.copy(alpha = 0.10f)
                            else Color.White.copy(alpha = if (isDark) 0.10f else 0.48f),
                            Color.Transparent,
                            Color.Transparent
                        ),
                        start = Offset(cx - maxR, cy - maxR),
                        end = Offset(cx + maxR, cy + maxR)
                    ),
                    radius = maxR * 0.84f,
                    center = center
                )

                // Inclination sweep arc around outer ring
                val ringR = maxR * 0.80f
                val ringStroke = 14.dp.toPx()
                val arcSpan = if (isFlatLevel) {
                    360f
                } else {
                    (28f + (tiltMagnitude / 45f).coerceIn(0f, 1f) * 165f)
                }
                val sweepStartDeg = if (isFlatLevel) 0f else (vectorClockDeg - 90f) - arcSpan / 2f

                drawCircle(
                    color = outlineColor.copy(alpha = 0.18f),
                    radius = ringR,
                    center = center,
                    style = Stroke(width = ringStroke)
                )

                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            activeAccent.copy(alpha = 0.12f),
                            activeAccent.copy(alpha = 0.68f),
                            activeAccent.copy(alpha = 0.12f)
                        ),
                        center = center
                    ),
                    startAngle = sweepStartDeg,
                    sweepAngle = arcSpan,
                    useCenter = false,
                    topLeft = Offset(cx - ringR, cy - ringR),
                    size = Size(ringR * 2f, ringR * 2f),
                    style = Stroke(width = ringStroke, cap = StrokeCap.Round)
                )

                // 360-degree precision ticks + 30-degree upright degree labels around the outer bezel
                for (deg in 0 until 360 step 2) {
                    val isMajor = deg % 30 == 0
                    val isMedium = deg % 10 == 0
                    val rad = Math.toRadians(deg - 90.0)
                    val outerTickR = maxR * 0.845f
                    val innerTickR = maxR * when {
                        isMajor -> 0.745f
                        isMedium -> 0.775f
                        else -> 0.805f
                    }
                    drawLine(
                        color = when {
                            isMajor -> primaryInk.copy(alpha = 0.88f)
                            isMedium -> mutedInk.copy(alpha = 0.58f)
                            else -> mutedInk.copy(alpha = 0.25f)
                        },
                        start = Offset(
                            cx + (outerTickR * cos(rad)).toFloat(),
                            cy + (outerTickR * sin(rad)).toFloat()
                        ),
                        end = Offset(
                            cx + (innerTickR * cos(rad)).toFloat(),
                            cy + (innerTickR * sin(rad)).toFloat()
                        ),
                        strokeWidth = when {
                            isMajor -> 2.4.dp.toPx()
                            isMedium -> 1.3.dp.toPx()
                            else -> 0.75.dp.toPx()
                        },
                        cap = StrokeCap.Round
                    )

                    if (isMajor) {
                        val labelR = maxR * 0.935f
                        val labelOffset = Offset(
                            cx + (labelR * cos(rad)).toFloat(),
                            cy + (labelR * sin(rad)).toFloat()
                        )
                        drawCenteredRingLabel(
                            textMeasurer = textMeasurer,
                            text = deg.toString(),
                            style = ringDegreeStyle,
                            anchor = labelOffset
                        )
                    }
                }

                // Concentric measurement rings (15°, 30°, and central 0° precision target)
                listOf(0.24f, 0.48f).forEach { frac ->
                    drawCircle(
                        color = outlineColor.copy(alpha = 0.24f),
                        radius = maxR * frac,
                        center = center,
                        style = Stroke(width = 0.9.dp.toPx())
                    )
                }

                // Fine orthogonal crosshairs
                val crossR = maxR * 0.68f
                drawLine(
                    color = outlineColor.copy(alpha = 0.20f),
                    start = Offset(cx - crossR, cy),
                    end = Offset(cx + crossR, cy),
                    strokeWidth = 0.8.dp.toPx()
                )
                drawLine(
                    color = outlineColor.copy(alpha = 0.20f),
                    start = Offset(cx, cy - crossR),
                    end = Offset(cx, cy + crossR),
                    strokeWidth = 0.8.dp.toPx()
                )

                // Zero-degree target ring
                drawCircle(
                    color = if (isFlatLevel) activeAccent else outlineColor.copy(alpha = 0.55f),
                    radius = maxR * 0.11f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Dynamic Morphing Orientation Bubble:
                // Circular at center, progressively morphs into a rounded triangle pointing toward tilt direction.
                val normX = (rollDegrees / 45f).coerceIn(-1f, 1f)
                val normY = (-pitchDegrees / 45f).coerceIn(-1f, 1f)
                val rawNormDist = hypot(normX.toDouble(), normY.toDouble()).toFloat()
                val clampedScale = if (rawNormDist > 1f) 1f / rawNormDist else 1f
                val effectiveNormDist = rawNormDist.coerceIn(0f, 1f)
                val travelR = maxR * 0.61f
                val bubbleCenter = Offset(
                    x = cx + normX * clampedScale * travelR,
                    y = cy + normY * clampedScale * travelR
                )
                val bubbleR = maxR * 0.082f
                val morphFraction = computeLevelBubbleMorphFraction(effectiveNormDist)
                val tiltDirectionRad = atan2(normY, normX)

                val bubblePath = buildMorphingLevelBubblePath(
                    center = bubbleCenter,
                    baseRadius = bubbleR,
                    morphFraction = morphFraction,
                    tiltDirectionRad = tiltDirectionRad
                )

                // Soften bubble fill when near dead-center so central tilt numerals always stay crisp
                val centerProximityFade = (effectiveNormDist / 0.22f).coerceIn(0f, 1f)
                val bubbleFillAlphaTop = 0.36f + centerProximityFade * 0.54f
                val bubbleFillAlphaBottom = 0.24f + centerProximityFade * 0.50f

                // Ambient glow halo around morphing orientation bubble
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(activeAccent.copy(alpha = 0.32f), Color.Transparent),
                        center = bubbleCenter,
                        radius = bubbleR * 2.2f
                    ),
                    radius = bubbleR * 2.2f,
                    center = bubbleCenter
                )
                drawPath(
                    path = bubblePath,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            activeAccent.copy(alpha = bubbleFillAlphaTop),
                            activeAccent.copy(alpha = bubbleFillAlphaBottom)
                        ),
                        center = Offset(
                            bubbleCenter.x - bubbleR * 0.2f,
                            bubbleCenter.y - bubbleR * 0.2f
                        ),
                        radius = bubbleR * 1.4f
                    )
                )
                drawPath(
                    path = bubblePath,
                    color = if (nightFilterActive) activeAccent else activeAccent.copy(alpha = 0.85f),
                    style = Stroke(width = 1.6.dp.toPx())
                )
                // Inner glass highlight dot
                drawCircle(
                    color = if (nightFilterActive) activeAccent.copy(alpha = 0.35f)
                    else Color.White.copy(alpha = 0.45f),
                    radius = bubbleR * 0.28f,
                    center = Offset(bubbleCenter.x - bubbleR * 0.24f, bubbleCenter.y - bubbleR * 0.24f)
                )
            }

            // Bold central tilt readout with subtle orientation tilt response
            val readoutRotation = if (reducedMotion) 0f else (rollDegrees.coerceIn(-25f, 25f) * 0.55f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.graphicsLayer { rotationZ = readoutRotation }
            ) {
                val formattedTilt = if (showDecimals) {
                    String.format(Locale.US, "%.1f°", totalTiltDegrees)
                } else {
                    "${totalTiltDegrees.roundToInt()}°"
                }
                Text(
                    text = formattedTilt,
                    style = MaterialTheme.arcTextRoles.display.copy(
                        fontSize = if (largeReadouts) 54.sp else 48.sp
                    ),
                    color = primaryInk,
                    modifier = Modifier.testTag("level_total_tilt_value")
                )
                Text(
                    text = if (hasCustomReference) "REL ZERO • TAP" else if (isFlatLevel) "LEVEL" else "TILT",
                    style = MaterialTheme.arcTextRoles.technical,
                    fontWeight = FontWeight.Bold,
                    color = activeAccent
                )
            }

            // Integrated Roll / Pitch compact glass pill inside lower lens arc
            GlassPill(
                tintColor = colorScheme.surface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp)
            ) {
                Text(
                    text = "R ${formatAngle(rollDegrees, showDecimals)}  •  P ${formatAngle(pitchDegrees, showDecimals)}",
                    style = MaterialTheme.arcTextRoles.technical,
                    fontWeight = FontWeight.Medium,
                    color = primaryInk,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingReferenceControlDeck(
    hasCustomReference: Boolean,
    onCaptureReference: () -> Unit,
    onClearReference: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassPill(
            tintColor = if (hasCustomReference) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onCaptureReference)
                .testTag("level_set_reference_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.CenterFocusStrong,
                    contentDescription = null,
                    tint = if (hasCustomReference) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (hasCustomReference) "Re-zero Reference" else "Zero Current Angle",
                    style = MaterialTheme.arcTextRoles.button,
                    color = if (hasCustomReference) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        if (hasCustomReference) {
            GlassPill(
                tintColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onClearReference)
                    .testTag("level_reset_true_zero_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = "Reset to true 0 degree gravity reference",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "True 0°",
                        style = MaterialTheme.arcTextRoles.button,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun PrecisionLevelTelemetryConsole(
    animRoll: Float,
    roll: Float,
    animPlumb: Float,
    levelState: LevelTiltState,
    totalTilt: Float?,
    settings: ArcCompassSettings
) {
    val colorScheme = MaterialTheme.colorScheme
    val levelColor = if (settings.redScreenFilterEnabled) colorScheme.primary else MaterialTheme.arcSemanticColors.success
    val rollLevel = abs(roll) <= 0.6f

    GlassCard(
        tier = GlassTier.CARD,
        borderColor = if (levelState.isFlatLevel) levelColor.copy(alpha = 0.55f) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExpressiveDisplayOnlySliderRow(
                title = "Horizontal Inclination (Roll)",
                angleDegrees = animRoll,
                isLevel = rollLevel,
                levelColor = levelColor,
                showDecimals = settings.showLevelDecimals,
                testTag = "level_roll_slider"
            )

            ExpressiveDisplayOnlySliderRow(
                title = "Vertical Inclination (Plumb)",
                angleDegrees = animPlumb,
                isLevel = levelState.isVerticalLevel,
                levelColor = levelColor,
                showDecimals = settings.showLevelDecimals,
                testTag = "level_plumb_slider"
            )

            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.22f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetrySubMetric(
                    label = "Roll",
                    value = levelState.rollDegrees?.let { formatAngle(it, settings.showLevelDecimals) } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                TelemetrySubMetric(
                    label = "Pitch",
                    value = levelState.pitchDegrees?.let { formatAngle(it, settings.showLevelDecimals) } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                TelemetrySubMetric(
                    label = "Total Tilt",
                    value = totalTilt?.let { formatAngle(it, settings.showLevelDecimals, signed = false) } ?: "--",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Material 3 Expressive measurement slider — strictly display-only (non-interactive / non-draggable).
 * Features a thick rounded capsule track, center 0° reference notch, active directional fill,
 * smooth moving pill indicator, and +/- directional endpoints.
 */
@Composable
private fun ExpressiveDisplayOnlySliderRow(
    title: String,
    angleDegrees: Float,
    isLevel: Boolean,
    levelColor: Color,
    showDecimals: Boolean,
    testTag: String
) {
    val colorScheme = MaterialTheme.colorScheme
    val indicatorColor = if (isLevel) levelColor else colorScheme.primary
    val trackBg = colorScheme.surfaceVariant.copy(alpha = 0.72f)
    val trackBorder = colorScheme.outline.copy(alpha = 0.32f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.arcTextRoles.settings,
                fontSize = 13.5.sp,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = formatAngle(angleDegrees, showDecimals),
                style = MaterialTheme.arcTextRoles.measurement,
                fontWeight = FontWeight.Bold,
                color = indicatorColor
            )
        }

        // Strictly display-only M3 Expressive capsule track + moving indicator pill
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
        ) {
            val midY = size.height / 2f
            val centerX = size.width / 2f
            val trackH = 14.dp.toPx()
            val trackTop = midY - trackH / 2f
            val trackRadius = trackH / 2f
            val travelHalfW = (size.width / 2f) - 10.dp.toPx()

            // Outer rounded track capsule
            drawRoundRect(
                color = trackBg,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackH),
                cornerRadius = CornerRadius(trackRadius, trackRadius)
            )
            drawRoundRect(
                color = trackBorder,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackH),
                cornerRadius = CornerRadius(trackRadius, trackRadius),
                style = Stroke(width = 1.dp.toPx())
            )

            // Active Expressive fill extending from center 0° reference to current angle position
            val norm = (angleDegrees / 45f).coerceIn(-1f, 1f)
            val thumbX = centerX + norm * travelHalfW
            val fillLeft = minOf(centerX, thumbX)
            val fillWidth = abs(thumbX - centerX).coerceAtLeast(4.dp.toPx())
            drawRoundRect(
                color = indicatorColor.copy(alpha = 0.34f),
                topLeft = Offset(fillLeft, trackTop + 2.dp.toPx()),
                size = Size(fillWidth, trackH - 4.dp.toPx()),
                cornerRadius = CornerRadius(trackRadius, trackRadius)
            )

            // Center 0° reference notch
            drawLine(
                color = if (isLevel) indicatorColor else colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                start = Offset(centerX, trackTop - 3.dp.toPx()),
                end = Offset(centerX, trackTop + trackH + 3.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Subtle M3 Expressive stop dots at -45° / +45°
            drawCircle(
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                radius = 2.2.dp.toPx(),
                center = Offset(centerX - travelHalfW, midY)
            )
            drawCircle(
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                radius = 2.2.dp.toPx(),
                center = Offset(centerX + travelHalfW, midY)
            )

            // Moving M3 Expressive vertical pill handle (display-only)
            val thumbW = 7.dp.toPx()
            val thumbH = 24.dp.toPx()
            drawRoundRect(
                color = indicatorColor.copy(alpha = 0.25f),
                topLeft = Offset(thumbX - thumbW * 0.85f, midY - thumbH * 0.6f),
                size = Size(thumbW * 1.7f, thumbH * 1.2f),
                cornerRadius = CornerRadius(thumbW, thumbW)
            )
            drawRoundRect(
                color = indicatorColor,
                topLeft = Offset(thumbX - thumbW / 2f, midY - thumbH / 2f),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "-45°",
                style = MaterialTheme.arcTextRoles.technical,
                color = colorScheme.tertiary
            )
            Text(
                text = "0°",
                style = MaterialTheme.arcTextRoles.technical,
                fontWeight = FontWeight.SemiBold,
                color = if (isLevel) indicatorColor else colorScheme.onSurfaceVariant
            )
            Text(
                text = "+45°",
                style = MaterialTheme.arcTextRoles.technical,
                color = colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun TelemetrySubMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.arcTextRoles.technical,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.arcTextRoles.measurement,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun UnsupportedLevelCard() {
    val warningColor = MaterialTheme.arcSemanticColors.warning
    GlassCard(
        tier = GlassTier.CARD,
        borderColor = warningColor.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unsupported_level_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = warningColor
                )
                Text(
                    text = "Surface Level Unavailable",
                    style = MaterialTheme.arcTextRoles.heading,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "This device does not expose a Gravity, Accelerometer, or Rotation Vector sensor required to measure tilt.",
                style = MaterialTheme.arcTextRoles.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun DrawScope.drawCenteredRingLabel(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    anchor: Offset
) {
    val result = textMeasurer.measure(text = text, style = style)
    drawText(
        textLayoutResult = result,
        topLeft = Offset(
            x = anchor.x - result.size.width / 2f,
            y = anchor.y - result.size.height / 2f
        )
    )
}

private fun formatAngle(angle: Float, showDecimals: Boolean, signed: Boolean = true): String {
    val cleaned = if (abs(angle) < 0.05f) 0f else angle
    return if (showDecimals) {
        if (signed) String.format(Locale.US, "%+.1f°", cleaned)
        else String.format(Locale.US, "%.1f°", abs(cleaned))
    } else {
        val rounded = cleaned.roundToInt()
        if (signed && rounded > 0) "+$rounded°"
        else if (!signed) "${abs(rounded)}°"
        else "$rounded°"
    }
}
