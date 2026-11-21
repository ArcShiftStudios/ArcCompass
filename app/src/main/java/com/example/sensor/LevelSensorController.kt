package com.example.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Dedicated controller for surface leveling sensors (Gravity -> Accelerometer -> Rotation Vector).
 * Strictly independent from the magnetometer so devices without a compass still support ArcLevel.
 * Supports relative reference zeroing (tap-to-reset orientation) and exposes accuracy state.
 */
class LevelSensorController(
    private val provider: SensorManagerProvider,
    private val accuracyMonitor: SensorAccuracyMonitor,
    private val onTelemetrySample: (RequiredSensorType, FloatArray) -> Unit
) : SensorEventListener {

    private val _readiness = MutableStateFlow(
        LevelReadinessState(
            isSupported = false,
            source = LevelSource.UNSUPPORTED,
            status = DiagnosticStatus.UNAVAILABLE
        )
    )
    val readiness: StateFlow<LevelReadinessState> = _readiness.asStateFlow()

    private val _levelState = MutableStateFlow(LevelTiltState())
    val levelState: StateFlow<LevelTiltState> = _levelState.asStateFlow()

    private var activeType: RequiredSensorType? = null
    private var activeSource: LevelSource = LevelSource.UNSUPPORTED
    private var currentAccuracy: SensorAccuracyLevel = SensorAccuracyLevel.INITIALIZING
    private var isRegistered = false

    private val gravityBuffer = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private var hasGravitySample = false
    private var lastLevelEmitMs = 0L

    private var latestRawRollDeg: Float? = null
    private var latestRawPitchDeg: Float? = null
    private var referenceRollOffsetDeg: Float = 0f
    private var referencePitchOffsetDeg: Float = 0f
    private var hasCustomReference: Boolean = false

    fun configureAndStart(
        resolvedSensors: Map<RequiredSensorType, Sensor>,
        samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_UI
    ) {
        stop()
        hasGravitySample = false

        val gravity = resolvedSensors[RequiredSensorType.GRAVITY]
        val accelerometer = resolvedSensors[RequiredSensorType.ACCELEROMETER]
        val rotationVector = resolvedSensors[RequiredSensorType.ROTATION_VECTOR]

        val (selectedType, selectedSensor, source) = when {
            gravity != null -> Triple(RequiredSensorType.GRAVITY, gravity, LevelSource.GRAVITY)
            accelerometer != null -> Triple(RequiredSensorType.ACCELEROMETER, accelerometer, LevelSource.ACCELEROMETER)
            rotationVector != null -> Triple(RequiredSensorType.ROTATION_VECTOR, rotationVector, LevelSource.ROTATION_VECTOR)
            else -> Triple(null, null, LevelSource.UNSUPPORTED)
        }

        if (selectedSensor == null || selectedType == null) {
            activeType = null
            activeSource = LevelSource.UNSUPPORTED
            currentAccuracy = SensorAccuracyLevel.NOT_APPLICABLE
            isRegistered = false
            _readiness.value = LevelReadinessState(
                isSupported = false,
                source = LevelSource.UNSUPPORTED,
                status = DiagnosticStatus.UNAVAILABLE
            )
            _levelState.value = LevelTiltState(
                isSupported = false,
                source = LevelSource.UNSUPPORTED,
                status = DiagnosticStatus.UNAVAILABLE,
                accuracy = SensorAccuracyLevel.NOT_APPLICABLE,
                hasCustomReference = hasCustomReference
            )
            return
        }

        val ok = provider.registerListener(this, selectedSensor, samplingPeriodUs)
        activeType = if (ok) selectedType else null
        activeSource = if (ok) source else LevelSource.UNSUPPORTED
        currentAccuracy = if (ok) {
            accuracyMonitor.accuracyBySensor.value[selectedType] ?: SensorAccuracyLevel.INITIALIZING
        } else {
            SensorAccuracyLevel.NOT_APPLICABLE
        }
        isRegistered = ok
        val status = if (ok) DiagnosticStatus.INITIALIZING else DiagnosticStatus.UNAVAILABLE
        _readiness.value = LevelReadinessState(
            isSupported = ok,
            source = activeSource,
            status = status
        )
        _levelState.value = LevelTiltState(
            isSupported = ok,
            source = activeSource,
            status = status,
            accuracy = currentAccuracy,
            hasCustomReference = hasCustomReference
        )
    }

    fun stop() {
        if (isRegistered) {
            provider.unregisterListener(this)
            isRegistered = false
            activeType = null
        }
    }

    /**
     * Captures the current physical orientation as 0.0° reference for relative leveling.
     */
    fun captureReferenceOffset() {
        val rawRoll = latestRawRollDeg ?: _levelState.value.rawRollDegrees ?: return
        val rawPitch = latestRawPitchDeg ?: _levelState.value.rawPitchDegrees ?: return
        referenceRollOffsetDeg = rawRoll
        referencePitchOffsetDeg = rawPitch
        hasCustomReference = true
        emitCurrentAngles(rawRoll, rawPitch, _levelState.value.verticalPlumbDegrees ?: 0f)
    }

    /**
     * Resets back to true gravity horizontal (0° / 0°).
     */
    fun clearReferenceOffset() {
        referenceRollOffsetDeg = 0f
        referencePitchOffsetDeg = 0f
        hasCustomReference = false
        val rawRoll = latestRawRollDeg ?: _levelState.value.rawRollDegrees
        val rawPitch = latestRawPitchDeg ?: _levelState.value.rawPitchDegrees
        if (rawRoll != null && rawPitch != null) {
            emitCurrentAngles(rawRoll, rawPitch, _levelState.value.verticalPlumbDegrees ?: 0f)
        } else {
            _levelState.value = _levelState.value.copy(hasCustomReference = false)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorEvent = event ?: return
        val type = RequiredSensorType.fromAndroidType(sensorEvent.sensor.type) ?: return
        accuracyMonitor.markActiveSampleReceived(type)
        onTelemetrySample(type, sensorEvent.values.clone())
        currentAccuracy = accuracyMonitor.accuracyBySensor.value[type] ?: currentAccuracy

        if (_readiness.value.status == DiagnosticStatus.INITIALIZING) {
            _readiness.value = _readiness.value.copy(status = DiagnosticStatus.AVAILABLE)
        }

        when (type) {
            RequiredSensorType.GRAVITY -> {
                if (sensorEvent.values.size >= 3) {
                    updateTiltFromGravity(
                        sensorEvent.values[0],
                        sensorEvent.values[1],
                        sensorEvent.values[2],
                        alpha = 0.35f
                    )
                }
            }
            RequiredSensorType.ACCELEROMETER -> {
                if (sensorEvent.values.size >= 3) {
                    updateTiltFromGravity(
                        sensorEvent.values[0],
                        sensorEvent.values[1],
                        sensorEvent.values[2],
                        alpha = 0.16f
                    )
                }
            }
            RequiredSensorType.ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, sensorEvent.values)
                updateTiltFromGravity(
                    rotationMatrix[6] * SensorManager.GRAVITY_EARTH,
                    rotationMatrix[7] * SensorManager.GRAVITY_EARTH,
                    rotationMatrix[8] * SensorManager.GRAVITY_EARTH,
                    alpha = 0.30f
                )
            }
            else -> Unit
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        accuracyMonitor.onAccuracyChanged(sensor, accuracy)
        val type = sensor?.let { RequiredSensorType.fromAndroidType(it.type) } ?: return
        if (type == activeType) {
            val mapped = SensorAccuracyMonitor.mapAndroidAccuracy(accuracy)
            currentAccuracy = mapped
            val status = when (mapped) {
                SensorAccuracyLevel.UNRELIABLE,
                SensorAccuracyLevel.NO_CONTACT -> DiagnosticStatus.ACCURACY_UNRELIABLE
                else -> DiagnosticStatus.AVAILABLE
            }
            _readiness.value = _readiness.value.copy(status = status)
            _levelState.value = _levelState.value.copy(status = status, accuracy = mapped)
        }
    }

    private fun updateTiltFromGravity(gx: Float, gy: Float, gz: Float, alpha: Float) {
        if (!hasGravitySample) {
            gravityBuffer[0] = gx
            gravityBuffer[1] = gy
            gravityBuffer[2] = gz
            hasGravitySample = true
        } else {
            gravityBuffer[0] += alpha * (gx - gravityBuffer[0])
            gravityBuffer[1] += alpha * (gy - gravityBuffer[1])
            gravityBuffer[2] += alpha * (gz - gravityBuffer[2])
        }

        val now = System.currentTimeMillis()
        if (now - lastLevelEmitMs < LEVEL_UI_MIN_INTERVAL_MS) return
        lastLevelEmitMs = now

        val fx = gravityBuffer[0]
        val fy = gravityBuffer[1]
        val fz = gravityBuffer[2]
        val norm = sqrt(fx * fx + fy * fy + fz * fz)
        if (norm < 1.0f) return

        val rawRollDeg = Math.toDegrees(atan2(-fx.toDouble(), hypot(fy.toDouble(), fz.toDouble()))).toFloat()
        val rawPitchDeg = Math.toDegrees(atan2(fy.toDouble(), hypot(fx.toDouble(), fz.toDouble()))).toFloat()
        val rawTotalDeg = Math.toDegrees(acos((abs(fz) / norm).coerceIn(0f, 1f).toDouble())).toFloat()
        val verticalPlumbDeg = Math.toDegrees(atan2(fx.toDouble(), fy.toDouble())).toFloat()

        latestRawRollDeg = rawRollDeg
        latestRawPitchDeg = rawPitchDeg

        emitCurrentAngles(rawRollDeg, rawPitchDeg, verticalPlumbDeg, rawTotalDeg)
    }

    private fun emitCurrentAngles(
        rawRollDeg: Float,
        rawPitchDeg: Float,
        verticalPlumbDeg: Float,
        rawTotalDeg: Float? = null
    ) {
        val adjustedRoll = rawRollDeg - referenceRollOffsetDeg
        val adjustedPitch = rawPitchDeg - referencePitchOffsetDeg
        val totalTiltDeg = if (!hasCustomReference && rawTotalDeg != null) {
            rawTotalDeg
        } else {
            hypot(adjustedRoll.toDouble(), adjustedPitch.toDouble()).toFloat()
        }

        val status = if (_readiness.value.status == DiagnosticStatus.INITIALIZING) {
            DiagnosticStatus.AVAILABLE
        } else {
            _readiness.value.status
        }

        _levelState.value = LevelTiltState(
            isSupported = true,
            source = activeSource,
            status = status,
            accuracy = currentAccuracy,
            rollDegrees = adjustedRoll,
            pitchDegrees = adjustedPitch,
            rawRollDegrees = rawRollDeg,
            rawPitchDegrees = rawPitchDeg,
            totalTiltDegrees = totalTiltDeg,
            verticalPlumbDegrees = verticalPlumbDeg,
            isFlatLevel = abs(adjustedRoll) <= LEVEL_TOLERANCE_DEG && abs(adjustedPitch) <= LEVEL_TOLERANCE_DEG,
            isVerticalLevel = abs(verticalPlumbDeg) <= LEVEL_TOLERANCE_DEG,
            hasCustomReference = hasCustomReference
        )
    }

    companion object {
        private const val LEVEL_UI_MIN_INTERVAL_MS = 33L
        private const val LEVEL_TOLERANCE_DEG = 0.6f
    }
}
