package com.example.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ArcCompassViewModel
import com.example.BuildConfig
import com.example.R
import com.example.compass.CalibrationGuideDialog
import com.example.compass.CompassInteractiveCheckDialog
import com.example.data.AccentPreset
import com.example.data.AppThemeMode
import com.example.data.ArcCompassSettings
import com.example.data.CompassSensorMode
import com.example.data.DialStyle
import com.example.data.ExpressiveDialShape
import com.example.data.HeadingFormat
import com.example.sensor.CompassDiagnosticsState
import com.example.sensor.CompassHeadingState
import com.example.sensor.DiagnosticStatus
import com.example.sensor.RequiredSensorType
import com.example.sensor.SensorEngineState
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassPill
import com.example.ui.theme.GlassTier
import com.example.ui.theme.arcSemanticColors
import com.example.ui.theme.arcTextRoles
import com.example.ui.theme.resolveAccentColor
import java.util.Locale

private enum class ActiveSettingsDialog {
    DIAL_STYLE,
    EXPRESSIVE_SHAPE,
    EXPRESSIVE_ACCENT,
    HEADING_FORMAT,
    SENSOR_MODE,
    THEME_MODE,
    ACCENT_PRESET,
    CALIBRATION,
    CAPABILITY_SUMMARY,
    CREDITS,
    PRIVACY
}

