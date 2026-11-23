package com.example.compass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.ArcCompassSettings
import com.example.data.DialStyle
import com.example.data.ExpressiveDialShape
import com.example.data.GeoNavigationMath
import com.example.data.HeadingFormat
import com.example.data.SavedPlaceEntity
import com.example.sensor.AltitudeSource
import com.example.sensor.CompassHeadingState
import com.example.sensor.LocationPermissionState
import com.example.sensor.SensorEngineState
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassDialog
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassTier
import com.example.ui.theme.arcSemanticColors
import com.example.ui.theme.arcTextRoles
import java.util.Locale

enum class CompassSheetDetent(val label: String) {
    COLLAPSED("Collapsed"),
    HALF_EXPANDED("Half"),
    EXPANDED("Full")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompassInfoBottomSheet(
    detent: CompassSheetDetent,
    onDetentChange: (CompassSheetDetent) -> Unit,
    maxAvailableHeight: Dp,
    bottomClearance: Dp,
    engineState: SensorEngineState,
    headingState: CompassHeadingState,
    settings: ArcCompassSettings,
    savedPlaces: List<SavedPlaceEntity>,
    onToggleTrueNorth: (Boolean) -> Unit,
    onToggleImperial: (Boolean) -> Unit,
    onSelectHeadingFormat: (HeadingFormat) -> Unit,
    onToggleDecimals: (Boolean) -> Unit,
    onSelectDialStyle: (DialStyle) -> Unit,
    onSelectExpressiveShape: (ExpressiveDialShape) -> Unit,
    onOpenCalibration: () -> Unit,
    onRescanHardware: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onSavePlace: (name: String, category: String) -> Unit,
    onDeletePlace: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onToggleRawMode: (Boolean) -> Unit = {},
    onRunDiagnosticCheck: () -> Unit = {}
) {
    val density = LocalDensity.current
    var showAddPlaceDialog by remember { mutableStateOf(false) }

    val collapsedH = (76.dp + bottomClearance).coerceAtMost(maxAvailableHeight * 0.34f)
    val halfH = (maxAvailableHeight * 0.56f).coerceAtLeast(collapsedH + 140.dp)
    val expandedH = (maxAvailableHeight * 0.87f).coerceAtLeast(halfH + 96.dp)

    val targetHeight = when (detent) {
        CompassSheetDetent.COLLAPSED -> collapsedH
        CompassSheetDetent.HALF_EXPANDED -> halfH
        CompassSheetDetent.EXPANDED -> expandedH
    }

    var dragDeltaDp by remember { mutableFloatStateOf(0f) }
    val rawHeight = (targetHeight.value - dragDeltaDp).dp.coerceIn(collapsedH, expandedH)

    val animatedHeight by animateDpAsState(
        targetValue = rawHeight,
        animationSpec = if (settings.reducedMotionEnabled || dragDeltaDp != 0f) {
            snap()
        } else {
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "compass_sheet_height"
    )

    val loc = engineState.locationState
    val hasMag = engineState.capabilities.magneticFieldMonitor.source
    val altMeters = resolveBestAltitudeMeters(engineState)

    GlassSurface(
        tier = GlassTier.SHEET,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        elevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(animatedHeight)
            .testTag("compass_info_sheet")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Draggable Sheet Handle & Compact Telemetry Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { deltaPx ->
                            dragDeltaDp += with(density) { deltaPx.toDp().value }
                        },
                        onDragStopped = { velocity ->
                            val currentVal = (targetHeight.value - dragDeltaDp)
                            val next = when {
                                velocity < -600f -> when (detent) {
                                    CompassSheetDetent.COLLAPSED -> CompassSheetDetent.HALF_EXPANDED
                                    CompassSheetDetent.HALF_EXPANDED,
                                    CompassSheetDetent.EXPANDED -> CompassSheetDetent.EXPANDED
                                }
                                velocity > 600f -> when (detent) {
                                    CompassSheetDetent.EXPANDED -> CompassSheetDetent.HALF_EXPANDED
                                    CompassSheetDetent.HALF_EXPANDED,
                                    CompassSheetDetent.COLLAPSED -> CompassSheetDetent.COLLAPSED
                                }
                                else -> {
                                    val cDist = kotlin.math.abs(currentVal - collapsedH.value)
                                    val hDist = kotlin.math.abs(currentVal - halfH.value)
                                    val eDist = kotlin.math.abs(currentVal - expandedH.value)
                                    when (minOf(cDist, hDist, eDist)) {
                                        eDist -> CompassSheetDetent.EXPANDED
                                        hDist -> CompassSheetDetent.HALF_EXPANDED
                                        else -> CompassSheetDetent.COLLAPSED
                                    }
                                }
                            }
                            dragDeltaDp = 0f
                            onDetentChange(next)
                        }
                    )
                    .clickable {
                        val next = when (detent) {
                            CompassSheetDetent.COLLAPSED -> CompassSheetDetent.HALF_EXPANDED
                            CompassSheetDetent.HALF_EXPANDED -> CompassSheetDetent.EXPANDED
                            CompassSheetDetent.EXPANDED -> CompassSheetDetent.COLLAPSED
                        }
                        onDetentChange(next)
                    }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .testTag("info_panel_toggle"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 42.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Instrument Deck",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val summaryLine = buildString {
                            if (loc.latitude != null && loc.longitude != null) {
                                append(GeoNavigationMath.formatDmsCoordinates(loc.latitude, loc.longitude))
                            } else {
                                append(
                                    when {
                                        loc.permissionState == LocationPermissionState.DENIED -> "Location permission required"
                                        !loc.isLocationServiceEnabled -> "Location services off"
                                        else -> "Acquiring GPS…"
                                    }
                                )
                            }
                            if (altMeters != null) {
                                append("  •  ")
                                append(GeoNavigationMath.formatAltitude(altMeters, settings.useImperialUnits))
                            }
                            if (loc.horizontalAccuracyMeters != null) {
                                append("  •  ±${loc.horizontalAccuracyMeters.toInt()} m")
                            }
                        }
                        Text(
                            text = summaryLine,
                            style = MaterialTheme.arcTextRoles.technical,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier.testTag("sheet_detent_chip")
                        ) {
                            Text(
                                text = detent.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Icon(
                            imageVector = if (detent == CompassSheetDetent.EXPANDED) {
                                Icons.Outlined.ExpandMore
                            } else {
                                Icons.Outlined.ExpandLess
                            },
                            contentDescription = "Cycle sheet height",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Populated Half-Expanded & Full-Expanded Content
            AnimatedVisibility(visible = detent != CompassSheetDetent.COLLAPSED) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = bottomClearance + 22.dp
                        )
                        .testTag("info_panel_content"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Coordinates Card
                    GlassCard(tier = GlassTier.CARD) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.LocationOn,
                                        contentDescription = "Coordinates",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Coordinates",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (loc.availableProviders.isNotEmpty()) {
                                    Text(
                                        text = loc.availableProviders.joinToString("/").uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = when {
                                    !settings.showCoordinates -> "Hidden by preference"
                                    loc.latitude != null && loc.longitude != null ->
                                        GeoNavigationMath.formatDmsCoordinates(loc.latitude, loc.longitude)
                                    loc.permissionState == LocationPermissionState.DENIED ->
                                        "Location permission required"
                                    !loc.isLocationServiceEnabled ->
                                        "Location services disabled"
                                    else -> "Waiting for location fix…"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (settings.showCoordinates && loc.latitude != null && loc.longitude != null) {
                                Text(
                                    text = String.format(Locale.US, "%.6f, %.6f", loc.latitude, loc.longitude),
                                    style = MaterialTheme.arcTextRoles.technical,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (loc.permissionState == LocationPermissionState.DENIED) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = onRequestLocation,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("Grant Location")
                                }
                            } else if (!loc.isLocationServiceEnabled) {
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = onOpenLocationSettings,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("Location Settings")
                                }
                            }
                        }
                    }

                    // 2x2 Telemetry Grid: Altitude, Magnetic Field, Location Accuracy, Sensor Accuracy
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val altTitle = if (engineState.capabilities.altitude.source == AltitudeSource.BAROMETRIC_ESTIMATE) {
                            "Altitude (Baro)"
                        } else {
                            "Altitude (GNSS)"
                        }
                        SheetMetricTile(
                            label = altTitle,
                            value = when {
                                !settings.showAltitude -> "Hidden"
                                altMeters != null -> GeoNavigationMath.formatAltitude(altMeters, settings.useImperialUnits)
                                else -> "Unavailable"
                            },
                            icon = Icons.Outlined.Height,
                            modifier = Modifier.weight(1f)
                        )
                        val uT = engineState.telemetry.magneticFieldMicroTesla
                        SheetMetricTile(
                            label = "Magnetic Field",
                            value = when {
                                !settings.showMagneticField -> "Hidden"
                                !hasMag -> "Unavailable"
                                uT != null -> String.format(Locale.US, "%.1f μT", uT)
                                else -> "Initializing"
                            },
                            icon = Icons.Outlined.CompassCalibration,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SheetMetricTile(
                            label = "Location Accuracy",
                            value = loc.horizontalAccuracyMeters?.let {
                                String.format(Locale.US, "±%.1f m", it)
                            } ?: "Unavailable",
                            icon = Icons.Outlined.LocationOn,
                            modifier = Modifier.weight(1f)
                        )
                        SheetMetricTile(
                            label = "Sensor Accuracy",
                            value = if (headingState.isSupported) headingState.accuracy.label else "Unavailable",
                            icon = Icons.Outlined.CompassCalibration,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onOpenCalibration)
                        )
                    }

                    // Saved Places always surfaced in Half & Full states
                    GlassCard(
                        tier = GlassTier.CARD,
                        modifier = Modifier.testTag("saved_places_section")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.BookmarkBorder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Saved Places (${savedPlaces.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Button(
                                    onClick = { showAddPlaceDialog = true },
                                    enabled = loc.latitude != null && loc.longitude != null,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("save_place_button")
                                ) {
                                    Icon(Icons.Outlined.AddLocationAlt, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Pin Here")
                                }
                            }

                            if (savedPlaces.isEmpty()) {
                                Text(
                                    text = if (loc.latitude != null && loc.longitude != null) {
                                        "No saved places yet. Tap Pin Here to save these coordinates offline."
                                    } else {
                                        "Acquire a GPS fix to bookmark places offline."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                val displayedPlaces = if (detent == CompassSheetDetent.HALF_EXPANDED) {
                                    savedPlaces.take(2)
                                } else {
                                    savedPlaces
                                }
                                displayedPlaces.forEach { place ->
                                    SavedPlaceRow(
                                        place = place,
                                        currentLat = loc.latitude,
                                        currentLon = loc.longitude,
                                        imperial = settings.useImperialUnits,
                                        onDelete = { onDeletePlace(place.id) }
                                    )
                                }
                            }
                        }
                    }

                    // Full-Expanded Quick Instrument Controls (no duplicate Settings navigation button)
                    if (detent == CompassSheetDetent.EXPANDED) {
                        GlassCard(tier = GlassTier.CARD) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "True North Correction",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val declMsg = headingState.declinationDegrees?.let {
                                        String.format(Locale.US, "Geomagnetic declination %+.1f°", it)
                                    } ?: "Requires GPS fix to compute declination"
                                    Text(
                                        text = declMsg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (settings.trueNorthEnabled && headingState.declinationDegrees == null) {
                                            MaterialTheme.arcSemanticColors.warning
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                Switch(
                                    checked = settings.trueNorthEnabled,
                                    onCheckedChange = onToggleTrueNorth,
                                    modifier = Modifier.testTag("sheet_true_north_switch")
                                )
                            }
                        }

                        GlassCard(tier = GlassTier.CARD) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Units & Formats",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = !settings.useImperialUnits,
                                        onClick = { onToggleImperial(false) },
                                        label = { Text("Metric (m)") }
                                    )
                                    FilterChip(
                                        selected = settings.useImperialUnits,
                                        onClick = { onToggleImperial(true) },
                                        label = { Text("Imperial (ft)") }
                                    )
                                    FilterChip(
                                        selected = settings.headingFormat == HeadingFormat.AZIMUTH,
                                        onClick = { onSelectHeadingFormat(HeadingFormat.AZIMUTH) },
                                        label = { Text("360° Azimuth") }
                                    )
                                    FilterChip(
                                        selected = settings.headingFormat == HeadingFormat.QUADRANT,
                                        onClick = { onSelectHeadingFormat(HeadingFormat.QUADRANT) },
                                        label = { Text("Quadrant") }
                                    )
                                    FilterChip(
                                        selected = settings.showHeadingDecimals,
                                        onClick = { onToggleDecimals(!settings.showHeadingDecimals) },
                                        label = { Text("0.1° Decimals") }
                                    )
                                }
                            }
                        }

                        GlassCard(tier = GlassTier.CARD) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Explore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Instrument Quick Controls",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    DialStyle.entries.forEach { style ->
                                        FilterChip(
                                            selected = settings.dialStyle == style,
                                            onClick = { onSelectDialStyle(style) },
                                            label = { Text("${style.label} Dial") }
                                        )
                                    }
                                    if (settings.dialStyle == DialStyle.EXPRESSIVE) {
                                        ExpressiveDialShape.entries.forEach { shape ->
                                            FilterChip(
                                                selected = settings.expressiveDialShape == shape,
                                                onClick = { onSelectExpressiveShape(shape) },
                                                label = { Text(shape.label) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            )
                                        }
                                    }
                                    FilterChip(
                                        selected = settings.rawModeEnabled,
                                        onClick = { onToggleRawMode(!settings.rawModeEnabled) },
                                        label = { Text("RAW Mode") },
                                        modifier = Modifier.testTag("sheet_raw_mode_chip")
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(onClick = onRescanHardware) {
                                        Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Rescan Hardware")
                                    }
                                    OutlinedButton(
                                        onClick = onRunDiagnosticCheck,
                                        modifier = Modifier.testTag("sheet_sensor_check_button")
                                    ) {
                                        Icon(Icons.Outlined.Explore, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Sensor Check")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddPlaceDialog) {
        SavePlaceGlassDialog(
            onSave = { name, category ->
                onSavePlace(name, category)
                showAddPlaceDialog = false
            },
            onDismiss = { showAddPlaceDialog = false }
        )
    }
}

@Composable
private fun SavedPlaceRow(
    place: SavedPlaceEntity,
    currentLat: Double?,
    currentLon: Double?,
    imperial: Boolean,
    onDelete: () -> Unit
) {
    val distAndBearing = if (currentLat != null && currentLon != null) {
        val dist = GeoNavigationMath.haversineDistanceMeters(
            currentLat, currentLon, place.latitude, place.longitude
        )
        val brg = GeoNavigationMath.initialBearingDegrees(
            currentLat, currentLon, place.latitude, place.longitude
        )
        "${GeoNavigationMath.formatDistance(dist, imperial)} • ${brg.toInt()}° ${GeoNavigationMath.cardinal16(brg)}"
    } else {
        GeoNavigationMath.formatDmsCoordinates(place.latitude, place.longitude)
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${place.category} • $distAndBearing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete saved place",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SheetMetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    GlassCard(tier = GlassTier.CARD, modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SavePlaceGlassDialog(
    onSave: (name: String, category: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("Trailhead") }
    var category by remember { mutableStateOf("Waypoint") }

    GlassDialog(onDismissRequest = onDismiss) {
        Text(
            text = "Pin Current Location",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Place name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Category") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSave(name, category) }) { Text("Save Place") }
        }
    }
}

private fun resolveBestAltitudeMeters(engineState: SensorEngineState): Double? {
    return if (engineState.capabilities.altitude.source == AltitudeSource.BAROMETRIC_ESTIMATE) {
        engineState.telemetry.barometricAltitudeEstimateMeters ?: engineState.locationState.altitudeMeters
    } else {
        engineState.locationState.altitudeMeters
    }
}
