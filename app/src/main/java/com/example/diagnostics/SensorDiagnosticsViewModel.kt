package com.example.diagnostics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.ArcCompassApplication
import com.example.sensor.CompassReadinessState
import com.example.sensor.LevelReadinessState
import com.example.sensor.SensorEngineState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SensorListFilter(val label: String) {
    ALL("All"),
    AVAILABLE("Available"),
    UNAVAILABLE("Unavailable")
}

class SensorDiagnosticsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = (application as ArcCompassApplication).sensorRepository

    val engineState: StateFlow<SensorEngineState> = repository.engineState
    val compassReadiness: StateFlow<CompassReadinessState> = repository.compassReadiness
    val levelReadiness: StateFlow<LevelReadinessState> = repository.levelReadiness

    private val _selectedFilter = MutableStateFlow(SensorListFilter.ALL)
    val selectedFilter: StateFlow<SensorListFilter> = _selectedFilter.asStateFlow()

    fun onStartObserving() {
        repository.startObserving()
    }

    fun onStopObserving() {
        repository.stopObserving()
    }

    fun rescanHardware() {
        repository.refreshScan()
    }

    fun onLocationPermissionUpdated() {
        repository.onPermissionResult()
    }

    fun setFilter(filter: SensorListFilter) {
        _selectedFilter.value = filter
    }
}
