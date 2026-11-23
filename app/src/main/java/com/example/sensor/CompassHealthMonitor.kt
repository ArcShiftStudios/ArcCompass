package com.example.sensor

import com.example.data.CompassSensorMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Monitors live compass-related sensor streams to distinguish stationary resting states from
 * frozen, unresponsive, or invalid sensors, detects magnetic interference and vertical
 * orientation instability with hysteresis/debounce, manages deduplicated informational banners,
 * and runs user-confirmed interactive checks without ever fabricating headings.
 */
class CompassHealthMonitor(
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {

    private data class StreamTracker(
        var lastValues: FloatArray? = null,
        var sampleCount: Int = 0,
        var identicalStreak: Int = 0,
        var varyingStreak: Int = 0,
        var streakStartMs: Long = 0L,
        var lastSampleMs: Long = 0L,
        var invalidCount: Int = 0,
        var testBaseline: FloatArray? = null,
        var testSamples: Int = 0,
        var testMaxDelta: Float = 0f,
        var testInvalidCount: Int = 0
    )

    private val trackers = mutableMapOf<RequiredSensorType, StreamTracker>()
    private var observingStartedMs: Long = 0L
    private var isObserving: Boolean = false
    private var lastMotionObservedMs: Long = 0L
    private var motionSampleStreak: Int = 0
    private var dismissedAtMs: Long = 0L
    private var testStartedMs: Long = 0L

    private var activeAvailableSensors: Set<RequiredSensorType> = emptySet()
    private var activeCompassSource: CompassSource = CompassSource.ROTATION_VECTOR
    private var activePrimaryCompassSensor: RequiredSensorType? = null
    private var hasGameRotationVector: Boolean = false

    // Heading & pitch monitoring for frozen recovery + vertical orientation warning
    private var lastRawAzimuthDeg: Float? = null
    private var lastHeadingChangeMs: Long = 0L
    private var healthyHeadingStreak: Int = 0
    private var rawJitterStreak: Int = 0
    private var lastPitchDegrees: Float? = null
    private var isCurrentlyUpright: Boolean = false
    private var uprightUnreliableSinceMs: Long = 0L
    private var orientationRecoveredSinceMs: Long = 0L
    private var lastReportedAccuracy: SensorAccuracyLevel = SensorAccuracyLevel.INITIALIZING

    // Magnetometer interference hysteresis window
    private val recentMagMicroTesla = ArrayDeque<Float>(MAG_WINDOW_SIZE + 1)
    private var magInterferenceSinceMs: Long = 0L
    private var magInterferenceSamples: Int = 0
    private var magStableSinceMs: Long = 0L
    private var magStableSamples: Int = 0

    // Info banner deduplication
    private var lastShownInfoEventKey: String? = null

    private val _state = MutableStateFlow(CompassDiagnosticsState())
    val state: StateFlow<CompassDiagnosticsState> = _state.asStateFlow()

    fun onObservingStarted(
        availableSensors: Set<RequiredSensorType>,
        primaryCompassSensor: RequiredSensorType?,
        gameRotationVectorAvailable: Boolean,
        activeSource: CompassSource = CompassSource.ROTATION_VECTOR,
        sensorMode: CompassSensorMode = CompassSensorMode.AUTO_DEFAULT
    ) {
        val now = nowMillis()
        isObserving = true
        observingStartedMs = now
        activeAvailableSensors = availableSensors
        activeCompassSource = activeSource
        activePrimaryCompassSensor = primaryCompassSensor ?: primarySensorForSource(activeSource, availableSensors)
        hasGameRotationVector = gameRotationVectorAvailable
        resetTransientTrackers(now, clearConditionStates = true)
        _state.update {
            it.copy(
                activeSensorMode = sensorMode,
                suspectedAnomaly = null,
                bannerDismissed = false
            )
        }
    }

    fun onObservingStopped() {
        isObserving = false
        if (_state.value.checkPhase == InteractiveCheckPhase.RUNNING) {
            _state.update { it.copy(checkPhase = InteractiveCheckPhase.IDLE, checkProgressFraction = 0f) }
        }
    }

    fun onCompassSourceChanged(
        newSource: CompassSource,
        sensorMode: CompassSensorMode,
        availableSensors: Set<RequiredSensorType> = activeAvailableSensors
    ) {
        val now = nowMillis()
        activeAvailableSensors = availableSensors
        activeCompassSource = newSource
        activePrimaryCompassSensor = primarySensorForSource(newSource, availableSensors)
        resetTransientTrackers(now, clearConditionStates = false)
        // Re-evaluate health cleanly in the new mode; never carry stale frozen/timeout warnings over
        _state.update { current ->
            val preservedRecovery = newSource.takeIf {
                sensorMode == CompassSensorMode.FUSION_ACCEL_MAG ||
                    (it != CompassSource.ROTATION_VECTOR && it != CompassSource.UNSUPPORTED)
            }
            current.copy(
                suspectedAnomaly = null,
                bannerDismissed = false,
                activeRecoverySource = preservedRecovery,
                activeSensorMode = sensorMode,
                verticalOrientationWarningActive = false,
                verticalOrientationDismissed = false
            )
        }
    }

    fun onSample(type: RequiredSensorType, values: FloatArray) {
        if (!isObserving) return
        val now = nowMillis()
        val tracker = trackers.getOrPut(type) { StreamTracker() }
        tracker.sampleCount += 1
        tracker.lastSampleMs = now

        val valid = areValuesValid(type, values)
        if (!valid) {
            tracker.invalidCount += 1
            tracker.varyingStreak = 0
        }

        val prev = tracker.lastValues
        val delta = if (prev != null) maxAxisDelta(prev, values) else 0f

        if (prev != null && delta == 0f) {
            tracker.identicalStreak += 1
            tracker.varyingStreak = 0
            if (tracker.streakStartMs == 0L) tracker.streakStartMs = now
        } else {
            tracker.identicalStreak = 0
            if (valid) tracker.varyingStreak += 1
            tracker.streakStartMs = now
        }
        tracker.lastValues = values.copyOf()

        // Track genuine sustained physical device motion separately from magnetic/fused orientation
        var sawMotionSample = false
        if (type == RequiredSensorType.ACCELEROMETER && values.size >= 3) {
            if (delta > ACCEL_MOTION_DELTA_THRESHOLD) {
                sawMotionSample = true
            }
            // Fallback pitch estimate from accelerometer when rotation vector is absent or frozen
            val horiz = sqrt(values[0] * values[0] + values[1] * values[1])
            val tiltDeg = Math.toDegrees(atan2(horiz.toDouble(), abs(values[2]).toDouble())).toFloat()
            if (tiltDeg.isFinite()) {
                lastPitchDegrees = tiltDeg
                if (activeCompassSource == CompassSource.ACCELEROMETER_AND_MAGNETOMETER ||
                    activeCompassSource == CompassSource.GYROSCOPE_RELATIVE
                ) {
                    evaluateOrientationCondition(now, tiltDeg, lastReportedAccuracy, rawDeltaDeg = 0f)
                }
            }
        } else if (type == RequiredSensorType.GYROSCOPE && values.size >= 3) {
            val gyroMag = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
            if (gyroMag > GYRO_MOTION_THRESHOLD_RAD_S && valid) {
                sawMotionSample = true
            }
        }
        if (sawMotionSample) {
            motionSampleStreak += 1
            if (motionSampleStreak >= MOTION_STREAK_CONFIRM) {
                lastMotionObservedMs = now
            }
        } else if (type == RequiredSensorType.ACCELEROMETER || type == RequiredSensorType.GYROSCOPE) {
            motionSampleStreak = (motionSampleStreak - 1).coerceAtLeast(0)
        }

        // Magnetic interference monitoring on hardware-present magnetometer streams only
        if (type == RequiredSensorType.MAGNETIC_FIELD &&
            activeAvailableSensors.contains(RequiredSensorType.MAGNETIC_FIELD) &&
            valid &&
            values.size >= 3
        ) {
            val magnitudeUt = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
            evaluateMagneticInterference(now, magnitudeUt)
        }

        // Update active interactive test window if running
        if (_state.value.checkPhase == InteractiveCheckPhase.RUNNING) {
            tracker.testSamples += 1
            if (!valid) tracker.testInvalidCount += 1
            val baseline = tracker.testBaseline
            if (baseline == null) {
                tracker.testBaseline = values.copyOf()
            } else {
                val testDelta = maxAxisDelta(baseline, values)
                if (testDelta > tracker.testMaxDelta) {
                    tracker.testMaxDelta = testDelta
                }
            }
            val elapsed = (now - testStartedMs).coerceAtLeast(0L)
            val progress = (elapsed.toFloat() / INTERACTIVE_TEST_DURATION_MS).coerceIn(0f, 1f)
            val movedDuringTest = lastMotionObservedMs >= testStartedMs
            _state.update {
                it.copy(
                    checkProgressFraction = progress,
                    motionDetectedDuringCheck = movedDuringTest
                )
            }
            if (elapsed >= INTERACTIVE_TEST_DURATION_MS) {
                finalizeInteractiveCheck()
            }
        } else {
            evaluatePassiveAnomaly(now)
        }
    }

    fun onHeadingComputed(
        rawAzimuthDeg: Float,
        smoothedAzimuthDeg: Float,
        pitchDeg: Float,
        accuracy: SensorAccuracyLevel
    ) {
        if (!isObserving) return
        val now = nowMillis()
        lastReportedAccuracy = accuracy
        lastPitchDegrees = abs(pitchDeg)

        val prevRaw = lastRawAzimuthDeg
        val rawDelta = if (prevRaw != null) {
            abs(shortestAngleDiff(prevRaw, rawAzimuthDeg))
        } else {
            0f
        }
        lastRawAzimuthDeg = rawAzimuthDeg

        val accuracyHealthy = accuracy == SensorAccuracyLevel.HIGH || accuracy == SensorAccuracyLevel.MEDIUM
        if (rawDelta >= 0.4f && rawDelta <= 45f) {
            lastHeadingChangeMs = now
            if (accuracyHealthy) {
                healthyHeadingStreak += 1
            } else {
                healthyHeadingStreak = 0
            }
        } else if (accuracyHealthy && lastPitchDegrees != null && trackers[activePrimaryCompassSensor]?.varyingStreak ?: 0 >= 3) {
            healthyHeadingStreak += 1
        }

        // Auto-clear frozen/timeout anomaly once compass readings + headings are operating normally
        val currentAnomaly = _state.value.suspectedAnomaly
        if ((currentAnomaly == CompassAnomalyReason.FROZEN_DURING_MOTION ||
                currentAnomaly == CompassAnomalyReason.SENSOR_TIMEOUT) &&
            healthyHeadingStreak >= HEALTHY_HEADING_RECOVERY_STREAK
        ) {
            _state.update {
                it.copy(
                    suspectedAnomaly = null,
                    bannerDismissed = false
                )
            }
        }

        evaluateOrientationCondition(now, abs(pitchDeg), accuracy, rawDelta)
    }

    fun evaluateTimeoutIfNeeded() {
        if (!isObserving) return
        val now = nowMillis()
        if (_state.value.checkPhase == InteractiveCheckPhase.RUNNING) {
            val elapsed = (now - testStartedMs).coerceAtLeast(0L)
            val progress = (elapsed.toFloat() / INTERACTIVE_TEST_DURATION_MS).coerceIn(0f, 1f)
            _state.update { it.copy(checkProgressFraction = progress) }
            if (elapsed >= INTERACTIVE_TEST_DURATION_MS) {
                finalizeInteractiveCheck()
            }
        } else {
            evaluatePassiveAnomaly(now)
        }
    }

    fun requestCheckConfirmation() {
        _state.update {
            it.copy(checkPhase = InteractiveCheckPhase.AWAITING_CONFIRMATION)
        }
    }

    fun cancelCheckConfirmation() {
        _state.update {
            it.copy(checkPhase = InteractiveCheckPhase.IDLE)
        }
    }

    fun startInteractiveCheck() {
        val now = nowMillis()
        testStartedMs = now
        trackers.values.forEach { t ->
            t.testBaseline = t.lastValues?.copyOf()
            t.testSamples = 0
            t.testMaxDelta = 0f
            t.testInvalidCount = 0
        }
        _state.update {
            it.copy(
                checkPhase = InteractiveCheckPhase.RUNNING,
                checkProgressFraction = 0f,
                motionDetectedDuringCheck = false,
                sensorOutcomes = emptyList(),
                diagnosticSummary = null
            )
        }
    }

    fun finalizeInteractiveCheck(): CompassSource {
        val now = nowMillis()
        val motionObserved = lastMotionObservedMs >= testStartedMs
        val monitoredTypes = listOf(
            RequiredSensorType.ROTATION_VECTOR,
            RequiredSensorType.MAGNETIC_FIELD,
            RequiredSensorType.ACCELEROMETER,
            RequiredSensorType.GYROSCOPE
        )

        val outcomes = monitoredTypes.map { type ->
            if (!activeAvailableSensors.contains(type)) {
                SensorDiagnosticOutcome(type, SensorStreamHealth.UNAVAILABLE, 0, 0f)
            } else {
                val t = trackers[type]
                val samples = t?.testSamples ?: 0
                val invalid = t?.testInvalidCount ?: 0
                val maxDelta = t?.testMaxDelta ?: 0f
                val health = when {
                    invalid > 0 -> SensorStreamHealth.INVALID_VALUES
                    samples == 0 -> SensorStreamHealth.UNRESPONSIVE
                    maxDelta >= varyingThreshold(type) -> SensorStreamHealth.HEALTHY
                    motionObserved -> SensorStreamHealth.FROZEN
                    else -> SensorStreamHealth.STATIONARY
                }
                SensorDiagnosticOutcome(type, health, samples, maxDelta)
            }
        }

        val byType = outcomes.associateBy { it.sensorType }
        val rvHealth = byType[RequiredSensorType.ROTATION_VECTOR]?.health
        val magHealth = byType[RequiredSensorType.MAGNETIC_FIELD]?.health
        val accelHealth = byType[RequiredSensorType.ACCELEROMETER]?.health
        val gyroHealth = byType[RequiredSensorType.GYROSCOPE]?.health

        val rvUsable = rvHealth == SensorStreamHealth.HEALTHY ||
            (!motionObserved && rvHealth == SensorStreamHealth.STATIONARY)
        val magAccelUsable = (magHealth == SensorStreamHealth.HEALTHY || (!motionObserved && magHealth == SensorStreamHealth.STATIONARY)) &&
            (accelHealth == SensorStreamHealth.HEALTHY || (!motionObserved && accelHealth == SensorStreamHealth.STATIONARY))

        val recommendedSource: CompassSource
        val disclosure: String?
        val summary: String

        when {
            rvUsable && activeAvailableSensors.contains(RequiredSensorType.ROTATION_VECTOR) -> {
                recommendedSource = CompassSource.ROTATION_VECTOR
                disclosure = null
                summary = if (motionObserved) {
                    "Rotation Vector and motion sensors responded normally during movement."
                } else {
                    "No device movement detected during the check; sensors appear steady. Move in a figure-8 if heading feels off."
                }
            }
            magAccelUsable -> {
                recommendedSource = CompassSource.ACCELEROMETER_AND_MAGNETOMETER
                disclosure = "Switched to Accelerometer + Magnetometer sensor fusion because Rotation Vector did not respond properly."
                summary = "Recovered absolute heading using Accelerometer + Magnetometer sensor fusion."
            }
            hasGameRotationVector -> {
                recommendedSource = CompassSource.GAME_ROTATION_VECTOR_RELATIVE
                disclosure = "Magnetometer/Rotation Vector unusable. Using Game Rotation Vector for relative turns only — no absolute North lock."
                summary = "Magnetometer issue detected; switched to relative Game Rotation Vector (no absolute North)."
            }
            gyroHealth == SensorStreamHealth.HEALTHY || gyroHealth == SensorStreamHealth.STATIONARY -> {
                recommendedSource = CompassSource.GYROSCOPE_RELATIVE
                disclosure = "Magnetic heading unavailable/faulty. Gyroscope-assisted relative tracking active — drifts over time."
                summary = "Switched to gyroscope-assisted relative tracking (subject to drift)."
            }
            else -> {
                recommendedSource = CompassSource.UNSUPPORTED
                disclosure = "Compass sensors failed validation or are missing. Headings are paused rather than fabricated."
                summary = "No healthy compass or fallback sensor available on this device."
            }
        }

        dismissedAtMs = now
        val clearAnomaly = recommendedSource != CompassSource.UNSUPPORTED
        if (clearAnomaly) {
            activeCompassSource = recommendedSource
            activePrimaryCompassSensor = primarySensorForSource(recommendedSource, activeAvailableSensors)
        }
        val persistedMode = modeForSource(recommendedSource)

        _state.update {
            it.copy(
                suspectedAnomaly = if (clearAnomaly) null else it.suspectedAnomaly,
                bannerDismissed = if (clearAnomaly) false else it.bannerDismissed,
                checkPhase = InteractiveCheckPhase.COMPLETED,
                checkProgressFraction = 1f,
                motionDetectedDuringCheck = motionObserved,
                sensorOutcomes = outcomes,
                diagnosticSummary = summary,
                activeRecoverySource = recommendedSource.takeIf { s -> s != CompassSource.ROTATION_VECTOR },
                recoveryDisclosure = disclosure,
                activeSensorMode = persistedMode
            )
        }
        return recommendedSource
    }

    fun showInfoBanner(
        eventKey: String,
        title: String,
        message: String,
        force: Boolean = false
    ): Boolean {
        if (!force && lastShownInfoEventKey == eventKey) {
            return false
        }
        lastShownInfoEventKey = eventKey
        _state.update {
            it.copy(
                infoBanner = InfoBannerNotice(
                    eventKey = eventKey,
                    title = title,
                    message = message,
                    createdAtMillis = nowMillis()
                )
            )
        }
        return true
    }

    fun dismissInfoBanner(eventKey: String? = null) {
        _state.update { current ->
            val currentBanner = current.infoBanner ?: return@update current
            if (eventKey == null || currentBanner.eventKey == eventKey) {
                current.copy(infoBanner = null)
            } else {
                current
            }
        }
    }

    fun dismissBanner() {
        dismissedAtMs = nowMillis()
        _state.update { it.copy(bannerDismissed = true) }
    }

    fun dismissMagneticInterferenceBanner() {
        _state.update { it.copy(magneticInterferenceDismissed = true) }
    }

    fun dismissVerticalOrientationBanner() {
        _state.update { it.copy(verticalOrientationDismissed = true) }
    }

    fun clearRecoveryState() {
        val now = nowMillis()
        dismissedAtMs = now
        lastShownInfoEventKey = null
        resetTransientTrackers(now, clearConditionStates = true)
        _state.value = CompassDiagnosticsState(activeSensorMode = CompassSensorMode.AUTO_DEFAULT)
    }

    private fun resetTransientTrackers(now: Long, clearConditionStates: Boolean) {
        trackers.clear()
        activeAvailableSensors.forEach { trackers[it] = StreamTracker(streakStartMs = now) }
        observingStartedMs = now
        lastMotionObservedMs = 0L
        motionSampleStreak = 0
        lastRawAzimuthDeg = null
        lastHeadingChangeMs = 0L
        healthyHeadingStreak = 0
        rawJitterStreak = 0
        isCurrentlyUpright = false
        uprightUnreliableSinceMs = 0L
        orientationRecoveredSinceMs = 0L
        if (clearConditionStates) {
            recentMagMicroTesla.clear()
            magInterferenceSinceMs = 0L
            magInterferenceSamples = 0
            magStableSinceMs = 0L
            magStableSamples = 0
            lastPitchDegrees = null
        }
    }

    private fun evaluateMagneticInterference(now: Long, magnitudeUt: Float) {
        if (recentMagMicroTesla.size >= MAG_WINDOW_SIZE) {
            recentMagMicroTesla.removeFirst()
        }
        val prevUt = recentMagMicroTesla.lastOrNull()
        recentMagMicroTesla.addLast(magnitudeUt)

        val windowMin = recentMagMicroTesla.minOrNull() ?: magnitudeUt
        val windowMax = recentMagMicroTesla.maxOrNull() ?: magnitudeUt
        val peakToPeak = windowMax - windowMin
        val stepDelta = if (prevUt != null) abs(magnitudeUt - prevUt) else 0f

        val isHighOrLowField = magnitudeUt > MAG_HIGH_THRESHOLD_UT ||
            (magnitudeUt in MAG_MIN_VALID_UT..MAG_LOW_SHIELD_THRESHOLD_UT)
        val isUnstableSwing = recentMagMicroTesla.size >= 3 &&
            (peakToPeak > MAG_UNSTABLE_PEAK_TO_PEAK_UT || stepDelta > MAG_STEP_SPIKE_UT)
        val isInAcceptableStableRange = magnitudeUt in MAG_SAFE_LOW_UT..MAG_SAFE_HIGH_UT &&
            peakToPeak <= MAG_SAFE_PEAK_TO_PEAK_UT &&
            stepDelta <= MAG_SAFE_STEP_UT

        val currentlyActive = _state.value.magneticInterferenceActive

        if (isHighOrLowField || isUnstableSwing) {
            magStableSinceMs = 0L
            magStableSamples = 0
            if (magInterferenceSinceMs == 0L) magInterferenceSinceMs = now
            magInterferenceSamples += 1
            if (!currentlyActive &&
                magInterferenceSamples >= MAG_ENTER_MIN_SAMPLES &&
                (now - magInterferenceSinceMs) >= MAG_ENTER_DEBOUNCE_MS
            ) {
                _state.update {
                    it.copy(
                        magneticInterferenceActive = true,
                        magneticInterferenceMicroTesla = magnitudeUt
                    )
                }
            } else if (currentlyActive) {
                _state.update { it.copy(magneticInterferenceMicroTesla = magnitudeUt) }
            }
        } else if (isInAcceptableStableRange) {
            magInterferenceSinceMs = 0L
            magInterferenceSamples = 0
            if (currentlyActive) {
                if (magStableSinceMs == 0L) magStableSinceMs = now
                magStableSamples += 1
                if (magStableSamples >= MAG_EXIT_MIN_SAMPLES &&
                    (now - magStableSinceMs) >= MAG_EXIT_DEBOUNCE_MS
                ) {
                    _state.update {
                        it.copy(
                            magneticInterferenceActive = false,
                            magneticInterferenceDismissed = false,
                            magneticInterferenceMicroTesla = null
                        )
                    }
                }
            }
        }
    }

    private fun evaluateOrientationCondition(
        now: Long,
        absPitchDeg: Float,
        accuracy: SensorAccuracyLevel,
        rawDeltaDeg: Float
    ) {
        if (!activePrimarySensorsAvailable()) {
            if (_state.value.verticalOrientationWarningActive) {
                _state.update {
                    it.copy(
                        verticalOrientationWarningActive = false,
                        verticalOrientationDismissed = false
                    )
                }
            }
            return
        }

        // Upright hysteresis: enter >= 60°, stay upright until <= 48°
        val uprightNow = if (isCurrentlyUpright) {
            absPitchDeg >= VERTICAL_EXIT_PITCH_DEG
        } else {
            absPitchDeg >= VERTICAL_ENTER_PITCH_DEG
        }
        isCurrentlyUpright = uprightNow

        if (rawDeltaDeg >= RAW_JITTER_SPIKE_DEG) {
            rawJitterStreak += 1
        } else if (rawDeltaDeg > 0f && rawDeltaDeg < 10f) {
            rawJitterStreak = 0
        }

        val readingsStruggling = accuracy == SensorAccuracyLevel.LOW ||
            accuracy == SensorAccuracyLevel.UNRELIABLE ||
            accuracy == SensorAccuracyLevel.NO_CONTACT ||
            rawJitterStreak >= 2 ||
            _state.value.suspectedAnomaly != null

        val currentlyActive = _state.value.verticalOrientationWarningActive

        if (uprightNow && readingsStruggling) {
            orientationRecoveredSinceMs = 0L
            if (uprightUnreliableSinceMs == 0L) uprightUnreliableSinceMs = now
            if (!currentlyActive &&
                _state.value.checkPhase != InteractiveCheckPhase.RUNNING &&
                (now - uprightUnreliableSinceMs) >= ORIENTATION_ENTER_DEBOUNCE_MS
            ) {
                _state.update {
                    it.copy(verticalOrientationWarningActive = true)
                }
            }
        } else {
            uprightUnreliableSinceMs = 0L
            if (currentlyActive) {
                if (orientationRecoveredSinceMs == 0L) orientationRecoveredSinceMs = now
                val fastClear = !uprightNow &&
                    (accuracy == SensorAccuracyLevel.HIGH || accuracy == SensorAccuracyLevel.MEDIUM)
                if (fastClear || (now - orientationRecoveredSinceMs) >= ORIENTATION_EXIT_DEBOUNCE_MS) {
                    _state.update {
                        it.copy(
                            verticalOrientationWarningActive = false,
                            verticalOrientationDismissed = false
                        )
                    }
                }
            }
        }
    }

    private fun activePrimarySensorsAvailable(): Boolean =
        activeCompassSource != CompassSource.UNSUPPORTED && activeAvailableSensors.isNotEmpty()

    private fun evaluatePassiveAnomaly(now: Long) {
        val current = _state.value
        val inCooldown = dismissedAtMs > 0L && (now - dismissedAtMs) < BANNER_COOLDOWN_MS

        val primaryType = activePrimaryCompassSensor ?: return
        val primaryTracker = trackers[primaryType]
        val magTracker = if (activeAvailableSensors.contains(RequiredSensorType.MAGNETIC_FIELD)) {
            trackers[RequiredSensorType.MAGNETIC_FIELD]
        } else {
            null
        }

        val invalidDetected = (primaryTracker?.invalidCount ?: 0) >= 2 ||
            (activeCompassSource != CompassSource.GAME_ROTATION_VECTOR_RELATIVE &&
                activeCompassSource != CompassSource.GYROSCOPE_RELATIVE &&
                (magTracker?.invalidCount ?: 0) >= 2)

        val movingRecently = lastMotionObservedMs > 0L && (now - lastMotionObservedMs) <= MOTION_RECENT_WINDOW_MS
        val headingUpdatedRecently = lastHeadingChangeMs > 0L && (now - lastHeadingChangeMs) <= HEADING_RECENT_WINDOW_MS

        val frozenPrimary = movingRecently &&
            !headingUpdatedRecently &&
            primaryTracker != null &&
            primaryTracker.identicalStreak >= FROZEN_STREAK_MIN &&
            (now - primaryTracker.streakStartMs) >= FROZEN_DURATION_MS

        val usesMagnetometer = activeCompassSource == CompassSource.ROTATION_VECTOR ||
            activeCompassSource == CompassSource.ACCELEROMETER_AND_MAGNETOMETER
        val frozenMag = movingRecently &&
            !headingUpdatedRecently &&
            usesMagnetometer &&
            magTracker != null &&
            magTracker.identicalStreak >= FROZEN_STREAK_MIN &&
            (now - magTracker.streakStartMs) >= FROZEN_DURATION_MS

        val timedOut = observingStartedMs > 0L &&
            (now - observingStartedMs) >= SENSOR_TIMEOUT_MS &&
            (primaryTracker?.sampleCount ?: 0) == 0

        val detected: CompassAnomalyReason? = when {
            invalidDetected -> CompassAnomalyReason.INVALID_SENSOR_DATA
            frozenPrimary || frozenMag -> CompassAnomalyReason.FROZEN_DURING_MOTION
            timedOut -> CompassAnomalyReason.SENSOR_TIMEOUT
            else -> null
        }

        if (detected != null) {
            if (!inCooldown && current.suspectedAnomaly != detected) {
                _state.update {
                    it.copy(
                        suspectedAnomaly = detected,
                        bannerDismissed = false
                    )
                }
            }
        } else if (current.suspectedAnomaly != null) {
            // Condition resolved naturally: if sensor is actively delivering valid varying samples, clear warning
            val primaryHealthyNow = primaryTracker != null &&
                primaryTracker.sampleCount > 0 &&
                (primaryTracker.varyingStreak >= 3 || healthyHeadingStreak >= HEALTHY_HEADING_RECOVERY_STREAK)
            if (primaryHealthyNow) {
                _state.update {
                    it.copy(
                        suspectedAnomaly = null,
                        bannerDismissed = false
                    )
                }
            }
        }
    }

    companion object {
        const val INTERACTIVE_TEST_DURATION_MS = 4_000L
        const val INFO_BANNER_DURATION_MS = 5_000L
        private const val BANNER_COOLDOWN_MS = 60_000L
        private const val SENSOR_TIMEOUT_MS = 4_000L
        private const val FROZEN_DURATION_MS = 2_500L
        private const val FROZEN_STREAK_MIN = 8
        private const val MOTION_RECENT_WINDOW_MS = 2_500L
        private const val HEADING_RECENT_WINDOW_MS = 2_000L
        private const val MOTION_STREAK_CONFIRM = 2
        private const val HEALTHY_HEADING_RECOVERY_STREAK = 3
        private const val ACCEL_MOTION_DELTA_THRESHOLD = 0.45f
        private const val GYRO_MOTION_THRESHOLD_RAD_S = 0.18f

        // Vertical orientation warning thresholds & debounce
        private const val VERTICAL_ENTER_PITCH_DEG = 60f
        private const val VERTICAL_EXIT_PITCH_DEG = 48f
        private const val RAW_JITTER_SPIKE_DEG = 18f
        private const val ORIENTATION_ENTER_DEBOUNCE_MS = 1_200L
        private const val ORIENTATION_EXIT_DEBOUNCE_MS = 800L

        // Magnetic interference thresholds & hysteresis
        private const val MAG_WINDOW_SIZE = 6
        private const val MAG_MIN_VALID_UT = 0.5f
        private const val MAG_LOW_SHIELD_THRESHOLD_UT = 15f
        private const val MAG_HIGH_THRESHOLD_UT = 80f
        private const val MAG_UNSTABLE_PEAK_TO_PEAK_UT = 18f
        private const val MAG_STEP_SPIKE_UT = 12f
        private const val MAG_SAFE_LOW_UT = 20f
        private const val MAG_SAFE_HIGH_UT = 68f
        private const val MAG_SAFE_PEAK_TO_PEAK_UT = 8f
        private const val MAG_SAFE_STEP_UT = 4f
        private const val MAG_ENTER_MIN_SAMPLES = 3
        private const val MAG_ENTER_DEBOUNCE_MS = 1_000L
        private const val MAG_EXIT_MIN_SAMPLES = 4
        private const val MAG_EXIT_DEBOUNCE_MS = 1_500L

        fun primarySensorForSource(
            source: CompassSource,
            availableSensors: Set<RequiredSensorType>
        ): RequiredSensorType? = when (source) {
            CompassSource.ROTATION_VECTOR ->
                RequiredSensorType.ROTATION_VECTOR.takeIf { availableSensors.contains(it) }
            CompassSource.ACCELEROMETER_AND_MAGNETOMETER ->
                RequiredSensorType.MAGNETIC_FIELD.takeIf { availableSensors.contains(it) }
                    ?: RequiredSensorType.ACCELEROMETER.takeIf { availableSensors.contains(it) }
            CompassSource.GYROSCOPE_RELATIVE ->
                RequiredSensorType.GYROSCOPE.takeIf { availableSensors.contains(it) }
            CompassSource.GAME_ROTATION_VECTOR_RELATIVE,
            CompassSource.UNSUPPORTED -> null
        }

        fun modeForSource(source: CompassSource): CompassSensorMode = when (source) {
            CompassSource.ROTATION_VECTOR -> CompassSensorMode.AUTO_DEFAULT
            CompassSource.ACCELEROMETER_AND_MAGNETOMETER -> CompassSensorMode.FUSION_ACCEL_MAG
            CompassSource.GAME_ROTATION_VECTOR_RELATIVE -> CompassSensorMode.GAME_ROTATION_VECTOR
            CompassSource.GYROSCOPE_RELATIVE -> CompassSensorMode.GYROSCOPE_RELATIVE
            CompassSource.UNSUPPORTED -> CompassSensorMode.AUTO_DEFAULT
        }

        fun areValuesValid(type: RequiredSensorType, values: FloatArray): Boolean {
            if (values.isEmpty()) return false
            for (v in values) {
                if (!v.isFinite()) return false
            }
            if (type == RequiredSensorType.MAGNETIC_FIELD) {
                if (values.size < 3) return false
                val mag = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
                if (mag < MAG_MIN_VALID_UT || mag > 5_000f) return false
            }
            return true
        }

        private fun shortestAngleDiff(fromDeg: Float, toDeg: Float): Float {
            return ((toDeg - fromDeg + 540f) % 360f) - 180f
        }

        private fun maxAxisDelta(a: FloatArray, b: FloatArray): Float {
            val count = minOf(a.size, b.size, 4)
            var maxD = 0f
            for (i in 0 until count) {
                val d = abs(a[i] - b[i])
                if (d > maxD) maxD = d
            }
            return maxD
        }

        private fun varyingThreshold(type: RequiredSensorType): Float = when (type) {
            RequiredSensorType.ROTATION_VECTOR -> 0.008f
            RequiredSensorType.MAGNETIC_FIELD -> 0.6f
            RequiredSensorType.ACCELEROMETER -> 0.25f
            RequiredSensorType.GYROSCOPE -> 0.08f
            else -> 0.05f
        }
    }
}
