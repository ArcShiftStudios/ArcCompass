package com.example.data

enum class DialStyle(val label: String, val description: String) {
    EXPRESSIVE("Expressive", "Customizable organic shapes with adaptive frosted dial"),
    PRECISION("Precision", "Technical 360° instrument bezel with 3-tier markings")
}

enum class ExpressiveDialShape(val label: String, val description: String) {
    CIRCLE("Circle", "Clean circular instrument silhouette"),
    SOFT_SCALLOPED("Soft Scalloped", "Gentle 16-lobe organic contour"),
    ROUNDED_FLOWER("Rounded Flower", "Expressive 8-petal blossom bezel"),
    ORGANIC_WAVE("Organic Wave", "Fluid asymmetric harmonic contour"),
    GEOMETRIC("Geometric", "Crisp 12-sided chamfered bezel")
}

enum class HeadingFormat(val label: String) {
    AZIMUTH("360° Azimuth (232° SW)"),
    QUADRANT("Quadrant Bearing (S 52° W)")
}

enum class AppThemeMode(val label: String) {
    DARK("Dark (Charcoal)"),
    SYSTEM("System"),
    LIGHT("Light")
}

enum class AccentPreset(val label: String) {
    COOL_BLUE("Cool Steel Blue"),
    MONOCHROME("Monochrome White"),
    AMBER("Instrument Amber"),
    EMERALD("Tactical Emerald")
}

enum class CompassSensorMode(val label: String, val description: String) {
    AUTO_DEFAULT(
        label = "Default (Auto)",
        description = "Uses hardware Rotation Vector when available, with automatic fallback if unsupported"
    ),
    ROTATION_VECTOR(
        label = "Rotation Vector",
        description = "Hardware 9-axis / 6-axis fused orientation sensor"
    ),
    FUSION_ACCEL_MAG(
        label = "Sensor Fusion (Accel + Mag)",
        description = "Tilt-compensated Accelerometer + Magnetometer compass fusion"
    ),
    GAME_ROTATION_VECTOR(
        label = "Game Rotation Vector",
        description = "Relative gyro + accelerometer tracking (no magnetic North lock)"
    ),
    GYROSCOPE_RELATIVE(
        label = "Gyroscope Relative",
        description = "Relative turn tracking via gyroscope (drifts over time, no North lock)"
    )
}

data class ArcCompassSettings(
    // Compass & Dial Customization
    val trueNorthEnabled: Boolean = false,
    val dialStyle: DialStyle = DialStyle.EXPRESSIVE,
    val expressiveDialShape: ExpressiveDialShape = ExpressiveDialShape.SOFT_SCALLOPED,
    val expressiveAccentPreset: AccentPreset = AccentPreset.COOL_BLUE,
    val headingFormat: HeadingFormat = HeadingFormat.AZIMUTH,
    val showHeadingDecimals: Boolean = false,
    val rawModeEnabled: Boolean = false,
    val compassSensorMode: CompassSensorMode = CompassSensorMode.AUTO_DEFAULT,
    val hasSeenCalibrationGuidance: Boolean = false,
    val isLoaded: Boolean = false,
    // Appearance
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val accentPreset: AccentPreset = AccentPreset.COOL_BLUE,
    val dynamicColorEnabled: Boolean = false,
    val frostedBlurEnabled: Boolean = true,
    // Instruments
    val useImperialUnits: Boolean = false,
    val showCoordinates: Boolean = true,
    val showAltitude: Boolean = true,
    val showMagneticField: Boolean = true,
    val showLevelDecimals: Boolean = true,
    // Accessibility
    val hapticFeedbackEnabled: Boolean = true,
    val highContrastEnabled: Boolean = false,
    val reducedMotionEnabled: Boolean = false,
    val largeReadoutsEnabled: Boolean = false,
    // Night mode
    val redScreenFilterEnabled: Boolean = false
)
