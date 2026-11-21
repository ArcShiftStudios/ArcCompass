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

data class ArcCompassSettings(
    // Compass & Dial Customization
    val trueNorthEnabled: Boolean = false,
    val dialStyle: DialStyle = DialStyle.EXPRESSIVE,
    val expressiveDialShape: ExpressiveDialShape = ExpressiveDialShape.SOFT_SCALLOPED,
    val expressiveAccentPreset: AccentPreset = AccentPreset.COOL_BLUE,
    val headingFormat: HeadingFormat = HeadingFormat.AZIMUTH,
    val showHeadingDecimals: Boolean = false,
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
