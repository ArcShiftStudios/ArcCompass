package com.example.diagnostics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.sensor.CapabilityReport
import com.example.sensor.DiagnosticStatus
import com.example.sensor.LiveTelemetrySnapshot
import com.example.sensor.LocationDiagnosticSnapshot
import com.example.sensor.LocationPermissionState
import com.example.sensor.RequiredSensorType
import com.example.sensor.SensorSpec
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassTier
import com.example.ui.theme.StatusUnavailableSlate
import java.util.Locale

@Composable
internal fun CapabilityMatrixSection(
    capabilities: CapabilityReport,
    locationState: LocationDiagnosticSnapshot
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CapabilityRowCard(
            title = stringResource(R.string.cap_compass),
            summary = capabilities.compass.source.label,
            detail = capabilities.compass.detail,
            status = capabilities.compass.status,
            icon = Icons.Outlined.Explore,
            testTag = "capability_compass_card"
        )
        CapabilityRowCard(
            title = stringResource(R.string.cap_level),
            summary = capabilities.level.source.label,
            detail = capabilities.level.detail,
            status = capabilities.level.status,
            icon = Icons.Outlined.Straighten,
            testTag = "capability_level_card"
        )
        CapabilityRowCard(
            title = stringResource(R.string.cap_magnetic_monitor),
            summary = capabilities.magneticFieldMonitor.summary,
            detail = capabilities.magneticFieldMonitor.detail,
            status = capabilities.magneticFieldMonitor.status,
            icon = Icons.Outlined.CompassCalibration,
            testTag = "capability_magnetic_card"
        )
        CapabilityRowCard(
            title = stringResource(R.string.cap_gyro_diagnostics),
            summary = capabilities.gyroscopeDiagnostics.summary,
            detail = capabilities.gyroscopeDiagnostics.detail,
            status = capabilities.gyroscopeDiagnostics.status,
            icon = Icons.Outlined.ScreenRotation,
            testTag = "capability_gyro_card"
        )
        CapabilityRowCard(
            title = stringResource(R.string.cap_location),
            summary = "${locationState.permissionState.label} • Providers: ${locationState.availableProviders.size}",
            detail = capabilities.location.detail,
            status = capabilities.location.status,
            icon = Icons.Outlined.LocationOn,
            testTag = "capability_location_card"
        )
        CapabilityRowCard(
            title = stringResource(R.string.cap_altitude),
            summary = capabilities.altitude.source.label,
            detail = capabilities.altitude.detail,
            status = capabilities.altitude.status,
            icon = Icons.Outlined.Height,
            testTag = "capability_altitude_card"
        )
    }
}

@Composable
private fun CapabilityRowCard(
    title: String,
    summary: String,
    detail: String,
    status: DiagnosticStatus,
    icon: ImageVector,
    testTag: String
) {
    GlassCard(
        tier = GlassTier.CARD,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(status = status)
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SensorFilterRow(
    selectedFilter: SensorListFilter,
    allCount: Int,
    availableCount: Int,
    unavailableCount: Int,
    onSelectFilter: (SensorListFilter) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SensorListFilter.entries.forEach { filter ->
            val count = when (filter) {
                SensorListFilter.ALL -> allCount
                SensorListFilter.AVAILABLE -> availableCount
                SensorListFilter.UNAVAILABLE -> unavailableCount
            }
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onSelectFilter(filter) },
                label = { Text("${filter.label} ($count)") },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("filter_chip_${filter.name.lowercase()}")
            )
        }
    }
}

