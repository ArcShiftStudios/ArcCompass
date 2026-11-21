package com.example.sensor

import android.hardware.Sensor

/**
 * The 8 core hardware sensor types evaluated by the ArcCompass Sensor Detection Engine.
 */
enum class RequiredSensorType(
    val androidType: Int,
    val displayName: String,
    val unitLabel: String,
    val isTriggerSensor: Boolean = false
) {
    ACCELEROMETER(
        androidType = Sensor.TYPE_ACCELEROMETER,
        displayName = "Accelerometer",
        unitLabel = "m/s²"
    ),
    MAGNETIC_FIELD(
        androidType = Sensor.TYPE_MAGNETIC_FIELD,
        displayName = "Magnetic Field (Magnetometer)",
        unitLabel = "μT"
    ),
    GYROSCOPE(
        androidType = Sensor.TYPE_GYROSCOPE,
        displayName = "Gyroscope",
        unitLabel = "rad/s"
    ),
    ROTATION_VECTOR(
        androidType = Sensor.TYPE_ROTATION_VECTOR,
        displayName = "Rotation Vector",
        unitLabel = "quaternion"
    ),
    GRAVITY(
        androidType = Sensor.TYPE_GRAVITY,
        displayName = "Gravity",
        unitLabel = "m/s²"
    ),
    LINEAR_ACCELERATION(
        androidType = Sensor.TYPE_LINEAR_ACCELERATION,
        displayName = "Linear Acceleration",
        unitLabel = "m/s²"
    ),
    PRESSURE(
        androidType = Sensor.TYPE_PRESSURE,
        displayName = "Pressure (Barometer)",
        unitLabel = "hPa"
    ),
    SIGNIFICANT_MOTION(
        androidType = Sensor.TYPE_SIGNIFICANT_MOTION,
        displayName = "Significant Motion",
        unitLabel = "trigger",
        isTriggerSensor = true
    );

    companion object {
        fun fromAndroidType(type: Int): RequiredSensorType? =
            entries.firstOrNull { it.androidType == type }
    }
}

enum class SensorReportingMode(val label: String) {
    CONTINUOUS("Continuous"),
    ON_CHANGE("On-change"),
    ONE_SHOT("One-shot"),
    SPECIAL_TRIGGER("Special trigger"),
    UNKNOWN("Unknown")
}

enum class SensorAccuracyLevel(val label: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low"),
    UNRELIABLE("Unreliable"),
    NO_CONTACT("No contact"),
    INITIALIZING("Initializing"),
    NOT_APPLICABLE("N/A")
}

enum class DiagnosticStatus(val label: String) {
    AVAILABLE("Available"),
    UNAVAILABLE("Unavailable"),
    PERMISSION_REQUIRED("Permission required"),
    INITIALIZING("Initializing"),
    ACCURACY_UNRELIABLE("Accuracy unreliable")
}

/**
 * Hardware specification and live runtime status for a single sensor.
 */
data class SensorSpec(
    val sensorType: RequiredSensorType,
    val isAvailable: Boolean,
    val name: String,
    val vendor: String,
    val version: Int,
    val powerMa: Float,
    val resolution: Float,
    val maxRange: Float,
    val minDelayUs: Int,
    val reportingMode: SensorReportingMode,
    val isWakeUpSensor: Boolean,
    val accuracy: SensorAccuracyLevel,
    val status: DiagnosticStatus
)

data class DeviceHardwareInfo(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val device: String,
    val androidVersion: String,
    val sdkInt: Int,
    val totalDetectedSensors: Int
)

enum class CompassSource(val label: String) {
    ROTATION_VECTOR("Rotation Vector (9-axis/6-axis fused)"),
    ACCELEROMETER_AND_MAGNETOMETER("Accelerometer + Magnetometer fallback"),
    UNSUPPORTED("Unsupported — missing required magnetic hardware")
}

enum class LevelSource(val label: String) {
    GRAVITY("Gravity Sensor"),
    ACCELEROMETER("Accelerometer"),
    ROTATION_VECTOR("Rotation Vector"),
    UNSUPPORTED("Unsupported — no gravity or acceleration sensor")
}

enum class AltitudeSource(val label: String) {
    BAROMETRIC_ESTIMATE("Barometric Estimate (requires ref. pressure)"),
    LOCATION_GNSS("GNSS Location Altitude"),
    UNAVAILABLE("Unavailable")
}

enum class LocationPermissionState(val label: String) {
    GRANTED_FINE("Precise location granted"),
    GRANTED_COARSE("Approximate location granted"),
    DENIED("Permission denied / not granted")
}

data class FeatureCapability<T>(
    val status: DiagnosticStatus,
    val source: T,
    val summary: String,
    val detail: String
)

data class CapabilityReport(
    val compass: FeatureCapability<CompassSource>,
    val level: FeatureCapability<LevelSource>,
    val magneticFieldMonitor: FeatureCapability<Boolean>,
    val gyroscopeDiagnostics: FeatureCapability<Boolean>,
    val location: FeatureCapability<LocationPermissionState>,
    val altitude: FeatureCapability<AltitudeSource>
)

data class AxisReading(
    val x: Float,
    val y: Float,
    val z: Float
)

