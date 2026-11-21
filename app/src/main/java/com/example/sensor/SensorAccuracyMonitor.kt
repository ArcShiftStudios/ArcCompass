package com.example.sensor

import android.hardware.Sensor
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Tracks real-time sensor accuracy changes reported by Android [SensorManager]
 * without inventing or smoothing out unreliable states.
 */
class SensorAccuracyMonitor {

    private val _accuracyBySensor =
        MutableStateFlow<Map<RequiredSensorType, SensorAccuracyLevel>>(emptyMap())
    val accuracyBySensor: StateFlow<Map<RequiredSensorType, SensorAccuracyLevel>> =
        _accuracyBySensor.asStateFlow()

    fun onAccuracyChanged(sensor: Sensor?, rawAccuracy: Int) {
        val type = sensor?.let { RequiredSensorType.fromAndroidType(it.type) } ?: return
        val mapped = mapAndroidAccuracy(rawAccuracy)
        _accuracyBySensor.update { current ->
            if (current[type] == mapped) current else current + (type to mapped)
        }
    }

    fun markActiveSampleReceived(sensorType: RequiredSensorType) {
        _accuracyBySensor.update { current ->
            val existing = current[sensorType]
            if (existing == null || existing == SensorAccuracyLevel.INITIALIZING) {
                current + (sensorType to SensorAccuracyLevel.MEDIUM)
            } else {
                current
            }
        }
    }

    fun clear() {
        _accuracyBySensor.value = emptyMap()
    }

    companion object {
        fun mapAndroidAccuracy(rawAccuracy: Int): SensorAccuracyLevel = when (rawAccuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> SensorAccuracyLevel.HIGH
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> SensorAccuracyLevel.MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> SensorAccuracyLevel.LOW
            SensorManager.SENSOR_STATUS_UNRELIABLE -> SensorAccuracyLevel.UNRELIABLE
            SensorManager.SENSOR_STATUS_NO_CONTACT -> SensorAccuracyLevel.NO_CONTACT
            else -> SensorAccuracyLevel.INITIALIZING
        }
    }
}