@Composable
internal fun SensorSpecificationCard(
    spec: SensorSpec,
    telemetry: LiveTelemetrySnapshot
) {
    val liveSampleText = remember(spec.sensorType, telemetry) {
        formatLiveSample(spec.sensorType, telemetry)
    }

    GlassCard(
        tier = GlassTier.CARD,
        shape = MaterialTheme.shapes.medium,
        tintColor = if (spec.isAvailable) {
            null
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sensor_card_${spec.sensorType.name.lowercase()}")
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
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Sensors,
                        contentDescription = spec.sensorType.displayName,
                        tint = if (spec.isAvailable) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            StatusUnavailableSlate
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = spec.sensorType.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = spec.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(status = spec.status)
            }

            if (spec.isAvailable) {
                if (liveSampleText != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ) {
                        Text(
                            text = liveSampleText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SpecRow("Vendor", spec.vendor, "Version", "v${spec.version}")
                    SpecRow(
                        "Power",
                        String.format(Locale.US, "%.2f mA", spec.powerMa),
                        "Accuracy",
                        spec.accuracy.label
                    )
                    SpecRow(
                        "Resolution",
                        formatScientificOrDecimal(spec.resolution, spec.sensorType.unitLabel),
                        "Max Range",
                        formatScientificOrDecimal(spec.maxRange, spec.sensorType.unitLabel)
                    )
                    SpecRow(
                        "Reporting",
                        spec.reportingMode.label,
                        "Wake-up",
                        if (spec.isWakeUpSensor) "Yes" else "No"
                    )
                }
            } else {
                Text(
                    text = "Hardware sensor not present on this device. Dependent features are cleanly disabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SpecRow(
    label1: String,
    value1: String,
    label2: String,
    value2: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SpecCell(label = label1, value = value1, modifier = Modifier.weight(1f))
        SpecCell(label = label2, value = value2, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SpecCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun EmptySensorFilterCard(filter: SensorListFilter) {
    GlassCard(
        tier = GlassTier.CARD,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Sensors,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No ${filter.label.lowercase()} sensors in this category.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun PermissionsAndLifecycleCard(
    locationState: LocationDiagnosticSnapshot,
    isObserving: Boolean,
    onRequestLocationPermission: () -> Unit
) {
    GlassCard(
        tier = GlassTier.CARD,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permissions_lifecycle_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.section_permissions_lifecycle),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Location permission: ${locationState.permissionState.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Sensor listeners: ${if (isObserving) "Registered (foreground active)" else "Unregistered (idle/background)"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (locationState.permissionState == LocationPermissionState.DENIED) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onRequestLocationPermission,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Request Location Permission")
                }
            }
        }
    }
}

private fun formatLiveSample(
    type: RequiredSensorType,
    telemetry: LiveTelemetrySnapshot
): String? = when (type) {
    RequiredSensorType.ACCELEROMETER -> telemetry.accelerometerAxes?.let {
        String.format(Locale.US, "Live: X %.2f  Y %.2f  Z %.2f m/s²", it.x, it.y, it.z)
    }
    RequiredSensorType.MAGNETIC_FIELD -> telemetry.magneticFieldMicroTesla?.let { mag ->
        val axes = telemetry.magneticAxes
        if (axes != null) {
            String.format(Locale.US, "Live: %.1f μT (X %.1f Y %.1f Z %.1f)", mag, axes.x, axes.y, axes.z)
        } else {
            String.format(Locale.US, "Live: %.1f μT", mag)
        }
    }
    RequiredSensorType.GYROSCOPE -> telemetry.gyroscopeAxes?.let {
        String.format(Locale.US, "Live: X %.3f  Y %.3f  Z %.3f rad/s", it.x, it.y, it.z)
    }
    RequiredSensorType.ROTATION_VECTOR -> telemetry.rotationVectorComponents?.let { rv ->
        val x = rv.getOrElse(0) { 0f }
        val y = rv.getOrElse(1) { 0f }
        val z = rv.getOrElse(2) { 0f }
        String.format(Locale.US, "Live: qx %.3f  qy %.3f  qz %.3f", x, y, z)
    }
    RequiredSensorType.GRAVITY -> telemetry.gravityAxes?.let {
        String.format(Locale.US, "Live: X %.2f  Y %.2f  Z %.2f m/s²", it.x, it.y, it.z)
    }
    RequiredSensorType.LINEAR_ACCELERATION -> telemetry.linearAccelerationAxes?.let {
        String.format(Locale.US, "Live: X %.2f  Y %.2f  Z %.2f m/s²", it.x, it.y, it.z)
    }
    RequiredSensorType.PRESSURE -> telemetry.pressureHpa?.let {
        String.format(Locale.US, "Live: %.2f hPa", it)
    }
    RequiredSensorType.SIGNIFICANT_MOTION ->
        "Trigger events observed this session: ${telemetry.significantMotionTriggeredCount}"
}

private fun formatScientificOrDecimal(value: Float, unit: String): String {
    if (value == 0f) return "0 $unit"
    val absVal = kotlin.math.abs(value)
    return if (absVal < 0.001f || absVal >= 10_000f) {
        String.format(Locale.US, "%.2e %s", value, unit)
    } else {
        String.format(Locale.US, "%.3f %s", value, unit)
    }
}
