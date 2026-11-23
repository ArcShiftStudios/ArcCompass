package com.example.arcpark

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ArcCompassSettings
import com.example.data.GeoNavigationMath
import com.example.data.ParkingSpotEntity
import com.example.sensor.CompassHeadingState
import com.example.sensor.LocationDiagnosticSnapshot
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassPill
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassTier
import com.example.ui.theme.arcSemanticColors
import com.example.ui.theme.arcTextRoles
import java.util.Locale
import kotlin.math.abs

@Composable
fun ArcParkFindModeScreen(
    spot: ParkingSpotEntity,
    locationState: LocationDiagnosticSnapshot,
    headingState: CompassHeadingState,
    settings: ArcCompassSettings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme
    val semanticColors = MaterialTheme.arcSemanticColors
    val textRoles = MaterialTheme.arcTextRoles
    val isDark = colorScheme.background.luminance() < 0.5f

    // Adapt seamlessly across Dark, Light, High Contrast, and Night-Red themes
    val findBg = colorScheme.background
    val findFg = colorScheme.onBackground
    val findMuted = colorScheme.onSurfaceVariant
    val findGlassTint = colorScheme.surfaceVariant
    val accentTint = colorScheme.primary

    val curLat = locationState.latitude
    val curLon = locationState.longitude
    val hasLocationFix = curLat != null && curLon != null

    val distanceMeters = if (curLat != null && curLon != null) {
        GeoNavigationMath.haversineDistanceMeters(curLat, curLon, spot.latitude, spot.longitude)
    } else null

    val absoluteBearing = if (curLat != null && curLon != null) {
        GeoNavigationMath.initialBearingDegrees(curLat, curLon, spot.latitude, spot.longitude)
    } else null

    // Use True North heading when True North declination is active; otherwise Magnetic heading
    val phoneHeading = if (settings.trueNorthEnabled && headingState.trueAzimuthDegrees != null) {
        headingState.trueAzimuthDegrees
    } else {
        headingState.magneticAzimuthDegrees
    }

    val canShowPhoneRelativeArrow = hasLocationFix &&
        headingState.hasReliableOrientation &&
        phoneHeading != null &&
        absoluteBearing != null

    val relativeTurn = if (canShowPhoneRelativeArrow) {
        GeoNavigationMath.relativeTurnDegrees(absoluteBearing, phoneHeading)
    } else null

    var continuousArrowAngle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(relativeTurn) {
        if (relativeTurn != null) {
            val currentMod = ((continuousArrowAngle % 360f) + 360f) % 360f
            val targetMod = ((relativeTurn % 360f) + 360f) % 360f
            val delta = GeoNavigationMath.shortestSignedDelta(currentMod, targetMod)
            continuousArrowAngle += delta
        }
    }

    val animatedArrowRotation by animateFloatAsState(
        targetValue = continuousArrowAngle,
        animationSpec = if (settings.reducedMotionEnabled) {
            snap()
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        },
        label = "arcpark_arrow_rotation"
    )

    var wasAligned by remember { mutableStateOf(false) }
    LaunchedEffect(relativeTurn, settings.hapticFeedbackEnabled) {
        val alignedNow = relativeTurn != null && abs(relativeTurn) <= 15f
        if (settings.hapticFeedbackEnabled && alignedNow && !wasAligned) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        wasAligned = alignedNow
    }

    val isAligned = relativeTurn != null && abs(relativeTurn) <= 18f
    val activeArrowColor = when {
        distanceMeters != null && distanceMeters <= 8.0 && !settings.redScreenFilterEnabled -> semanticColors.success
        else -> findFg
    }

    Surface(
        color = findBg,
        contentColor = findFg,
        modifier = modifier
            .fillMaxSize()
            .testTag("arcpark_find_mode_screen")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter
        ) {
            val minScreenHeight = maxHeight

            // Subtle atmospheric beacon glow behind the dominant arrow
            Canvas(modifier = Modifier.fillMaxSize()) {
                val glowColor = if (distanceMeters != null && distanceMeters <= 8.0 && !settings.redScreenFilterEnabled) {
                    semanticColors.success
                } else {
                    findFg
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.44f),
                        radius = size.minDimension * 0.65f
                    ),
                    radius = size.minDimension * 0.65f,
                    center = Offset(size.width / 2f, size.height * 0.44f)
                )
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(min = minScreenHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Frosted Glass Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    GlassSurface(
                        tier = GlassTier.FLOATING_CHROME,
                        shape = RoundedCornerShape(20.dp),
                        tintColor = findGlassTint,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "ARCPARK FIND",
                                style = textRoles.technical,
                                color = findMuted,
                                letterSpacing = 1.4.sp
                            )
                            Text(
                                text = spot.name,
                                style = textRoles.heading,
                                color = findFg
                            )
                            if (spot.floorOrLevel.isNotBlank()) {
                                Text(
                                    text = "Floor / Level: ${spot.floorOrLevel}",
                                    style = textRoles.description,
                                    color = findMuted
                                )
                            }
                        }
                    }

                    GlassSurface(
                        tier = GlassTier.FLOATING_CHROME,
                        shape = CircleShape,
                        tintColor = findGlassTint,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("arcpark_find_close_button")
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Exit Find Mode",
                                tint = findFg
                            )
                        }
                    }
                }

                // Dominant Directional Arrow or Honest Bearing Fallback
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when {
                        !hasLocationFix -> {
                            GlassCard(tier = GlassTier.CARD, tintColor = findGlassTint) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.WarningAmber,
                                        contentDescription = null,
                                        tint = semanticColors.warning,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Text(
                                        text = "Waiting for current GPS fix…",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = findFg,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = locationState.statusMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = findMuted,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        canShowPhoneRelativeArrow -> {
                            GlassSurface(
                                tier = GlassTier.FLOATING_CHROME,
                                shape = CircleShape,
                                tintColor = findGlassTint.copy(alpha = if (isDark) 0.65f else 0.85f),
                                borderColor = activeArrowColor.copy(alpha = if (isAligned) 0.52f else 0.22f),
                                modifier = Modifier
                                    .size(258.dp)
                                    .testTag("arcpark_directional_arrow")
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val center = Offset(cx, cy)
                                    val radius = size.minDimension / 2f * 0.88f

                                    // Target sector arc at top indicating straight-ahead alignment
                                    drawArc(
                                        color = if (isAligned) {
                                            activeArrowColor.copy(alpha = 0.72f)
                                        } else {
                                            findMuted.copy(alpha = 0.28f)
                                        },
                                        startAngle = -112f,
                                        sweepAngle = 44f,
                                        useCenter = false,
                                        topLeft = Offset(cx - radius, cy - radius),
                                        size = Size(radius * 2f, radius * 2f),
                                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                                    )

                                    if (isAligned) {
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    activeArrowColor.copy(alpha = 0.16f),
                                                    Color.Transparent
                                                ),
                                                center = center,
                                                radius = radius * 0.82f
                                            ),
                                            radius = radius * 0.82f,
                                            center = center
                                        )
                                    }

                                    rotate(degrees = animatedArrowRotation, pivot = center) {
                                        val arrowPath = buildSmoothRoundedArrowPath(center, radius)

                                        // Soft atmospheric drop halo for sculpted depth
                                        drawPath(
                                            path = arrowPath,
                                            color = activeArrowColor.copy(alpha = if (isDark) 0.16f else 0.12f),
                                            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                                        )

                                        // Frosted glass body gradient
                                        val baseFillTop = if (isAligned) {
                                            activeArrowColor.copy(alpha = 0.96f)
                                        } else {
                                            activeArrowColor.copy(alpha = if (isDark) 0.92f else 0.88f)
                                        }
                                        val baseFillBottom = if (isAligned) {
                                            accentTint.copy(alpha = 0.72f)
                                        } else {
                                            activeArrowColor.copy(alpha = if (isDark) 0.54f else 0.62f)
                                        }
                                        drawPath(
                                            path = arrowPath,
                                            brush = Brush.linearGradient(
                                                colors = listOf(baseFillTop, baseFillBottom),
                                                start = Offset(cx - radius * 0.4f, cy - radius * 0.6f),
                                                end = Offset(cx + radius * 0.4f, cy + radius * 0.55f)
                                            )
                                        )

                                        // Subtle top-left glass specular sheen
                                        drawPath(
                                            path = arrowPath,
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = if (settings.redScreenFilterEnabled) 0.10f else 0.34f),
                                                    Color.Transparent
                                                ),
                                                start = Offset(cx - radius * 0.5f, cy - radius * 0.7f),
                                                end = Offset(cx, cy + radius * 0.2f)
                                            )
                                        )

                                        // Crisp translucent rim border
                                        drawPath(
                                            path = arrowPath,
                                            color = if (settings.redScreenFilterEnabled) {
                                                accentTint.copy(alpha = 0.65f)
                                            } else {
                                                Color.White.copy(alpha = if (isDark) 0.45f else 0.70f)
                                            },
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            GlassCard(
                                tier = GlassTier.CARD,
                                tintColor = findGlassTint,
                                borderColor = semanticColors.warning.copy(alpha = 0.48f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("arcpark_unreliable_orientation_banner")
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Explore,
                                        contentDescription = null,
                                        tint = semanticColors.warning,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = if (!headingState.isSupported) {
                                            "Compass sensor unavailable"
                                        } else {
                                            "Compass orientation unreliable"
                                        },
                                        style = textRoles.heading.copy(fontSize = 18.sp),
                                        color = findFg,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Phone-relative arrow is disabled to avoid inaccurate direction. Follow the geographic bearing below.",
                                        style = textRoles.description,
                                        color = findMuted,
                                        textAlign = TextAlign.Center
                                    )
                                    if (absoluteBearing != null) {
                                        Text(
                                            text = String.format(
                                                Locale.US,
                                                "Geographic Bearing: %d° %s",
                                                absoluteBearing.toInt(),
                                                GeoNavigationMath.cardinal16(absoluteBearing)
                                            ),
                                            style = textRoles.measurement,
                                            color = findFg
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Frosted Glass Distance, Bearing & Accuracy Card
                GlassSurface(
                    tier = GlassTier.SHEET,
                    tintColor = findGlassTint,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (distanceMeters != null) {
                            Text(
                                text = GeoNavigationMath.formatDistance(distanceMeters, settings.useImperialUnits),
                                style = textRoles.display.copy(fontSize = 44.sp),
                                color = findFg,
                                modifier = Modifier.testTag("arcpark_find_distance")
                            )
                        }

                        val promptText = when {
                            distanceMeters == null -> "Location fix required"
                            relativeTurn != null -> GeoNavigationMath.relativeDirectionPrompt(relativeTurn, distanceMeters)
                            absoluteBearing != null -> "Head ${GeoNavigationMath.cardinal16(absoluteBearing)} (${absoluteBearing.toInt()}°)"
                            else -> "Bearing unavailable"
                        }
                        Text(
                            text = promptText,
                            style = textRoles.heading,
                            color = findMuted,
                            modifier = Modifier.testTag("arcpark_find_prompt")
                        )

                        if (absoluteBearing != null) {
                            GlassPill(tintColor = findBg.copy(alpha = 0.55f)) {
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "Geographic bearing: %d° %s",
                                        absoluteBearing.toInt(),
                                        GeoNavigationMath.cardinal16(absoluteBearing)
                                    ),
                                    style = textRoles.technical,
                                    color = findFg,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        val travelBearing = locationState.bearingDegrees
                        val speed = locationState.speedMetersPerSecond ?: 0f
                        if (travelBearing != null && speed > 0.8f) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Navigation,
                                    contentDescription = null,
                                    tint = findMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "GPS travel direction: %s (%d°)",
                                        GeoNavigationMath.cardinal8(travelBearing),
                                        travelBearing.toInt()
                                    ),
                                    style = textRoles.technical,
                                    color = findMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        val accText = locationState.horizontalAccuracyMeters?.let {
                            String.format(Locale.US, "Location accuracy: ±%.0f m", it)
                        } ?: "Location accuracy: unavailable"
                        val isPoorGps = (locationState.horizontalAccuracyMeters ?: 999f) > 25f
                        Text(
                            text = if (isPoorGps && hasLocationFix) {
                                "$accText (Low precision — GPS only)"
                            } else {
                                accText
                            },
                            style = textRoles.technical,
                            color = if (isPoorGps && hasLocationFix) semanticColors.warning else findMuted,
                            modifier = Modifier.testTag("arcpark_find_accuracy")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Constructs a large, thick, smoothly rounded sculptural arrow oriented toward 12 o'clock.
 */
private fun buildSmoothRoundedArrowPath(center: Offset, radius: Float): Path {
    val cx = center.x
    val cy = center.y
    val topY = cy - radius * 0.62f
    val headW = radius * 0.56f
    val wingY = cy + radius * 0.04f
    val notchY = cy - radius * 0.05f
    val shaftHalfW = radius * 0.175f
    val tailY = cy + radius * 0.52f

    return Path().apply {
        // Start just left of rounded nose tip and sweep clockwise around smooth curves
        moveTo(cx - radius * 0.075f, topY + radius * 0.095f)
        quadraticTo(cx, topY - radius * 0.04f, cx + radius * 0.075f, topY + radius * 0.095f)

        // Right slope into rounded right wingtip
        lineTo(cx + headW * 0.90f, wingY - radius * 0.12f)
        quadraticTo(cx + headW * 1.04f, wingY + radius * 0.06f, cx + headW * 0.72f, wingY + radius * 0.04f)

        // Smooth inward waist curve to thick shaft
        quadraticTo(cx + shaftHalfW * 1.35f, notchY + radius * 0.08f, cx + shaftHalfW, wingY + radius * 0.09f)

        // Down right side of stem into rounded tail cap
        lineTo(cx + shaftHalfW, tailY - radius * 0.10f)
        quadraticTo(cx + shaftHalfW, tailY + radius * 0.06f, cx, tailY + radius * 0.06f)
        quadraticTo(cx - shaftHalfW, tailY + radius * 0.06f, cx - shaftHalfW, tailY - radius * 0.10f)

        // Up left side of stem & outward waist curve to rounded left wingtip
        lineTo(cx - shaftHalfW, wingY + radius * 0.09f)
        quadraticTo(cx - shaftHalfW * 1.35f, notchY + radius * 0.08f, cx - headW * 0.72f, wingY + radius * 0.04f)
        quadraticTo(cx - headW * 1.04f, wingY + radius * 0.06f, cx - headW * 0.90f, wingY - radius * 0.12f)
        close()
    }
}
