package com.example.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Dedicated controller for compass-capable sensors.
 * Computes tilt-compensated smoothed magnetic heading and True North declination
 * using either Rotation Vector (preferred) or Accelerometer + Magnetometer fallback.
 *
 * Pre-allocates matrices to avoid per-sample allocations on entry-level hardware.
 */
class CompassSensorController(
    private val provider: SensorManagerProvider,
    private val accuracyMonitor: SensorAccuracyMonitor,
    private val onTelemetrySample: (RequiredSensorType, FloatArray) -> Unit
) : SensorEventListener {

    private val _readiness = MutableStateFlow(
        CompassReadinessState(
            isSupported = false,
            source = CompassSource.UNSUPPORTED,
            status = DiagnosticStatus.UNAVAILABLE,
            accuracy = SensorAccuracyLevel.NOT_APPLICABLE,
            trueNorthDeclinationAvailable = false
        )
    )
    val readiness: StateFlow<CompassReadinessState> = _readiness.asStateFlow()

    private val _headingState = MutableStateFlow(CompassHeadingState())
    val headingState: StateFlow<CompassHeadingState> = _headingState.asStateFlow()

    private var activeSource: CompassSource = CompassSource.UNSUPPORTED
    private var isRegistered = false
    private var declinationDegrees: Float? = null

    // Pre-allocated buffers for zero-GC sensor processing
    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val accelBuffer = FloatArray(3)
    private val magBuffer = FloatArray(3)
    private var hasAccelSample = false
    private var hasMagSample = false

    private var smoothedAzimuthDeg: Float? = null
    private var lastHeadingEmitMs = 0L

    fun configureAndStart(
        resolvedSensors: Map<RequiredSensorType, Sensor>,
        hasLocationFix: Boolean,
        samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_UI
    ) {
        stop()
        hasAccelSample = false
        hasMagSample = false
        smoothedAzimuthDeg = null

        val rotationVector = resolvedSensors[RequiredSensorType.ROTATION_VECTOR]
        val accelerometer = resolvedSensors[RequiredSensorType.ACCELEROMETER]
        val magnetometer = resolvedSensors[RequiredSensorType.MAGNETIC_FIELD]

        when {
            rotationVector != null -> {
                activeSource = CompassSource.ROTATION_VECTOR
                val ok = provider.registerListener(this, rotationVector, samplingPeriodUs)
                if (magnetometer != null) {
                    provider.registerListener(this, magnetometer, samplingPeriodUs)
                }
                isRegistered = ok
                val status = if (ok) DiagnosticStatus.INITIALIZING else DiagnosticStatus.UNAVAILABLE
                val source = if (ok) CompassSource.ROTATION_VECTOR else CompassSource.UNSUPPORTED
                _readiness.value = CompassReadinessState(
                    isSupported = ok,
                    source = source,
                    status = status,
                    accuracy = if (ok) SensorAccuracyLevel.INITIALIZING else SensorAccuracyLevel.NOT_APPLICABLE,
                    trueNorthDeclinationAvailable = hasLocationFix
                )
                _headingState.value = CompassHeadingState(
                    isSupported = ok,
                    source = source,
                    status = status,
                    accuracy = if (ok) SensorAccuracyLevel.INITIALIZING else SensorAccuracyLevel.NOT_APPLICABLE,
                    declinationDegrees = declinationDegrees
                )
            }
            accelerometer != null && magnetometer != null -> {
                activeSource = CompassSource.ACCELEROMETER_AND_MAGNETOMETER
                val accelOk = provider.registerListener(this, accelerometer, samplingPeriodUs)
                val magOk = provider.registerListener(this, magnetometer, samplingPeriodUs)
                isRegistered = accelOk && magOk
                if (!isRegistered) {
                    provider.unregisterListener(this)
                }
                val status = if (isRegistered) DiagnosticStatus.INITIALIZING else DiagnosticStatus.UNAVAILABLE
                val source = if (isRegistered) {
                    CompassSource.ACCELEROMETER_AND_MAGNETOMETER
                } else {
                    CompassSource.UNSUPPORTED
                }
                _readiness.value = CompassReadinessState(
                    isSupported = isRegistered,
                    source = source,
                    status = status,
                    accuracy = if (isRegistered) SensorAccuracyLevel.INITIALIZING else SensorAccuracyLevel.NOT_APPLICABLE,
                    trueNorthDeclinationAvailable = hasLocationFix
                )
                _headingState.value = CompassHeadingState(
                    isSupported = isRegistered,
                    source = source,
                    status = status,
                    accuracy = if (isRegistered) SensorAccuracyLevel.INITIALIZING else SensorAccuracyLevel.NOT_APPLICABLE,
                    declinationDegrees = declinationDegrees
                )
            }
            else -> {
                activeSource = CompassSource.UNSUPPORTED
                isRegistered = false
                _readiness.value = CompassReadinessState(
                    isSupported = false,
                    source = CompassSource.UNSUPPORTED,
                    status = DiagnosticStatus.UNAVAILABLE,
                    accuracy = SensorAccuracyLevel.NOT_APPLICABLE,
                    trueNorthDeclinationAvailable = false
                )
                _headingState.value = CompassHeadingState(
                    isSupported = false,
                    source = CompassSource.UNSUPPORTED,
                    status = DiagnosticStatus.UNAVAILABLE,
                    accuracy = SensorAccuracyLevel.NOT_APPLICABLE,
                    declinationDegrees = declinationDegrees
                )
            }
        }
    }

    fun updateDeclination(declinationDeg: Float?) {
        declinationDegrees = declinationDeg
        val currentReadiness = _readiness.value
        val hasDecl = declinationDeg != null
        if (currentReadiness.trueNorthDeclinationAvailable != hasDecl) {
            _readiness.value = currentReadiness.copy(trueNorthDeclinationAvailable = hasDecl)
        }
        val currentHeading = _headingState.value
        val updatedTrue = currentHeading.magneticAzimuthDegrees?.let { mag ->
            declinationDeg?.let { decl -> normalizeDegrees(mag + decl) }
        }
        _headingState.value = currentHeading.copy(
            declinationDegrees = declinationDeg,
            trueAzimuthDegrees = updatedTrue
        )
    }

    fun updateDeclinationAvailability(hasLocationFix: Boolean) {
        val current = _readiness.value
        if (current.trueNorthDeclinationAvailable != hasLocationFix) {
            _readiness.value = current.copy(trueNorthDeclinationAvailable = hasLocationFix)
        }
    }

    fun stop() {
        if (isRegistered) {
            provider.unregisterListener(this)
            isRegistered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorEvent = event ?: return
        val type = RequiredSensorType.fromAndroidType(sensorEvent.sensor.type) ?: return
        accuracyMonitor.markActiveSampleReceived(type)
        onTelemetrySample(type, sensorEvent.values.clone())

        when (type) {
            RequiredSensorType.ROTATION_VECTOR -> {
                if (activeSource == CompassSource.ROTATION_VECTOR) {
                    processRotationVector(sensorEvent.values)
                }
            }
            RequiredSensorType.ACCELEROMETER -> {
                if (sensorEvent.values.size >= 3) {
                    lowPassInto(sensorEvent.values, accelBuffer, hasAccelSample, ACCEL_ALPHA)
                    hasAccelSample = true
                    if (activeSource == CompassSource.ACCELEROMETER_AND_MAGNETOMETER && hasMagSample) {
                        processAccelAndMag()
                    }
                }
            }
            RequiredSensorType.MAGNETIC_FIELD -> {
                if (sensorEvent.values.size >= 3) {
                    lowPassInto(sensorEvent.values, magBuffer, hasMagSample, MAG_ALPHA)
                    hasMagSample = true
                    if (activeSource == CompassSource.ACCELEROMETER_AND_MAGNETOMETER && hasAccelSample) {
                        processAccelAndMag()
                    }
                }
            }
            else -> Unit
        }

        val relevantType = if (activeSource == CompassSource.ROTATION_VECTOR) {
            RequiredSensorType.ROTATION_VECTOR
        } else {
            RequiredSensorType.MAGNETIC_FIELD
        }
        updateReadinessAccuracy(accuracyMonitor.accuracyBySensor.value[relevantType])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        accuracyMonitor.onAccuracyChanged(sensor, accuracy)
        val type = sensor?.let { RequiredSensorType.fromAndroidType(it.type) } ?: return
        if (type == RequiredSensorType.ROTATION_VECTOR || type == RequiredSensorType.MAGNETIC_FIELD) {
            updateReadinessAccuracy(SensorAccuracyMonitor.mapAndroidAccuracy(accuracy))
        }
    }

    private fun processRotationVector(values: FloatArray) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()

        // When held upright (e.g. Find mode), remap Z-axis to avoid azimuth gimbal lock
        val azimuthRad = if (abs(pitchDeg) > UPRIGHT_REMAP_THRESHOLD_DEG) {
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remappedMatrix
            )
            SensorManager.getOrientation(remappedMatrix, orientationAngles)
            orientationAngles[0]
        } else {
            orientationAngles[0]
        }

        val rawDeg = normalizeDegrees(Math.toDegrees(azimuthRad.toDouble()).toFloat())
        val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
        emitHeading(rawDeg, pitchDeg, rollDeg)
    }

    private fun processAccelAndMag() {
        val ok = SensorManager.getRotationMatrix(rotationMatrix, null, accelBuffer, magBuffer)
        if (!ok) return
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val azimuthRad = if (abs(pitchDeg) > UPRIGHT_REMAP_THRESHOLD_DEG) {
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remappedMatrix
            )
            SensorManager.getOrientation(remappedMatrix, orientationAngles)
            orientationAngles[0]
        } else {
            orientationAngles[0]
        }
        val rawDeg = normalizeDegrees(Math.toDegrees(azimuthRad.toDouble()).toFloat())
        val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
        emitHeading(rawDeg, pitchDeg, rollDeg)
    }

    private fun emitHeading(rawDegrees: Float, pitchDeg: Float, rollDeg: Float) {
        val smoothed = smoothCircularDegrees(smoothedAzimuthDeg, rawDegrees, HEADING_SMOOTH_ALPHA)
        smoothedAzimuthDeg = smoothed

        val now = System.currentTimeMillis()
        val previous = _headingState.value
        val prevAzimuth = previous.magneticAzimuthDegrees
        val delta = if (prevAzimuth != null) abs(shortestSignedAngle(prevAzimuth, smoothed)) else 999f

        if (now - lastHeadingEmitMs < HEADING_UI_MIN_INTERVAL_MS && delta < 0.2f) {
            return
        }
        lastHeadingEmitMs = now

        val decl = declinationDegrees
        val trueAzimuth = decl?.let { normalizeDegrees(smoothed + it) }
        val currentReadiness = _readiness.value
        val effectiveStatus = if (currentReadiness.status == DiagnosticStatus.INITIALIZING) {
            DiagnosticStatus.AVAILABLE
        } else {
            currentReadiness.status
        }
        if (currentReadiness.status == DiagnosticStatus.INITIALIZING) {
            _readiness.value = currentReadiness.copy(status = DiagnosticStatus.AVAILABLE)
        }

        _headingState.value = previous.copy(
            isSupported = true,
            source = activeSource,
            status = effectiveStatus,
            accuracy = currentReadiness.accuracy,
            magneticAzimuthDegrees = smoothed,
            trueAzimuthDegrees = trueAzimuth,
            declinationDegrees = decl,
            pitchDegrees = pitchDeg,
            rollDegrees = rollDeg
        )
    }

    private fun updateReadinessAccuracy(level: SensorAccuracyLevel?) {
        val current = _readiness.value
        if (!current.isSupported || level == null) return
        val newStatus = when (level) {
            SensorAccuracyLevel.UNRELIABLE,
            SensorAccuracyLevel.NO_CONTACT -> DiagnosticStatus.ACCURACY_UNRELIABLE
            SensorAccuracyLevel.INITIALIZING -> DiagnosticStatus.INITIALIZING
            else -> DiagnosticStatus.AVAILABLE
        }
        if (current.accuracy != level || current.status != newStatus) {
            _readiness.value = current.copy(accuracy = level, status = newStatus)
            _headingState.value = _headingState.value.copy(accuracy = level, status = newStatus)
        }
    }

    companion object {
        private const val HEADING_UI_MIN_INTERVAL_MS = 33L
        private const val HEADING_SMOOTH_ALPHA = 0.22f
        private const val ACCEL_ALPHA = 0.20f
        private const val MAG_ALPHA = 0.25f
        private const val UPRIGHT_REMAP_THRESHOLD_DEG = 68f

        fun normalizeDegrees(deg: Float): Float {
            val mod = deg % 360f
            return if (mod < 0f) mod + 360f else mod
        }

        fun shortestSignedAngle(fromDeg: Float, toDeg: Float): Float {
            return ((toDeg - fromDeg + 540f) % 360f) - 180f
        }

        fun smoothCircularDegrees(previous: Float?, target: Float, alpha: Float): Float {
            if (previous == null) return normalizeDegrees(target)
            val delta = shortestSignedAngle(previous, target)
            return normalizeDegrees(previous + alpha * delta)
        }

        private fun lowPassInto(
            input: FloatArray,
            output: FloatArray,
            initialized: Boolean,
            alpha: Float
        ) {
            if (!initialized) {
                output[0] = input[0]
                output[1] = input[1]
                output[2] = input[2]
            } else {
                output[0] += alpha * (input[0] - output[0])
                output[1] += alpha * (input[1] - output[1])
                output[2] += alpha * (input[2] - output[2])
            }
        }
    }
}
