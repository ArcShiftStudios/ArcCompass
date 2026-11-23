package com.example.diagnostics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.sensor.DiagnosticStatus
import com.example.sensor.LiveTelemetrySnapshot
import com.example.sensor.LocationDiagnosticSnapshot
import com.example.sensor.LocationPermissionState
import com.example.sensor.SensorEngineState
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassTier
import com.example.ui.theme.arcSemanticColors
import java.util.Locale

@Composable
internal fun ObservationPill(isObserving: Boolean) {
    val semanticColors = MaterialTheme.arcSemanticColors
    val tint = if (isObserving) semanticColors.success else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(50),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
            Text(
                text = if (isObserving) "LIVE" else "PAUSED",
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun DeviceSummaryCard(
    state: SensorEngineState,
    availableCoreCount: Int
) {
    val device = state.deviceInfo
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        )
    )
    GlassCard(
        tier = GlassTier.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_summary_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${device.manufacturer.replaceFirstChar { it.uppercase() }} ${device.model}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Android ${device.androidVersion} (API ${device.sdkInt}) • ${device.device}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = "Device hardware profile",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryStatTile(
                    label = "Total Sensors",
                    value = device.totalDetectedSensors.toString(),
                    modifier = Modifier.weight(1f)
                )
                SummaryStatTile(
                    label = "Core Ready",
                    value = "$availableCoreCount / ${state.sensorSpecs.size}",
                    modifier = Modifier.weight(1f)
                )
                SummaryStatTile(
                    label = "Compass",
                    value = when (state.capabilities.compass.status) {
                        DiagnosticStatus.AVAILABLE -> "Ready"
                        DiagnosticStatus.INITIALIZING -> "Init"
                        DiagnosticStatus.ACCURACY_UNRELIABLE -> "Calibrate"
                        DiagnosticStatus.UNAVAILABLE -> "No Mag"
                        DiagnosticStatus.PERMISSION_REQUIRED -> "Perm"
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun LiveTelemetrySection(
    telemetry: LiveTelemetrySnapshot,
    locationState: LocationDiagnosticSnapshot,
    hasMagnetometer: Boolean,
    hasBarometer: Boolean,
    onRequestLocationPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryTile(
                title = "Magnetic Field",
                value = when {
                    !hasMagnetometer -> "Unavailable"
                    telemetry.magneticFieldMicroTesla != null ->
                        String.format(Locale.US, "%.1f μT", telemetry.magneticFieldMicroTesla)
                    else -> "Initializing…"
                },
                caption = when {
                    !hasMagnetometer -> "No magnetometer"
                    telemetry.magneticAxes != null -> String.format(
                        Locale.US,
                        "X %.0f Y %.0f Z %.0f",
                        telemetry.magneticAxes.x,
                        telemetry.magneticAxes.y,
                        telemetry.magneticAxes.z
                    )
                    else -> "Awaiting sample"
                },
                icon = Icons.Outlined.CompassCalibration,
                accentColor = if (hasMagnetometer) MaterialTheme.colorScheme.secondary else MaterialTheme.arcSemanticColors.muted,
                modifier = Modifier
                    .weight(1f)
                    .testTag("telemetry_magnetic_tile")
            )

            TelemetryTile(
                title = "Barometer",
                value = when {
                    !hasBarometer -> "Unavailable"
                    telemetry.pressureHpa != null ->
                        String.format(Locale.US, "%.1f hPa", telemetry.pressureHpa)
                    else -> "Initializing…"
                },
                caption = when {
                    !hasBarometer -> "No pressure sensor"
                    telemetry.barometricAltitudeEstimateMeters != null -> String.format(
                        Locale.US,
                        "Est. %.0f m (1013.25 ref)",
                        telemetry.barometricAltitudeEstimateMeters
                    )
                    else -> "Est. requires ref hPa"
                },
                icon = Icons.Outlined.Height,
                accentColor = if (hasBarometer) MaterialTheme.colorScheme.primary else MaterialTheme.arcSemanticColors.muted,
                modifier = Modifier
                    .weight(1f)
                    .testTag("telemetry_barometer_tile")
            )
        }

        LocationTelemetryCard(
            locationState = locationState,
            onRequestLocationPermission = onRequestLocationPermission,
            onOpenLocationSettings = onOpenLocationSettings
        )
    }
}

@Composable
private fun TelemetryTile(
    title: String,
    value: String,
    caption: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(
        tier = GlassTier.CARD,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LocationTelemetryCard(
    locationState: LocationDiagnosticSnapshot,
    onRequestLocationPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit
) {
    GlassCard(
        tier = GlassTier.CARD,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("telemetry_location_card")
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = "Location status",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Android Location & GNSS Altitude",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                StatusBadge(status = locationState.status)
            }

            if (locationState.latitude != null && locationState.longitude != null) {
                Text(
                    text = String.format(
                        Locale.US,
                        "%.6f°, %.6f°  (±%.1f m)",
                        locationState.latitude,
                        locationState.longitude,
                        locationState.horizontalAccuracyMeters ?: 0f
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val altText = locationState.altitudeMeters?.let { alt ->
                    val vAcc = locationState.verticalAccuracyMeters?.let {
                        String.format(Locale.US, " ±%.1f m", it)
                    }.orEmpty()
                    String.format(Locale.US, "GNSS Altitude: %.1f m%s", alt, vAcc)
                } ?: "GNSS Altitude: unavailable in this fix"
                Text(
                    text = "$altText • Provider: ${locationState.providerName ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = locationState.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                locationState.permissionState == LocationPermissionState.DENIED -> {
                    Button(
                        onClick = onRequestLocationPermission,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("grant_location_button")
                    ) {
                        Text(text = stringResource(R.string.action_grant_location))
                    }
                }
                !locationState.isLocationServiceEnabled -> {
                    OutlinedButton(
                        onClick = onOpenLocationSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_location_settings_button")
                    ) {
                        Text(text = stringResource(R.string.action_location_settings))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusBadge(status: DiagnosticStatus) {
    val semanticColors = MaterialTheme.arcSemanticColors
    val (color, text) = when (status) {
        DiagnosticStatus.AVAILABLE ->
            semanticColors.success to stringResource(R.string.status_available)
        DiagnosticStatus.UNAVAILABLE ->
            semanticColors.muted to stringResource(R.string.status_unavailable)
        DiagnosticStatus.PERMISSION_REQUIRED ->
            semanticColors.warning to stringResource(R.string.status_permission_required)
        DiagnosticStatus.INITIALIZING ->
            semanticColors.info to stringResource(R.string.status_initializing)
        DiagnosticStatus.ACCURACY_UNRELIABLE ->
            semanticColors.error to stringResource(R.string.status_accuracy_unreliable)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}
