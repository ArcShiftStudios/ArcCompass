package com.example.compass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.sensor.CompassDiagnosticsState
import com.example.sensor.CompassHeadingState
import com.example.sensor.InfoBannerNotice
import com.example.sensor.InteractiveCheckPhase
import com.example.sensor.LiveTelemetrySnapshot
import com.example.sensor.RequiredSensorType
import com.example.sensor.SensorEngineState
import com.example.sensor.SensorSpec
import com.example.sensor.SensorStreamHealth
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassDialog
import com.example.ui.theme.GlassPill
import com.example.ui.theme.GlassTier
import com.example.ui.theme.arcSemanticColors
import com.example.ui.theme.arcTextRoles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CompassRawModeDeck(
    engineState: SensorEngineState,
    headingState: CompassHeadingState,
    onExitRawMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val roles = MaterialTheme.arcTextRoles
    var isPaused by remember { mutableStateOf(false) }
    var frozenTelemetry by remember { mutableStateOf<LiveTelemetrySnapshot?>(null) }

    val displayedTelemetry = if (isPaused && frozenTelemetry != null) {
        frozenTelemetry!!
    } else {
        engineState.telemetry
    }

    val availableSpecs = remember(engineState.sensorSpecs) {
        engineState.sensorSpecs.filter { it.isAvailable }
    }

    val timeFormatter = remember {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .testTag("compass_raw_mode_deck"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassCard(
            tier = GlassTier.FLOATING_CHROME,
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Sensors,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "RAW Sensor Telemetry",
                                style = roles.heading.copy(fontSize = roles.measurement.fontSize),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${availableSpecs.size} available • ${headingState.source.label}",
                                style = roles.technical,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val semanticColors = MaterialTheme.arcSemanticColors
                    val pillAccent = if (isPaused) semanticColors.warning else semanticColors.success
                    GlassPill(
                        tintColor = pillAccent.copy(alpha = 0.16f),
                        borderColor = pillAccent.copy(alpha = 0.45f)
                    ) {
                        Text(
                            text = if (isPaused) "PAUSED" else "LIVE",
                            style = roles.technical,
                            fontWeight = FontWeight.Bold,
                            color = pillAccent,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!isPaused) {
                                frozenTelemetry = engineState.telemetry
                                isPaused = true
                            } else {
                                isPaused = false
                                frozenTelemetry = null
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("raw_mode_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isPaused) "Resume" else "Pause",
                            style = roles.button
                        )
                    }

                    OutlinedButton(
                        onClick = onExitRawMode,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("raw_mode_exit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Exit RAW", style = roles.button)
                    }
                }
            }
        }

        if (availableSpecs.isEmpty()) {
            GlassCard(
                tier = GlassTier.CARD,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("raw_mode_empty_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "No supported hardware sensors detected",
                        style = roles.settings,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "RAW mode displays only sensors physically present on this device.",
                        style = roles.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            availableSpecs.forEach { spec ->
                RawSensorCard(
                    spec = spec,
                    telemetry = displayedTelemetry,
                    timeFormatter = timeFormatter
                )
            }
        }
    }
}

@Composable
private fun RawSensorCard(
    spec: SensorSpec,
    telemetry: LiveTelemetrySnapshot,
    timeFormatter: SimpleDateFormat
) {
    val roles = MaterialTheme.arcTextRoles
    val sampleTimestamp = telemetry.sensorUpdateTimestamps[spec.sensorType]
    val timestampText = sampleTimestamp?.let { timeFormatter.format(Date(it)) } ?: "Awaiting event"
    val rawVectorLines = formatRawVectorLines(spec.sensorType, telemetry)

    GlassCard(
        tier = GlassTier.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("raw_sensor_card_${spec.sensorType.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spec.sensorType.displayName,
                        style = roles.settings,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${spec.vendor} • ${spec.sensorType.unitLabel} • $timestampText",
                        style = roles.technical,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                GlassPill(tintColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = spec.accuracy.label,
                        style = roles.technical,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val hasReadings = sampleTimestamp != null ||
                        (spec.sensorType == RequiredSensorType.SIGNIFICANT_MOTION &&
                            telemetry.significantMotionTriggeredCount > 0)
                    rawVectorLines.forEach { line ->
                        Text(
                            text = line,
                            style = if (hasReadings) roles.rawNumeric else roles.description,
                            color = if (hasReadings) {
                                MaterialTheme.colorScheme.onSurface
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

@Composable
fun CompassAnomalyBanner(
    diagnosticsState: CompassDiagnosticsState,
    onRunCheck: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val anomaly = diagnosticsState.suspectedAnomaly ?: return
    if (diagnosticsState.bannerDismissed) return

    val roles = MaterialTheme.arcTextRoles
    val warningColor = MaterialTheme.arcSemanticColors.warning
    GlassCard(
        tier = GlassTier.FLOATING_CHROME,
        tintColor = warningColor.copy(alpha = 0.14f),
        borderColor = warningColor.copy(alpha = 0.55f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .testTag("compass_anomaly_banner")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = anomaly.title,
                        style = roles.settings,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = anomaly.summary,
                        style = roles.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("compass_anomaly_dismiss_button")
                ) {
                    Text("Dismiss", style = roles.button)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onRunCheck,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("compass_anomaly_run_check_button")
                ) {
                    Text("Run Sensor Check", style = roles.button)
                }
            }
        }
    }
}

@Composable
fun CompassConditionWarningBanners(
    diagnosticsState: CompassDiagnosticsState,
    onRunCheck: () -> Unit,
    onDismissAnomaly: () -> Unit,
    onDismissMagneticInterference: () -> Unit,
    onDismissVerticalOrientation: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        diagnosticsState.suspectedAnomaly != null && !diagnosticsState.bannerDismissed -> {
            CompassAnomalyBanner(
                diagnosticsState = diagnosticsState,
                onRunCheck = onRunCheck,
                onDismiss = onDismissAnomaly,
                modifier = modifier
            )
        }
        diagnosticsState.magneticInterferenceActive && !diagnosticsState.magneticInterferenceDismissed -> {
            MagneticInterferenceBanner(
                microTesla = diagnosticsState.magneticInterferenceMicroTesla,
                onDismiss = onDismissMagneticInterference,
                modifier = modifier
            )
        }
        diagnosticsState.verticalOrientationWarningActive && !diagnosticsState.verticalOrientationDismissed -> {
            VerticalOrientationBanner(
                onDismiss = onDismissVerticalOrientation,
                modifier = modifier
            )
        }
    }
}

@Composable
fun CompassInfoToastBanner(
    infoBanner: InfoBannerNotice?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (infoBanner == null) return
    val roles = MaterialTheme.arcTextRoles

    GlassCard(
        tier = GlassTier.FLOATING_CHROME,
        tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .testTag("info_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = infoBanner.title,
                    style = roles.technical,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = infoBanner.message,
                    style = roles.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("info_banner_dismiss")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss notification",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun MagneticInterferenceBanner(
    microTesla: Float?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val roles = MaterialTheme.arcTextRoles
    val warningColor = MaterialTheme.arcSemanticColors.warning

    GlassCard(
        tier = GlassTier.FLOATING_CHROME,
        tintColor = warningColor.copy(alpha = 0.14f),
        borderColor = warningColor.copy(alpha = 0.55f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .testTag("magnetic_interference_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = "Magnetic interference",
                tint = warningColor,
                modifier = Modifier.size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Magnetic Interference",
                        style = roles.technical,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (microTesla != null) {
                        GlassPill(tintColor = warningColor.copy(alpha = 0.18f)) {
                            Text(
                                text = String.format(Locale.US, "%.0f μT", microTesla),
                                style = roles.technical,
                                color = warningColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "Magnetic interference detected. Move away from magnets or electronic equipment and check again.",
                    style = roles.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("magnetic_interference_dismiss_button")
            ) {
                Text("Dismiss", style = roles.button)
            }
        }
    }
}

@Composable
fun VerticalOrientationBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val roles = MaterialTheme.arcTextRoles
    val warningColor = MaterialTheme.arcSemanticColors.warning

    GlassCard(
        tier = GlassTier.CARD,
        tintColor = warningColor.copy(alpha = 0.11f),
        borderColor = warningColor.copy(alpha = 0.45f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .testTag("vertical_orientation_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ScreenRotation,
                contentDescription = "Orientation notice",
                tint = warningColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Compass readings may be unreliable in this position. Try holding your device flatter.",
                style = roles.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("vertical_orientation_dismiss_button")
            ) {
                Text("Dismiss", style = roles.button)
            }
        }
    }
}

@Composable
fun FirstLaunchCalibrationBanner(
    onOpenCalibration: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val roles = MaterialTheme.arcTextRoles
    GlassCard(
        tier = GlassTier.CARD,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .testTag("first_launch_calibration_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CompassCalibration,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Compass Calibration & Smart Fallback",
                        style = roles.settings,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Calibrate away from metal or magnets for peak accuracy. If your primary sensor ever freezes or fails, ArcCompass can automatically switch to sensor fusion and remember your preference.",
                        style = roles.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onOpenCalibration,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("first_launch_open_calibration_button")
                ) {
                    Text("Calibration Settings", style = roles.button)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("first_launch_dismiss_button")
                ) {
                    Text("Got it", style = roles.button)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompassInteractiveCheckDialog(
    diagnosticsState: CompassDiagnosticsState,
    onConfirmStart: () -> Unit,
    onCancel: () -> Unit,
    onResetRecovery: () -> Unit
) {
    if (diagnosticsState.checkPhase == InteractiveCheckPhase.IDLE) return
    val roles = MaterialTheme.arcTextRoles
    val isRunning = diagnosticsState.checkPhase == InteractiveCheckPhase.RUNNING

    GlassDialog(
        onDismissRequest = {
            if (!isRunning) {
                onCancel()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isRunning,
            dismissOnClickOutside = !isRunning
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag("compass_interactive_check_dialog"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (diagnosticsState.checkPhase) {
                InteractiveCheckPhase.AWAITING_CONFIRMATION -> {
                    Text(
                        text = "Interactive Compass Check",
                        style = roles.heading,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "To verify whether your compass sensors respond properly without false warnings while resting on a desk, rotate or tilt the phone slowly in multiple directions for 4 seconds after starting.",
                        style = roles.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("compass_check_cancel")
                        ) {
                            Text("Cancel", style = roles.button, maxLines = 1, softWrap = false)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onConfirmStart,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("compass_check_confirm_start")
                        ) {
                            Text("Start 4s Check", style = roles.button, maxLines = 1, softWrap = false)
                        }
                    }
                }

                InteractiveCheckPhase.RUNNING -> {
                    Text(
                        text = "Testing Compass Sensors…",
                        style = roles.heading,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Move and rotate your phone gently across axes now.",
                        style = roles.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { diagnosticsState.checkProgressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                    )
                    val semanticColors = MaterialTheme.arcSemanticColors
                    GlassPill(
                        tintColor = if (diagnosticsState.motionDetectedDuringCheck) {
                            semanticColors.success.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        borderColor = if (diagnosticsState.motionDetectedDuringCheck) {
                            semanticColors.success.copy(alpha = 0.45f)
                        } else null
                    ) {
                        Text(
                            text = if (diagnosticsState.motionDetectedDuringCheck) {
                                "Movement detected — evaluating orientation response"
                            } else {
                                "Waiting for gentle movement…"
                            },
                            style = roles.technical,
                            color = if (diagnosticsState.motionDetectedDuringCheck) {
                                semanticColors.success
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                InteractiveCheckPhase.COMPLETED -> {
                    val semanticColors = MaterialTheme.arcSemanticColors
                    Text(
                        text = "Sensor Check Results",
                        style = roles.heading,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    diagnosticsState.diagnosticSummary?.let { summary ->
                        Text(
                            text = summary,
                            style = roles.description,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    diagnosticsState.recoveryDisclosure?.let { disclosure ->
                        GlassCard(
                            tier = GlassTier.CARD,
                            borderColor = semanticColors.warning.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = disclosure,
                                style = roles.description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        diagnosticsState.sensorOutcomes.forEach { outcome ->
                            val healthColor = when (outcome.health) {
                                SensorStreamHealth.HEALTHY -> semanticColors.success
                                SensorStreamHealth.STATIONARY -> MaterialTheme.colorScheme.primary
                                SensorStreamHealth.FROZEN,
                                SensorStreamHealth.INVALID_VALUES,
                                SensorStreamHealth.UNRESPONSIVE -> semanticColors.error
                                SensorStreamHealth.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = outcome.sensorType.displayName,
                                        style = roles.technical,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${outcome.sampleCount} samples • max Δ ${String.format(Locale.US, "%.3f", outcome.maxObservedDelta)}",
                                        style = roles.technical,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (outcome.health == SensorStreamHealth.HEALTHY) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            tint = healthColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = outcome.health.label,
                                        style = roles.technical,
                                        fontWeight = FontWeight.SemiBold,
                                        color = healthColor
                                    )
                                }
                            }
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (diagnosticsState.activeRecoverySource != null) {
                            TextButton(
                                onClick = onResetRecovery,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("compass_check_use_default")
                            ) {
                                Text("Use Default", style = roles.button, maxLines = 1, softWrap = false)
                            }
                        }
                        TextButton(
                            onClick = onConfirmStart,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("compass_check_retest")
                        ) {
                            Text("Retest", style = roles.button, maxLines = 1, softWrap = false)
                        }
                        Button(
                            onClick = onCancel,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("compass_check_done")
                        ) {
                            Text("Done", style = roles.button, maxLines = 1, softWrap = false)
                        }
                    }
                }

                InteractiveCheckPhase.IDLE -> Unit
            }
        }
    }
}

private fun formatRawVectorLines(
    type: RequiredSensorType,
    telemetry: LiveTelemetrySnapshot
): List<String> = when (type) {
    RequiredSensorType.ACCELEROMETER -> telemetry.accelerometerAxes?.let {
        listOf(
            String.format(Locale.US, "X %+9.4f   Y %+9.4f", it.x, it.y),
            String.format(Locale.US, "Z %+9.4f", it.z)
        )
    } ?: listOf("Awaiting accelerometer samples…")

    RequiredSensorType.MAGNETIC_FIELD -> telemetry.magneticAxes?.let { axes ->
        val norm = telemetry.magneticFieldMicroTesla?.let {
            String.format(Locale.US, "|B| %7.2f uT", it)
        } ?: ""
        listOf(
            String.format(Locale.US, "X %+8.3f   Y %+8.3f", axes.x, axes.y),
            String.format(Locale.US, "Z %+8.3f   %s", axes.z, norm)
        )
    } ?: listOf("Awaiting magnetometer samples…")

    RequiredSensorType.GYROSCOPE -> telemetry.gyroscopeAxes?.let {
        listOf(
            String.format(Locale.US, "X %+9.5f   Y %+9.5f", it.x, it.y),
            String.format(Locale.US, "Z %+9.5f", it.z)
        )
    } ?: listOf("Awaiting gyroscope samples…")

    RequiredSensorType.ROTATION_VECTOR -> telemetry.rotationVectorComponents?.let { rv ->
        val x = rv.getOrElse(0) { 0f }
        val y = rv.getOrElse(1) { 0f }
        val z = rv.getOrElse(2) { 0f }
        val w = rv.getOrNull(3)
        listOf(
            String.format(Locale.US, "qx %+8.5f  qy %+8.5f", x, y),
            if (w != null) {
                String.format(Locale.US, "qz %+8.5f  qw %+8.5f", z, w)
            } else {
                String.format(Locale.US, "qz %+8.5f", z)
            }
        )
    } ?: listOf("Awaiting rotation vector…")

    RequiredSensorType.GRAVITY -> telemetry.gravityAxes?.let {
        listOf(
            String.format(Locale.US, "X %+9.4f   Y %+9.4f", it.x, it.y),
            String.format(Locale.US, "Z %+9.4f", it.z)
        )
    } ?: listOf("Awaiting gravity samples…")

    RequiredSensorType.LINEAR_ACCELERATION -> telemetry.linearAccelerationAxes?.let {
        listOf(
            String.format(Locale.US, "X %+9.4f   Y %+9.4f", it.x, it.y),
            String.format(Locale.US, "Z %+9.4f", it.z)
        )
    } ?: listOf("Awaiting linear acceleration…")

    RequiredSensorType.PRESSURE -> telemetry.pressureHpa?.let {
        listOf(String.format(Locale.US, "P %9.3f hPa", it))
    } ?: listOf("Awaiting barometer samples…")

    RequiredSensorType.SIGNIFICANT_MOTION -> listOf(
        "Triggers observed: ${telemetry.significantMotionTriggeredCount}"
    )
}
