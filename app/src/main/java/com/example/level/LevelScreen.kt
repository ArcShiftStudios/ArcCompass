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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StatusAvailableGreen
import com.example.ui.theme.StatusWarningAmber
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

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
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
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
    val levelColor = if (nightFilterActive) MaterialTheme.colorScheme.primary else StatusAvailableGreen
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
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (levelState.isSupported) {
                    "${levelState.source.label} • ${levelState.accuracy.label} accuracy • No magnetometer"
                } else {
                    "Orientation sensor unavailable"
                },
                style = MaterialTheme.typography.labelMedium,
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
                        style = MaterialTheme.typography.labelMedium,
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
    val levelColor = if (nightFilterActive) colorScheme.primary else StatusAvailableGreen
    val activeAccent = if (isFlatLevel) levelColor else colorScheme.primary
    val primaryInk = colorScheme.onSurface
    val mutedInk = colorScheme.onSurfaceVariant
    val outlineColor = colorScheme.outline
    val textMeasurer = rememberTextMeasurer()

    val ringDegreeStyle = remember(mutedInk) {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            color = mutedInk.copy(alpha = 0.85f)
        )
    }

    // Smooth visual tilt direction angle (degrees, 0 = top 12 o'clock)
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

                // Deep frosted lens interior with subtle radial depth
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

                // Luminous inclination sweep arc around outer ring (inspired by high-precision inclinometer)
                val ringR = maxR * 0.80f
                val ringStroke = 14.dp.toPx()
                val arcSpan = if (isFlatLevel) {
                    360f
                } else {
                    (28f + (tiltMagnitude / 45f).coerceIn(0f, 1f) * 165f)
                }
                val sweepStartDeg = if (isFlatLevel) 0f else (vectorClockDeg - 90f) - arcSpan / 2f

                // Track ring behind sweep
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
                            isMajor -> 2.1.dp.toPx()
                            isMedium -> 1.2.dp.toPx()
                            else -> 0.7.dp.toPx()
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

                // Animated floating orientation indicator orb (clamped gracefully inside lens)
                val normX = (rollDegrees / 45f).coerceIn(-1f, 1f)
                val normY = (-pitchDegrees / 45f).coerceIn(-1f, 1f)
                val normDist = hypot(normX.toDouble(), normY.toDouble()).toFloat()
                val clampedScale = if (normDist > 1f) 1f / normDist else 1f
                val travelR = maxR * 0.64f
                val orbCenter = Offset(
                    x = cx + normX * clampedScale * travelR,
                    y = cy + normY * clampedScale * travelR
                )
                val orbR = maxR * 0.068f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(activeAccent.copy(alpha = 0.42f), Color.Transparent),
                        center = orbCenter,
                        radius = orbR * 2.1f
                    ),
                    radius = orbR * 2.1f,
                    center = orbCenter
                )
                drawCircle(
                    color = activeAccent,
                    radius = orbR,
                    center = orbCenter
                )
                drawCircle(
                    color = Color.White.copy(alpha = if (nightFilterActive) 0.28f else 0.68f),
                    radius = orbR * 0.34f,
                    center = Offset(orbCenter.x - orbR * 0.28f, orbCenter.y - orbR * 0.28f)
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
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (largeReadouts) 54.sp else 48.sp,
                        letterSpacing = (-1.5).sp
                    ),
                    color = if (isFlatLevel) activeAccent else primaryInk,
                    modifier = Modifier.testTag("level_total_tilt_value")
                )
                Text(
                    text = if (hasCustomReference) "REL ZERO • TAP" else if (isFlatLevel) "LEVEL" else "TILT",
                    style = MaterialTheme.typography.labelSmall,
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
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
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
                    style = MaterialTheme.typography.labelLarge
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
                    Text("True 0°", style = MaterialTheme.typography.labelLarge)
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
    val levelColor = if (settings.redScreenFilterEnabled) colorScheme.primary else StatusAvailableGreen
    val rollLevel = abs(roll) <= 0.6f

    GlassCard(
        tier = GlassTier.CARD,
        borderColor = if (levelState.isFlatLevel) levelColor.copy(alpha = 0.55f) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            VernierScaleRow(
                title = "Horizontal Inclination (Roll)",
                angleDegrees = animRoll,
                isLevel = rollLevel,
                levelColor = levelColor,
                showDecimals = settings.showLevelDecimals
            )

            VernierScaleRow(
                title = "Vertical Inclination (Plumb)",
                angleDegrees = animPlumb,
                isLevel = levelState.isVerticalLevel,
                levelColor = levelColor,
                showDecimals = settings.showLevelDecimals
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

@Composable
private fun VernierScaleRow(
    title: String,
    angleDegrees: Float,
    isLevel: Boolean,
    levelColor: Color,
    showDecimals: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val indicatorColor = if (isLevel) levelColor else colorScheme.primary
    val outlineColor = colorScheme.outline

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = formatAngle(angleDegrees, showDecimals),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                fontWeight = FontWeight.Bold,
                color = indicatorColor
            )
        }

        // Precision vernier reticle bar (replaces generic progress bar)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
        ) {
            val midY = size.height / 2f
            val centerX = size.width / 2f

            drawLine(
                color = outlineColor.copy(alpha = 0.30f),
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = 1.2.dp.toPx()
            )

            // 13 graduated vernier ticks across -45°..+45°
            for (step in -6..6) {
                val tx = centerX + (step / 6f) * (size.width * 0.47f)
                val tickH = if (step == 0) 9.dp.toPx() else if (step % 3 == 0) 6.dp.toPx() else 3.5.dp.toPx()
                drawLine(
                    color = if (step == 0) indicatorColor.copy(alpha = 0.7f) else outlineColor.copy(alpha = 0.45f),
                    start = Offset(tx, midY - tickH),
                    end = Offset(tx, midY + tickH),
                    strokeWidth = if (step == 0) 1.6.dp.toPx() else 1.dp.toPx()
                )
            }

            val norm = (angleDegrees / 45f).coerceIn(-1f, 1f)
            val pipX = centerX + norm * (size.width * 0.47f)
            drawCircle(
                color = indicatorColor.copy(alpha = 0.28f),
                radius = 9.dp.toPx(),
                center = Offset(pipX, midY)
            )
            drawCircle(
                color = indicatorColor,
                radius = 4.5.dp.toPx(),
                center = Offset(pipX, midY)
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
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun UnsupportedLevelCard() {
    GlassCard(
        tier = GlassTier.CARD,
        borderColor = StatusWarningAmber.copy(alpha = 0.55f),
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
                    tint = StatusWarningAmber
                )
                Text("Surface Level Unavailable", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "This device does not expose a Gravity, Accelerometer, or Rotation Vector sensor required to measure tilt.",
                style = MaterialTheme.typography.bodyMedium,
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
