package com.example.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.data.CompassSensorMode
import com.example.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Central repository coordinating hardware sensor detection, capability evaluation,
 * lifecycle-aware listener registration, and Android Location diagnostics.
 *
 * Designed for low-RAM, low-battery devices (baseline: Vivo Y21):
 * - Throttles UI telemetry snapshots (~150 ms) to prevent Compose over-recomposition.
 * - Immediately unregisters all sensor and location listeners when [stopObserving] is called.
 */
class SensorRepository(
    context: Context,
    private val provider: SensorManagerProvider = AndroidSensorManagerProvider(context),
    private val detector: SensorDetector = SensorDetector(provider),
    private val evaluator: SensorCapabilityEvaluator = SensorCapabilityEvaluator(),
    private val accuracyMonitor: SensorAccuracyMonitor = SensorAccuracyMonitor(),
    private val settingsRepository: SettingsRepository? = null
) {
    private val appContext = context.applicationContext

    private val healthMonitor = CompassHealthMonitor()

    private val compassController = CompassSensorController(
        provider = provider,
        accuracyMonitor = accuracyMonitor,
        onTelemetrySample = ::handleSensorSample,
        onHeadingComputed = { rawDeg, smoothedDeg, pitchDeg, accuracy ->
            healthMonitor.onHeadingComputed(rawDeg, smoothedDeg, pitchDeg, accuracy)
        }
    )

    private val levelController = LevelSensorController(
        provider = provider,
        accuracyMonitor = accuracyMonitor,
        onTelemetrySample = ::handleSensorSample
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var diagnosticTimerJob: Job? = null
    private var infoBannerTimerJob: Job? = null
    private var configuredSensorMode: CompassSensorMode = CompassSensorMode.AUTO_DEFAULT
    private var activeRecoveryOverride: CompassSource? = null
    private var activeRecoveryNote: String? = null

    val compassReadiness: StateFlow<CompassReadinessState> = compassController.readiness
    val levelReadiness: StateFlow<LevelReadinessState> = levelController.readiness
    val headingState: StateFlow<CompassHeadingState> = compassController.headingState
    val levelState: StateFlow<LevelTiltState> = levelController.levelState
    val compassDiagnosticsState: StateFlow<CompassDiagnosticsState> = healthMonitor.state

    private var resolvedSensors: Map<RequiredSensorType, Sensor> = emptyMap()
    private var isObservingActive = false
    private var isLocationRegistered = false
    private var lastTelemetryEmitMs = 0L
    private var latestTelemetry = LiveTelemetrySnapshot()

    private val _engineState: MutableStateFlow<SensorEngineState>

    init {
        val initialLocation = inspectLocationSnapshot(lastLocation = null)
        val initialDetection = detector.detect(
            accuracyMap = accuracyMonitor.accuracyBySensor.value,
            isObserving = false
        )
        resolvedSensors = initialDetection.resolvedSensors
        val initialCapabilities = evaluator.evaluate(
            specs = initialDetection.sensorSpecs,
            locationState = initialLocation,
            isObserving = false
        )
        _engineState = MutableStateFlow(
            SensorEngineState(
                deviceInfo = initialDetection.deviceInfo,
                sensorSpecs = initialDetection.sensorSpecs,
                capabilities = initialCapabilities,
                telemetry = LiveTelemetrySnapshot(),
                locationState = initialLocation,
                isObserving = false,
                lastScanMillis = System.currentTimeMillis()
            )
        )

        settingsRepository?.let { repo ->
            scope.launch {
                repo.settingsFlow
                    .map { it.compassSensorMode }
                    .distinctUntilChanged()
                    .collect { persistedMode ->
                        if (configuredSensorMode != persistedMode) {
                            configuredSensorMode = persistedMode
                            activeRecoveryOverride = overrideSourceForPersistedMode(persistedMode)
                            activeRecoveryNote = null
                            if (isObservingActive) {
                                applyActiveModeToControllers(notifyUser = false)
                            } else {
                                healthMonitor.onCompassSourceChanged(
                                    newSource = resolveEffectiveSource(persistedMode, resolvedSensors),
                                    sensorMode = persistedMode,
                                    availableSensors = resolvedSensors.keys
                                )
                            }
                        }
                    }
            }
        }
    }

    val engineState: StateFlow<SensorEngineState> = _engineState.asStateFlow()

    /**
     * Listener for auxiliary sensors not already held by Compass/Level controllers
     * (e.g. Gyroscope, Linear Acceleration, Pressure, or standalone Magnetometer/Accelerometer).
     */
    private val auxiliaryListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            val sensorEvent = event ?: return
            val type = RequiredSensorType.fromAndroidType(sensorEvent.sensor.type) ?: return
            accuracyMonitor.markActiveSampleReceived(type)
            handleSensorSample(type, sensorEvent.values.clone())
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            accuracyMonitor.onAccuracyChanged(sensor, accuracy)
            recomputeSpecsAndCapabilities()
        }
    }

    private val significantMotionListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            latestTelemetry = latestTelemetry.copy(
                significantMotionTriggeredCount = latestTelemetry.significantMotionTriggeredCount + 1,
                lastUpdatedMillis = System.currentTimeMillis()
            )
            _engineState.update { it.copy(telemetry = latestTelemetry) }
            // Re-arm one-shot trigger if still observing
            if (isObservingActive) {
                resolvedSensors[RequiredSensorType.SIGNIFICANT_MOTION]?.let { sensor ->
                    provider.requestTriggerSensor(this, sensor)
                }
            }
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            updateLocationSnapshot(location)
        }

        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) {
            refreshLocationStatusOnly()
        }

        override fun onProviderDisabled(provider: String) {
            refreshLocationStatusOnly()
        }
    }

    fun startObserving() {
        if (isObservingActive) return
        isObservingActive = true

        val detection = detector.detect(
            accuracyMap = accuracyMonitor.accuracyBySensor.value,
            isObserving = true
        )
        resolvedSensors = detection.resolvedSensors

        val hasLocationFix = _engineState.value.locationState.latitude != null
        val requestedOverride = activeRecoveryOverride ?: overrideSourceForPersistedMode(configuredSensorMode)
        val effectiveOverride = requestedOverride?.let { desired ->
            resolveGracefulSourceOverride(desired, resolvedSensors)
        }
        compassController.configureAndStart(
            resolvedSensors = resolvedSensors,
            hasLocationFix = hasLocationFix,
            sourceOverride = effectiveOverride,
            limitationNote = activeRecoveryNote
        )
        levelController.configureAndStart(resolvedSensors)

        val actualSource = compassController.readiness.value.source
        healthMonitor.onObservingStarted(
            availableSensors = resolvedSensors.keys,
            primaryCompassSensor = CompassHealthMonitor.primarySensorForSource(actualSource, resolvedSensors.keys),
            gameRotationVectorAvailable = provider.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null,
            activeSource = actualSource,
            sensorMode = configuredSensorMode
        )

        registerAuxiliaryDiagnosticSensors()

        resolvedSensors[RequiredSensorType.SIGNIFICANT_MOTION]?.let { triggerSensor ->
            provider.requestTriggerSensor(significantMotionListener, triggerSensor)
        }

        startLocationUpdatesIfPermitted()
        recomputeSpecsAndCapabilities()
    }

    private fun registerAuxiliaryDiagnosticSensors() {
        provider.unregisterListener(auxiliaryListener)
        // Register auxiliary diagnostic sensors once (avoiding double-registering sensors already claimed)
        val auxiliaryTypes = listOf(
            RequiredSensorType.ACCELEROMETER,
            RequiredSensorType.MAGNETIC_FIELD,
            RequiredSensorType.GYROSCOPE,
            RequiredSensorType.ROTATION_VECTOR,
            RequiredSensorType.GRAVITY,
            RequiredSensorType.LINEAR_ACCELERATION,
            RequiredSensorType.PRESSURE
        )
        auxiliaryTypes.forEach { type ->
            val sensor = resolvedSensors[type] ?: return@forEach
            val alreadyHandledByCompassOrLevel = isHandledByControllers(type)
            if (!alreadyHandledByCompassOrLevel) {
                provider.registerListener(auxiliaryListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    fun stopObserving() {
        if (!isObservingActive) return
        isObservingActive = false

        diagnosticTimerJob?.cancel()
        diagnosticTimerJob = null
        infoBannerTimerJob?.cancel()
        infoBannerTimerJob = null
        healthMonitor.onObservingStopped()
        compassController.stop()
        levelController.stop()
        provider.unregisterListener(auxiliaryListener)
        resolvedSensors[RequiredSensorType.SIGNIFICANT_MOTION]?.let { triggerSensor ->
            provider.cancelTriggerSensor(significantMotionListener, triggerSensor)
        }
        stopLocationUpdates()
        recomputeSpecsAndCapabilities()
    }

    fun refreshScan() {
        val currentLocation = _engineState.value.locationState
        val refreshedLocation = inspectLocationSnapshot(
            lastLocation = buildCachedLocationIfPresent(currentLocation)
        )
        val detection = detector.detect(
            accuracyMap = accuracyMonitor.accuracyBySensor.value,
            isObserving = isObservingActive
        )
        resolvedSensors = detection.resolvedSensors
        val capabilities = evaluator.evaluate(
            specs = detection.sensorSpecs,
            locationState = refreshedLocation,
            isObserving = isObservingActive
        )
        _engineState.update { state ->
            state.copy(
                deviceInfo = detection.deviceInfo,
                sensorSpecs = detection.sensorSpecs,
                capabilities = capabilities,
                locationState = refreshedLocation,
                isObserving = isObservingActive,
                lastScanMillis = System.currentTimeMillis()
            )
        }
        if (isObservingActive) {
            startLocationUpdatesIfPermitted()
        }
    }

    fun onPermissionResult() {
        refreshLocationStatusOnly()
        if (isObservingActive) {
            startLocationUpdatesIfPermitted()
        }
    }

    fun captureLevelReference() = levelController.captureReferenceOffset()

    fun clearLevelReference() = levelController.clearReferenceOffset()

    fun requestCompassDiagnosticCheck() = healthMonitor.requestCheckConfirmation()

    fun cancelCompassDiagnosticConfirmation() = healthMonitor.cancelCheckConfirmation()

    fun dismissCompassAnomalyBanner() = healthMonitor.dismissBanner()

    fun dismissInfoBanner() {
        infoBannerTimerJob?.cancel()
        infoBannerTimerJob = null
        healthMonitor.dismissInfoBanner()
    }

    fun dismissMagneticInterferenceBanner() = healthMonitor.dismissMagneticInterferenceBanner()

    fun dismissVerticalOrientationBanner() = healthMonitor.dismissVerticalOrientationBanner()

    fun selectCompassSensorMode(mode: CompassSensorMode) {
        diagnosticTimerJob?.cancel()
        configuredSensorMode = mode
        activeRecoveryOverride = overrideSourceForPersistedMode(mode)
        activeRecoveryNote = null
        settingsRepository?.let { repo ->
            scope.launch { repo.setCompassSensorMode(mode) }
        }
        if (isObservingActive) {
            applyActiveModeToControllers(notifyUser = true)
        } else {
            val resolved = resolveEffectiveSource(mode, resolvedSensors)
            healthMonitor.onCompassSourceChanged(resolved, mode, resolvedSensors.keys)
        }
    }

    fun startCompassDiagnosticCheck() {
        healthMonitor.startInteractiveCheck()
        diagnosticTimerJob?.cancel()
        diagnosticTimerJob = scope.launch {
            val steps = 20
            val stepDelay = CompassHealthMonitor.INTERACTIVE_TEST_DURATION_MS / steps
            repeat(steps) {
                delay(stepDelay)
                healthMonitor.evaluateTimeoutIfNeeded()
                if (healthMonitor.state.value.checkPhase == InteractiveCheckPhase.COMPLETED) {
                    applyRecommendedRecoveryIfNeeded()
                    return@launch
                }
            }
            if (healthMonitor.state.value.checkPhase == InteractiveCheckPhase.RUNNING) {
                healthMonitor.finalizeInteractiveCheck()
                applyRecommendedRecoveryIfNeeded()
            }
        }
    }

    fun resetCompassRecovery() {
        diagnosticTimerJob?.cancel()
        configuredSensorMode = CompassSensorMode.AUTO_DEFAULT
        activeRecoveryOverride = null
        activeRecoveryNote = null
        settingsRepository?.let { repo ->
            scope.launch { repo.setCompassSensorMode(CompassSensorMode.AUTO_DEFAULT) }
        }
        healthMonitor.clearRecoveryState()
        if (isObservingActive) {
            val hasLocationFix = _engineState.value.locationState.latitude != null
            compassController.configureAndStart(
                resolvedSensors = resolvedSensors,
                hasLocationFix = hasLocationFix
            )
            registerAuxiliaryDiagnosticSensors()
            val actual = compassController.readiness.value.source
            healthMonitor.onCompassSourceChanged(actual, CompassSensorMode.AUTO_DEFAULT, resolvedSensors.keys)
            triggerTimedInfoBanner(
                eventKey = "mode_restored_default",
                title = "Default Sensor Mode Restored",
                message = "Compass returned to ${actual.label}."
            )
        }
    }

    private fun applyRecommendedRecoveryIfNeeded() {
        val state = healthMonitor.state.value
        val recommended = state.activeRecoverySource
        if (recommended != null && recommended != compassController.readiness.value.source) {
            activeRecoveryOverride = recommended
            activeRecoveryNote = state.recoveryDisclosure
            if (recommended == CompassSource.ACCELEROMETER_AND_MAGNETOMETER) {
                configuredSensorMode = CompassSensorMode.FUSION_ACCEL_MAG
                settingsRepository?.let { repo ->
                    scope.launch { repo.setCompassSensorMode(CompassSensorMode.FUSION_ACCEL_MAG) }
                }
            }
            if (isObservingActive) {
                val hasLocationFix = _engineState.value.locationState.latitude != null
                compassController.configureAndStart(
                    resolvedSensors = resolvedSensors,
                    hasLocationFix = hasLocationFix,
                    sourceOverride = recommended,
                    limitationNote = state.recoveryDisclosure
                )
                registerAuxiliaryDiagnosticSensors()
                healthMonitor.onCompassSourceChanged(
                    newSource = compassController.readiness.value.source,
                    sensorMode = configuredSensorMode,
                    availableSensors = resolvedSensors.keys
                )
            }
            val bannerTitle = if (recommended == CompassSource.ACCELEROMETER_AND_MAGNETOMETER) {
                "Sensor Fusion Active"
            } else {
                "Fallback Mode Active"
            }
            val bannerMessage = state.recoveryDisclosure ?: "Switched to ${recommended.label}."
            triggerTimedInfoBanner(
                eventKey = "recovery_${recommended.name}",
                title = bannerTitle,
                message = bannerMessage
            )
        } else if (recommended == null && (activeRecoveryOverride != null || configuredSensorMode != CompassSensorMode.AUTO_DEFAULT)) {
            configuredSensorMode = CompassSensorMode.AUTO_DEFAULT
            activeRecoveryOverride = null
            activeRecoveryNote = null
            settingsRepository?.let { repo ->
                scope.launch { repo.setCompassSensorMode(CompassSensorMode.AUTO_DEFAULT) }
            }
            if (isObservingActive) {
                val hasLocationFix = _engineState.value.locationState.latitude != null
                compassController.configureAndStart(
                    resolvedSensors = resolvedSensors,
                    hasLocationFix = hasLocationFix
                )
                registerAuxiliaryDiagnosticSensors()
                healthMonitor.onCompassSourceChanged(
                    newSource = compassController.readiness.value.source,
                    sensorMode = CompassSensorMode.AUTO_DEFAULT,
                    availableSensors = resolvedSensors.keys
                )
            }
            triggerTimedInfoBanner(
                eventKey = "recovery_default_healthy",
                title = "Default Compass Healthy",
                message = "Primary orientation sensor is responsive; using ${compassController.readiness.value.source.label}."
            )
        } else if (recommended != null && isObservingActive) {
            // Already active on that source (e.g. Retest confirmed same fallback)
            healthMonitor.onCompassSourceChanged(
                newSource = compassController.readiness.value.source,
                sensorMode = configuredSensorMode,
                availableSensors = resolvedSensors.keys
            )
        }
    }

    private fun applyActiveModeToControllers(notifyUser: Boolean) {
        val hasLocationFix = _engineState.value.locationState.latitude != null
        val desiredOverride = activeRecoveryOverride ?: overrideSourceForPersistedMode(configuredSensorMode)
        val effectiveOverride = desiredOverride?.let { resolveGracefulSourceOverride(it, resolvedSensors) }
        compassController.configureAndStart(
            resolvedSensors = resolvedSensors,
            hasLocationFix = hasLocationFix,
            sourceOverride = effectiveOverride,
            limitationNote = activeRecoveryNote
        )
        registerAuxiliaryDiagnosticSensors()
        val actualSource = compassController.readiness.value.source
        healthMonitor.onCompassSourceChanged(actualSource, configuredSensorMode, resolvedSensors.keys)
        if (notifyUser) {
            triggerTimedInfoBanner(
                eventKey = "manual_mode_${configuredSensorMode.name}_${actualSource.name}",
                title = "Sensor Mode Updated",
                message = "Active source: ${actualSource.label}."
            )
        }
    }

    private fun triggerTimedInfoBanner(
        eventKey: String,
        title: String,
        message: String
    ) {
        val emitted = healthMonitor.showInfoBanner(
            eventKey = eventKey,
            title = title,
            message = message
        )
        if (emitted) {
            infoBannerTimerJob?.cancel()
            infoBannerTimerJob = scope.launch {
                delay(CompassHealthMonitor.INFO_BANNER_DURATION_MS)
                healthMonitor.dismissInfoBanner(eventKey)
            }
        }
    }

    private fun overrideSourceForPersistedMode(mode: CompassSensorMode): CompassSource? = when (mode) {
        CompassSensorMode.AUTO_DEFAULT -> null
        CompassSensorMode.ROTATION_VECTOR -> CompassSource.ROTATION_VECTOR
        CompassSensorMode.FUSION_ACCEL_MAG -> CompassSource.ACCELEROMETER_AND_MAGNETOMETER
        CompassSensorMode.GAME_ROTATION_VECTOR -> CompassSource.GAME_ROTATION_VECTOR_RELATIVE
        CompassSensorMode.GYROSCOPE_RELATIVE -> CompassSource.GYROSCOPE_RELATIVE
    }

    private fun resolveGracefulSourceOverride(
        desired: CompassSource,
        sensors: Map<RequiredSensorType, Sensor>
    ): CompassSource? {
        val hasRv = sensors.containsKey(RequiredSensorType.ROTATION_VECTOR)
        val hasAccelMag = sensors.containsKey(RequiredSensorType.ACCELEROMETER) &&
            sensors.containsKey(RequiredSensorType.MAGNETIC_FIELD)
        val hasGameRv = provider.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null
        val hasGyro = sensors.containsKey(RequiredSensorType.GYROSCOPE)

        return when (desired) {
            CompassSource.ROTATION_VECTOR -> if (hasRv) CompassSource.ROTATION_VECTOR else null
            CompassSource.ACCELEROMETER_AND_MAGNETOMETER -> if (hasAccelMag) CompassSource.ACCELEROMETER_AND_MAGNETOMETER else null
            CompassSource.GAME_ROTATION_VECTOR_RELATIVE -> if (hasGameRv) CompassSource.GAME_ROTATION_VECTOR_RELATIVE else null
            CompassSource.GYROSCOPE_RELATIVE -> if (hasGyro) CompassSource.GYROSCOPE_RELATIVE else null
            CompassSource.UNSUPPORTED -> null
        }
    }

    private fun resolveEffectiveSource(
        mode: CompassSensorMode,
        sensors: Map<RequiredSensorType, Sensor>
    ): CompassSource {
        val desired = overrideSourceForPersistedMode(mode)
        val resolvedOverride = desired?.let { resolveGracefulSourceOverride(it, sensors) }
        return resolvedOverride ?: when {
            sensors.containsKey(RequiredSensorType.ROTATION_VECTOR) -> CompassSource.ROTATION_VECTOR
            sensors.containsKey(RequiredSensorType.ACCELEROMETER) &&
                sensors.containsKey(RequiredSensorType.MAGNETIC_FIELD) -> CompassSource.ACCELEROMETER_AND_MAGNETOMETER
            else -> CompassSource.UNSUPPORTED
        }
    }

    private fun isHandledByControllers(type: RequiredSensorType): Boolean {
        val compassSrc = compassController.readiness.value.source
        val levelSrc = levelController.readiness.value.source
        val handledByCompass = when (compassSrc) {
            CompassSource.ROTATION_VECTOR ->
                type == RequiredSensorType.ROTATION_VECTOR || type == RequiredSensorType.MAGNETIC_FIELD
            CompassSource.ACCELEROMETER_AND_MAGNETOMETER ->
                type == RequiredSensorType.ACCELEROMETER || type == RequiredSensorType.MAGNETIC_FIELD
            CompassSource.GYROSCOPE_RELATIVE ->
                type == RequiredSensorType.GYROSCOPE
            CompassSource.GAME_ROTATION_VECTOR_RELATIVE,
            CompassSource.UNSUPPORTED -> false
        }
        val handledByLevel = when (levelSrc) {
            LevelSource.GRAVITY -> type == RequiredSensorType.GRAVITY
            LevelSource.ACCELEROMETER -> type == RequiredSensorType.ACCELEROMETER
            LevelSource.ROTATION_VECTOR -> type == RequiredSensorType.ROTATION_VECTOR
            LevelSource.UNSUPPORTED -> false
        }
        return handledByCompass || handledByLevel
    }

    private fun handleSensorSample(type: RequiredSensorType, values: FloatArray) {
        val now = System.currentTimeMillis()
        healthMonitor.onSample(type, values)
        val updatedTimestamps = latestTelemetry.sensorUpdateTimestamps + (type to now)
        latestTelemetry = when (type) {
            RequiredSensorType.MAGNETIC_FIELD -> {
                val x = values.getOrElse(0) { 0f }
                val y = values.getOrElse(1) { 0f }
                val z = values.getOrElse(2) { 0f }
                val magnitude = sqrt(x * x + y * y + z * z)
                latestTelemetry.copy(
                    magneticFieldMicroTesla = magnitude,
                    magneticAxes = AxisReading(x, y, z),
                    sensorUpdateTimestamps = updatedTimestamps,
                    lastUpdatedMillis = now
                )
            }
            RequiredSensorType.ACCELEROMETER -> {
                latestTelemetry.copy(
                    accelerometerAxes = AxisReading(
                        values.getOrElse(0) { 0f },
                        values.getOrElse(1) { 0f },
                        values.getOrElse(2) { 0f }
                    ),
                    sensorUpdateTimestamps = updatedTimestamps,
                    lastUpdatedMillis = now
                )
            }
            RequiredSensorType.GYROSCOPE -> {
                latestTelemetry.copy(
                    gyroscopeAxes = AxisReading(
                        values.getOrElse(0) { 0f },
                        values.getOrElse(1) { 0f },
                        values.getOrElse(2) { 0f }
                    ),
                    sensorUpdateTimestamps = updatedTimestamps,
                    lastUpdatedMillis = now
                )
            }
            RequiredSensorType.GRAVITY -> {
                latestTelemetry.copy(
                    gravityAxes = AxisReading(
                        values.getOrElse(0) { 0f },
                        values.getOrElse(1) { 0f },
                        values.getOrElse(2) { 0f }
                    ),
                    sensorUpdateTimestamps = updatedTimestamps,
                    lastUpdatedMillis = now
                )
            }
            RequiredSensorType.LINEAR_ACCELERATION -> {
                latestTelemetry.copy(
                    linearAccelerationAxes = AxisReading(
                        values.getOrElse(0) { 0f },
                        values.getOrElse(1) { 0f },
                        values.getOrElse(2) { 0f }
                    ),
                    sensorUpdateTimestamps = updatedTimestamps,
                    lastUpdatedMillis = now
                )
            }
            RequiredSensorType.ROTATION_VECTOR -> {
                latestTelemetry.copy(
                    rotationVectorComponents = values,
                    sensorUpdateTimestamps = updatedTimestamps,
                    lastUpdatedMillis = now
                )
            }
            RequiredSensorType.PRESSURE -> {
                val hpa = values.getOrElse(0) { 0f }
                val estAltitude = if (hpa > 0f) {
                    SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hpa).toDouble()
                } else {
                    null
                }
                latestTelemetry.copy(
                    pressureHpa = hpa,
                    barometricAltitudeEstimateMeters = estAltitude,
                    sensorUpdateTimestamps = updatedTimestamps,
                    lastUpdatedMillis = now
                )
            }
            RequiredSensorType.SIGNIFICANT_MOTION -> latestTelemetry
        }

        // Throttle Compose UI state emission to ~150 ms (~6.6 Hz) to keep Vivo Y21 smooth and cool
        if (now - lastTelemetryEmitMs >= TELEMETRY_UI_THROTTLE_MS) {
            lastTelemetryEmitMs = now
            val detection = detector.detect(
                accuracyMap = accuracyMonitor.accuracyBySensor.value,
                isObserving = isObservingActive
            )
            val capabilities = evaluator.evaluate(
                specs = detection.sensorSpecs,
                locationState = _engineState.value.locationState,
                isObserving = isObservingActive
            )
            _engineState.update { state ->
                state.copy(
                    sensorSpecs = detection.sensorSpecs,
                    capabilities = capabilities,
                    telemetry = latestTelemetry
                )
            }
        }
    }

    private fun recomputeSpecsAndCapabilities() {
        val detection = detector.detect(
            accuracyMap = accuracyMonitor.accuracyBySensor.value,
            isObserving = isObservingActive
        )
        val currentLocation = _engineState.value.locationState
        val capabilities = evaluator.evaluate(
            specs = detection.sensorSpecs,
            locationState = currentLocation,
            isObserving = isObservingActive
        )
        _engineState.update { state ->
            state.copy(
                deviceInfo = detection.deviceInfo,
                sensorSpecs = detection.sensorSpecs,
                capabilities = capabilities,
                telemetry = latestTelemetry,
                isObserving = isObservingActive
            )
        }
    }

    private fun refreshLocationStatusOnly() {
        val updatedLocation = inspectLocationSnapshot(lastLocation = null)
        val specs = _engineState.value.sensorSpecs
        val capabilities = evaluator.evaluate(
            specs = specs,
            locationState = updatedLocation,
            isObserving = isObservingActive
        )
        compassController.updateDeclination(computeDeclination(updatedLocation))
        _engineState.update { state ->
            state.copy(
                locationState = updatedLocation,
                capabilities = capabilities
            )
        }
    }

    private fun updateLocationSnapshot(location: Location) {
        val snapshot = inspectLocationSnapshot(lastLocation = location)
        val specs = _engineState.value.sensorSpecs
        val capabilities = evaluator.evaluate(
            specs = specs,
            locationState = snapshot,
            isObserving = isObservingActive
        )
        compassController.updateDeclination(computeDeclination(snapshot))
        _engineState.update { state ->
            state.copy(
                locationState = snapshot,
                capabilities = capabilities
            )
        }
    }

    private fun computeDeclination(snapshot: LocationDiagnosticSnapshot): Float? {
        val lat = snapshot.latitude ?: return null
        val lon = snapshot.longitude ?: return null
        val alt = snapshot.altitudeMeters ?: 0.0
        return try {
            GeomagneticField(
                lat.toFloat(),
                lon.toFloat(),
                alt.toFloat(),
                System.currentTimeMillis()
            ).declination
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdatesIfPermitted() {
        if (isLocationRegistered) return
        val permission = readPermissionState()
        if (permission == LocationPermissionState.DENIED) return

        val locationManager = provider.getLocationManager() ?: return
        if (!provider.isLocationEnabled()) return

        // Try reading last known location immediately for fast diagnostic feedback
        val bestLastKnown = pickBestLastKnownLocation(locationManager)
        if (bestLastKnown != null) {
            updateLocationSnapshot(bestLastKnown)
        }

        val targetProvider = when {
            permission == LocationPermissionState.GRANTED_FINE &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            else -> null
        } ?: return

        try {
            locationManager.requestLocationUpdates(
                targetProvider,
                LOCATION_MIN_TIME_MS,
                LOCATION_MIN_DISTANCE_M,
                locationListener,
                Looper.getMainLooper()
            )
            isLocationRegistered = true
        } catch (_: SecurityException) {
            isLocationRegistered = false
        } catch (_: IllegalArgumentException) {
            isLocationRegistered = false
        }
    }

    private fun stopLocationUpdates() {
        if (!isLocationRegistered) return
        try {
            provider.getLocationManager()?.removeUpdates(locationListener)
        } catch (_: SecurityException) {
            // Safe cleanup if permission was revoked mid-session
        } finally {
            isLocationRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun pickBestLastKnownLocation(locationManager: LocationManager): Location? {
        val candidates = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { providerName ->
            try {
                if (locationManager.allProviders.contains(providerName)) {
                    locationManager.getLastKnownLocation(providerName)
                } else {
                    null
                }
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        return candidates.maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private fun inspectLocationSnapshot(lastLocation: Location?): LocationDiagnosticSnapshot {
        val permissionState = readPermissionState()
        val locationManager = provider.getLocationManager()
        val isServiceEnabled = provider.isLocationEnabled()
        val providers = locationManager?.allProviders.orEmpty()

        val resolvedLocation = lastLocation ?: if (permissionState != LocationPermissionState.DENIED && locationManager != null) {
            pickBestLastKnownLocation(locationManager)
        } else {
            null
        }

        val status = when {
            permissionState == LocationPermissionState.DENIED -> DiagnosticStatus.PERMISSION_REQUIRED
            !isServiceEnabled -> DiagnosticStatus.UNAVAILABLE
            resolvedLocation != null -> DiagnosticStatus.AVAILABLE
            else -> DiagnosticStatus.INITIALIZING
        }

        val message = when (status) {
            DiagnosticStatus.PERMISSION_REQUIRED ->
                "Grant coarse or fine location permission to inspect coordinates and GNSS altitude."
            DiagnosticStatus.UNAVAILABLE ->
                "Location services are disabled in Android system settings."
            DiagnosticStatus.AVAILABLE ->
                "Fix acquired via ${resolvedLocation?.provider ?: "provider"}."
            DiagnosticStatus.INITIALIZING ->
                "Waiting for a GNSS or network location fix..."
            DiagnosticStatus.ACCURACY_UNRELIABLE ->
                "Location accuracy degraded."
        }

        val verticalAccuracy = if (
            resolvedLocation != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            resolvedLocation.hasVerticalAccuracy()
        ) {
            resolvedLocation.verticalAccuracyMeters
        } else {
            null
        }

        return LocationDiagnosticSnapshot(
            permissionState = permissionState,
            isLocationServiceEnabled = isServiceEnabled,
            availableProviders = providers,
            latitude = resolvedLocation?.latitude,
            longitude = resolvedLocation?.longitude,
            horizontalAccuracyMeters = if (resolvedLocation?.hasAccuracy() == true) resolvedLocation.accuracy else null,
            altitudeMeters = if (resolvedLocation?.hasAltitude() == true) resolvedLocation.altitude else null,
            verticalAccuracyMeters = verticalAccuracy,
            bearingDegrees = if (resolvedLocation?.hasBearing() == true) resolvedLocation.bearing else null,
            speedMetersPerSecond = if (resolvedLocation?.hasSpeed() == true) resolvedLocation.speed else null,
            providerName = resolvedLocation?.provider,
            fixTimestampMillis = resolvedLocation?.time,
            status = status,
            statusMessage = message
        )
    }

    private fun readPermissionState(): LocationPermissionState {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fineGranted) return LocationPermissionState.GRANTED_FINE

        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (coarseGranted) return LocationPermissionState.GRANTED_COARSE

        return LocationPermissionState.DENIED
    }

    private fun buildCachedLocationIfPresent(snapshot: LocationDiagnosticSnapshot): Location? {
        val lat = snapshot.latitude ?: return null
        val lon = snapshot.longitude ?: return null
        return Location(snapshot.providerName ?: LocationManager.PASSIVE_PROVIDER).apply {
            latitude = lat
            longitude = lon
            snapshot.horizontalAccuracyMeters?.let { accuracy = it }
            snapshot.altitudeMeters?.let { altitude = it }
            snapshot.fixTimestampMillis?.let { time = it }
        }
    }

    companion object {
        private const val TELEMETRY_UI_THROTTLE_MS = 150L
        private const val LOCATION_MIN_TIME_MS = 5_000L
        private const val LOCATION_MIN_DISTANCE_M = 5f
    }
}
