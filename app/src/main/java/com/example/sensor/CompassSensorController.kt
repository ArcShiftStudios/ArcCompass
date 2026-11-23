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
 * using Rotation Vector (preferred), Accelerometer + Magnetometer fallback, or
 * diagnostic-verified relative recovery fallbacks (Game Rotation Vector / Gyroscope)
 * without ever fabricating absolute North headings.
 *
 * Pre-allocates matrices to avoid per-sample allocations on entry-level hardware.
 */
class CompassSensorController(
    private val provider: SensorManagerProvider,
    private val accuracyMonitor: SensorAccuracyMonitor,
    private val onTelemetrySample: (RequiredSensorType, FloatArray) -> Unit,
    private val onHeadingComputed: (
        rawAzimuthDeg: Float,
        smoothedAzimuthDeg: Float,
        pitchDeg: Float,
        accuracy: SensorAccuracyLevel
    ) -> Unit = { _, _, _, _ -> }
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
    private var gameRotationVectorSensor: Sensor? = null

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
    private var gyroRelativeHeadingDeg: Float = 0f
    private var lastGyroTimestampNs: Long = 0L

    fun configureAndStart(
        resolvedSensors: Map<RequiredSensorType, Sensor>,
        hasLocationFix: Boolean,
        samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_UI,
        sourceOverride: CompassSource? = null,
        limitationNote: String? = null
    ) {
        val lastKnownDeg = smoothedAzimuthDeg ?: 0f
        stop()
        hasAccelSample = false
        hasMagSample = false
        smoothedAzimuthDeg = null
        lastGyroTimestampNs = 0L
        gyroRelativeHeadingDeg = lastKnownDeg

        val rotationVector = resolvedSensors[RequiredSensorType.ROTATION_VECTOR]
        val accelerometer = resolvedSensors[RequiredSensorType.ACCELEROMETER]
        val magnetometer = resolvedSensors[RequiredSensorType.MAGNETIC_FIELD]
        val gyroscope = resolvedSensors[RequiredSensorType.GYROSCOPE]
        gameRotationVectorSensor = provider.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        val desiredSource = sourceOverride ?: when {
            rotationVector != null -> CompassSource.ROTATION_VECTOR
            accelerometer != null && magnetometer != null -> CompassSource.ACCELEROMETER_AND_MAGNETOMETER
            else -> CompassSource.UNSUPPORTED
        }

        when (desiredSource) {
            CompassSource.ROTATION_VECTOR -> {
                if (rotationVector != null) {
                    activeSource = CompassSource.ROTATION_VECTOR
                    val ok = provider.registerListener(this, rotationVector, samplingPeriodUs)
                    val magOk = if (magnetometer != null) {
                        provider.registerListener(this, magnetometer, samplingPeriodUs)
                    } else {
                        false
                    }
                    isRegistered = ok || magOk
                    publishInitialState(ok, CompassSource.ROTATION_VECTOR, hasLocationFix, null)
                } else {
                    publishUnsupported(limitationNote)
                }
            }
            CompassSource.ACCELEROMETER_AND_MAGNETOMETER -> {
                if (accelerometer != null && magnetometer != null) {
                    activeSource = CompassSource.ACCELEROMETER_AND_MAGNETOMETER
                    val accelOk = provider.registerListener(this, accelerometer, samplingPeriodUs)
                    val magOk = provider.registerListener(this, magnetometer, samplingPeriodUs)
                    isRegistered = accelOk && magOk
                    if (!isRegistered) provider.unregisterListener(this)
                    publishInitialState(
                        isRegistered,
                        CompassSource.ACCELEROMETER_AND_MAGNETOMETER,
                        hasLocationFix,
                        limitationNote
                    )
                } else {
                    publishUnsupported(limitationNote)
                }
            }
            CompassSource.GAME_ROTATION_VECTOR_RELATIVE -> {
                val gameRv = gameRotationVectorSensor
                if (gameRv != null) {
                    activeSource = CompassSource.GAME_ROTATION_VECTOR_RELATIVE
                    val ok = provider.registerListener(this, gameRv, samplingPeriodUs)
                    isRegistered = ok
                    publishInitialState(
                        ok,
                        CompassSource.GAME_ROTATION_VECTOR_RELATIVE,
                        hasLocationFix,
                        limitationNote ?: "Relative heading only — no magnetic North reference."
                    )
                } else {
                    publishUnsupported(limitationNote)
                }
            }
            CompassSource.GYROSCOPE_RELATIVE -> {
                if (gyroscope != null) {
                    activeSource = CompassSource.GYROSCOPE_RELATIVE
                    val ok = provider.registerListener(this, gyroscope, samplingPeriodUs)
                    isRegistered = ok
                    publishInitialState(
                        ok,
                        CompassSource.GYROSCOPE_RELATIVE,
                        hasLocationFix,
                        limitationNote ?: "Gyroscope relative tracking — drifts over time, no absolute North."
                    )
                } else {
                    publishUnsupported(limitationNote)
                }
            }
            CompassSource.UNSUPPORTED -> {
                publishUnsupported(limitationNote)
            }
        }
    }

    private fun publishInitialState(
        ok: Boolean,
        source: CompassSource,
        hasLocationFix: Boolean,
        limitationNote: String?
    ) {
        val status = if (ok) DiagnosticStatus.INITIALIZING else DiagnosticStatus.UNAVAILABLE
        val resolved = if (ok) source else CompassSource.UNSUPPORTED
        val accuracy = if (ok) SensorAccuracyLevel.INITIALIZING else SensorAccuracyLevel.NOT_APPLICABLE
        _readiness.value = CompassReadinessState(
            isSupported = ok,
            source = resolved,
            status = status,
            accuracy = accuracy,
            trueNorthDeclinationAvailable = hasLocationFix && !resolved.isRelativeFallback
        )
        _headingState.value = CompassHeadingState(
            isSupported = ok,
            source = resolved,
            status = status,
            accuracy = accuracy,
            declinationDegrees = declinationDegrees,
            recoveryLimitationNote = limitationNote
        )
    }

    private fun publishUnsupported(limitationNote: String? = null) {
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
            declinationDegrees = declinationDegrees,
            recoveryLimitationNote = limitationNote
        )
    }

    fun updateDeclination(declinationDeg: Float?) {
        declinationDegrees = declinationDeg
        val currentReadiness = _readiness.value
        val hasDecl = declinationDeg != null && !activeSource.isRelativeFallback
        if (currentReadiness.trueNorthDeclinationAvailable != hasDecl) {
            _readiness.value = currentReadiness.copy(trueNorthDeclinationAvailable = hasDecl)
        }
        val currentHeading = _headingState.value
        val updatedTrue = if (!activeSource.isRelativeFallback) {
            currentHeading.magneticAzimuthDegrees?.let { mag ->
                declinationDeg?.let { decl -> normalizeDegrees(mag + decl) }
            }
        } else {
            null
        }
        _headingState.value = currentHeading.copy(
            declinationDegrees = declinationDeg,
            trueAzimuthDegrees = updatedTrue
        )
    }

    fun updateDeclinationAvailability(hasLocationFix: Boolean) {
        val current = _readiness.value
        val available = hasLocationFix && !activeSource.isRelativeFallback
        if (current.trueNorthDeclinationAvailable != available) {
            _readiness.value = current.copy(trueNorthDeclinationAvailable = available)
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

        if (sensorEvent.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            if (activeSource == CompassSource.GAME_ROTATION_VECTOR_RELATIVE &&
                CompassHealthMonitor.areValuesValid(RequiredSensorType.ROTATION_VECTOR, sensorEvent.values)
            ) {
                processRotationVector(sensorEvent.values)
            }
            return
        }

        val type = RequiredSensorType.fromAndroidType(sensorEvent.sensor.type) ?: return
        val valuesCopy = sensorEvent.values.clone()
        val isValid = CompassHealthMonitor.areValuesValid(type, valuesCopy)
        if (isValid) {
            accuracyMonitor.markActiveSampleReceived(type)
        }
        onTelemetrySample(type, valuesCopy)
        if (!isValid) return

        when (type) {
            RequiredSensorType.ROTATION_VECTOR -> {
                if (activeSource == CompassSource.ROTATION_VECTOR) {
                    processRotationVector(valuesCopy)
                }
            }
            RequiredSensorType.ACCELEROMETER -> {
                if (valuesCopy.size >= 3) {
                    lowPassInto(valuesCopy, accelBuffer, hasAccelSample, ACCEL_ALPHA)
                    hasAccelSample = true
                    if (activeSource == CompassSource.ACCELEROMETER_AND_MAGNETOMETER && hasMagSample) {
                        processAccelAndMag()
                    }
                }
            }
            RequiredSensorType.MAGNETIC_FIELD -> {
                if (valuesCopy.size >= 3) {
                    lowPassInto(valuesCopy, magBuffer, hasMagSample, MAG_ALPHA)
                    hasMagSample = true
                    if (activeSource == CompassSource.ACCELEROMETER_AND_MAGNETOMETER && hasAccelSample) {
                        processAccelAndMag()
                    }
                }
            }
            RequiredSensorType.GYROSCOPE -> {
                if (activeSource == CompassSource.GYROSCOPE_RELATIVE && valuesCopy.size >= 3) {
                    processGyroRelative(valuesCopy, sensorEvent.timestamp)
                }
            }
            else -> Unit
        }

        val relevantType = when (activeSource) {
            CompassSource.ROTATION_VECTOR -> RequiredSensorType.ROTATION_VECTOR
            CompassSource.ACCELEROMETER_AND_MAGNETOMETER -> RequiredSensorType.MAGNETIC_FIELD
            CompassSource.GYROSCOPE_RELATIVE -> RequiredSensorType.GYROSCOPE
            CompassSource.GAME_ROTATION_VECTOR_RELATIVE,
            CompassSource.UNSUPPORTED -> null
        }
        if (relevantType != null) {
            updateReadinessAccuracy(accuracyMonitor.accuracyBySensor.value[relevantType])
        }
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
        if (rawDeg.isFinite() && pitchDeg.isFinite() && rollDeg.isFinite()) {
            emitHeading(rawDeg, pitchDeg, rollDeg)
        }
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
        if (rawDeg.isFinite() && pitchDeg.isFinite() && rollDeg.isFinite()) {
            emitHeading(rawDeg, pitchDeg, rollDeg)
        }
    }

    private fun processGyroRelative(values: FloatArray, timestampNs: Long) {
        val prevNs = lastGyroTimestampNs
        lastGyroTimestampNs = timestampNs
        if (prevNs <= 0L || timestampNs <= prevNs) {
            emitHeading(gyroRelativeHeadingDeg, 0f, 0f)
            return
        }
        val dtSec = ((timestampNs - prevNs) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.25f)
        // Positive gyro Z is counter-clockwise looking down at screen; compass azimuth increases clockwise
        val deltaDeg = Math.toDegrees((-values[2] * dtSec).toDouble()).toFloat()
        if (deltaDeg.isFinite()) {
            gyroRelativeHeadingDeg = normalizeDegrees(gyroRelativeHeadingDeg + deltaDeg)
            emitHeading(gyroRelativeHeadingDeg, 0f, 0f)
        }
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
        val trueAzimuth = if (!activeSource.isRelativeFallback) {
            decl?.let { normalizeDegrees(smoothed + it) }
        } else {
            null
        }
        val currentReadiness = _readiness.value
        val effectiveStatus = if (currentReadiness.status == DiagnosticStatus.INITIALIZING) {
            DiagnosticStatus.AVAILABLE
        } else {
            currentReadiness.status
        }
        if (currentReadiness.status == DiagnosticStatus.INITIALIZING) {
            _readiness.value = currentReadiness.copy(status = DiagnosticStatus.AVAILABLE)
        }

        onHeadingComputed(rawDegrees, smoothed, pitchDeg, currentReadiness.accuracy)

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
