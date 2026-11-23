package com.example.compass

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ArcCompassSettings
import com.example.data.CompassSensorMode
import com.example.data.DialStyle
import com.example.data.ExpressiveDialShape
import com.example.data.GeoNavigationMath
import com.example.data.HeadingFormat
import com.example.data.SavedPlaceEntity
import com.example.sensor.AltitudeSource
import com.example.sensor.CompassDiagnosticsState
import com.example.sensor.CompassHeadingState
import com.example.sensor.CompassSource
import com.example.sensor.DiagnosticStatus
import com.example.sensor.SensorAccuracyLevel
import com.example.sensor.SensorEngineState
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassDialog
import com.example.ui.theme.GlassPill
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassTier
import com.example.ui.theme.arcSemanticColors
import com.example.ui.theme.arcTextRoles
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CompassScreen(
    engineState: SensorEngineState,
    headingState: CompassHeadingState,
    settings: ArcCompassSettings,
    onToggleTrueNorth: (Boolean) -> Unit,
    onToggleDialStyle: (DialStyle) -> Unit,
    onOpenSettings: () -> Unit = {},
    onLocationPermissionResult: () -> Unit,
    modifier: Modifier = Modifier,
    savedPlaces: List<SavedPlaceEntity> = emptyList(),
    onSelectExpressiveShape: (ExpressiveDialShape) -> Unit = {},
    onToggleImperial: (Boolean) -> Unit = {},
    onSelectHeadingFormat: (HeadingFormat) -> Unit = {},
    onToggleDecimals: (Boolean) -> Unit = {},
    onRescanHardware: () -> Unit = {},
    onSavePlace: (name: String, category: String, lat: Double, lon: Double, alt: Double?) -> Unit = { _, _, _, _, _ -> },
    onDeletePlace: (Long) -> Unit = {},
    compassDiagnosticsState: CompassDiagnosticsState = CompassDiagnosticsState(),
    onToggleRawMode: (Boolean) -> Unit = {},
    onRequestDiagnosticCheck: () -> Unit = {},
    onStartDiagnosticCheck: () -> Unit = {},
    onCancelDiagnosticCheck: () -> Unit = {},
    onDismissAnomalyBanner: () -> Unit = {},
    onDismissInfoBanner: () -> Unit = {},
    onDismissMagneticBanner: () -> Unit = {},
    onDismissOrientationBanner: () -> Unit = {},
    onMarkGuidanceSeen: () -> Unit = {},
    onOpenCalibrationSettings: () -> Unit = onOpenSettings,
    onResetCompassRecovery: () -> Unit = {},
    bottomNavClearance: Dp = 16.dp
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var sheetDetent by remember { mutableStateOf(CompassSheetDetent.COLLAPSED) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onLocationPermissionResult()
    }

    val trueNorthActive = settings.trueNorthEnabled && headingState.trueAzimuthDegrees != null
    val displayedHeading = if (trueNorthActive) {
        headingState.trueAzimuthDegrees
    } else {
        headingState.magneticAzimuthDegrees
    }

    // Subtle haptic tick when crossing 30-degree major headings
    var lastMajorSector by remember { mutableIntStateOf(-1) }
    LaunchedEffect(displayedHeading, settings.hapticFeedbackEnabled) {
        if (settings.hapticFeedbackEnabled && displayedHeading != null && headingState.hasReliableOrientation) {
            val sector = (displayedHeading.roundToInt() % 360) / 30
            if (lastMajorSector != -1 && sector != lastMajorSector) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            lastMajorSector = sector
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("compass_screen")
    ) {
        val screenHeight = maxHeight
        val isWideLandscape = maxWidth > maxHeight && maxWidth >= 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 10.dp,
                    end = 10.dp,
                    top = 8.dp,
                    bottom = 104.dp + bottomNavClearance
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompassHeaderBar(
                headingState = headingState,
                engineState = engineState,
                settings = settings,
                trueNorthActive = trueNorthActive,
                onToggleTrueNorth = { onToggleTrueNorth(!settings.trueNorthEnabled) },
                onAccuracyClick = onOpenCalibrationSettings,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            CompassConditionWarningBanners(
                diagnosticsState = compassDiagnosticsState,
                onRunCheck = onRequestDiagnosticCheck,
                onDismissAnomaly = onDismissAnomalyBanner,
                onDismissMagneticInterference = onDismissMagneticBanner,
                onDismissVerticalOrientation = onDismissOrientationBanner
            )

            CompassInfoToastBanner(
                infoBanner = compassDiagnosticsState.infoBanner,
                onDismiss = onDismissInfoBanner
            )

            if (settings.rawModeEnabled) {
                CompassRawModeDeck(
                    engineState = engineState,
                    headingState = headingState,
                    onExitRawMode = { onToggleRawMode(false) }
                )

                StatusBanners(
                    headingState = headingState,
                    settings = settings,
                    onCalibrationClick = onOpenCalibrationSettings
                )
            } else {
                // Integrated frosted-glass dial mode + Expressive silhouette selector bar
                DialCustomizationGlassBar(
                    dialStyle = settings.dialStyle,
                    expressiveShape = settings.expressiveDialShape,
                    onToggleDialStyle = onToggleDialStyle,
                    onSelectExpressiveShape = onSelectExpressiveShape,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                if (isWideLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompassDial(
                            headingDegrees = displayedHeading,
                            headingState = headingState,
                            dialStyle = settings.dialStyle,
                            expressiveShape = settings.expressiveDialShape,
                            expressiveAccentPreset = settings.expressiveAccentPreset,
                            headingFormat = settings.headingFormat,
                            showDecimals = settings.showHeadingDecimals,
                            trueNorthActive = trueNorthActive,
                            reducedMotion = settings.reducedMotionEnabled,
                            largeReadouts = settings.largeReadoutsEnabled,
                            nightFilterActive = settings.redScreenFilterEnabled,
                            modifier = Modifier.weight(1f)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CompassLiveTelemetryPill(engineState = engineState, settings = settings)
                            if (settings.isLoaded && !settings.hasSeenCalibrationGuidance) {
                                FirstLaunchCalibrationBanner(
                                    onOpenCalibration = {
                                        onMarkGuidanceSeen()
                                        onOpenCalibrationSettings()
                                    },
                                    onDismiss = onMarkGuidanceSeen
                                )
                            }
                            StatusBanners(
                                headingState = headingState,
                                settings = settings,
                                onCalibrationClick = onOpenCalibrationSettings
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                    CompassDial(
                        headingDegrees = displayedHeading,
                        headingState = headingState,
                        dialStyle = settings.dialStyle,
                        expressiveShape = settings.expressiveDialShape,
                        expressiveAccentPreset = settings.expressiveAccentPreset,
                        headingFormat = settings.headingFormat,
                        showDecimals = settings.showHeadingDecimals,
                        trueNorthActive = trueNorthActive,
                        reducedMotion = settings.reducedMotionEnabled,
                        largeReadouts = settings.largeReadoutsEnabled,
                        nightFilterActive = settings.redScreenFilterEnabled
                    )

                    CompassLiveTelemetryPill(engineState = engineState, settings = settings)

                    if (settings.isLoaded && !settings.hasSeenCalibrationGuidance) {
                        FirstLaunchCalibrationBanner(
                            onOpenCalibration = {
                                onMarkGuidanceSeen()
                                onOpenCalibrationSettings()
                            },
                            onDismiss = onMarkGuidanceSeen
                        )
                    }

                    StatusBanners(
                        headingState = headingState,
                        settings = settings,
                        onCalibrationClick = onOpenCalibrationSettings
                    )
                }
            }
        }

        // Draggable Frosted-Glass Expandable Bottom Sheet
        CompassInfoBottomSheet(
            detent = sheetDetent,
            onDetentChange = { sheetDetent = it },
            maxAvailableHeight = screenHeight,
            bottomClearance = bottomNavClearance,
            engineState = engineState,
            headingState = headingState,
            settings = settings,
            savedPlaces = savedPlaces,
            onToggleTrueNorth = onToggleTrueNorth,
            onToggleImperial = onToggleImperial,
            onSelectHeadingFormat = onSelectHeadingFormat,
            onToggleDecimals = onToggleDecimals,
            onSelectDialStyle = onToggleDialStyle,
            onSelectExpressiveShape = onSelectExpressiveShape,
            onToggleRawMode = onToggleRawMode,
            onRunDiagnosticCheck = onRequestDiagnosticCheck,
            onOpenCalibration = onOpenCalibrationSettings,
            onRescanHardware = onRescanHardware,
            onRequestLocation = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            },
            onOpenLocationSettings = {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            onSavePlace = { name, category ->
                val loc = engineState.locationState
                val lat = loc.latitude
                val lon = loc.longitude
                if (lat != null && lon != null) {
                    val alt = if (engineState.capabilities.altitude.source == AltitudeSource.BAROMETRIC_ESTIMATE) {
                        engineState.telemetry.barometricAltitudeEstimateMeters ?: loc.altitudeMeters
                    } else {
                        loc.altitudeMeters
                    }
                    onSavePlace(name, category, lat, lon, alt)
                }
            },
            onDeletePlace = onDeletePlace,
            modifier = Modifier
                .widthIn(max = 680.dp)
                .align(Alignment.BottomCenter)
        )
    }

    CompassInteractiveCheckDialog(
        diagnosticsState = compassDiagnosticsState,
        onConfirmStart = onStartDiagnosticCheck,
        onCancel = onCancelDiagnosticCheck,
        onResetRecovery = onResetCompassRecovery
    )
}

@Composable
private fun CompassHeaderBar(
    headingState: CompassHeadingState,
    engineState: SensorEngineState,
    settings: ArcCompassSettings,
    trueNorthActive: Boolean,
    onToggleTrueNorth: () -> Unit,
    onAccuracyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.brand_studio),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Single unified frosted-glass telemetry capsule (no extra Settings circular control)
        val semanticColors = MaterialTheme.arcSemanticColors
        val accColor = when (headingState.accuracy) {
            SensorAccuracyLevel.HIGH -> semanticColors.success
            SensorAccuracyLevel.MEDIUM -> MaterialTheme.colorScheme.primary
            SensorAccuracyLevel.LOW -> semanticColors.warning
            SensorAccuracyLevel.UNRELIABLE,
            SensorAccuracyLevel.NO_CONTACT -> semanticColors.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        GlassSurface(
            tier = GlassTier.FLOATING_CHROME,
            shape = RoundedCornerShape(50),
            elevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (trueNorthActive) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                            }
                        )
                        .clickable(onClick = onToggleTrueNorth)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("north_mode_pill"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (settings.trueNorthEnabled) "T" else "M",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (trueNorthActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                if (settings.showMagneticField && engineState.capabilities.magneticFieldMonitor.source) {
                    val uT = engineState.telemetry.magneticFieldMicroTesla
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uT != null) String.format(Locale.US, "%.0f μT", uT) else "-- μT",
                            style = MaterialTheme.arcTextRoles.technical,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(50))
                        .background(accColor.copy(alpha = 0.16f))
                        .clickable(onClick = onAccuracyClick)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("compass_accuracy_pill"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when {
                            !headingState.isSupported -> "N/A"
                            headingState.accuracy == SensorAccuracyLevel.HIGH -> "3"
                            headingState.accuracy == SensorAccuracyLevel.MEDIUM -> "2"
                            headingState.accuracy == SensorAccuracyLevel.LOW -> "1"
                            headingState.accuracy == SensorAccuracyLevel.UNRELIABLE -> "CAL"
                            else -> "•"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accColor
                    )
                    Icon(
                        imageVector = Icons.Outlined.CompassCalibration,
                        contentDescription = "Sensor accuracy and calibration",
                        tint = accColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DialCustomizationGlassBar(
    dialStyle: DialStyle,
    expressiveShape: ExpressiveDialShape,
    onToggleDialStyle: (DialStyle) -> Unit,
    onSelectExpressiveShape: (ExpressiveDialShape) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        tier = GlassTier.CARD,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
                    .clickable {
                        val next = if (dialStyle == DialStyle.EXPRESSIVE) {
                            DialStyle.PRECISION
                        } else {
                            DialStyle.EXPRESSIVE
                        }
                        onToggleDialStyle(next)
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("dial_style_toggle"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${dialStyle.label} Dial",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            AnimatedVisibility(
                visible = dialStyle == DialStyle.EXPRESSIVE,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 22.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    )
                    Spacer(Modifier.width(2.dp))
                    ExpressiveDialShape.entries.forEach { shape ->
                        val selected = shape == expressiveShape
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    } else {
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.20f)
                                    }
                                )
                                .clickable { onSelectExpressiveShape(shape) }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                                .testTag("expressive_shape_chip_${shape.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = shape.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompassLiveTelemetryPill(
    engineState: SensorEngineState,
    settings: ArcCompassSettings
) {
    val loc = engineState.locationState
    val altMeters = if (engineState.capabilities.altitude.source == AltitudeSource.BAROMETRIC_ESTIMATE) {
        engineState.telemetry.barometricAltitudeEstimateMeters ?: loc.altitudeMeters
    } else {
        loc.altitudeMeters
    }

    val hasCoords = settings.showCoordinates && loc.latitude != null && loc.longitude != null
    val hasAlt = settings.showAltitude && altMeters != null
    if (!hasCoords && !hasAlt) return

    GlassPill(
        tintColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (hasCoords) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = GeoNavigationMath.formatDmsCoordinates(loc.latitude, loc.longitude),
                        style = MaterialTheme.arcTextRoles.technical,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (hasAlt) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Height,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = GeoNavigationMath.formatAltitude(altMeters, settings.useImperialUnits),
                        style = MaterialTheme.arcTextRoles.technical,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBanners(
    headingState: CompassHeadingState,
    settings: ArcCompassSettings,
    onCalibrationClick: () -> Unit
) {
    if (!headingState.isSupported) {
        UnsupportedCompassBanner()
    } else if (headingState.status == DiagnosticStatus.ACCURACY_UNRELIABLE) {
        CalibrationWarningBanner(onClick = onCalibrationClick)
    }

    if (settings.trueNorthEnabled && headingState.declinationDegrees == null) {
        val warningColor = MaterialTheme.arcSemanticColors.warning
        GlassPill(
            tintColor = warningColor.copy(alpha = 0.14f),
            borderColor = warningColor.copy(alpha = 0.45f)
        ) {
            Text(
                text = "True North awaiting GPS fix for declination",
                style = MaterialTheme.typography.labelSmall,
                color = warningColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun UnsupportedCompassBanner() {
    val warningColor = MaterialTheme.arcSemanticColors.warning
    GlassCard(
        tier = GlassTier.CARD,
        borderColor = warningColor.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .testTag("unsupported_compass_banner")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = warningColor
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Compass hardware unavailable",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "This device lacks a usable magnetometer or rotation vector sensor. Level and GPS features remain available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CalibrationWarningBanner(onClick: () -> Unit) {
    val errorColor = MaterialTheme.arcSemanticColors.error
    GlassSurface(
        tier = GlassTier.FLOATING_CHROME,
        shape = MaterialTheme.shapes.medium,
        tintColor = errorColor.copy(alpha = 0.14f),
        borderColor = errorColor.copy(alpha = 0.50f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CompassCalibration,
                contentDescription = "Calibration recommended",
                tint = errorColor
            )
            Text(
                text = "Compass accuracy unreliable. Tap to open Sensor Calibration in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalibrationGuideDialog(
    accuracy: SensorAccuracyLevel,
    microTesla: Float?,
    hasMagnetometer: Boolean,
    onDismiss: () -> Unit,
    sensorMode: CompassSensorMode = CompassSensorMode.AUTO_DEFAULT,
    compassSource: CompassSource = CompassSource.ROTATION_VECTOR,
    diagnosticStatus: DiagnosticStatus = DiagnosticStatus.AVAILABLE,
    onRunSensorCheck: (() -> Unit)? = null,
    onResetToDefault: (() -> Unit)? = null
) {
    GlassDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag("calibration_guide_dialog"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Sensor Calibration",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Move your phone slowly in a figure-8 pattern across all three axes for 5–10 seconds, away from metal cases, magnets, or electronics.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            GlassCard(tier = GlassTier.CARD) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "Sensor mode: ${sensorMode.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Active source: ${compassSource.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Status: ${diagnosticStatus.label} • Accuracy: ${accuracy.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            !hasMagnetometer -> "Magnetometer: Not present on hardware"
                            microTesla != null -> String.format(
                                Locale.US,
                                "Ambient field: %.1f μT (Earth nominal: 25–65 μT)",
                                microTesla
                            )
                            else -> "Ambient field: Initializing…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            GlassCard(tier = GlassTier.CARD) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "When calibration is recommended",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Accuracy drops to Low or Unreliable\n• After magnetic interference near speakers, metal, or chargers\n• After traveling a significant distance or changing cases",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onResetToDefault != null &&
                    (sensorMode != CompassSensorMode.AUTO_DEFAULT || compassSource != CompassSource.ROTATION_VECTOR)
                ) {
                    TextButton(
                        onClick = {
                            onResetToDefault()
                            onDismiss()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("calibration_use_default_button")
                    ) {
                        Text("Use Default", maxLines = 1, softWrap = false)
                    }
                }
                if (onRunSensorCheck != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onRunSensorCheck()
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("calibration_run_check_button")
                    ) {
                        Text("Run Sensor Check", maxLines = 1, softWrap = false)
                    }
                }
                Button(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("calibration_done_button")
                ) {
                    Text("Done", maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
