package com.example.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorCapabilityEvaluatorTest {

    private val evaluator = SensorCapabilityEvaluator()

    private fun spec(
        type: RequiredSensorType,
        available: Boolean,
        accuracy: SensorAccuracyLevel = if (available) SensorAccuracyLevel.HIGH else SensorAccuracyLevel.NOT_APPLICABLE
    ): SensorSpec = SensorSpec(
        sensorType = type,
        isAvailable = available,
        name = if (available) "${type.displayName} HW" else "Not present",
        vendor = if (available) "TestVendor" else "—",
        version = if (available) 1 else 0,
        powerMa = if (available) 0.5f else 0f,
        resolution = if (available) 0.01f else 0f,
        maxRange = if (available) 100f else 0f,
        minDelayUs = if (available) 10000 else 0,
        reportingMode = if (available) SensorReportingMode.CONTINUOUS else SensorReportingMode.UNKNOWN,
        isWakeUpSensor = false,
        accuracy = accuracy,
        status = if (available) DiagnosticStatus.AVAILABLE else DiagnosticStatus.UNAVAILABLE
    )

    private fun defaultLocation(
        permission: LocationPermissionState = LocationPermissionState.DENIED,
        enabled: Boolean = true,
        lat: Double? = null,
        lon: Double? = null,
        alt: Double? = null
    ): LocationDiagnosticSnapshot = LocationDiagnosticSnapshot(
        permissionState = permission,
        isLocationServiceEnabled = enabled,
        availableProviders = listOf("gps", "network"),
        latitude = lat,
        longitude = lon,
        altitudeMeters = alt,
        status = when {
            permission == LocationPermissionState.DENIED -> DiagnosticStatus.PERMISSION_REQUIRED
            !enabled -> DiagnosticStatus.UNAVAILABLE
            lat != null && lon != null -> DiagnosticStatus.AVAILABLE
            else -> DiagnosticStatus.INITIALIZING
        },
        statusMessage = "Test status"
    )

    @Test
    fun `prefers rotation vector over accel and magnetometer when all are present`() {
        val specs = RequiredSensorType.entries.map { spec(it, available = true) }
        val report = evaluator.evaluate(specs, defaultLocation())

        assertEquals(CompassSource.ROTATION_VECTOR, report.compass.source)
        assertEquals(DiagnosticStatus.AVAILABLE, report.compass.status)
        assertEquals(LevelSource.GRAVITY, report.level.source)
        assertTrue(report.magneticFieldMonitor.source)
        assertTrue(report.gyroscopeDiagnostics.source)
        assertEquals(AltitudeSource.BAROMETRIC_ESTIMATE, report.altitude.source)
    }

    @Test
    fun `falls back to accelerometer and magnetometer when rotation vector is absent`() {
        val specs = RequiredSensorType.entries.map { type ->
            val available = type != RequiredSensorType.ROTATION_VECTOR
            spec(type, available)
        }
        val report = evaluator.evaluate(specs, defaultLocation())

        assertEquals(CompassSource.ACCELEROMETER_AND_MAGNETOMETER, report.compass.source)
        assertEquals(DiagnosticStatus.AVAILABLE, report.compass.status)
    }

    @Test
    fun `device without magnetometer disables compass and magnetic monitor but keeps level and gyro`() {
        val specs = RequiredSensorType.entries.map { type ->
            val available = when (type) {
                RequiredSensorType.MAGNETIC_FIELD,
                RequiredSensorType.ROTATION_VECTOR,
                RequiredSensorType.PRESSURE -> false
                else -> true
            }
            spec(type, available)
        }
        val report = evaluator.evaluate(
            specs,
            defaultLocation(
                permission = LocationPermissionState.GRANTED_FINE,
                enabled = true,
                lat = 12.95,
                lon = 77.62,
                alt = 920.0
            )
        )

        assertEquals(CompassSource.UNSUPPORTED, report.compass.source)
        assertEquals(DiagnosticStatus.UNAVAILABLE, report.compass.status)
        assertFalse(report.magneticFieldMonitor.source)
        assertEquals(DiagnosticStatus.UNAVAILABLE, report.magneticFieldMonitor.status)

        // Level and Gyro must remain operational
        assertEquals(LevelSource.GRAVITY, report.level.source)
        assertEquals(DiagnosticStatus.AVAILABLE, report.level.status)
        assertTrue(report.gyroscopeDiagnostics.source)
        assertEquals(DiagnosticStatus.AVAILABLE, report.gyroscopeDiagnostics.status)

        // Altitude falls back to GNSS when barometer is absent
        assertEquals(AltitudeSource.LOCATION_GNSS, report.altitude.source)
        assertEquals(DiagnosticStatus.AVAILABLE, report.altitude.status)
    }

    @Test
    fun `unreliable magnetometer accuracy flags compass and magnetic monitor`() {
        val specs = RequiredSensorType.entries.map { type ->
            when (type) {
                RequiredSensorType.ROTATION_VECTOR -> spec(type, false)
                RequiredSensorType.MAGNETIC_FIELD -> spec(type, true, SensorAccuracyLevel.UNRELIABLE)
                else -> spec(type, true)
            }
        }
        val report = evaluator.evaluate(specs, defaultLocation())

        assertEquals(CompassSource.ACCELEROMETER_AND_MAGNETOMETER, report.compass.source)
        assertEquals(DiagnosticStatus.ACCURACY_UNRELIABLE, report.compass.status)
        assertEquals(DiagnosticStatus.ACCURACY_UNRELIABLE, report.magneticFieldMonitor.status)
    }
}
