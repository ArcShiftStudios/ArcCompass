package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccentPreset
import com.example.data.AppThemeMode
import com.example.data.ArcCompassSettings
import com.example.data.CompassSensorMode
import com.example.data.DialStyle
import com.example.data.ExpressiveDialShape
import com.example.data.HeadingFormat
import com.example.data.SavedPlaceEntity
import com.example.sensor.CompassDiagnosticsState
import com.example.sensor.CompassHeadingState
import com.example.sensor.LevelTiltState
import com.example.sensor.SensorEngineState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared application-level ViewModel connecting Compass, Level, Saved Places, and Settings
 * to the single [SensorRepository], [SettingsRepository], and [SavedPlacesRepository] instances.
 */
class ArcCompassViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ArcCompassApplication
    private val sensorRepository = app.sensorRepository
    private val settingsRepository = app.settingsRepository
    private val savedPlacesRepository = app.savedPlacesRepository

    val engineState: StateFlow<SensorEngineState> = sensorRepository.engineState
    val headingState: StateFlow<CompassHeadingState> = sensorRepository.headingState
    val levelState: StateFlow<LevelTiltState> = sensorRepository.levelState
    val compassDiagnosticsState: StateFlow<CompassDiagnosticsState> = sensorRepository.compassDiagnosticsState

    val settings: StateFlow<ArcCompassSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ArcCompassSettings()
    )

    val savedPlaces: StateFlow<List<SavedPlaceEntity>> = savedPlacesRepository.allPlaces.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    fun startSensors() = sensorRepository.startObserving()
    fun stopSensors() = sensorRepository.stopObserving()
    fun rescanHardware() = sensorRepository.refreshScan()
    fun onLocationPermissionUpdated() = sensorRepository.onPermissionResult()

    fun captureLevelReference() = sensorRepository.captureLevelReference()
    fun clearLevelReference() = sensorRepository.clearLevelReference()

    fun requestCompassDiagnosticCheck() = sensorRepository.requestCompassDiagnosticCheck()
    fun cancelCompassDiagnosticConfirmation() = sensorRepository.cancelCompassDiagnosticConfirmation()
    fun startCompassDiagnosticCheck() = sensorRepository.startCompassDiagnosticCheck()
    fun dismissCompassAnomalyBanner() = sensorRepository.dismissCompassAnomalyBanner()
    fun dismissInfoBanner() = sensorRepository.dismissInfoBanner()
    fun dismissMagneticInterferenceBanner() = sensorRepository.dismissMagneticInterferenceBanner()
    fun dismissVerticalOrientationBanner() = sensorRepository.dismissVerticalOrientationBanner()
    fun resetCompassRecovery() = sensorRepository.resetCompassRecovery()
    fun setCompassSensorMode(mode: CompassSensorMode) = sensorRepository.selectCompassSensorMode(mode)
    fun markCalibrationGuidanceSeen() = viewModelScope.launch {
        settingsRepository.setHasSeenCalibrationGuidance(true)
    }

    fun saveCurrentPlace(
        name: String,
        category: String,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double?
    ) {
        viewModelScope.launch {
            savedPlacesRepository.savePlace(
                SavedPlaceEntity(
                    name = name.trim().ifBlank { "Waypoint" },
                    category = category.trim().ifBlank { "Field Pin" },
                    latitude = latitude,
                    longitude = longitude,
                    altitudeMeters = altitudeMeters,
                    savedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteSavedPlace(id: Long) {
        viewModelScope.launch {
            savedPlacesRepository.deletePlace(id)
        }
    }

    fun setTrueNorth(enabled: Boolean) = viewModelScope.launch { settingsRepository.setTrueNorth(enabled) }
    fun setDialStyle(style: DialStyle) = viewModelScope.launch { settingsRepository.setDialStyle(style) }
    fun setExpressiveDialShape(shape: ExpressiveDialShape) =
        viewModelScope.launch { settingsRepository.setExpressiveDialShape(shape) }
    fun setExpressiveAccentPreset(preset: AccentPreset) =
        viewModelScope.launch { settingsRepository.setExpressiveAccentPreset(preset) }
    fun setHeadingFormat(format: HeadingFormat) = viewModelScope.launch { settingsRepository.setHeadingFormat(format) }
    fun setShowHeadingDecimals(enabled: Boolean) = viewModelScope.launch { settingsRepository.setShowHeadingDecimals(enabled) }
    fun setRawMode(enabled: Boolean) = viewModelScope.launch { settingsRepository.setRawMode(enabled) }
    fun setThemeMode(mode: AppThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setAccentPreset(preset: AccentPreset) = viewModelScope.launch { settingsRepository.setAccentPreset(preset) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    fun setFrostedBlur(enabled: Boolean) = viewModelScope.launch { settingsRepository.setFrostedBlur(enabled) }
    fun setImperialUnits(enabled: Boolean) = viewModelScope.launch { settingsRepository.setImperialUnits(enabled) }
    fun setShowCoordinates(enabled: Boolean) = viewModelScope.launch { settingsRepository.setShowCoordinates(enabled) }
    fun setShowAltitude(enabled: Boolean) = viewModelScope.launch { settingsRepository.setShowAltitude(enabled) }
    fun setShowMagneticField(enabled: Boolean) = viewModelScope.launch { settingsRepository.setShowMagneticField(enabled) }
    fun setShowLevelDecimals(enabled: Boolean) = viewModelScope.launch { settingsRepository.setShowLevelDecimals(enabled) }
    fun setHapticFeedback(enabled: Boolean) = viewModelScope.launch { settingsRepository.setHapticFeedback(enabled) }
    fun setHighContrast(enabled: Boolean) = viewModelScope.launch { settingsRepository.setHighContrast(enabled) }
    fun setReducedMotion(enabled: Boolean) = viewModelScope.launch { settingsRepository.setReducedMotion(enabled) }
    fun setLargeReadouts(enabled: Boolean) = viewModelScope.launch { settingsRepository.setLargeReadouts(enabled) }
    fun setRedScreenFilter(enabled: Boolean) = viewModelScope.launch { settingsRepository.setRedScreenFilter(enabled) }
}
