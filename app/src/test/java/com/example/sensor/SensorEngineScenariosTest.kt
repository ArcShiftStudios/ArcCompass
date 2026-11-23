package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEventListener
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SensorEngineScenariosTest {

    private class FakeSensorProvider(
        private val context: Context,
        private val sensorsByType: Map<Int, Sensor>,
        var locationEnabled: Boolean = true
    ) : SensorManagerProvider {

        val registeredListeners = mutableListOf<Pair<SensorEventListener, Int>>()

        override fun getDefaultSensor(type: Int): Sensor? = sensorsByType[type]

        override fun getAllSensors(): List<Sensor> = sensorsByType.values.toList()

        override fun registerListener(
            listener: SensorEventListener,
            sensor: Sensor,
            samplingPeriodUs: Int
        ): Boolean {
            registeredListeners.add(listener to sensor.type)
            return true
        }

        override fun unregisterListener(listener: SensorEventListener) {
            registeredListeners.removeAll { it.first === listener }
        }

        override fun requestTriggerSensor(listener: TriggerEventListener, sensor: Sensor): Boolean = false

        override fun cancelTriggerSensor(listener: TriggerEventListener, sensor: Sensor): Boolean = false

        override fun getLocationManager(): LocationManager? =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        override fun isLocationEnabled(): Boolean = locationEnabled

        override fun getDeviceInfo(totalDetectedSensors: Int): DeviceHardwareInfo =
            DeviceHardwareInfo(
                manufacturer = "ArcTest",
                model = "Baseline-Y21",
                brand = "Test",
                device = "y21",
                androidVersion = "13",
                sdkInt = 33,
                totalDetectedSensors = totalDetectedSensors
            )
    }

    private fun fakeSensor(type: Int): Sensor = ShadowSensor.newInstance(type)

    private fun fullSensorMap(): Map<Int, Sensor> = mapOf(
        Sensor.TYPE_ACCELEROMETER to fakeSensor(Sensor.TYPE_ACCELEROMETER),
        Sensor.TYPE_MAGNETIC_FIELD to fakeSensor(Sensor.TYPE_MAGNETIC_FIELD),
        Sensor.TYPE_GYROSCOPE to fakeSensor(Sensor.TYPE_GYROSCOPE),
        Sensor.TYPE_ROTATION_VECTOR to fakeSensor(Sensor.TYPE_ROTATION_VECTOR),
        Sensor.TYPE_GRAVITY to fakeSensor(Sensor.TYPE_GRAVITY),
        Sensor.TYPE_LINEAR_ACCELERATION to fakeSensor(Sensor.TYPE_LINEAR_ACCELERATION),
        Sensor.TYPE_PRESSURE to fakeSensor(Sensor.TYPE_PRESSURE),
        Sensor.TYPE_SIGNIFICANT_MOTION to fakeSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
    )

    // Scenario 1: Device with all required compass sensors
    @Test
    fun `scenario 1 full sensor device selects rotation vector and supports all instruments`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = FakeSensorProvider(context, fullSensorMap())
        val detector = SensorDetector(provider)
        val evaluator = SensorCapabilityEvaluator()
        val accuracyMonitor = SensorAccuracyMonitor()

        val detection = detector.detect(isObserving = true)
        val compass = CompassSensorController(provider, accuracyMonitor, onTelemetrySample = { _, _ -> })
        val level = LevelSensorController(provider, accuracyMonitor) { _, _ -> }

        compass.configureAndStart(detection.resolvedSensors, hasLocationFix = true)
        level.configureAndStart(detection.resolvedSensors)

        assertTrue(compass.headingState.value.isSupported)
        assertEquals(CompassSource.ROTATION_VECTOR, compass.headingState.value.source)
        assertTrue(level.levelState.value.isSupported)
        assertEquals(LevelSource.GRAVITY, level.levelState.value.source)

        val caps = evaluator.evaluate(
            detection.sensorSpecs,
            LocationDiagnosticSnapshot(
                permissionState = LocationPermissionState.GRANTED_FINE,
                isLocationServiceEnabled = true,
                availableProviders = listOf("gps"),
                latitude = 37.42,
                longitude = -122.08,
                altitudeMeters = 25.0,
                status = DiagnosticStatus.AVAILABLE,
                statusMessage = "Fix ready"
            )
        )
        assertTrue(caps.magneticFieldMonitor.source)
        assertTrue(caps.gyroscopeDiagnostics.source)
        assertEquals(AltitudeSource.BAROMETRIC_ESTIMATE, caps.altitude.source)
    }

    // Scenario 2: Device without magnetometer
    @Test
    fun `scenario 2 missing magnetometer reports compass unsupported without fabricating heading while level works`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sensors = fullSensorMap() - Sensor.TYPE_MAGNETIC_FIELD - Sensor.TYPE_ROTATION_VECTOR
        val provider = FakeSensorProvider(context, sensors)
        val detector = SensorDetector(provider)
        val accuracyMonitor = SensorAccuracyMonitor()

        val detection = detector.detect(isObserving = true)
        val compass = CompassSensorController(provider, accuracyMonitor, onTelemetrySample = { _, _ -> })
        val level = LevelSensorController(provider, accuracyMonitor) { _, _ -> }

        compass.configureAndStart(detection.resolvedSensors, hasLocationFix = false)
        level.configureAndStart(detection.resolvedSensors)

        assertFalse(compass.headingState.value.isSupported)
        assertFalse(compass.headingState.value.hasReliableOrientation)
        assertNull(compass.headingState.value.magneticAzimuthDegrees)
        assertNull(compass.headingState.value.trueAzimuthDegrees)
        assertEquals(CompassSource.UNSUPPORTED, compass.headingState.value.source)

        assertTrue(level.levelState.value.isSupported)
        assertEquals(LevelSource.GRAVITY, level.levelState.value.source)
    }

    // Scenario 3: Device without gyroscope
    @Test
    fun `scenario 3 missing gyroscope keeps compass and level operational`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sensors = fullSensorMap() - Sensor.TYPE_GYROSCOPE - Sensor.TYPE_ROTATION_VECTOR
        val provider = FakeSensorProvider(context, sensors)
        val detector = SensorDetector(provider)
        val evaluator = SensorCapabilityEvaluator()
        val accuracyMonitor = SensorAccuracyMonitor()

        val detection = detector.detect(isObserving = true)
        val compass = CompassSensorController(provider, accuracyMonitor, onTelemetrySample = { _, _ -> })
        compass.configureAndStart(detection.resolvedSensors, hasLocationFix = false)

        assertTrue(compass.headingState.value.isSupported)
        assertEquals(CompassSource.ACCELEROMETER_AND_MAGNETOMETER, compass.headingState.value.source)

        val report = evaluator.evaluate(
            detection.sensorSpecs,
            LocationDiagnosticSnapshot(
                permissionState = LocationPermissionState.DENIED,
                isLocationServiceEnabled = true,
                availableProviders = emptyList(),
                status = DiagnosticStatus.PERMISSION_REQUIRED,
                statusMessage = "Denied"
            )
        )
        assertFalse(report.gyroscopeDiagnostics.source)
        assertEquals(DiagnosticStatus.UNAVAILABLE, report.gyroscopeDiagnostics.status)
        assertEquals(LevelSource.GRAVITY, report.level.source)
    }

    // Scenario 4: Device without barometer
    @Test
    fun `scenario 4 missing barometer falls back to GNSS altitude or unavailable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sensors = fullSensorMap() - Sensor.TYPE_PRESSURE
        val provider = FakeSensorProvider(context, sensors)
        val detection = SensorDetector(provider).detect()
        val evaluator = SensorCapabilityEvaluator()

        val withGnss = evaluator.evaluate(
            detection.sensorSpecs,
            LocationDiagnosticSnapshot(
                permissionState = LocationPermissionState.GRANTED_FINE,
                isLocationServiceEnabled = true,
                availableProviders = listOf("gps"),
                latitude = 12.9,
                longitude = 77.5,
                altitudeMeters = 910.0,
                status = DiagnosticStatus.AVAILABLE,
                statusMessage = "GPS Fix"
            )
        )
        assertEquals(AltitudeSource.LOCATION_GNSS, withGnss.altitude.source)
        assertEquals(DiagnosticStatus.AVAILABLE, withGnss.altitude.status)

        val withoutGnss = evaluator.evaluate(
            detection.sensorSpecs,
            LocationDiagnosticSnapshot(
                permissionState = LocationPermissionState.DENIED,
                isLocationServiceEnabled = true,
                availableProviders = emptyList(),
                status = DiagnosticStatus.PERMISSION_REQUIRED,
                statusMessage = "Denied"
            )
        )
        assertEquals(AltitudeSource.UNAVAILABLE, withoutGnss.altitude.source)
        assertEquals(DiagnosticStatus.PERMISSION_REQUIRED, withoutGnss.altitude.status)
    }

    // Scenario 5 & 6: Location permission denied & Location services disabled
    @Test
    fun `scenario 5 and 6 handle denied permission and disabled location services gracefully`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = FakeSensorProvider(context, fullSensorMap(), locationEnabled = false)
        val detection = SensorDetector(provider).detect()
        val evaluator = SensorCapabilityEvaluator()

        val deniedCaps = evaluator.evaluate(
            detection.sensorSpecs,
            LocationDiagnosticSnapshot(
                permissionState = LocationPermissionState.DENIED,
                isLocationServiceEnabled = true,
                availableProviders = emptyList(),
                status = DiagnosticStatus.PERMISSION_REQUIRED,
                statusMessage = "Permission denied"
            )
        )
        assertEquals(DiagnosticStatus.PERMISSION_REQUIRED, deniedCaps.location.status)

        val disabledCaps = evaluator.evaluate(
            detection.sensorSpecs,
            LocationDiagnosticSnapshot(
                permissionState = LocationPermissionState.GRANTED_FINE,
                isLocationServiceEnabled = false,
                availableProviders = listOf("gps"),
                status = DiagnosticStatus.UNAVAILABLE,
                statusMessage = "Location disabled"
            )
        )
        assertEquals(DiagnosticStatus.UNAVAILABLE, disabledCaps.location.status)
    }

    // Scenario 8: Sensor interruption & accuracy drop + stop/restart cleanup
    @Test
    fun `scenario 8 accuracy drop marks orientation unreliable and stop unregisters listeners cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sensors = fullSensorMap()
        val provider = FakeSensorProvider(context, sensors)
        val detection = SensorDetector(provider).detect(isObserving = true)
        val accuracyMonitor = SensorAccuracyMonitor()
        val compass = CompassSensorController(provider, accuracyMonitor, onTelemetrySample = { _, _ -> })

        compass.configureAndStart(detection.resolvedSensors, hasLocationFix = false)
        assertTrue(provider.registeredListeners.isNotEmpty())

        // Simulate accuracy drop to unreliable
        compass.onAccuracyChanged(
            sensors[Sensor.TYPE_ROTATION_VECTOR],
            SensorManager.SENSOR_STATUS_UNRELIABLE
        )
        assertEquals(DiagnosticStatus.ACCURACY_UNRELIABLE, compass.headingState.value.status)
        assertEquals(SensorAccuracyLevel.UNRELIABLE, compass.headingState.value.accuracy)
        assertFalse(compass.headingState.value.hasReliableOrientation)

        // Simulate high accuracy recovery + declination update
        compass.onAccuracyChanged(
            sensors[Sensor.TYPE_ROTATION_VECTOR],
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        )
        assertEquals(DiagnosticStatus.AVAILABLE, compass.headingState.value.status)

        // Stop must unregister every listener cleanly
        compass.stop()
        assertTrue(provider.registeredListeners.isEmpty())
    }
}