data class LiveTelemetrySnapshot(
    val magneticFieldMicroTesla: Float? = null,
    val magneticAxes: AxisReading? = null,
    val accelerometerAxes: AxisReading? = null,
    val gyroscopeAxes: AxisReading? = null,
    val gravityAxes: AxisReading? = null,
    val linearAccelerationAxes: AxisReading? = null,
    val rotationVectorComponents: FloatArray? = null,
    val pressureHpa: Float? = null,
    val barometricAltitudeEstimateMeters: Double? = null,
    val significantMotionTriggeredCount: Int = 0,
    val lastUpdatedMillis: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiveTelemetrySnapshot) return false
        return magneticFieldMicroTesla == other.magneticFieldMicroTesla &&
            magneticAxes == other.magneticAxes &&
            accelerometerAxes == other.accelerometerAxes &&
            gyroscopeAxes == other.gyroscopeAxes &&
            gravityAxes == other.gravityAxes &&
            linearAccelerationAxes == other.linearAccelerationAxes &&
            (rotationVectorComponents contentEquals other.rotationVectorComponents) &&
            pressureHpa == other.pressureHpa &&
            barometricAltitudeEstimateMeters == other.barometricAltitudeEstimateMeters &&
            significantMotionTriggeredCount == other.significantMotionTriggeredCount &&
            lastUpdatedMillis == other.lastUpdatedMillis
    }

    override fun hashCode(): Int {
        var result = magneticFieldMicroTesla?.hashCode() ?: 0
        result = 31 * result + (magneticAxes?.hashCode() ?: 0)
        result = 31 * result + (accelerometerAxes?.hashCode() ?: 0)
        result = 31 * result + (gyroscopeAxes?.hashCode() ?: 0)
        result = 31 * result + (gravityAxes?.hashCode() ?: 0)
        result = 31 * result + (linearAccelerationAxes?.hashCode() ?: 0)
        result = 31 * result + (rotationVectorComponents?.contentHashCode() ?: 0)
        result = 31 * result + (pressureHpa?.hashCode() ?: 0)
        result = 31 * result + (barometricAltitudeEstimateMeters?.hashCode() ?: 0)
        result = 31 * result + significantMotionTriggeredCount
        result = 31 * result + lastUpdatedMillis.hashCode()
        return result
    }
}

data class LocationDiagnosticSnapshot(
    val permissionState: LocationPermissionState,
    val isLocationServiceEnabled: Boolean,
    val availableProviders: List<String>,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val horizontalAccuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val verticalAccuracyMeters: Float? = null,
    val bearingDegrees: Float? = null,
    val speedMetersPerSecond: Float? = null,
    val providerName: String? = null,
    val fixTimestampMillis: Long? = null,
    val status: DiagnosticStatus,
    val statusMessage: String
)

/**
 * Live tilt-compensated heading output produced by [CompassSensorController].
 * Null heading values indicate missing hardware or uninitialized readings (never fabricated).
 */
data class CompassHeadingState(
    val isSupported: Boolean = false,
    val source: CompassSource = CompassSource.UNSUPPORTED,
    val status: DiagnosticStatus = DiagnosticStatus.UNAVAILABLE,
    val accuracy: SensorAccuracyLevel = SensorAccuracyLevel.NOT_APPLICABLE,
    val magneticAzimuthDegrees: Float? = null,
    val trueAzimuthDegrees: Float? = null,
    val declinationDegrees: Float? = null,
    val pitchDegrees: Float? = null,
    val rollDegrees: Float? = null
) {
    val hasReliableOrientation: Boolean
        get() = isSupported &&
            magneticAzimuthDegrees != null &&
            accuracy != SensorAccuracyLevel.UNRELIABLE &&
            accuracy != SensorAccuracyLevel.NO_CONTACT
}

/**
 * Live surface & plumb tilt output produced by [LevelSensorController].
 * Strictly independent of magnetometer hardware.
 */
data class LevelTiltState(
    val isSupported: Boolean = false,
    val source: LevelSource = LevelSource.UNSUPPORTED,
    val status: DiagnosticStatus = DiagnosticStatus.UNAVAILABLE,
    val accuracy: SensorAccuracyLevel = SensorAccuracyLevel.NOT_APPLICABLE,
    val rollDegrees: Float? = null,
    val pitchDegrees: Float? = null,
    val rawRollDegrees: Float? = null,
    val rawPitchDegrees: Float? = null,
    val totalTiltDegrees: Float? = null,
    val verticalPlumbDegrees: Float? = null,
    val isFlatLevel: Boolean = false,
    val isVerticalLevel: Boolean = false,
    val hasCustomReference: Boolean = false
)

/**
 * Future-ready contract for Milestone 3 (Compass Engine) and ArcLevel.
 */
data class CompassReadinessState(
    val isSupported: Boolean,
    val source: CompassSource,
    val status: DiagnosticStatus,
    val accuracy: SensorAccuracyLevel,
    val trueNorthDeclinationAvailable: Boolean
)

data class LevelReadinessState(
    val isSupported: Boolean,
    val source: LevelSource,
    val status: DiagnosticStatus,
    val independentOfMagnetometer: Boolean = true
)

data class SensorEngineState(
    val deviceInfo: DeviceHardwareInfo,
    val sensorSpecs: List<SensorSpec>,
    val capabilities: CapabilityReport,
    val telemetry: LiveTelemetrySnapshot,
    val locationState: LocationDiagnosticSnapshot,
    val isObserving: Boolean,
    val lastScanMillis: Long
)
