package com.example.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompassHealthMonitorTest {

    @Test
    fun `stationary phone with constant sensor samples does not trigger frozen anomaly`() {
        var clockMs = 1_000L
        val monitor = CompassHealthMonitor(nowMillis = { clockMs })
        monitor.onObservingStarted(
            availableSensors = setOf(
                RequiredSensorType.ROTATION_VECTOR,
                RequiredSensorType.MAGNETIC_FIELD,
                RequiredSensorType.ACCELEROMETER
            ),
            primaryCompassSensor = RequiredSensorType.ROTATION_VECTOR,
            gameRotationVectorAvailable = true
        )

        repeat(15) {
            clockMs += 300L
            monitor.onSample(RequiredSensorType.ACCELEROMETER, floatArrayOf(0.01f, 0.02f, 9.81f))
            monitor.onSample(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(22f, -14f, 41f))
            monitor.onSample(RequiredSensorType.ROTATION_VECTOR, floatArrayOf(0f, 0f, 0.25f, 0.96f))
        }

        assertNull(monitor.state.value.suspectedAnomaly)
        assertEquals(InteractiveCheckPhase.IDLE, monitor.state.value.checkPhase)
    }

    @Test
    fun `frozen magnetometer while device moves triggers anomaly banner requiring user confirmation`() {
        var clockMs = 1_000L
        val monitor = CompassHealthMonitor(nowMillis = { clockMs })
        monitor.onObservingStarted(
            availableSensors = setOf(
                RequiredSensorType.ROTATION_VECTOR,
                RequiredSensorType.MAGNETIC_FIELD,
                RequiredSensorType.ACCELEROMETER
            ),
            primaryCompassSensor = RequiredSensorType.ROTATION_VECTOR,
            gameRotationVectorAvailable = true
        )

        repeat(12) { idx ->
            clockMs += 320L
            // Accelerometer varies significantly (phone is moving)
            monitor.onSample(
                RequiredSensorType.ACCELEROMETER,
                floatArrayOf((idx % 3) * 1.4f, ((idx + 1) % 3) * 1.2f, 9.81f)
            )
            // Magnetometer stays bit-identical during motion
            monitor.onSample(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(20f, -15f, 40f))
            monitor.onSample(
                RequiredSensorType.ROTATION_VECTOR,
                floatArrayOf(0f, 0f, 0.1f + idx * 0.02f, 0.95f)
            )
        }

        assertEquals(CompassAnomalyReason.FROZEN_DURING_MOTION, monitor.state.value.suspectedAnomaly)
        // Must NOT auto-start interactive test without confirmation
        assertEquals(InteractiveCheckPhase.IDLE, monitor.state.value.checkPhase)

        monitor.requestCheckConfirmation()
        assertEquals(InteractiveCheckPhase.AWAITING_CONFIRMATION, monitor.state.value.checkPhase)
    }

    @Test
    fun `invalid non-finite or zeroed readings fail validation and trigger alert`() {
        assertFalse(CompassHealthMonitor.areValuesValid(RequiredSensorType.ROTATION_VECTOR, floatArrayOf(Float.NaN, 0f, 1f)))
        assertFalse(CompassHealthMonitor.areValuesValid(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(0f, 0f, 0f)))
        assertTrue(CompassHealthMonitor.areValuesValid(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(20f, -18f, 38f)))

        var clockMs = 1_000L
        val monitor = CompassHealthMonitor(nowMillis = { clockMs })
        monitor.onObservingStarted(
            availableSensors = setOf(RequiredSensorType.MAGNETIC_FIELD, RequiredSensorType.ROTATION_VECTOR),
            primaryCompassSensor = RequiredSensorType.ROTATION_VECTOR,
            gameRotationVectorAvailable = false
        )

        repeat(3) {
            clockMs += 100L
            monitor.onSample(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(0f, 0f, 0f))
        }

        assertEquals(CompassAnomalyReason.INVALID_SENSOR_DATA, monitor.state.value.suspectedAnomaly)
    }

    @Test
    fun `interactive check selects supported fallbacks honestly without fabricating North`() {
        var clockMs = 1_000L
        val monitor = CompassHealthMonitor(nowMillis = { clockMs })
        monitor.onObservingStarted(
            availableSensors = setOf(
                RequiredSensorType.ROTATION_VECTOR,
                RequiredSensorType.MAGNETIC_FIELD,
                RequiredSensorType.ACCELEROMETER,
                RequiredSensorType.GYROSCOPE
            ),
            primaryCompassSensor = RequiredSensorType.ROTATION_VECTOR,
            gameRotationVectorAvailable = true
        )

        // Case 1: Rotation Vector frozen during movement, but Mag + Accel healthy -> Accel+Mag fallback
        monitor.startInteractiveCheck()
        repeat(6) { idx ->
            clockMs += 800L
            monitor.onSample(RequiredSensorType.ROTATION_VECTOR, floatArrayOf(0f, 0f, 0.2f, 0.9f))
            monitor.onSample(RequiredSensorType.ACCELEROMETER, floatArrayOf(idx * 0.9f, -idx * 0.5f, 9.6f))
            monitor.onSample(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(18f + idx * 3f, -12f, 40f))
            monitor.onSample(RequiredSensorType.GYROSCOPE, floatArrayOf(0.4f, 0.1f, 0.3f))
        }
        val firstResult = monitor.state.value
        assertEquals(InteractiveCheckPhase.COMPLETED, firstResult.checkPhase)
        assertEquals(CompassSource.ACCELEROMETER_AND_MAGNETOMETER, firstResult.activeRecoverySource)
        assertNotNull(firstResult.recoveryDisclosure)

        // Case 2: Both Rotation Vector and Magnetometer frozen during movement -> Relative Game Rotation Vector
        val monitor2 = CompassHealthMonitor(nowMillis = { clockMs })
        monitor2.onObservingStarted(
            availableSensors = setOf(
                RequiredSensorType.ROTATION_VECTOR,
                RequiredSensorType.MAGNETIC_FIELD,
                RequiredSensorType.ACCELEROMETER,
                RequiredSensorType.GYROSCOPE
            ),
            primaryCompassSensor = RequiredSensorType.ROTATION_VECTOR,
            gameRotationVectorAvailable = true
        )
        monitor2.startInteractiveCheck()
        repeat(6) { idx ->
            clockMs += 800L
            monitor2.onSample(RequiredSensorType.ROTATION_VECTOR, floatArrayOf(0f, 0f, 0.2f, 0.9f))
            monitor2.onSample(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(18f, -12f, 40f))
            monitor2.onSample(RequiredSensorType.ACCELEROMETER, floatArrayOf(idx * 1.1f, 0.2f, 9.8f))
            monitor2.onSample(RequiredSensorType.GYROSCOPE, floatArrayOf(0.5f, 0.2f, -0.3f))
        }
        val secondResult = monitor2.state.value
        assertEquals(CompassSource.GAME_ROTATION_VECTOR_RELATIVE, secondResult.activeRecoverySource)
        assertTrue(secondResult.recoveryDisclosure.orEmpty().contains("no absolute North", ignoreCase = true) ||
            secondResult.recoveryDisclosure.orEmpty().contains("no true/magnetic North", ignoreCase = true))
    }

    @Test
    fun `interactive check reports gyro drift fallback and unsupported when no backup works`() {
        var clockMs = 1_000L
        val monitor = CompassHealthMonitor(nowMillis = { clockMs })
        monitor.onObservingStarted(
            availableSensors = setOf(
                RequiredSensorType.MAGNETIC_FIELD,
                RequiredSensorType.ACCELEROMETER,
                RequiredSensorType.GYROSCOPE
            ),
            primaryCompassSensor = RequiredSensorType.MAGNETIC_FIELD,
            gameRotationVectorAvailable = false
        )

        monitor.startInteractiveCheck()
        repeat(6) { idx ->
            clockMs += 800L
            monitor.onSample(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(0f, 0f, 0f))
            monitor.onSample(RequiredSensorType.ACCELEROMETER, floatArrayOf(idx * 1.2f, 0f, 9.8f))
            monitor.onSample(RequiredSensorType.GYROSCOPE, floatArrayOf(0.1f, 0.6f + idx * 0.2f, 0.1f))
        }
        val gyroResult = monitor.state.value
        assertEquals(CompassSource.GYROSCOPE_RELATIVE, gyroResult.activeRecoverySource)
        assertTrue(gyroResult.recoveryDisclosure.orEmpty().contains("drift", ignoreCase = true))
    }

    @Test
    fun `done dismisses completed results while preserving fallback recovery and clearRecoveryState restores default`() {
        var clockMs = 1_000L
        val monitor = CompassHealthMonitor(nowMillis = { clockMs })
        monitor.onObservingStarted(
            availableSensors = setOf(
                RequiredSensorType.ROTATION_VECTOR,
                RequiredSensorType.MAGNETIC_FIELD,
                RequiredSensorType.ACCELEROMETER,
                RequiredSensorType.GYROSCOPE
            ),
            primaryCompassSensor = RequiredSensorType.ROTATION_VECTOR,
            gameRotationVectorAvailable = true
        )

        monitor.requestCheckConfirmation()
        assertEquals(InteractiveCheckPhase.AWAITING_CONFIRMATION, monitor.state.value.checkPhase)

        monitor.startInteractiveCheck()
        assertEquals(InteractiveCheckPhase.RUNNING, monitor.state.value.checkPhase)

        repeat(6) { idx ->
            clockMs += 800L
            monitor.onSample(RequiredSensorType.ROTATION_VECTOR, floatArrayOf(0f, 0f, 0.2f, 0.9f))
            monitor.onSample(RequiredSensorType.ACCELEROMETER, floatArrayOf(idx * 0.9f, -idx * 0.5f, 9.6f))
            monitor.onSample(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(18f + idx * 3f, -12f, 40f))
            monitor.onSample(RequiredSensorType.GYROSCOPE, floatArrayOf(0.4f, 0.1f, 0.3f))
        }
        assertEquals(InteractiveCheckPhase.COMPLETED, monitor.state.value.checkPhase)
        assertEquals(CompassSource.ACCELEROMETER_AND_MAGNETOMETER, monitor.state.value.activeRecoverySource)
        assertTrue(monitor.state.value.sensorOutcomes.isNotEmpty())

        // Tapping Done must dismiss to IDLE while keeping activeRecoverySource intact
        monitor.cancelCheckConfirmation()
        assertEquals(InteractiveCheckPhase.IDLE, monitor.state.value.checkPhase)
        assertEquals(CompassSource.ACCELEROMETER_AND_MAGNETOMETER, monitor.state.value.activeRecoverySource)
        assertNotNull(monitor.state.value.recoveryDisclosure)

        // Retest clears stale outcomes while running
        monitor.startInteractiveCheck()
        assertEquals(InteractiveCheckPhase.RUNNING, monitor.state.value.checkPhase)
        assertTrue(monitor.state.value.sensorOutcomes.isEmpty())

        // Clear recovery ("Use Default") dismisses dialog and restores default state
        monitor.clearRecoveryState()
        assertEquals(InteractiveCheckPhase.IDLE, monitor.state.value.checkPhase)
        assertNull(monitor.state.value.activeRecoverySource)
        assertNull(monitor.state.value.recoveryDisclosure)
    }

    @Test
    fun `frozen anomaly clears after switching to fusion and stationary fusion does not falsely retrigger`() {
        var clockMs = 1_000L
        val monitor = CompassHealthMonitor(nowMillis = { clockMs })
        monitor.onObservingStarted(
            availableSensors = setOf(
                RequiredSensorType.ROTATION_VECTOR,
                RequiredSensorType.MAGNETIC_FIELD,
                RequiredSensorType.ACCELEROMETER
            ),
            primaryCompassSensor = RequiredSensorType.ROTATION_VECTOR,
            gameRotationVectorAvailable = true
        )

        // Trigger frozen RV anomaly while moving
        repeat(12) { idx ->
            clockMs += 320L
            monitor.onSample(RequiredSensorType.ACCELEROMETER, floatArrayOf((idx % 3) * 1.3f, 0.2f, 9.8f))
            monitor.onSample(RequiredSensorType.ROTATION_VECTOR, floatArrayOf(0f, 0f, 0.2f, 0.9f))
            monitor.onSample(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(22f, -14f, 41f))
        }
        assertEquals(CompassAnomalyReason.FROZEN_DURING_MOTION, monitor.state.value.suspectedAnomaly)

        // Run interactive check where Accel + Mag recover
        monitor.startInteractiveCheck()
        repeat(6) { idx ->
            clockMs += 800L
            monitor.onSample(RequiredSensorType.ROTATION_VECTOR, floatArrayOf(0f, 0f, 0.2f, 0.9f))
            monitor.onSample(RequiredSensorType.ACCELEROMETER, floatArrayOf(idx * 0.9f, -idx * 0.5f, 9.6f))
            monitor.onSample(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(18f + idx * 3f, -12f, 40f))
        }
        val recommended = monitor.finalizeInteractiveCheck()
        assertEquals(CompassSource.ACCELEROMETER_AND_MAGNETOMETER, recommended)
        assertNull("Frozen anomaly must clear immediately after fusion recovery", monitor.state.value.suspectedAnomaly)

        monitor.onCompassSourceChanged(
            CompassSource.ACCELEROMETER_AND_MAGNETOMETER,
            com.example.data.CompassSensorMode.FUSION_ACCEL_MAG
        )
        // Stationary low-variance stream in fusion mode must NOT re-trigger frozen warning
        repeat(15) {
            clockMs += 300L
            monitor.onSample(RequiredSensorType.ACCELEROMETER, floatArrayOf(0.01f, 0.01f, 9.81f))
            monitor.onSample(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(21f, -13f, 40f))
            monitor.onHeadingComputed(142f, 142f, 4f, SensorAccuracyLevel.MEDIUM)
        }
        assertNull(monitor.state.value.suspectedAnomaly)
    }

    @Test
    fun `frozen anomaly auto-clears once healthy changing headings resume`() {
        var clockMs = 1_000L
        val monitor = CompassHealthMonitor(nowMillis = { clockMs })
        monitor.onObservingStarted(
            availableSensors = setOf(
                RequiredSensorType.ROTATION_VECTOR,
                RequiredSensorType.MAGNETIC_FIELD,
                RequiredSensorType.ACCELEROMETER
            ),
            primaryCompassSensor = RequiredSensorType.ROTATION_VECTOR,
            gameRotationVectorAvailable = true
        )

        repeat(12) { idx ->
            clockMs += 320L
            monitor.onSample(RequiredSensorType.ACCELEROMETER, floatArrayOf((idx % 3) * 1.4f, 0.4f, 9.7f))
            monitor.onSample(RequiredSensorType.ROTATION_VECTOR, floatArrayOf(0f, 0f, 0.2f, 0.9f))
            monitor.onSample(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(20f, -15f, 40f))
        }
        assertEquals(CompassAnomalyReason.FROZEN_DURING_MOTION, monitor.state.value.suspectedAnomaly)

        // Healthy headings resume across several frames
        repeat(4) { step ->
            clockMs += 200L
            val deg = 100f + step * 5f
            monitor.onHeadingComputed(deg, deg, 5f, SensorAccuracyLevel.HIGH)
        }
        assertNull(monitor.state.value.suspectedAnomaly)
    }

    @Test
    fun `info banners deduplicate repeated event keys and clear on dismiss`() {
        var clockMs = 5_000L
        val monitor = CompassHealthMonitor(nowMillis = { clockMs })

        val firstShown = monitor.showInfoBanner("fusion_active", "Sensor Fusion Active", "Switched to fusion")
        assertTrue(firstShown)
        assertEquals("fusion_active", monitor.state.value.infoBanner?.eventKey)

        // Repeated identical event during recomposition / re-evaluation is deduplicated
        clockMs += 100L
        val duplicateShown = monitor.showInfoBanner("fusion_active", "Sensor Fusion Active", "Switched to fusion")
        assertFalse(duplicateShown)

        monitor.dismissInfoBanner("other_key")
        assertNotNull(monitor.state.value.infoBanner)

        monitor.dismissInfoBanner("fusion_active")
        assertNull(monitor.state.value.infoBanner)
    }

    @Test
    fun `vertical orientation warning triggers only when upright and unreliable, and clears when flat or stable`() {
        var clockMs = 1_000L
        val monitor = CompassHealthMonitor(nowMillis = { clockMs })
        monitor.onObservingStarted(
            availableSensors = setOf(RequiredSensorType.ROTATION_VECTOR, RequiredSensorType.MAGNETIC_FIELD),
            primaryCompassSensor = RequiredSensorType.ROTATION_VECTOR,
            gameRotationVectorAvailable = true
        )

        // Upright (72°) but HIGH accuracy & smooth heading -> MUST NOT warn
        repeat(6) { step ->
            clockMs += 400L
            val deg = 45f + step * 2f
            monitor.onHeadingComputed(deg, deg, 72f, SensorAccuracyLevel.HIGH)
        }
        assertFalse(monitor.state.value.verticalOrientationWarningActive)

        // Upright (74°) with LOW accuracy sustained >= 1200ms -> warns cleanly
        repeat(5) { step ->
            clockMs += 400L
            val deg = 60f + step * 3f
            monitor.onHeadingComputed(deg, deg, 74f, SensorAccuracyLevel.LOW)
        }
        assertTrue(monitor.state.value.verticalOrientationWarningActive)

        // Holding flatter (32°) with MEDIUM/HIGH accuracy -> auto-clears promptly
        clockMs += 200L
        monitor.onHeadingComputed(64f, 64f, 32f, SensorAccuracyLevel.HIGH)
        assertFalse(monitor.state.value.verticalOrientationWarningActive)
    }

    @Test
    fun `magnetic interference triggers on sustained abnormal field and clears when Earth baseline stabilizes`() {
        var clockMs = 1_000L
        val monitor = CompassHealthMonitor(nowMillis = { clockMs })
        monitor.onObservingStarted(
            availableSensors = setOf(RequiredSensorType.ROTATION_VECTOR, RequiredSensorType.MAGNETIC_FIELD),
            primaryCompassSensor = RequiredSensorType.ROTATION_VECTOR,
            gameRotationVectorAvailable = true
        )

        // Normal Earth field (45-48 uT) -> no warning
        repeat(5) {
            clockMs += 400L
            monitor.onSample(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(18f, -12f, 40f))
        }
        assertFalse(monitor.state.value.magneticInterferenceActive)

        // Sustained elevated magnetic field (> 80 uT) across >= 1000ms -> triggers warning
        repeat(4) {
            clockMs += 400L
            monitor.onSample(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(85f, 40f, 65f))
        }
        assertTrue(monitor.state.value.magneticInterferenceActive)

        // Return to steady nominal Earth field (~45 uT) across >= 1500ms -> clears warning
        repeat(6) {
            clockMs += 400L
            monitor.onSample(RequiredSensorType.MAGNETIC_FIELD, floatArrayOf(18f, -12f, 40f))
        }
        assertFalse(monitor.state.value.magneticInterferenceActive)
        assertNull(monitor.state.value.magneticInterferenceMicroTesla)
    }
}
