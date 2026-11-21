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
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StatusAvailableGreen
import com.example.ui.theme.StatusWarningAmber
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

    // Minimal dark surface inspired by Apple Find My, adapting cleanly to Red Night Filter
    val findBg = if (settings.redScreenFilterEnabled) Color.Black else Color(0xFF0A0D12)
    val findFg = if (settings.redScreenFilterEnabled) MaterialTheme.colorScheme.primary else Color(0xFFF8FAFC)
    val findMuted = if (settings.redScreenFilterEnabled) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF94A3B8)
    val findGlassTint = if (settings.redScreenFilterEnabled) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF171D26)

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

    val relativeTurn = if (canShowPhoneRelativeArrow && absoluteBearing != null && phoneHeading != null) {
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
        distanceMeters != null && distanceMeters <= 8.0 && !settings.redScreenFilterEnabled -> StatusAvailableGreen
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
                    StatusAvailableGreen
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
                                style = MaterialTheme.typography.labelSmall,
                                color = findMuted,
                                letterSpacing = 1.6.sp
                            )
                            Text(
                                text = spot.name,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = findFg
                            )
                            if (spot.floorOrLevel.isNotBlank()) {
                                Text(
                                    text = "Floor / Level: ${spot.floorOrLevel}",
                                    style = MaterialTheme.typography.bodySmall,
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
                                        tint = StatusWarningAmber,
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
                                tintColor = findGlassTint.copy(alpha = 0.65f),
                                borderColor = activeArrowColor.copy(alpha = if (isAligned) 0.45f else 0.16f),
                                modifier = Modifier
                                    .size(236.dp)
                                    .testTag("arcpark_directional_arrow")
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val radius = size.minDimension / 2f * 0.85f

                                    // Target sector arc at top indicating straight-ahead alignment
                                    drawArc(
                                        color = findMuted.copy(alpha = 0.28f),
                                        startAngle = -110f,
                                        sweepAngle = 40f,
                                        useCenter = false,
                                        topLeft = Offset(cx - radius, cy - radius),
                                        size = Size(radius * 2f, radius * 2f),
                                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                                    )

                                    if (isAligned) {
                                        drawCircle(
                                            color = activeArrowColor.copy(alpha = 0.12f),
                                            radius = radius * 0.72f,
                                            center = Offset(cx, cy)
                                        )
                                    }

                                    rotate(degrees = animatedArrowRotation, pivot = Offset(cx, cy)) {
                                        val shaftHalfW = radius * 0.115f
                                        val headW = radius * 0.46f
                                        val topY = cy - radius * 0.56f
                                        val neckY = cy - radius * 0.08f
                                        val tailY = cy + radius * 0.48f

                                        val arrowPath = Path().apply {
                                            moveTo(cx, topY)
                                            lineTo(cx + headW, neckY)
                                            lineTo(cx + shaftHalfW, neckY)
                                            lineTo(cx + shaftHalfW, tailY)
                                            lineTo(cx - shaftHalfW, tailY)
                                            lineTo(cx - shaftHalfW, neckY)
                                            lineTo(cx - headW, neckY)
                                            close()
                                        }
                                        drawPath(path = arrowPath, color = activeArrowColor)
                                    }
                                }
                            }
                        }

                        else -> {
                            GlassCard(
                                tier = GlassTier.CARD,
                                tintColor = findGlassTint,
                                borderColor = StatusWarningAmber.copy(alpha = 0.48f),
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
                                        tint = StatusWarningAmber,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = if (!headingState.isSupported) {
                                            "Compass sensor unavailable"
                                        } else {
                                            "Compass orientation unreliable"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = findFg,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Phone-relative arrow is disabled to avoid inaccurate direction. Follow the geographic bearing below.",
                                        style = MaterialTheme.typography.bodySmall,
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
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = JetBrainsMonoFontFamily
                                            ),
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
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontSize = 42.sp,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
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
                            style = MaterialTheme.typography.headlineSmall,
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
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
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
                                    style = MaterialTheme.typography.labelMedium,
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
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = if (isPoorGps && hasLocationFix) StatusWarningAmber else findMuted,
                            modifier = Modifier.testTag("arcpark_find_accuracy")
                        )
                    }
                }
            }
        }
    }
}
