package com.example.sensor

import android.hardware.Sensor

/**
 * Hardware detection unit that inspects all required sensor types on the host device
 * without making assumptions about hardware availability.
 */
class SensorDetector(
    private val provider: SensorManagerProvider
) {

    data class DetectionSnapshot(
        val deviceInfo: DeviceHardwareInfo,
        val sensorSpecs: List<SensorSpec>,
        val resolvedSensors: Map<RequiredSensorType, Sensor>
    )

    fun detect(
        accuracyMap: Map<RequiredSensorType, SensorAccuracyLevel> = emptyMap(),
        isObserving: Boolean = false
    ): DetectionSnapshot {
        val allSensors = provider.getAllSensors()
        val deviceInfo = provider.getDeviceInfo(totalDetectedSensors = allSensors.size)
        val resolvedSensors = mutableMapOf<RequiredSensorType, Sensor>()

        val specs = RequiredSensorType.entries.map { requiredType ->
            val sensor = provider.getDefaultSensor(requiredType.androidType)
            if (sensor == null) {
                SensorSpec(
                    sensorType = requiredType,
                    isAvailable = false,
                    name = "Not present on hardware",
                    vendor = "—",
                    version = 0,
                    powerMa = 0f,
                    resolution = 0f,
                    maxRange = 0f,
                    minDelayUs = 0,
                    reportingMode = SensorReportingMode.UNKNOWN,
                    isWakeUpSensor = false,
                    accuracy = SensorAccuracyLevel.NOT_APPLICABLE,
                    status = DiagnosticStatus.UNAVAILABLE
                )
            } else {
                resolvedSensors[requiredType] = sensor
                val accuracy = resolveInitialAccuracy(
                    sensorType = requiredType,
                    reportedAccuracy = accuracyMap[requiredType],
                    isObserving = isObserving
                )
                val status = resolveDiagnosticStatus(
                    sensorType = requiredType,
                    accuracy = accuracy,
                    isObserving = isObserving
                )
                SensorSpec(
                    sensorType = requiredType,
                    isAvailable = true,
                    name = sensor.name.orEmpty().ifBlank { requiredType.displayName },
                    vendor = sensor.vendor.orEmpty().ifBlank { "Unknown vendor" },
                    version = sensor.version,
                    powerMa = sensor.power.coerceAtLeast(0f),
                    resolution = sensor.resolution,
                    maxRange = sensor.maximumRange,
                    minDelayUs = sensor.minDelay,
                    reportingMode = mapReportingMode(sensor.reportingMode),
                    isWakeUpSensor = sensor.isWakeUpSensor,
                    accuracy = accuracy,
                    status = status
                )
            }
        }

        return DetectionSnapshot(
            deviceInfo = deviceInfo,
            sensorSpecs = specs,
            resolvedSensors = resolvedSensors
        )
    }

    private fun resolveInitialAccuracy(
        sensorType: RequiredSensorType,
        reportedAccuracy: SensorAccuracyLevel?,
        isObserving: Boolean
    ): SensorAccuracyLevel {
        if (sensorType.isTriggerSensor) {
            return SensorAccuracyLevel.NOT_APPLICABLE
        }
        return when {
            reportedAccuracy != null -> reportedAccuracy
            isObserving -> SensorAccuracyLevel.INITIALIZING
            else -> SensorAccuracyLevel.INITIALIZING
        }
    }

    private fun resolveDiagnosticStatus(
        sensorType: RequiredSensorType,
        accuracy: SensorAccuracyLevel,
        isObserving: Boolean
    ): DiagnosticStatus {
        if (sensorType.isTriggerSensor) {
            return DiagnosticStatus.AVAILABLE
        }
        return when (accuracy) {
            SensorAccuracyLevel.UNRELIABLE,
            SensorAccuracyLevel.NO_CONTACT -> DiagnosticStatus.ACCURACY_UNRELIABLE
            SensorAccuracyLevel.INITIALIZING -> {
                if (isObserving) DiagnosticStatus.INITIALIZING else DiagnosticStatus.AVAILABLE
            }
            SensorAccuracyLevel.LOW,
            SensorAccuracyLevel.MEDIUM,
            SensorAccuracyLevel.HIGH,
            SensorAccuracyLevel.NOT_APPLICABLE -> DiagnosticStatus.AVAILABLE
        }
    }

    companion object {
        fun mapReportingMode(mode: Int): SensorReportingMode = when (mode) {
            Sensor.REPORTING_MODE_CONTINUOUS -> SensorReportingMode.CONTINUOUS
            Sensor.REPORTING_MODE_ON_CHANGE -> SensorReportingMode.ON_CHANGE
            Sensor.REPORTING_MODE_ONE_SHOT -> SensorReportingMode.ONE_SHOT
            Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> SensorReportingMode.SPECIAL_TRIGGER
            else -> SensorReportingMode.UNKNOWN
        }
    }
}
