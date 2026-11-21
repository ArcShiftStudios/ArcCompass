package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.arcCompassSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "arccompass_settings"
)

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.arcCompassSettingsDataStore

    val settingsFlow: Flow<ArcCompassSettings> = dataStore.data.map { prefs ->
        ArcCompassSettings(
            trueNorthEnabled = prefs[KEY_TRUE_NORTH] ?: false,
            dialStyle = prefs[KEY_DIAL_STYLE]?.let { safeEnum<DialStyle>(it) } ?: DialStyle.EXPRESSIVE,
            expressiveDialShape = prefs[KEY_EXPRESSIVE_SHAPE]?.let { safeEnum<ExpressiveDialShape>(it) }
                ?: ExpressiveDialShape.SOFT_SCALLOPED,
            expressiveAccentPreset = prefs[KEY_EXPRESSIVE_ACCENT]?.let { safeEnum<AccentPreset>(it) }
                ?: AccentPreset.COOL_BLUE,
            headingFormat = prefs[KEY_HEADING_FORMAT]?.let { safeEnum<HeadingFormat>(it) } ?: HeadingFormat.AZIMUTH,
            showHeadingDecimals = prefs[KEY_HEADING_DECIMALS] ?: false,
            themeMode = prefs[KEY_THEME_MODE]?.let { safeEnum<AppThemeMode>(it) } ?: AppThemeMode.DARK,
            accentPreset = prefs[KEY_ACCENT_PRESET]?.let { safeEnum<AccentPreset>(it) } ?: AccentPreset.COOL_BLUE,
            dynamicColorEnabled = prefs[KEY_DYNAMIC_COLOR] ?: false,
            frostedBlurEnabled = prefs[KEY_FROSTED_BLUR] ?: true,
            useImperialUnits = prefs[KEY_IMPERIAL_UNITS] ?: false,
            showCoordinates = prefs[KEY_SHOW_COORDINATES] ?: true,
            showAltitude = prefs[KEY_SHOW_ALTITUDE] ?: true,
            showMagneticField = prefs[KEY_SHOW_MAGNETIC_FIELD] ?: true,
            showLevelDecimals = prefs[KEY_LEVEL_DECIMALS] ?: true,
            hapticFeedbackEnabled = prefs[KEY_HAPTIC_FEEDBACK] ?: true,
            highContrastEnabled = prefs[KEY_HIGH_CONTRAST] ?: false,
            reducedMotionEnabled = prefs[KEY_REDUCED_MOTION] ?: false,
            largeReadoutsEnabled = prefs[KEY_LARGE_READOUTS] ?: false,
            redScreenFilterEnabled = prefs[KEY_RED_FILTER] ?: false
        )
    }

    suspend fun setTrueNorth(enabled: Boolean) = editBool(KEY_TRUE_NORTH, enabled)
    suspend fun setDialStyle(style: DialStyle) = editString(KEY_DIAL_STYLE, style.name)
    suspend fun setExpressiveDialShape(shape: ExpressiveDialShape) = editString(KEY_EXPRESSIVE_SHAPE, shape.name)
    suspend fun setExpressiveAccentPreset(preset: AccentPreset) = editString(KEY_EXPRESSIVE_ACCENT, preset.name)
    suspend fun setHeadingFormat(format: HeadingFormat) = editString(KEY_HEADING_FORMAT, format.name)
    suspend fun setShowHeadingDecimals(enabled: Boolean) = editBool(KEY_HEADING_DECIMALS, enabled)
    suspend fun setThemeMode(mode: AppThemeMode) = editString(KEY_THEME_MODE, mode.name)
    suspend fun setAccentPreset(preset: AccentPreset) = editString(KEY_ACCENT_PRESET, preset.name)
    suspend fun setDynamicColor(enabled: Boolean) = editBool(KEY_DYNAMIC_COLOR, enabled)
    suspend fun setFrostedBlur(enabled: Boolean) = editBool(KEY_FROSTED_BLUR, enabled)
    suspend fun setImperialUnits(enabled: Boolean) = editBool(KEY_IMPERIAL_UNITS, enabled)
    suspend fun setShowCoordinates(enabled: Boolean) = editBool(KEY_SHOW_COORDINATES, enabled)
    suspend fun setShowAltitude(enabled: Boolean) = editBool(KEY_SHOW_ALTITUDE, enabled)
    suspend fun setShowMagneticField(enabled: Boolean) = editBool(KEY_SHOW_MAGNETIC_FIELD, enabled)
    suspend fun setShowLevelDecimals(enabled: Boolean) = editBool(KEY_LEVEL_DECIMALS, enabled)
    suspend fun setHapticFeedback(enabled: Boolean) = editBool(KEY_HAPTIC_FEEDBACK, enabled)
    suspend fun setHighContrast(enabled: Boolean) = editBool(KEY_HIGH_CONTRAST, enabled)
    suspend fun setReducedMotion(enabled: Boolean) = editBool(KEY_REDUCED_MOTION, enabled)
    suspend fun setLargeReadouts(enabled: Boolean) = editBool(KEY_LARGE_READOUTS, enabled)
    suspend fun setRedScreenFilter(enabled: Boolean) = editBool(KEY_RED_FILTER, enabled)

    private suspend fun editBool(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { it[key] = value }
    }

    private suspend fun editString(key: Preferences.Key<String>, value: String) {
        dataStore.edit { it[key] = value }
    }

    private inline fun <reified T : Enum<T>> safeEnum(name: String): T? =
        runCatching { enumValueOf<T>(name) }.getOrNull()

    companion object {
        private val KEY_TRUE_NORTH = booleanPreferencesKey("true_north")
        private val KEY_DIAL_STYLE = stringPreferencesKey("dial_style")
        private val KEY_EXPRESSIVE_SHAPE = stringPreferencesKey("expressive_dial_shape")
        private val KEY_EXPRESSIVE_ACCENT = stringPreferencesKey("expressive_accent_preset")
        private val KEY_HEADING_FORMAT = stringPreferencesKey("heading_format")
        private val KEY_HEADING_DECIMALS = booleanPreferencesKey("heading_decimals")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_ACCENT_PRESET = stringPreferencesKey("accent_preset")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_FROSTED_BLUR = booleanPreferencesKey("frosted_blur")
        private val KEY_IMPERIAL_UNITS = booleanPreferencesKey("imperial_units")
        private val KEY_SHOW_COORDINATES = booleanPreferencesKey("show_coordinates")
        private val KEY_SHOW_ALTITUDE = booleanPreferencesKey("show_altitude")
        private val KEY_SHOW_MAGNETIC_FIELD = booleanPreferencesKey("show_magnetic_field")
        private val KEY_LEVEL_DECIMALS = booleanPreferencesKey("level_decimals")
        private val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        private val KEY_HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        private val KEY_REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        private val KEY_LARGE_READOUTS = booleanPreferencesKey("large_readouts")
        private val KEY_RED_FILTER = booleanPreferencesKey("red_filter")
    }
}
