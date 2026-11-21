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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val accuracyMonitor: SensorAccuracyMonitor = SensorAccuracyMonitor()
) {
    private val appContext = context.applicationContext

    private val compassController = CompassSensorController(
        provider = provider,
        accuracyMonitor = accuracyMonitor,
        onTelemetrySample = ::handleSensorSample
    )

    private val levelController = LevelSensorController(
        provider = provider,
        accuracyMonitor = accuracyMonitor,
        onTelemetrySample = ::handleSensorSample
    )

    val compassReadiness: StateFlow<CompassReadinessState> = compassController.readiness
    val levelReadiness: StateFlow<LevelReadinessState> = levelController.readiness
    val headingState: StateFlow<CompassHeadingState> = compassController.headingState
    val levelState: StateFlow<LevelTiltState> = levelController.levelState

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
        compassController.configureAndStart(resolvedSensors, hasLocationFix)
        levelController.configureAndStart(resolvedSensors)

        // Register auxiliary diagnostic sensors once (avoiding double-registering sensors already claimed)
        val auxiliaryTypes = listOf(
            RequiredSensorType.ACCELEROMETER,
            RequiredSensorType.MAGNETIC_FIELD,
            RequiredSensorType.GYROSCOPE,
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

        resolvedSensors[RequiredSensorType.SIGNIFICANT_MOTION]?.let { triggerSensor ->
            provider.requestTriggerSensor(significantMotionListener, triggerSensor)
        }

        startLocationUpdatesIfPermitted()
        recomputeSpecsAndCapabilities()
    }

    fun stopObserving() {
        if (!isObservingActive) return
        isObservingActive = false

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

    private fun isHandledByControllers(type: RequiredSensorType): Boolean {
        val compassSrc = compassController.readiness.value.source
        val levelSrc = levelController.readiness.value.source
        val handledByCompass = when (compassSrc) {
            CompassSource.ROTATION_VECTOR ->
                type == RequiredSensorType.ROTATION_VECTOR || type == RequiredSensorType.MAGNETIC_FIELD
            CompassSource.ACCELEROMETER_AND_MAGNETOMETER ->
                type == RequiredSensorType.ACCELEROMETER || type == RequiredSensorType.MAGNETIC_FIELD
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
        latestTelemetry = when (type) {
            RequiredSensorType.MAGNETIC_FIELD -> {
                val x = values.getOrElse(0) { 0f }
                val y = values.getOrElse(1) { 0f }
                val z = values.getOrElse(2) { 0f }
                val magnitude = sqrt(x * x + y * y + z * z)
                latestTelemetry.copy(
                    magneticFieldMicroTesla = magnitude,
                    magneticAxes = AxisReading(x, y, z),
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
                    lastUpdatedMillis = now
                )
            }
            RequiredSensorType.ROTATION_VECTOR -> {
                latestTelemetry.copy(
                    rotationVectorComponents = values,
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