@Composable
fun SettingsScreen(
    settings: ArcCompassSettings,
    engineState: SensorEngineState,
    headingState: CompassHeadingState,
    viewModel: ArcCompassViewModel,
    onOpenSensorDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    compassDiagnosticsState: CompassDiagnosticsState = CompassDiagnosticsState(),
    openCalibrationOnLaunch: Boolean = false,
    onCalibrationLaunchConsumed: () -> Unit = {},
    bottomNavClearance: Dp = 16.dp
) {
    var activeDialog by rememberSaveable { mutableStateOf<ActiveSettingsDialog?>(null) }
    var compassExpanded by rememberSaveable { mutableStateOf(true) }
    var appearanceExpanded by rememberSaveable { mutableStateOf(true) }
    var instrumentsExpanded by rememberSaveable { mutableStateOf(false) }
    var accessibilityExpanded by rememberSaveable { mutableStateOf(false) }
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openCalibrationOnLaunch) {
        if (openCalibrationOnLaunch) {
            compassExpanded = true
            activeDialog = ActiveSettingsDialog.CALIBRATION
            onCalibrationLaunchConsumed()
        }
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val roles = MaterialTheme.arcTextRoles

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("settings_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = bottomNavClearance + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "settings_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Settings",
                            style = roles.heading,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Instruments, frosted glass & hardware diagnostics",
                            style = roles.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GlassPill(tintColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = "${settings.dialStyle.label} • ${settings.themeMode.label}",
                            style = roles.technical,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // 1. Hardware & Diagnostics — Sensor Diagnostics MUST remain the first settings option
            item(key = "section_hardware") {
                val availableCount = engineState.sensorSpecs.count { it.isAvailable }
                val totalCount = engineState.sensorSpecs.size.coerceAtLeast(1)
                val compassOk = engineState.capabilities.compass.status == DiagnosticStatus.AVAILABLE ||
                    engineState.capabilities.compass.status == DiagnosticStatus.INITIALIZING
                GlassCard(
                    tier = GlassTier.FLOATING_CHROME,
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable(onClick = onOpenSensorDiagnostics)
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("settings_sensor_diagnostics_item"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SettingsIconBadge(
                                icon = Icons.Outlined.Memory,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.diagnostics_entry_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.diagnostics_entry_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (compassOk) MaterialTheme.arcSemanticColors.success
                                                else MaterialTheme.colorScheme.primary
                                            )
                                    )
                                    Text(
                                        text = if (engineState.sensorSpecs.isNotEmpty()) {
                                            "$availableCount/$totalCount hardware sensors detected"
                                        } else {
                                            "Inspect live sensor capabilities"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        SettingsDivider()

                        SettingsNavigationRow(
                            icon = Icons.Outlined.Info,
                            title = "Device capability summary",
                            subtitle = "Compass, level, location & altitude readiness",
                            onClick = { activeDialog = ActiveSettingsDialog.CAPABILITY_SUMMARY }
                        )
                    }
                }
            }

            // 2. Compass (Expandable)
            item(key = "section_compass_studio") {
                val summary = "${settings.compassSensorMode.label} • ${settings.dialStyle.label} • ${if (settings.trueNorthEnabled) "True N" else "Mag N"}"
                ExpandableSettingsSection(
                    title = "Compass",
                    summary = summary,
                    icon = Icons.Outlined.Explore,
                    expanded = compassExpanded,
                    reducedMotion = settings.reducedMotionEnabled,
                    onToggleExpand = { compassExpanded = !compassExpanded },
                    headerTestTag = "settings_section_compass"
                ) {
                    val declText = headingState.declinationDegrees?.let {
                        String.format(Locale.US, "True North (%+.1f° declination)", it)
                    } ?: "True North (requires location fix)"
                    SettingsToggleRow(
                        icon = Icons.Outlined.Explore,
                        title = "True North correction",
                        subtitle = declText,
                        checked = settings.trueNorthEnabled,
                        onCheckedChange = viewModel::setTrueNorth,
                        testTag = "setting_true_north"
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Outlined.Tune,
                        title = "Heading format",
                        subtitle = settings.headingFormat.label,
                        onClick = { activeDialog = ActiveSettingsDialog.HEADING_FORMAT },
                        testTag = "setting_heading_format"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Outlined.Straighten,
                        title = "Degree decimals",
                        subtitle = "Display 0.1° precision on main readout",
                        checked = settings.showHeadingDecimals,
                        onCheckedChange = viewModel::setShowHeadingDecimals
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Outlined.CompassCalibration,
                        title = "Dial style",
                        subtitle = "${settings.dialStyle.label} • ${settings.dialStyle.description}",
                        onClick = { activeDialog = ActiveSettingsDialog.DIAL_STYLE },
                        testTag = "setting_dial_style"
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "Expressive dial shape",
                        subtitle = "${settings.expressiveDialShape.label} • Applies to Expressive dial only",
                        onClick = { activeDialog = ActiveSettingsDialog.EXPRESSIVE_SHAPE },
                        testTag = "setting_expressive_shape"
                    )
                    SettingsDivider()
                    val expSwatch = resolveAccentColor(
                        preset = settings.expressiveAccentPreset,
                        isDark = isDark,
                        nightFilterActive = settings.redScreenFilterEnabled
                    )
                    SettingsNavigationRow(
                        icon = Icons.Outlined.Palette,
                        title = "Expressive dial accent",
                        subtitle = "${settings.expressiveAccentPreset.label} • Precision dial stays technical",
                        swatchColor = expSwatch,
                        onClick = { activeDialog = ActiveSettingsDialog.EXPRESSIVE_ACCENT },
                        testTag = "setting_expressive_accent"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Outlined.Memory,
                        title = "RAW sensor mode",
                        subtitle = "Display live raw axes & timestamps for available hardware sensors on Compass",
                        checked = settings.rawModeEnabled,
                        onCheckedChange = viewModel::setRawMode,
                        testTag = "setting_raw_mode"
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Outlined.Sensors,
                        title = "Sensor mode",
                        subtitle = "${settings.compassSensorMode.label} • Active: ${headingState.source.label}",
                        onClick = { activeDialog = ActiveSettingsDialog.SENSOR_MODE },
                        testTag = "setting_compass_sensor_mode"
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Outlined.CompassCalibration,
                        title = "Sensor Calibration",
                        subtitle = "${settings.compassSensorMode.label} • ${headingState.status.label} (${headingState.accuracy.label})",
                        onClick = { activeDialog = ActiveSettingsDialog.CALIBRATION },
                        testTag = "setting_compass_sensor_calibration"
                    )
                }
            }

            // 3. Glass & Appearance (Expandable)
            item(key = "section_appearance") {
                val blurTag = if (settings.frostedBlurEnabled) "Blur On" else "Gradient Glass"
                ExpandableSettingsSection(
                    title = "Glass & Appearance",
                    summary = "${settings.themeMode.label} • ${settings.accentPreset.label} • $blurTag",
                    icon = Icons.Outlined.Palette,
                    expanded = appearanceExpanded,
                    reducedMotion = settings.reducedMotionEnabled,
                    onToggleExpand = { appearanceExpanded = !appearanceExpanded },
                    headerTestTag = "settings_section_appearance"
                ) {
                    SettingsNavigationRow(
                        icon = Icons.Outlined.DarkMode,
                        title = "Theme",
                        subtitle = settings.themeMode.label,
                        onClick = { activeDialog = ActiveSettingsDialog.THEME_MODE },
                        testTag = "setting_theme_mode"
                    )
                    SettingsDivider()
                    val appSwatch = resolveAccentColor(
                        preset = settings.accentPreset,
                        isDark = isDark,
                        nightFilterActive = settings.redScreenFilterEnabled
                    )
                    SettingsNavigationRow(
                        icon = Icons.Outlined.Palette,
                        title = "App accent colour",
                        subtitle = settings.accentPreset.label,
                        swatchColor = appSwatch,
                        onClick = { activeDialog = ActiveSettingsDialog.ACCENT_PRESET },
                        testTag = "setting_accent_preset"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Outlined.BlurOn,
                        title = "Frosted backdrop blur",
                        subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            "Ambient native blur behind glass layers (auto-disabled on low-RAM)"
                        } else {
                            "Translucent gradient fallback active on this Android version"
                        },
                        checked = settings.frostedBlurEnabled,
                        onCheckedChange = viewModel::setFrostedBlur,
                        testTag = "setting_frosted_blur"
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsDivider()
                        SettingsToggleRow(
                            icon = Icons.Outlined.Palette,
                            title = "Dynamic colours",
                            subtitle = "Adapt palette to device wallpaper (Android 12+)",
                            checked = settings.dynamicColorEnabled,
                            onCheckedChange = viewModel::setDynamicColor
                        )
                    }
                }
            }

            // 4. Instruments & Units (Expandable)
            item(key = "section_instruments") {
                val summary = buildList {
                    add(if (settings.useImperialUnits) "Imperial" else "Metric")
                    if (settings.showCoordinates) add("Coords")
                    if (settings.showAltitude) add("Alt")
                    if (settings.showMagneticField) add("μT")
                }.joinToString(" • ")

                ExpandableSettingsSection(
                    title = "Instruments & Units",
                    summary = summary,
                    icon = Icons.Outlined.Straighten,
                    expanded = instrumentsExpanded,
                    reducedMotion = settings.reducedMotionEnabled,
                    onToggleExpand = { instrumentsExpanded = !instrumentsExpanded },
                    headerTestTag = "settings_section_instruments"
                ) {
                    SettingsToggleRow(
                        icon = Icons.Outlined.Straighten,
                        title = "Imperial units",
                        subtitle = if (settings.useImperialUnits) "Feet & miles" else "Meters & kilometers",
                        checked = settings.useImperialUnits,
                        onCheckedChange = viewModel::setImperialUnits
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Outlined.LocationOn,
                        title = "Show coordinates",
                        subtitle = "Display latitude and longitude on compass",
                        checked = settings.showCoordinates,
                        onCheckedChange = viewModel::setShowCoordinates
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Outlined.Height,
                        title = "Show altitude",
                        subtitle = "Display barometer or GNSS elevation",
                        checked = settings.showAltitude,
                        onCheckedChange = viewModel::setShowAltitude
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Outlined.CompassCalibration,
                        title = "Magnetic field display",
                        subtitle = "Show ambient μT strength when supported",
                        checked = settings.showMagneticField,
                        onCheckedChange = viewModel::setShowMagneticField
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Outlined.Straighten,
                        title = "Level decimal precision",
                        subtitle = "Show 0.1° tilt increments on Surface Level",
                        checked = settings.showLevelDecimals,
                        onCheckedChange = viewModel::setShowLevelDecimals
                    )
                }
            }

            // 5. Accessibility & Night Vision (Expandable)
            item(key = "section_calibration_accessibility") {
                val summary = buildList {
                    if (settings.hapticFeedbackEnabled) add("Haptics")
                    if (settings.highContrastEnabled) add("High Contrast")
                    if (settings.reducedMotionEnabled) add("Reduced Motion")
                    if (settings.redScreenFilterEnabled) add("Night Red")
                }.ifEmpty { listOf("Standard") }.joinToString(" • ")

                ExpandableSettingsSection(
                    title = "Accessibility & Night",
                    summary = summary,
                    icon = Icons.Outlined.AccessibilityNew,
                    expanded = accessibilityExpanded,
                    reducedMotion = settings.reducedMotionEnabled,
                    onToggleExpand = { accessibilityExpanded = !accessibilityExpanded },
                    headerTestTag = "settings_section_accessibility"
                ) {
                    SettingsToggleRow(
                        icon = Icons.Outlined.Vibration,
                        title = "Haptic feedback",
                        subtitle = "Subtle pulses on major headings, level & alignment",
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = viewModel::setHapticFeedback
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Outlined.Visibility,
                        title = "High contrast",
                        subtitle = "Increase opacity, sharpen borders & foreground ink",
                        checked = settings.highContrastEnabled,
                        onCheckedChange = viewModel::setHighContrast
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Outlined.AccessibilityNew,
                        title = "Reduced motion",
                        subtitle = "Disable spring interpolation on dials, nav & bubbles",
                        checked = settings.reducedMotionEnabled,
                        onCheckedChange = viewModel::setReducedMotion
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Outlined.FormatSize,
                        title = "Large instrument readouts",
                        subtitle = "Scale primary heading and telemetry typography",
                        checked = settings.largeReadoutsEnabled,
                        onCheckedChange = viewModel::setLargeReadouts
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Outlined.Visibility,
                        title = "Red screen filter",
                        subtitle = "Preserve night vision in dark environments",
                        checked = settings.redScreenFilterEnabled,
                        onCheckedChange = viewModel::setRedScreenFilter,
                        testTag = "setting_red_filter"
                    )
                }
            }

            // 6. About ArcShift (Expandable)
            item(key = "section_about") {
                ExpandableSettingsSection(
                    title = "About ArcCompass",
                    summary = "v${BuildConfig.VERSION_NAME} • Offline • No Tracking",
                    icon = Icons.Outlined.Info,
                    expanded = aboutExpanded,
                    reducedMotion = settings.reducedMotionEnabled,
                    onToggleExpand = { aboutExpanded = !aboutExpanded },
                    headerTestTag = "settings_section_about"
                ) {
                    SettingsNavigationRow(
                        icon = Icons.Outlined.Info,
                        title = "ArcCompass by ArcShift Studios",
                        subtitle = "Version ${BuildConfig.VERSION_NAME} • Offline precision instrument",
                        onClick = { activeDialog = ActiveSettingsDialog.CREDITS }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Outlined.PrivacyTip,
                        title = "Privacy information",
                        subtitle = "100% local data • No accounts, ads, or cloud tracking",
                        onClick = { activeDialog = ActiveSettingsDialog.PRIVACY }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    when (activeDialog) {
        ActiveSettingsDialog.DIAL_STYLE -> {
            SingleChoiceSettingDialog(
                title = "Compass Dial Style",
                options = DialStyle.entries,
                selected = settings.dialStyle,
                labelProvider = { it.label },
                subtitleProvider = { it.description },
                onSelect = viewModel::setDialStyle,
                onDismiss = { activeDialog = null }
            )
        }
        ActiveSettingsDialog.EXPRESSIVE_SHAPE -> {
            SingleChoiceSettingDialog(
                title = "Expressive Dial Shape",
                options = ExpressiveDialShape.entries,
                selected = settings.expressiveDialShape,
                labelProvider = { it.label },
                subtitleProvider = { it.description },
                onSelect = viewModel::setExpressiveDialShape,
                onDismiss = { activeDialog = null }
            )
        }
        ActiveSettingsDialog.EXPRESSIVE_ACCENT -> {
            SingleChoiceSettingDialog(
                title = "Expressive Dial Accent",
                options = AccentPreset.entries,
                selected = settings.expressiveAccentPreset,
                labelProvider = { it.label },
                onSelect = viewModel::setExpressiveAccentPreset,
                onDismiss = { activeDialog = null }
            )
        }
        ActiveSettingsDialog.HEADING_FORMAT -> {
            SingleChoiceSettingDialog(
                title = "Heading Format",
                options = HeadingFormat.entries,
                selected = settings.headingFormat,
                labelProvider = { it.label },
                onSelect = viewModel::setHeadingFormat,
                onDismiss = { activeDialog = null }
            )
        }
        ActiveSettingsDialog.SENSOR_MODE -> {
            val availableTypes = engineState.sensorSpecs
                .filter { it.isAvailable }
                .map { it.sensorType }
                .toSet()
            SingleChoiceSettingDialog(
                title = "Compass Sensor Mode",
                options = CompassSensorMode.entries,
                selected = settings.compassSensorMode,
                labelProvider = { it.label },
                subtitleProvider = { mode ->
                    val supported = when (mode) {
                        CompassSensorMode.AUTO_DEFAULT -> true
                        CompassSensorMode.ROTATION_VECTOR -> availableTypes.contains(RequiredSensorType.ROTATION_VECTOR)
                        CompassSensorMode.FUSION_ACCEL_MAG ->
                            availableTypes.contains(RequiredSensorType.ACCELEROMETER) &&
                                availableTypes.contains(RequiredSensorType.MAGNETIC_FIELD)
                        CompassSensorMode.GAME_ROTATION_VECTOR -> true
                        CompassSensorMode.GYROSCOPE_RELATIVE -> availableTypes.contains(RequiredSensorType.GYROSCOPE)
                    }
                    if (supported) {
                        mode.description
                    } else {
                        "${mode.description} (Hardware sensor missing — falls back automatically)"
                    }
                },
                onSelect = viewModel::setCompassSensorMode,
                onDismiss = { activeDialog = null }
            )
        }
        ActiveSettingsDialog.THEME_MODE -> {
            SingleChoiceSettingDialog(
                title = "Theme",
                options = AppThemeMode.entries,
                selected = settings.themeMode,
                labelProvider = { it.label },
                onSelect = viewModel::setThemeMode,
                onDismiss = { activeDialog = null }
            )
        }
        ActiveSettingsDialog.ACCENT_PRESET -> {
            SingleChoiceSettingDialog(
                title = "App Accent Colour",
                options = AccentPreset.entries,
                selected = settings.accentPreset,
                labelProvider = { it.label },
                onSelect = viewModel::setAccentPreset,
                onDismiss = { activeDialog = null }
            )
        }
        ActiveSettingsDialog.CALIBRATION -> {
            CalibrationGuideDialog(
                accuracy = headingState.accuracy,
                microTesla = engineState.telemetry.magneticFieldMicroTesla,
                hasMagnetometer = engineState.capabilities.magneticFieldMonitor.source,
                onDismiss = { activeDialog = null },
                sensorMode = settings.compassSensorMode,
                compassSource = headingState.source,
                diagnosticStatus = headingState.status,
                onRunSensorCheck = viewModel::requestCompassDiagnosticCheck,
                onResetToDefault = viewModel::resetCompassRecovery
            )
        }
        ActiveSettingsDialog.CAPABILITY_SUMMARY -> {
            val caps = engineState.capabilities
            val summaryText = buildString {
                appendLine("Compass: ${caps.compass.status.label} (${caps.compass.source.label})")
                appendLine("Surface Level: ${caps.level.status.label} (${caps.level.source.label})")
                appendLine("Magnetic Monitor: ${caps.magneticFieldMonitor.status.label}")
                appendLine("Gyroscope: ${caps.gyroscopeDiagnostics.status.label}")
                appendLine("Location: ${caps.location.status.label}")
                append("Altitude: ${caps.altitude.status.label} (${caps.altitude.source.label})")
            }
            PrivacyAndAboutDialog(
                title = "Device Capability Report",
                body = summaryText,
                onDismiss = { activeDialog = null }
            )
        }
        ActiveSettingsDialog.CREDITS -> {
            PrivacyAndAboutDialog(
                title = "ArcCompass • ArcShift Studios",
                body = "ArcCompass v${BuildConfig.VERSION_NAME}\n\nDesigned as an offline-first precision compass, surface level, and parking retrieval utility. Built on the ArcShift Hardware Detection Engine so every device's actual sensor capabilities are respected without fabricated readings.",
                onDismiss = { activeDialog = null }
            )
        }
        ActiveSettingsDialog.PRIVACY -> {
            PrivacyAndAboutDialog(
                title = "Privacy Information",
                body = "ArcCompass is strictly offline-first.\n\n• No user accounts or sign-in.\n• No advertisements or analytics SDKs.\n• Location permission is requested only for coordinates, geomagnetic declination, GNSS altitude, Saved Places, and ArcPark.\n• Saved spots, places, and preferences stay exclusively on your device.",
                onDismiss = { activeDialog = null }
            )
        }
        null -> Unit
    }

    CompassInteractiveCheckDialog(
        diagnosticsState = compassDiagnosticsState,
        onConfirmStart = viewModel::startCompassDiagnosticCheck,
        onCancel = viewModel::cancelCompassDiagnosticConfirmation,
        onResetRecovery = viewModel::resetCompassRecovery
    )
}

@Composable
private fun ExpandableSettingsSection(
    title: String,
    summary: String,
    icon: ImageVector,
    expanded: Boolean,
    reducedMotion: Boolean,
    onToggleExpand: () -> Unit,
    headerTestTag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(tier = GlassTier.CARD) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag(headerTestTag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingsIconBadge(icon = icon, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = if (reducedMotion) EnterTransition.None else expandVertically() + fadeIn(),
                exit = if (reducedMotion) ExitTransition.None else shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsDivider()
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingsIconBadge(
    icon: ImageVector,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(tint.copy(alpha = 0.14f))
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.25f),
                shape = RoundedCornerShape(11.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    swatchColor: Color? = null,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (swatchColor != null) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(swatchColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
